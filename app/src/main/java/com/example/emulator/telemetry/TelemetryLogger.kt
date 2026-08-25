package com.example.emulator.telemetry


import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelemetryLogger {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private val sessionLogs = JSONArray()
    private val sessionStartTime = SystemClock.elapsedRealtime()

    fun startLogging() {
        if (job?.isActive == true) return
        sessionLogs.put(JSONObject().apply {
            put("event", "SESSION_START")
            put("timestamp", getCurrentTime())
        })
        
        job = scope.launch {
            while (isActive) {
                logTelemetryTick()
                delay(1000) // Log every second
            }
        }
    }

    fun stopLoggingAndExport() {
        job?.cancel()
        job = null
        
        sessionLogs.put(JSONObject().apply {
            put("event", "SESSION_END")
            put("timestamp", getCurrentTime())
            put("duration_ms", SystemClock.elapsedRealtime() - sessionStartTime)
        })
        
        exportToJsonFile()
    }

    private fun logTelemetryTick() {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        
        val tick = JSONObject().apply {
            put("event", "TELEMETRY_TICK")
            put("timestamp", getCurrentTime())
            put("memory_used_mb", usedMem)
            put("memory_max_mb", maxMem)
            put("cpu_temp_c", readCpuTemperature())
            // Note: FPS is normally logged from the graphics thread
        }
        
        synchronized(sessionLogs) {
            sessionLogs.put(tick)
        }
    }

    fun logFps(fps: Float) {
        synchronized(sessionLogs) {
            sessionLogs.put(JSONObject().apply {
                put("event", "FPS_UPDATE")
                put("timestamp", getCurrentTime())
                put("fps", fps)
            })
        }
    }

    private fun readCpuTemperature(): Float {
        // Simple heuristic to read thermal zones in Android
        val thermalFiles = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp"
        )
        for (path in thermalFiles) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val tempStr = file.readText().trim()
                    val tempInt = tempStr.toIntOrNull() ?: continue
                    // Some kernels report in millidegrees
                    return if (tempInt > 1000) tempInt / 1000f else tempInt.toFloat()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return 0f // Fallback if inaccessible
    }

    private fun exportToJsonFile() {
        try {
            val fileName = "telemetry_session_${System.currentTimeMillis()}.json"
            val file = File(System.getProperty("java.io.tmpdir"), fileName)
            val writer = FileWriter(file)
            writer.write(sessionLogs.toString(4))
            writer.flush()
            writer.close()
            Log.i("TelemetryLogger", "Exported telemetry JSON to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("TelemetryLogger", "Failed to export telemetry JSON", e)
        }
    }

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return sdf.format(Date())
    }
}
