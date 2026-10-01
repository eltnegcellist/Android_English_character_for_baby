package com.eltnegcellist.emma.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

internal data class KittenVoiceTable(
    val rows: Int,
    val columns: Int,
    val values: FloatArray,
) {
    fun styleFor(referenceIndex: Int): FloatArray {
        require(rows > 0 && columns > 0)
        val row = referenceIndex.coerceIn(0, rows - 1)
        return values.copyOfRange(row * columns, (row + 1) * columns)
    }
}

/** Reads only the Kiki entry from Kitten's voices.npz archive. */
internal object KittenVoiceLoader {
    private const val KIKI_ENTRY = "expr-voice-5-f.npy"

    fun loadKiki(file: File): KittenVoiceTable {
        ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith(KIKI_ENTRY) }
                ?: error("voices.npzにKiki音声がありません。")
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            return parseNpyFloat32(bytes)
        }
    }

    internal fun parseNpyFloat32(bytes: ByteArray): KittenVoiceTable {
        require(bytes.size >= 12) { "Kiki voice NPYが短すぎます。" }
        val magic = byteArrayOf(0x93.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), 'Y'.code.toByte())
        require(bytes.copyOfRange(0, 6).contentEquals(magic)) { "Kiki voice NPYの形式が不正です。" }

        val major = bytes[6].toInt() and 0xff
        val headerOffset: Int
        val headerLength: Int
        if (major >= 2) {
            headerOffset = 12
            headerLength = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        } else {
            headerOffset = 10
            headerLength = ByteBuffer.wrap(bytes, 8, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
        }

        require(headerOffset + headerLength <= bytes.size) { "Kiki voice NPYヘッダーが壊れています。" }
        val header = String(bytes, headerOffset, headerLength, StandardCharsets.US_ASCII).trim()

        val descr = Regex("""['\"]descr['\"]\s*:\s*['\"]([^'\"]+)['\"]""")
            .find(header)?.groupValues?.get(1)
            ?: error("Kiki voice NPY dtypeを読めません。")
        require(descr.endsWith("f4")) { "Kiki voice NPYはfloat32ではありません: " + descr }

        val shapeText = Regex("""['\"]shape['\"]\s*:\s*\(([^)]*)\)""")
            .find(header)?.groupValues?.get(1)
            ?: error("Kiki voice NPY shapeを読めません。")
        val shape = shapeText.split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(String::toInt)
        require(shape.size == 2) { "Kiki voice NPYは2次元ではありません。" }
        val rows = shape[0]
        val columns = shape[1]
        require(rows > 0 && columns > 0)

        val dataOffset = headerOffset + headerLength
        val expectedBytes = rows.toLong() * columns.toLong() * 4L
        require(dataOffset.toLong() + expectedBytes <= bytes.size.toLong()) {
            "Kiki voice NPYのデータが不足しています。"
        }

        val buffer = ByteBuffer.wrap(bytes, dataOffset, expectedBytes.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        val values = FloatArray(rows * columns)
        for (index in values.indices) values[index] = buffer.float

        val fortran = Regex("""['\"]fortran_order['\"]\s*:\s*True""").containsMatchIn(header)
        if (!fortran) return KittenVoiceTable(rows, columns, values)

        val transposed = FloatArray(values.size)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                transposed[row * columns + column] = values[column * rows + row]
            }
        }
        return KittenVoiceTable(rows, columns, transposed)
    }
}
