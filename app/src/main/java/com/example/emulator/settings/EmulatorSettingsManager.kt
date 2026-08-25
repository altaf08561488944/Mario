package com.example.emulator.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmulatorSettings(
    val targetFps: Int = 34, // 15 - 60 FPS
    val enableVsync: Boolean = true,
    val resolutionScale: Float = 1.0f, // 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x
    val frameSkip: Int = 0, // 0 = Off, 1 = Skip 1, 2 = Skip 2, 3 = Skip 3, -1 = Auto Frame Skip
    val isDockedMode: Boolean = true,
    val cpuAccuracy: CpuAccuracy = CpuAccuracy.AUTO,
    val graphicsBackend: GraphicsBackend = GraphicsBackend.VULKAN,
    val audioBackend: AudioBackend = AudioBackend.AAUDIO,
    val asynchronousShaders: Boolean = true,
    val anisotropicFiltering: AnisotropicFiltering = AnisotropicFiltering.X4,
    val diskShaderCache: Boolean = true
)

enum class CpuAccuracy(val label: String, val description: String) {
    AUTO("Auto", "Dynamically balances execution speed and cycle accuracy"),
    FAST("Fast", "Maximum JIT compilation speed, minor timing glitches"),
    ACCURATE("Accurate", "Strict ARM64 instruction cycle precision"),
    PARANOID("Paranoid", "Deep register and memory debugging inspection")
}

enum class AudioBackend(val label: String) {
    AAUDIO("AAudio"),
    OBOE("Oboe"),
    NULL("Mute (Null)")
}

enum class GraphicsBackend(val label: String, val description: String) {
    VULKAN("Vulkan (Native)", "Direct hardware acceleration layer for Maxwell GM20B GPU"),
    OPENGL_ES("OpenGL ES", "Compatibility fallback rendering layer")
}

enum class AnisotropicFiltering(val label: String, val multiplier: Int) {
    OFF("Off", 1),
    X2("2x", 2),
    X4("4x", 4),
    X8("8x", 8),
    X16("16x", 16)
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
            frameSkip = prefs.getInt("frameSkip", 0),
            isDockedMode = prefs.getBoolean("isDockedMode", true),
            cpuAccuracy = try {
                CpuAccuracy.valueOf(prefs.getString("cpuAccuracy", CpuAccuracy.AUTO.name) ?: CpuAccuracy.AUTO.name)
            } catch (e: Exception) { CpuAccuracy.AUTO },
            graphicsBackend = try {
                GraphicsBackend.valueOf(prefs.getString("graphicsBackend", GraphicsBackend.VULKAN.name) ?: GraphicsBackend.VULKAN.name)
            } catch (e: Exception) { GraphicsBackend.VULKAN },
            audioBackend = try {
                AudioBackend.valueOf(prefs.getString("audioBackend", AudioBackend.AAUDIO.name) ?: AudioBackend.AAUDIO.name)
            } catch (e: Exception) { AudioBackend.AAUDIO },
            asynchronousShaders = prefs.getBoolean("asynchronousShaders", true),
            anisotropicFiltering = try {
                AnisotropicFiltering.valueOf(prefs.getString("anisotropicFiltering", AnisotropicFiltering.X4.name) ?: AnisotropicFiltering.X4.name)
            } catch (e: Exception) { AnisotropicFiltering.X4 },
            diskShaderCache = prefs.getBoolean("diskShaderCache", true)
        )
    }

    fun updateSettings(newSettings: EmulatorSettings) {
        prefs.edit().apply {
            putInt("targetFps", newSettings.targetFps)
            putBoolean("enableVsync", newSettings.enableVsync)
            putFloat("resolutionScale", newSettings.resolutionScale)
            putInt("frameSkip", newSettings.frameSkip)
            putBoolean("isDockedMode", newSettings.isDockedMode)
            putString("cpuAccuracy", newSettings.cpuAccuracy.name)
            putString("graphicsBackend", newSettings.graphicsBackend.name)
            putString("audioBackend", newSettings.audioBackend.name)
            putBoolean("asynchronousShaders", newSettings.asynchronousShaders)
            putString("anisotropicFiltering", newSettings.anisotropicFiltering.name)
            putBoolean("diskShaderCache", newSettings.diskShaderCache)
        }.apply()

        _settings.value = newSettings
    }
}

