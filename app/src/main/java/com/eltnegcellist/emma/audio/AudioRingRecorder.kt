package com.eltnegcellist.emma.audio

import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class AudioRingRecorder(
    private val assets: AssetManager? = null,
    private val sampleRate: Int = 16_000,
    private val capacitySeconds: Int = 30,
) {
    private val buffer = PcmRingBuffer(sampleRate * capacitySeconds)
    private val detectorLock = Any()
    private val legacyDetector = LegacyEndpointDetector(sampleRate)
    private var endpointDetector: EndpointDetector = legacyDetector
    var endpointFallbackReason: String? = null
        private set

    @Volatile private var running = false
    @Volatile private var endpointingEnabled = false

    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile var onError: ((String) -> Unit)? = null
    @Volatile var onVoiceActivity: ((VoiceActivityEvent) -> Unit)? = null
    private var runFlag: java.util.concurrent.atomic.AtomicBoolean? = null

    // Called from the main thread. Each worker owns and releases its own AudioRecord.
    fun start(mode: EndpointMode = EndpointMode.SILERO) {
        if (running) return
        check(worker?.isAlive != true) { "録音の終了処理中です。少し待って再開してください。" }
        synchronized(detectorLock) {
            endpointDetector.close()
            endpointFallbackReason = null
            endpointDetector = if (mode == EndpointMode.SILERO) {
                try {
                    SileroEndpointDetector(requireNotNull(assets).open("vad/silero_vad.onnx").use { it.readBytes() }, sampleRate)
                } catch (error: Exception) {
                    endpointFallbackReason = error.message ?: error.javaClass.simpleName
                    legacyDetector
                }
            } else legacyDetector
        }
        clear()
        endpointingEnabled = true
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferSize > 0) { "Unable to determine audio buffer size." }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize * 2,
        )
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed." }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start." }
        } catch (error: Throwable) {
            recorder.release()
            throw error
        }
        val flag = java.util.concurrent.atomic.AtomicBoolean(true)
        runFlag = flag
        audioRecord = recorder
        buffer.resume()
        running = true
        val workerDetector = endpointDetector
        worker = thread(name = "EmmaAudioRing", isDaemon = true) {
            try {
                val temp = ShortArray(maxOf(2_048, minBufferSize / 2))
                while (flag.get()) {
                    val readEpoch = buffer.epoch()
                    val read = recorder.read(temp, 0, temp.size, AudioRecord.READ_BLOCKING)
                    if (!flag.get()) break
                    check(read > 0) { "Microphone read failed ($read)." }
                    if (!flag.get()) break

                    buffer.append(temp, read, readEpoch)
                    if (endpointingEnabled) {
                        val events = synchronized(detectorLock) {
                            if (endpointingEnabled && readEpoch == buffer.epoch()) workerDetector.process(temp, read) else emptyList()
                        }
                        for (event in events) {
                            if (!endpointingEnabled || readEpoch != buffer.epoch()) break
                            when (event) {
                            VoiceActivityEvent.SpeechStarted -> {
                                // Detector confirms speech after a short onset window. Keep a little
                                // pre-roll, but drop long idle-room audio so Gemma receives the turn.
                                buffer.retainLast((sampleRate * 0.55f).toInt())
                                onVoiceActivity?.invoke(event)
                            }
                            is VoiceActivityEvent.Endpoint -> onVoiceActivity?.invoke(event)
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (flag.getAndSet(false)) {
                    running = false
                    endpointingEnabled = false
                    onError?.invoke("録音が中断されました。再開してください: ${error.message}")
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                synchronized(detectorLock) { workerDetector.close() }
            }
        }
    }

    fun stop() {
        endpointingEnabled = false
        synchronized(detectorLock) { endpointDetector.reset() }
        runFlag?.set(false)
        running = false
        runCatching { audioRecord?.stop() }
        worker?.join(500)
        if (worker?.isAlive != true) synchronized(detectorLock) { endpointDetector.close() }
        // A slow worker retains ownership of its recorder; never release it under read().
        audioRecord = null
        clear()
    }

    fun pauseBuffering() {
        endpointingEnabled = false
        synchronized(detectorLock) { endpointDetector.reset() }
        buffer.pause()
    }

    fun resumeBuffering(clearExisting: Boolean = false) {
        if (clearExisting) clear()
        synchronized(detectorLock) { endpointDetector.reset() }
        buffer.resume()
        endpointingEnabled = true
    }

    fun clear() {
        synchronized(detectorLock) { endpointDetector.reset() }
        buffer.clear()
    }

    fun secondsAvailable(): Double = buffer.size().toDouble() / sampleRate

    fun snapshotWav(maxSeconds: Int = 20, consume: Boolean = false): ByteArray =
        encodeWav(buffer.snapshot(sampleRate * maxSeconds, consume), sampleRate)

    private fun encodeWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val pcmBytes = samples.size * 2
        val out = ByteArrayOutputStream(44 + pcmBytes)

        fun writeAscii(value: String) = value.forEach { out.write(it.code) }
        fun writeLe16(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
        }
        fun writeLe32(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 24) and 0xFF)
        }

        writeAscii("RIFF")
        writeLe32(36 + pcmBytes)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeLe32(16)
        writeLe16(1)
        writeLe16(1)
        writeLe32(sampleRate)
        writeLe32(sampleRate * 2)
        writeLe16(2)
        writeLe16(16)
        writeAscii("data")
        writeLe32(pcmBytes)

        samples.forEach { sample ->
            val value = sample.toInt()
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
        }

        return out.toByteArray()
    }
}
