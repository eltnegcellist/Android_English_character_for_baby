package com.eltnegcellist.emma.ai

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import kotlin.math.exp

internal data class SemanticTopicPrediction(
    val topic: String,
    val probability: Double,
    val margin: Double,
)

/**
 * Android runtime for the exact Ruri 70M INT8 topic model used by Web Lite.
 */
internal class RuriSemanticClassifier(
    context: Context,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()
    private var sessionOptions: OrtSession.SessionOptions? = null
    private var session: OrtSession? = null
    private var tokenizer: RuriSemanticTokenizer? = null
    private var topicHead: FloatArray? = null

    @Synchronized
    private fun ensureReady() {
        if (session != null && tokenizer != null && topicHead != null) return
        check(RuriSemanticModelStore.isInstalled(appContext)) {
            "Semanticモデルがまだ準備されていません。"
        }

        val dir = RuriSemanticModelStore.directory(appContext)
        val options = OrtSession.SessionOptions()
        try {
            val nextSession = environment.createSession(
                File(dir, "model.onnx").absolutePath,
                options,
            )
            val nextTokenizer = RuriSemanticTokenizer(File(dir, "tokenizer.json"))
            val nextHead = readTopicHead(File(dir, "head-topic.f32"))

            sessionOptions = options
            session = nextSession
            tokenizer = nextTokenizer
            topicHead = nextHead
        } catch (error: Throwable) {
            options.close()
            throw error
        }
    }

    fun predict(text: String): Result<SemanticTopicPrediction?> = runCatching {
        ensureReady()
        val ids = tokenizer!!.encode(text)
        if (ids.size > MAX_LENGTH) return@runCatching null

        val mask = LongArray(ids.size) { 1L }
        val inputIdsTensor = OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(ids),
            longArrayOf(1L, ids.size.toLong()),
        )
        val attentionMaskTensor = OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(mask),
            longArrayOf(1L, ids.size.toLong()),
        )

        try {
            session!!.run(
                mapOf(
                    "input_ids" to inputIdsTensor,
                    "attention_mask" to attentionMaskTensor,
                ),
            ).use { output ->
                val tensor = output[0] as? OnnxTensor
                    ?: error("Semanticモデルの埋め込み出力がありません。")
                val buffer = tensor.floatBuffer
                    ?: error("Semanticモデルの埋め込みがfloat32ではありません。")
                require(buffer.remaining() == EMBEDDING_DIMENSIONS) {
                    "Semanticモデルの埋め込み次元が一致しません。"
                }
                val embedding = FloatArray(EMBEDDING_DIMENSIONS)
                buffer.get(embedding)
                classify(embedding, topicHead!!)
            }
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
        }
    }

    private fun classify(
        embedding: FloatArray,
        head: FloatArray,
    ): SemanticTopicPrediction {
        require(embedding.size == EMBEDDING_DIMENSIONS)
        require(head.size == TOPICS.size * HEAD_WIDTH)

        val logits = DoubleArray(TOPICS.size)
        for (classIndex in TOPICS.indices) {
            val offset = classIndex * HEAD_WIDTH
            var value = head[offset + EMBEDDING_DIMENSIONS].toDouble()
            for (dimension in 0 until EMBEDDING_DIMENSIONS) {
                value += head[offset + dimension] * embedding[dimension]
            }
            logits[classIndex] = value
        }

        val max = logits.maxOrNull() ?: 0.0
        val exponentials = DoubleArray(logits.size) { exp(logits[it] - max) }
        val sum = exponentials.sum().takeIf { it > 0.0 }
            ?: error("Semantic分類スコアが不正です。")
        val probabilities = DoubleArray(logits.size) { exponentials[it] / sum }
        val ranking = probabilities.indices.sortedByDescending { probabilities[it] }
        val best = ranking[0]
        val second = ranking[1]
        val probability = probabilities[best]
        val margin = probability - probabilities[second]
        val topic = if (margin < TOPIC_MARGIN) "generic" else TOPICS[best]

        return SemanticTopicPrediction(
            topic = topic,
            probability = probability,
            margin = margin,
        )
    }

    private fun readTopicHead(file: File): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size == TOPICS.size * HEAD_WIDTH * 4) {
            "Semantic話題分類ヘッドのサイズが一致しません。"
        }
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
        return FloatArray(buffer.remaining()).also(buffer::get)
    }

    @Synchronized
    override fun close() {
        session?.close()
        sessionOptions?.close()
        session = null
        sessionOptions = null
        tokenizer = null
        topicHead = null
    }

    companion object {
        private const val MAX_LENGTH = 128
        private const val EMBEDDING_DIMENSIONS = 384
        private const val HEAD_WIDTH = EMBEDDING_DIMENSIONS + 1
        private const val TOPIC_MARGIN = 0.05

        // Same order as Web semantic-assets/.../heads-config.json.
        private val TOPICS = listOf(
            "drink",
            "bath",
            "milk",
            "sleep",
            "wake",
            "diaper",
            "clothes",
            "hug",
            "hands",
            "feet",
            "smile",
            "cry",
            "voice",
            "tummy",
            "play",
            "outside",
            "rain",
            "sun",
            "food",
            "book",
            "music",
            "generic",
        )
    }
}
