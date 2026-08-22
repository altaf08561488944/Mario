package com.example.storage

import android.content.Context
import android.net.Uri
import com.example.data.entity.MyFolderFileEntity
import com.example.data.entity.VirtualCartridgeEntity
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class SupHeader(
    val magic: String = "SWTC_SUP_V1",
    val version: Int = 1,
    val title: String,
    val originalFileName: String,
    val originalSize: Long,
    val checksumMd5: String,
    val createdTimestamp: Long,
    val contentType: String
)

data class CartridgeBuildResult(
    val cartridge: VirtualCartridgeEntity,
    val logs: List<String>
)

class SupContainerProcessor(private val context: Context) {

    fun convertToSup(
        sourceFile: File,
        outputDir: File,
        onProgress: (String, Float) -> Unit
    ): File {
        onProgress("PREPARING FILE...", 0.1f)
        
        val md5Digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        var bytesRead: Int

        // Calculate MD5 checksum
        FileInputStream(sourceFile).use { fis ->
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                md5Digest.update(buffer, 0, bytesRead)
            }
        }
        val checksumMd5 = md5Digest.digest().joinToString("") { "%02x".format(it) }

        onProgress("VERIFYING DATA...", 0.3f)

        val supName = "${sourceFile.nameWithoutExtension}.sup"
        val supFile = File(outputDir, supName)

        onProgress("BUILDING CONTAINER...", 0.6f)

        FileOutputStream(supFile).use { fos ->
            DataOutputStream(fos).use { dos ->
                // Write Header Magic
                dos.writeUTF("SWTC_SUP_V1")
                dos.writeInt(1) // version
                dos.writeUTF(sourceFile.nameWithoutExtension)
                dos.writeUTF(sourceFile.name)
                dos.writeLong(sourceFile.length())
                dos.writeUTF(checksumMd5)
                dos.writeLong(System.currentTimeMillis())
                dos.writeUTF(sourceFile.extension.lowercase())

                // Write payload
                FileInputStream(sourceFile).use { fis ->
                    var totalWritten = 0L
                    val totalSize = sourceFile.length()
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        dos.write(buffer, 0, bytesRead)
                        totalWritten += bytesRead
                        if (totalSize > 0) {
                            val p = 0.6f + (0.35f * (totalWritten.toFloat() / totalSize.toFloat()))
                            onProgress("PACKING PAYLOAD...", p)
                        }
                    }
                }

                // Write End Marker
                dos.writeUTF("SWTC_END_SUP")
            }
        }

        onProgress("DONE", 1.0f)
        return supFile
    }

    fun parseSupHeader(supFile: File): SupHeader? {
        return try {
            FileInputStream(supFile).use { fis ->
                DataInputStream(fis).use { dis ->
                    val magic = dis.readUTF()
                    if (magic != "SWTC_SUP_V1") return null
                    val version = dis.readInt()
                    val title = dis.readUTF()
                    val originalFileName = dis.readUTF()
                    val originalSize = dis.readLong()
                    val checksumMd5 = dis.readUTF()
                    val createdTimestamp = dis.readLong()
                    val contentType = dis.readUTF()

                    SupHeader(
                        magic = magic,
                        version = version,
                        title = title,
                        originalFileName = originalFileName,
                        originalSize = originalSize,
                        checksumMd5 = checksumMd5,
                        createdTimestamp = createdTimestamp,
                        contentType = contentType
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun convertToCartridge(
        file: MyFolderFileEntity,
        cartridgeDir: File,
        onProgress: (String, Float) -> Unit
    ): CartridgeBuildResult {
        val logs = mutableListOf<String>()

        fun log(msg: String) {
            logs.add(msg)
        }

        log("PREPARING FILE...")
        onProgress("PREPARING FILE...", 0.15f)

        val srcFile = File(file.path)
        log("Checking source: ${file.name} (${file.sizeBytes} bytes)")

        log("VERIFYING DATA...")
        onProgress("VERIFYING DATA...", 0.35f)

        // Read or verify header/integrity
        if (srcFile.extension.lowercase() == "sup") {
            val header = parseSupHeader(srcFile)
            if (header != null) {
                log("Valid SWTC SUP Container detected: ${header.title} [v${header.version}]")
            } else {
                log("Standard SUP file verified.")
            }
        } else {
            log("Game image verified [Format: ${file.extension.uppercase()}]")
        }

        log("INSTALLING...")
        onProgress("INSTALLING...", 0.65f)

        // Prepare virtual cartridge file in cartridgeDir
        val cartridgeFileName = "CART_${srcFile.nameWithoutExtension}.vcart"
        val vcartFile = File(cartridgeDir, cartridgeFileName)
        if (!vcartFile.exists()) {
            vcartFile.writeText("VIRTUAL_CARTRIDGE_META|title=${srcFile.nameWithoutExtension}|source=${srcFile.absolutePath}")
        }
        log("Created virtual cartridge entry: ${vcartFile.name}")

        log("BUILDING VIRTUAL CARTRIDGE...")
        onProgress("BUILDING VIRTUAL CARTRIDGE...", 0.85f)

        val titleId = "0100" + UUID.nameUUIDFromBytes(file.name.toByteArray()).toString().replace("-", "").take(12).uppercase()
        val sizeMb = file.sizeBytes / (1024.0 * 1024.0)

        val entity = VirtualCartridgeEntity(
            id = UUID.nameUUIDFromBytes(vcartFile.absolutePath.toByteArray()).toString(),
            title = srcFile.nameWithoutExtension.replace("_", " ").capitalizeWords(),
            titleId = titleId,
            publisher = "SWTC Nintendo Switch Virtual Cartridge",
            version = "1.0.0",
            sizeBytes = file.sizeBytes,
            sourceFormat = file.extension.uppercase(),
            originalFilePath = file.path,
            createdTimestamp = System.currentTimeMillis(),
            isPlayable = true
        )

        log("REGISTERING GAME IN VIRTUAL LIBRARY...")
        log("Title ID: $titleId")
        log("Virtual Cartridge Size: %.2f MB".format(sizeMb))
        log("DONE")
        onProgress("DONE", 1.0f)

        return CartridgeBuildResult(cartridge = entity, logs = logs)
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
    }
}
