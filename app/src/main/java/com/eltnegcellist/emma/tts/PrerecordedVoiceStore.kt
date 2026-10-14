package com.eltnegcellist.emma.tts

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

/** Qwen3 voice generated on the Mac; the app only decodes bundled AAC clips. */
internal object PrerecordedVoiceStore {
    private const val ROOT = "voice-1202/"
    private const val MANIFEST = ROOT + "manifest.json"
    @Volatile private var index: Map<String, String>? = null

    fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    fun isAvailable(context: Context): Boolean = runCatching {
        context.assets.open(MANIFEST).use { it.read() >= 0 }
    }.getOrDefault(false)

    fun isReady(context: Context): Boolean = isAvailable(context) || KittenModelStore.isInstalled(context)

    fun hasText(context: Context, text: String): Boolean =
        isAvailable(context) && catalog(context).containsKey(normalize(text))

    private fun catalog(context: Context): Map<String, String> {
        index?.let { return it }
        return synchronized(this) {
            index ?: run {
                val json = context.assets.open(MANIFEST).bufferedReader().use { JSONObject(it.readText()) }
                require(json.getInt("schemaVersion") == 1 && json.getInt("generationSeed") == 1202)
                val entries = json.getJSONArray("entries")
                val result = LinkedHashMap<String, String>()
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    val key = normalize(entry.getString("text"))
                    val file = entry.getString("file")
                    require(file.matches(Regex("clips/[a-f0-9]{24}\\.m4a")))
                    require(result.put(key, ROOT + file) == null) { "Duplicate voice text" }
                }
                result.toMap().also { index = it }
            }
        }
    }

    fun loadPcm(context: Context, text: String): ShortArray? {
        if (!isAvailable(context)) return null
        val asset = catalog(context)[normalize(text)] ?: return null
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            context.assets.openFd(asset).use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                val audioTrack = (0 until extractor.trackCount).first {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                val format = extractor.getTrackFormat(audioTrack)
                require(format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == 24000)
                require(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1)
                extractor.selectTrack(audioTrack)
                val codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
                decoder = codec
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                val pcm = ByteArrayOutputStream()
                var inputDone = false
                var outputDone = false
                val deadline = System.nanoTime() + 30_000_000_000L
                while (!outputDone) {
                    check(System.nanoTime() < deadline) { "録音音声の読み込みがタイムアウトしました。" }
                    if (!inputDone) {
                        val inputId = codec.dequeueInputBuffer(10_000L)
                        if (inputId >= 0) {
                            val buffer = requireNotNull(codec.getInputBuffer(inputId))
                            val count = extractor.readSampleData(buffer, 0)
                            if (count < 0) {
                                codec.queueInputBuffer(inputId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputId, 0, count, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputId = codec.dequeueOutputBuffer(info, 10_000L)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val output = codec.outputFormat
                            require(output.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1)
                            require(output.getInteger(MediaFormat.KEY_SAMPLE_RATE) == 24000)
                            if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                require(output.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_16BIT)
                            }
                        }
                        else -> if (outputId >= 0) {
                            if (info.size > 0) {
                                val buffer = requireNotNull(codec.getOutputBuffer(outputId))
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                buffer.get(bytes)
                                pcm.write(bytes)
                            }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputId, false)
                        }
                    }
                }
                val bytes = pcm.toByteArray()
                require(bytes.isNotEmpty() && bytes.size % 2 == 0)
                val shorts = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                return ShortArray(shorts.remaining()).also { shorts.get(it) }
            }
        } finally {
            decoder?.runCatching { stop() }
            decoder?.release()
            extractor.release()
        }
    }
}
