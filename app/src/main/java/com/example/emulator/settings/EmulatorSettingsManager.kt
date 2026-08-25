package com.example.emulator.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmulatorSettings(
    val targetFps: Int = 34, // User requested 34-50
    val enableVsync: Boolean = true,
    val resolutionScale: Float = 1.0f,
    val isDockedMode: Boolean = true,
    val cpuAccuracy: CpuAccuracy = CpuAccuracy.AUTO,
    val audioBackend: AudioBackend = AudioBackend.AAUDIO,
    val asynchronousShaders: Boolean = true
)

enum class CpuAccuracy {
    AUTO, FAST, ACCURATE, PARANOID
}

enum class AudioBackend {
    AAUDIO, OBOE, NULL
}

class EmulatorSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SwtcEmulatorSettings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<EmulatorSettings> = _settings.asStateFlow()

    private fun loadSettings(): EmulatorSettings {
        return EmulatorSettings(
            targetFps = prefs.getInt("targetFps", 34),
            enableVsync = prefs.getBoolean("enableVsync", true),
            resolutionScale = prefs.getFloat("resolutionScale", 1.0f),
            isDockedMode = prefs.getBoolean("isDockedMode", true),
            cpuAccuracy = CpuAccuracy.valueOf(prefs.getString("cpuAccuracy", CpuAccuracy.AUTO.name) ?: CpuAccuracy.AUTO.name),
            audioBackend = AudioBackend.valueOf(prefs.getString("audioBackend", AudioBackend.AAUDIO.name) ?: AudioBackend.AAUDIO.name),
            asynchronousShaders = prefs.getBoolean("asynchronousShaders", true)
        )
    }

    fun updateSettings(newSettings: EmulatorSettings) {
        prefs.edit().apply {
            putInt("targetFps", newSettings.targetFps)
            putBoolean("enableVsync", newSettings.enableVsync)
            putFloat("resolutionScale", newSettings.resolutionScale)
            putBoolean("isDockedMode", newSettings.isDockedMode)
            putString("cpuAccuracy", newSettings.cpuAccuracy.name)
            putString("audioBackend", newSettings.audioBackend.name)
            putBoolean("asynchronousShaders", newSettings.asynchronousShaders)
        }.apply()
        
        _settings.value = newSettings
    }
}
