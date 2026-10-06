package com.eltnegcellist.emma.ai

import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/**
 * Minimal Hugging Face Unigram tokenizer for the pinned Ruri tokenizer.json.
 *
 * The Web tokenizer has no normalizer, uses Metaspace without a prepended
 * marker, Unigram + byte fallback, then adds <s> / </s>. Keeping this
 * implementation narrow makes Android follow the same serialized tokenizer
 * contract without adding a second tokenizer runtime.
 */
internal class RuriSemanticTokenizer(tokenizerFile: File) {
    private data class Piece(
        val text: String,
        val id: Int,
        val score: Double,
    )

    private data class Edge(
        val previous: Int,
        val ids: IntArray,
    )

    private val piecesByFirstCodePoint = HashMap<Int, MutableList<Piece>>()
    private val byteTokenIds = IntArray(256) { -1 }
    private val unknownPenalty: Double

    init {
        val root = JSONObject(tokenizerFile.readText())
        val model = root.getJSONObject("model")
        require(model.getString("type") == "Unigram") {
            "Ruri tokenizerの形式が想定と異なります。"
        }
        require(model.optBoolean("byte_fallback", false)) {
            "Ruri tokenizerのbyte fallbackがありません。"
        }

        val vocab = model.getJSONArray("vocab")
        var minimum = Double.POSITIVE_INFINITY
        for (id in 0 until vocab.length()) {
            val row = vocab.getJSONArray(id)
            val text = row.getString(0)
            val score = row.getDouble(1)

            val byteMatch = BYTE_TOKEN.matchEntire(text)
            if (byteMatch != null) {
                val value = byteMatch.groupValues[1].toInt(16)
                byteTokenIds[value] = id
                continue
            }

            // Added/special tokens are handled by the post processor, not by
            // ordinary Unigram segmentation of parent speech.
            if (text.startsWith("<") && text.endsWith(">")) continue
            if (text.isEmpty()) continue

            minimum = minOf(minimum, score)
            val first = text.codePointAt(0)
            piecesByFirstCodePoint.getOrPut(first) { mutableListOf() }
                .add(Piece(text, id, score))
        }

        require(byteTokenIds.all { it >= 0 }) {
            "Ruri tokenizerのbyte fallback語彙が不足しています。"
        }
        require(minimum.isFinite()) {
            "Ruri tokenizerの語彙を読み込めませんでした。"
        }
        unknownPenalty = minimum - 10.0
    }

    fun encode(text: String): LongArray {
        // Hugging Face Metaspace(replacement="▁", prepend_scheme="never",
        // split=false) on the pinned tokenizer.
        val prepared = text.replace(" ", "▁")
        val length = prepared.length
        val score = DoubleArray(length + 1) { Double.NEGATIVE_INFINITY }
        val edge = arrayOfNulls<Edge>(length + 1)
        score[0] = 0.0

        for (start in 0 until length) {
            if (!score[start].isFinite()) continue
            if (start > 0 && Character.isLowSurrogate(prepared[start])) continue

            val codePoint = prepared.codePointAt(start)
            piecesByFirstCodePoint[codePoint].orEmpty().forEach { piece ->
                if (!prepared.regionMatches(start, piece.text, 0, piece.text.length)) return@forEach
                val end = start + piece.text.length
                val candidate = score[start] + piece.score
                if (candidate > score[end]) {
                    score[end] = candidate
                    edge[end] = Edge(start, intArrayOf(piece.id))
                }
            }

            // Byte fallback is always available but deliberately receives the
            // same very low unknown score used by SentencePiece/Unigram.
            val cpLength = Character.charCount(codePoint)
            val end = start + cpLength
            val bytes = String(Character.toChars(codePoint))
                .toByteArray(StandardCharsets.UTF_8)
            val fallbackIds = IntArray(bytes.size) { index ->
                byteTokenIds[bytes[index].toInt() and 0xff]
            }
            val candidate = score[start] + unknownPenalty
            if (candidate > score[end]) {
                score[end] = candidate
                edge[end] = Edge(start, fallbackIds)
            }
        }

        require(length == 0 || edge[length] != null) {
            "Ruri tokenizerで文章を分割できませんでした。"
        }

        val reversed = ArrayList<IntArray>()
        var cursor = length
        while (cursor > 0) {
            val selected = edge[cursor]
                ?: error("Ruri tokenizerの経路が途切れました。")
            reversed += selected.ids
            cursor = selected.previous
        }

        val result = ArrayList<Long>()
        result += BOS_ID.toLong()
        for (index in reversed.indices.reversed()) {
            reversed[index].forEach { result += it.toLong() }
        }
        result += EOS_ID.toLong()
        return result.toLongArray()
    }

    companion object {
        private const val BOS_ID = 1
        private const val EOS_ID = 2
        private val BYTE_TOKEN = Regex("""<0x([0-9A-F]{2})>""")
    }
}
