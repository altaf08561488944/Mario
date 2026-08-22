package com.example.emulator

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class EmulatorTargetInfo(
    val packageName: String,
    val appName: String,
    val isInstalled: Boolean,
    val iconRes: Int? = null
)

object TargetEmulatorManager {

    val KNOWN_EMULATORS = listOf(
        EmulatorTargetInfo("org.skyline.emu", "Skyline Emulator", false),
        EmulatorTargetInfo("org.stratoemu.strato", "Strato / Skyline Next", false),
        EmulatorTargetInfo("org.yuzu.yuzu_emu", "Yuzu Emulator", false),
        EmulatorTargetInfo("org.suyu.suyu_emu", "Suyu Emulator", false),
        EmulatorTargetInfo("org.sudachi.sudachi_emu", "Sudachi Emulator", false),
        EmulatorTargetInfo("com.eggns.emu", "Egg NS Emulator", false)
    )

    fun getInstalledTargetEmulators(context: Context): List<EmulatorTargetInfo> {
        val pm = context.packageManager
        return KNOWN_EMULATORS.map { target ->
            val isInstalled = try {
                pm.getPackageInfo(target.packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
            target.copy(isInstalled = isInstalled)
        }
    }

    fun launchEmulatorApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
