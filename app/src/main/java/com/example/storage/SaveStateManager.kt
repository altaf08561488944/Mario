package com.example.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.entity.SaveStateEntity
import com.example.emulator.SwitchCoreState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object SaveStateManager {

    private const val HEADER_MAGIC = "SWTC_SWS_V1\n"

    fun getSaveStatesDirectory(context: Context): File {
        val dir = File(context.filesDir, "savestates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun createSaveState(
        context: Context,
        gameTitle: String,
        titleId: String,
        slotName: String,
        coreState: SwitchCoreState?,
        slotIndex: Int = 0,
        isAutoSave: Boolean = false
    ): SaveStateEntity = withContext(Dispatchers.IO) {
        val dir = getSaveStatesDirectory(context)
        val stateId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val sanitizedTitle = gameTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val stateFileName = "state_${sanitizedTitle}_${titleId}_slot${slotIndex}_$timestamp.sws"
        val stateFile = File(dir, stateFileName)

        // Generate screenshot thumbnail
        val previewFileName = "thumb_$stateId.jpg"
        val previewFile = File(dir, previewFileName)
        val capturedBitmap = coreState?.frameBitmap ?: generatePlaceholderScreenshot(gameTitle, slotName)

        FileOutputStream(previewFile).use { out ->
            capturedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }

        val pcHex = coreState?.cpuCores?.firstOrNull()?.pc?.let { "0x" + java.lang.Long.toHexString(it) } ?: "0x7100041280"
        val totalInst = coreState?.cpuCores?.sumOf { it.instructionsExecuted } ?: 1_250_000L
        val isDocked = coreState?.isDockedMode ?: true
        val fps = coreState?.fps ?: 60

        val coreSummary = "Core 0 @ $pcHex • ${fps} FPS • ${if (isDocked) "1080p Docked" else "720p Handheld"}"

        // Build .sws binary content
        val metadataHeader = buildString {
            append(HEADER_MAGIC)
            append("ID=$stateId\n")
            append("TITLE=$gameTitle\n")
            append("TITLE_ID=$titleId\n")
            append("SLOT_NAME=$slotName\n")
            append("SLOT_INDEX=$slotIndex\n")
            append("TIMESTAMP=$timestamp\n")
            append("IS_AUTO_SAVE=$isAutoSave\n")
            append("PC=$pcHex\n")
            append("INSTRUCTIONS=$totalInst\n")
            append("DOCKED=$isDocked\n")
            append("FPS=$fps\n")
            append("---PAYLOAD_BEGIN---\n")
        }

        FileOutputStream(stateFile).use { fos ->
            fos.write(metadataHeader.toByteArray(StandardCharsets.UTF_8))
            // Write simulated guest memory snapshot dump
            val mockHeapBlock = ByteArray(1024 * 64) { (it % 256).toByte() }
            fos.write(mockHeapBlock)
        }

        val entity = SaveStateEntity(
            id = stateId,
            titleId = titleId,
            gameTitle = gameTitle,
            slotName = slotName,
            fileName = stateFileName,
            filePath = stateFile.absolutePath,
            previewImagePath = previewFile.absolutePath,
            slotIndex = slotIndex,
            timestamp = timestamp,
            sizeBytes = stateFile.length(),
            isAutoSave = isAutoSave,
            coreSummary = coreSummary,
            instructionsExecuted = totalInst,
            isDocked = isDocked
        )
        entity
    }

    suspend fun deleteStateFiles(state: SaveStateEntity) = withContext(Dispatchers.IO) {
        val file = File(state.filePath)
        if (file.exists()) file.delete()

        state.previewImagePath?.let { path ->
            val preview = File(path)
            if (preview.exists()) preview.delete()
        }
    }

    suspend fun exportStateToUri(context: Context, state: SaveStateEntity, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(state.filePath)
            if (!sourceFile.exists()) return@withContext false

            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createShareIntent(context: Context, state: SaveStateEntity): Intent? {
        val file = File(state.filePath)
        if (!file.exists()) return null

        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Save State: ${state.gameTitle} - ${state.slotName}")
            putExtra(Intent.EXTRA_TEXT, "Switch Virtual Save State (.sws) for ${state.gameTitle} (${state.slotName})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun importStateFromUri(context: Context, sourceUri: Uri): SaveStateEntity? = withContext(Dispatchers.IO) {
        try {
            val dir = getSaveStatesDirectory(context)
            val tempFile = File(dir, "temp_import_${System.currentTimeMillis()}.sws")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            // Parse metadata from header
            val content = tempFile.readText(StandardCharsets.UTF_8).take(2048)
            var gameTitle = "Imported Game"
            var titleId = "0100000000000000"
            var slotName = "Imported Save State"
            var timestamp = System.currentTimeMillis()
            var isAutoSave = false
            var pc = "0x7100040000"
            var totalInst = 500_000L
            var isDocked = true

            content.lines().forEach { line ->
                when {
                    line.startsWith("TITLE=") -> gameTitle = line.removePrefix("TITLE=")
                    line.startsWith("TITLE_ID=") -> titleId = line.removePrefix("TITLE_ID=")
                    line.startsWith("SLOT_NAME=") -> slotName = line.removePrefix("SLOT_NAME=")
                    line.startsWith("TIMESTAMP=") -> timestamp = line.removePrefix("TIMESTAMP=").toLongOrNull() ?: timestamp
                    line.startsWith("IS_AUTO_SAVE=") -> isAutoSave = line.removePrefix("IS_AUTO_SAVE=").toBoolean()
                    line.startsWith("PC=") -> pc = line.removePrefix("PC=")
                    line.startsWith("INSTRUCTIONS=") -> totalInst = line.removePrefix("INSTRUCTIONS=").toLongOrNull() ?: totalInst
                    line.startsWith("DOCKED=") -> isDocked = line.removePrefix("DOCKED=").toBoolean()
                }
            }

            val stateId = UUID.randomUUID().toString()
            val finalFileName = "state_${gameTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}_${titleId}_$timestamp.sws"
            val finalFile = File(dir, finalFileName)
            tempFile.renameTo(finalFile)

            val previewFileName = "thumb_$stateId.jpg"
            val previewFile = File(dir, previewFileName)
            val previewBmp = generatePlaceholderScreenshot(gameTitle, slotName)
            FileOutputStream(previewFile).use { out ->
                previewBmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            SaveStateEntity(
                id = stateId,
                titleId = titleId,
                gameTitle = gameTitle,
                slotName = slotName,
                fileName = finalFileName,
                filePath = finalFile.absolutePath,
                previewImagePath = previewFile.absolutePath,
                slotIndex = 1,
                timestamp = timestamp,
                sizeBytes = finalFile.length(),
                isAutoSave = isAutoSave,
                coreSummary = "Core 0 @ $pc • 60 FPS • ${if (isDocked) "1080p Docked" else "720p Handheld"}",
                instructionsExecuted = totalInst,
                isDocked = isDocked
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generatePlaceholderScreenshot(gameTitle: String, slotName: String): Bitmap {
        val width = 640
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw futuristic dark gradient backdrop
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(18, 22, 32)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Grid lines
        paint.color = Color.argb(40, 0, 229, 255)
        paint.strokeWidth = 2f
        for (x in 0..width step 40) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        }
        for (y in 0..height step 40) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
        }

        // Center card banner
        paint.color = Color.argb(200, 26, 32, 48)
        canvas.drawRoundRect(60f, 60f, width - 60f, height - 60f, 20f, 20f, paint)

        // Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(0, 229, 255)
        canvas.drawRoundRect(60f, 60f, width - 60f, height - 60f, 20f, 20f, paint)

        // Title text
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(gameTitle.take(28), width / 2f, 140f, paint)

        // Slot text
        paint.color = Color.rgb(0, 230, 118)
        paint.textSize = 20f
        paint.isFakeBoldText = false
        canvas.drawText(slotName, width / 2f, 190f, paint)

        // Status text
        paint.color = Color.rgb(255, 61, 0)
        paint.textSize = 16f
        canvas.drawText("SWTC HORIZON OS SAVE SNAPSHOT", width / 2f, 240f, paint)

        return bitmap
    }
}
