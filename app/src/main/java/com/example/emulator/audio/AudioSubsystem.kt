package com.example.emulator.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Switch Audio Subsystem.
 * Receives PCM Audio Output buffers from the Horizon 'audout' service,
 * performs sample-rate conversion/mixing, and plays via Android AudioTrack.
 */
class AudioSubsystem {
    
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 48000
    private val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun initialize() {
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize * 2,
            AudioTrack.MODE_STREAM
        )
        audioTrack?.play()
    }

    /**
     * Queues an audio buffer submitted by the game engine.
     */
    fun appendAudioBuffer(pcmData: ByteArray) {
        // In a complex implementation, this handles ring-buffering,
        // latency management, and mixing down 5.1 to Stereo if needed.
        audioTrack?.write(pcmData, 0, pcmData.size)
    }

    fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
    }
}
