package com.example.emulator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-Performance Low-Latency Audio Subsystem for SWTC NOOS.
 * Emulates the audio DSP by receiving PCM streams from the guest game,
 * handling buffering, and pushing to Android's hardware mixer.
 * 
 * Note: Uses AudioTrack with PERFORMANCE_MODE_LOW_LATENCY which leverages 
 * the AAudio / FastMixer native backend in modern Android SDKs.
 */
class AudioSubsystem {
    
    // Switch native audio is typically 48000 Hz, stereo, 16-bit PCM
    private val targetSampleRate = 48000
    private val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private var audioThread: Thread? = null

    // Concurrent Queue for buffering PCM data between Emulator Thread and Audio Thread
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    
    // Max buffered frames to prevent audio delay (Latency Control)
    // If the queue exceeds this, we drop the oldest frames to stay real-time.
    private val MAX_QUEUE_SIZE = 10 

    fun initialize() {
        if (isPlaying.get()) return

        val minBufferSize = AudioTrack.getMinBufferSize(targetSampleRate, channelConfig, audioFormat)
        
        // Configure AudioTrack for Low Latency (AAudio backend path)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(targetSampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioTrack?.play()
        isPlaying.set(true)

        // Start dedicated high-priority audio mixing thread
        audioThread = Thread {
            // CRITICAL: Prevent OS scheduler from preempting audio thread, reducing crackling
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            audioMixingLoop()
        }.apply { 
            name = "SWTC_Audio_Thread"
            start() 
        }
        
        Log.d("AudioSubsystem", "High-Performance Low-Latency Audio Initialized.")
    }

    /**
     * Dedicated hardware mixing loop. Runs completely independent of CPU/GPU threads.
     */
    private fun audioMixingLoop() {
        val silenceBuffer = ByteArray(4096) // Fallback silence to prevent popping
        
        while (isPlaying.get()) {
            val track = audioTrack ?: break
            
            // Fetch next PCM buffer from the emulation engine
            val pcmData = audioQueue.poll()
            
            if (pcmData != null) {
                // Write valid PCM data to the hardware mixer
                track.write(pcmData, 0, pcmData.size, AudioTrack.WRITE_BLOCKING)
            } else {
                // Buffer underrun (Emulator is running too slow): 
                // Write silence to prevent crackling/popping artifacts, or yield.
                // track.write(silenceBuffer, 0, silenceBuffer.size, AudioTrack.WRITE_BLOCKING)
                Thread.sleep(1) // Yield CPU to let emulator catch up
            }
        }
    }

    /**
     * Called by the HLE Horizon Audio Services (audren/audout) to submit PCM frames.
     */
    fun submitAudioBuffer(buffer: ByteArray, inSampleRate: Int, channels: Int) {
        if (!isPlaying.get()) return
        
        // Synchronized Latency Control: 
        // Drop frames if the engine is producing audio faster than the hardware can play.
        if (audioQueue.size > MAX_QUEUE_SIZE) {
            audioQueue.poll() // Discard oldest frame to prevent desync/delay
        }
        
        // Note: Advanced Sample-Rate Conversion (Resampling) would occur here 
        // if inSampleRate != 48000.
        
        audioQueue.offer(buffer)
    }

    fun stop() {
        isPlaying.set(false)
        audioThread?.join(500)
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        audioQueue.clear()
    }
}
