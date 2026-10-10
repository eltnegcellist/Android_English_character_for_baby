package com.eltnegcellist.emma.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

private const val EMBEDDING_DIMENSIONS = 384
private const val HEAD_WIDTH = EMBEDDING_DIMENSIONS + 1
private const val TOPIC_MARGIN = 0.05
private const val MIN_EMBEDDING_COSINE = 0.995
private const val MAX_PROBABILITY_DRIFT = 0.02
private const val MAX_MARGIN_DRIFT = 0.03

private val TOPICS = listOf(
    "drink", "bath", "milk", "sleep", "wake", "diaper", "clothes",
    "hug", "hands", "feet", "smile", "cry", "voice", "tummy",
    "play", "outside", "rain", "sun", "food", "book", "music", "generic",
)

private data class TopicScore(
    val id: String,
    val rawId: String,
    val probability: Double,
    val margin: Double,
)

fun main(rawArgs: Array<String>) {
    val args = rawArgs.toList().chunked(2).associate { pair ->
        require(pair.size == 2) { "Arguments must be --name value pairs." }
        pair[0] to pair[1]
    }
    val webFile = File(requireNotNull(args["--web"]) { "--web is required" })
    val modelFile = File(requireNotNull(args["--model"]) { "--model is required" })
    val tokenizerFile = File(requireNotNull(args["--tokenizer"]) { "--tokenizer is required" })
    val headFile = File(requireNotNull(args["--head"]) { "--head is required" })
    val outputFile = args["--output"]?.let(::File)

    require(webFile.isFile && modelFile.isFile && tokenizerFile.isFile && headFile.isFile) {
        "Semantic parity input file is missing."
    }

    val web = JSONObject(webFile.readText())
    require(web.getString("modelSha") == MODEL_SHA) { "Web parity model SHA changed." }
    val cases = web.getJSONArray("cases")
    require(cases.length() > 0) { "No Semantic parity cases." }

    val tokenizer = RuriSemanticTokenizer(tokenizerFile)
    val head = readHead(headFile)
    val environment = OrtEnvironment.getEnvironment()
    val options = OrtSession.SessionOptions()
    val session = environment.createSession(modelFile.absolutePath, options)

    var maxEmbeddingDrift = 0.0
    var minEmbeddingCosine = 1.0
    var maxProbabilityDrift = 0.0
    var maxMarginDrift = 0.0
    val summaries = JSONArray()
    val failures = mutableListOf<String>()

    try {
        for (index in 0 until cases.length()) {
            val expected = cases.getJSONObject(index)
            val id = expected.getString("id")
            val text = expected.getString("text")
            val actualIds = tokenizer.encode(text)
            val expectedIds = expected.getJSONArray("tokenIds").toLongArray()
            if (!actualIds.contentEquals(expectedIds)) {
                failures += "Tokenizer mismatch for $id"
            }

            val mask = LongArray(actualIds.size) { 1L }
            val inputIdsTensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(actualIds),
                longArrayOf(1L, actualIds.size.toLong()),
            )
            val attentionMaskTensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(mask),
                longArrayOf(1L, mask.size.toLong()),
            )
            val embedding = try {
                session.run(
                    mapOf(
                        "input_ids" to inputIdsTensor,
                        "attention_mask" to attentionMaskTensor,
                    ),
                ).use { output ->
                    val tensor = output.get("sentence_embedding").orElse(null) as? OnnxTensor
                        ?: error("sentence_embedding output is missing.")
                    val buffer = requireNotNull(tensor.floatBuffer)
                    require(buffer.remaining() == EMBEDDING_DIMENSIONS)
                    FloatArray(EMBEDDING_DIMENSIONS).also(buffer::get)
                }
            } finally {
                inputIdsTensor.close()
                attentionMaskTensor.close()
            }

            val expectedEmbedding = expected.getJSONArray("embedding")
            require(expectedEmbedding.length() == EMBEDDING_DIMENSIONS)
            var caseEmbeddingDrift = 0.0
            var dot = 0.0
            var actualNorm = 0.0
            var expectedNorm = 0.0
            for (dimension in 0 until EMBEDDING_DIMENSIONS) {
                val actual = embedding[dimension].toDouble()
                val expectedValue = expectedEmbedding.getDouble(dimension)
                caseEmbeddingDrift = maxOf(caseEmbeddingDrift, abs(actual - expectedValue))
                dot += actual * expectedValue
                actualNorm += actual * actual
                expectedNorm += expectedValue * expectedValue
            }
            val cosine = dot / sqrt(actualNorm * expectedNorm)
            maxEmbeddingDrift = maxOf(maxEmbeddingDrift, caseEmbeddingDrift)
            minEmbeddingCosine = minOf(minEmbeddingCosine, cosine)
            if (cosine < MIN_EMBEDDING_COSINE) {
                failures += "Embedding cosine for $id: $cosine"
            }

            val actualTopic = classify(embedding, head)
            val expectedTopic = expected.getJSONObject("topic")
            if (actualTopic.rawId != expectedTopic.getString("rawId")) {
                failures += "Raw topic mismatch for $id"
            }
            if (actualTopic.id != expectedTopic.getString("id")) {
                failures += "Topic mismatch for $id"
            }

            val probabilityDrift = abs(actualTopic.probability - expectedTopic.getDouble("probability"))
            val marginDrift = abs(actualTopic.margin - expectedTopic.getDouble("margin"))
            maxProbabilityDrift = maxOf(maxProbabilityDrift, probabilityDrift)
            maxMarginDrift = maxOf(maxMarginDrift, marginDrift)
            if (probabilityDrift > MAX_PROBABILITY_DRIFT) {
                failures += "Probability drift for $id: $probabilityDrift"
            }
            if (marginDrift > MAX_MARGIN_DRIFT) {
                failures += "Margin drift for $id: $marginDrift"
            }

            summaries.put(
                JSONObject()
                    .put("id", id)
                    .put("topic", actualTopic.id)
                    .put("rawId", actualTopic.rawId)
                    .put("embeddingMaxAbsDrift", caseEmbeddingDrift)
                    .put("embeddingCosine", cosine)
                    .put("probabilityDrift", probabilityDrift)
                    .put("marginDrift", marginDrift),
            )
        }
    } finally {
        session.close()
        options.close()
    }

    val summary = JSONObject()
        .put("version", 1)
        .put("androidRuntime", "onnxruntime-java-1.23.2")
        .put("webRuntime", web.getString("runtime"))
        .put("modelSha", MODEL_SHA)
        .put("caseCount", cases.length())
        .put("maxEmbeddingAbsDrift", maxEmbeddingDrift)
        .put("minEmbeddingCosine", minEmbeddingCosine)
        .put("maxProbabilityDrift", maxProbabilityDrift)
        .put("maxMarginDrift", maxMarginDrift)
        .put("cases", summaries)
        .put("failures", JSONArray(failures))

    outputFile?.let {
        it.parentFile?.mkdirs()
        it.writeText(summary.toString(2) + "\n")
    }
    println(summary.toString(2))
    check(failures.isEmpty()) { failures.joinToString("; ") }
}

private fun classify(embedding: FloatArray, head: FloatArray): TopicScore {
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
    val maximum = logits.maxOrNull() ?: error("No logits.")
    val exponentials = DoubleArray(logits.size) { exp(logits[it] - maximum) }
    val sum = exponentials.sum()
    val probabilities = DoubleArray(logits.size) { exponentials[it] / sum }
    val ranking = probabilities.indices.sortedByDescending { probabilities[it] }
    val best = ranking[0]
    val second = ranking[1]
    val probability = probabilities[best]
    val margin = probability - probabilities[second]
    return TopicScore(
        id = if (margin < TOPIC_MARGIN) "generic" else TOPICS[best],
        rawId = TOPICS[best],
        probability = probability,
        margin = margin,
    )
}

private fun readHead(file: File): FloatArray {
    val bytes = file.readBytes()
    require(bytes.size == TOPICS.size * HEAD_WIDTH * 4)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    return FloatArray(buffer.remaining()).also(buffer::get)
}

private fun JSONArray.toLongArray(): LongArray =
    LongArray(length()) { index -> getLong(index) }

private const val MODEL_SHA =
    "bd500193003fdeaba8c5422a47b974b40b49a5e0c91285c76f6bd949d2205264"
