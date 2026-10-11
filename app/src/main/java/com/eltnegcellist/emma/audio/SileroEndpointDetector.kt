package com.eltnegcellist.emma.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest

/** Uses copies for VAD inference; AudioRingRecorder keeps the original MIC PCM. */
internal class SileroEndpointDetector(model: ByteArray, sampleRate: Int) : EndpointDetector {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val state = FloatArray(256)
    private val context = FloatArray(64)
    private val pending = FloatArray(512)
    private var pendingCount = 0
    private val policy = SileroEndpointPolicy()
    private var closed = false

    init {
        require(sampleRate == 16_000) { "Silero microphone input must be 16 kHz." }
        val hash = MessageDigest.getInstance("SHA-256").digest(model).joinToString("") { "%02x".format(it) }
        check(hash == MODEL_SHA256) { "Silero model checksum mismatch." }
        session = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            environment.createSession(model, options)
        }
    }

    override fun process(samples: ShortArray, count: Int): List<VoiceActivityEvent> {
        check(!closed)
        require(count in 0..samples.size)
        val events = mutableListOf<VoiceActivityEvent>()
        for (i in 0 until count) {
            pending[pendingCount++] = samples[i] / 32768f
            if (pendingCount == pending.size) {
                val input = FloatArray(576)
                context.copyInto(input)
                pending.copyInto(input, 64)
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 576)).use { audio ->
                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(state), longArrayOf(2, 1, 128)).use { hidden ->
                        OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(16_000)), longArrayOf()).use { rate ->
                            session.run(mapOf("input" to audio, "state" to hidden, "sr" to rate)).use { result ->
                                val probability = (result[0] as OnnxTensor).floatBuffer.get(0)
                                (result[1] as OnnxTensor).floatBuffer.get(state)
                                policy.process(probability)?.let(events::add)
                            }
                        }
                    }
                }
                pending.copyInto(context, 0, 448, 512)
                pendingCount = 0
            }
        }
        return events
    }

    override fun reset() {
        state.fill(0f); context.fill(0f); pending.fill(0f); pendingCount = 0; policy.reset()
    }

    override fun close() {
        if (!closed) { closed = true; session.close() }
    }

    companion object {
        const val MODEL_SHA256 = "1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3"
    }
}
