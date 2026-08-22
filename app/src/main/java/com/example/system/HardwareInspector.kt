package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile

data class RealHardwareInfo(
    val deviceName: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val cpuArchitecture: String,
    val cpuHardwareName: String,
    val cpuCoreCount: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowRam: Boolean,
    val internalStorageTotalGb: Double,
    val internalStorageAvailableGb: Double,
    val hasVulkanSupport: Boolean,
    val openGlEsVersion: String,
    val isAndroid10Plus: Boolean
)

object HardwareInspector {

    fun inspectDevice(context: Context): RealHardwareInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val availableRamMb = memoryInfo.availMem / (1024 * 1024)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalStorageBytes = statFs.blockCountLong * statFs.blockSizeLong
        val availableStorageBytes = statFs.availableBlocksLong * statFs.blockSizeLong

        val internalStorageTotalGb = totalStorageBytes / (1024.0 * 1024.0 * 1024.0)
        val internalStorageAvailableGb = availableStorageBytes / (1024.0 * 1024.0 * 1024.0)

        val packageManager = context.packageManager
        val hasVulkan = packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        
        val glEsVersion = activityManager.deviceConfigurationInfo.glEsVersion ?: "3.2"

        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuName = getCpuProcessorName()

        val abis = Build.SUPPORTED_ABIS.joinToString(", ")

        return RealHardwareInfo(
            deviceName = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE ?: "10.0",
            apiLevel = Build.VERSION.SDK_INT,
            cpuArchitecture = abis,
            cpuHardwareName = cpuName,
            cpuCoreCount = cpuCores,
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isLowRam = memoryInfo.lowMemory,
            internalStorageTotalGb = internalStorageTotalGb,
            internalStorageAvailableGb = internalStorageAvailableGb,
            hasVulkanSupport = hasVulkan,
            openGlEsVersion = glEsVersion,
            isAndroid10Plus = Build.VERSION.SDK_INT >= 29
        )
    }

    private fun getCpuProcessorName(): String {
        return try {
            val cpuInfo = File("/proc/cpuinfo")
            if (cpuInfo.exists()) {
                var processorName = ""
                cpuInfo.forEachLine { line ->
                    if (line.startsWith("Hardware", ignoreCase = true) ||
                        line.startsWith("Processor", ignoreCase = true) ||
                        line.startsWith("model name", ignoreCase = true)
                    ) {
                        val parts = line.split(":")
                        if (parts.size > 1) {
                            processorName = parts[1].trim()
                        }
                    }
                }
                if (processorName.isNotEmpty()) return processorName
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL
            } else {
                Build.HARDWARE
            }
        } catch (e: Exception) {
            Build.HARDWARE ?: "ARMv8 Processor"
        }
    }
}
