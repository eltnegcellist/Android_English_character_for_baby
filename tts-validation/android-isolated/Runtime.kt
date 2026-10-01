package validation.tts

import ai.onnxruntime.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.math.abs

class Cancelled : RuntimeException("Cancelled")

class Cancellation {
    private var cancelled = false
    private val actions = mutableSetOf<() -> Unit>()
    @Synchronized fun check() { if (cancelled) throw Cancelled() }
    @Synchronized fun attach(action: () -> Unit) { check(); actions.add(action) }
    @Synchronized fun detach(action: () -> Unit) { actions.remove(action) }
    @Synchronized fun cancel() {
        if (cancelled) return
        cancelled = true
        actions.toList().forEach { runCatching { it() } }
        actions.clear()
    }
}

object AudioContract {
    // Output-only contract, not microphone endpoint detection. Apache source
    // attribution: ../candidates/SHERPA_CONTRACT_NOTICE and LICENSE.
    fun shortenPauses(y: FloatArray): FloatArray {
        require(y.isNotEmpty() && y.all { it.isFinite() })
        val intervals = mutableListOf<Pair<Int, Int>>()
        var start = -1
        for (i in y.indices) {
            if (abs(y[i]) <= .01f) { if (start < 0) start = i }
            else if (start >= 0) {
                if (i - start >= 4800) intervals.add(start to i)
                start = -1
            }
        }
        if (start >= 0 && y.size - start > 4800) intervals.add(start to y.size)
        val size = y.size - intervals.sumOf { (a,b) -> b-a-((b-a)*.2f).toInt() }
        val result = FloatArray(size); var read = 0; var write = 0
        for ((a,b) in intervals) {
            val end = a + ((b-a)*.2f).toInt()
            y.copyInto(result, write, read, end); write += end-read; read = b
        }
        y.copyInto(result, write, read, y.size)
        return result
    }
    fun pcm16(y: FloatArray): ShortArray {
        require(y.isNotEmpty() && y.all { it.isFinite() })
        val peak = y.maxOf { abs(it) }
        val gain = if (peak > 0) (.92f / peak).coerceIn(1f, 1.8f) else 1f
        return ShortArray(y.size) { (y[it].times(gain).coerceIn(-1f, 1f)*32767).toInt().toShort() }
    }
}

interface Backend : AutoCloseable {
    fun generate(chunks: List<String>, cancellation: Cancellation): FloatArray
}

class OnnxBackend(root: File) : Backend {
    companion object {
        val hashes = mapOf(
            "model.fp32.onnx" to "2174dbf67b58b7b50d7b65294f89c2c53c172834533519b853c579879a04cc22",
            "voices.bin" to "d520519c4a3519d44fcfcd943ed0b1e3c5da5cee0eea501d922fac1a93cd24dc",
            "tokens.txt" to "934a4188addc7665dd3410256bb622169242357fbb99d840d9351209b486dabb",
            "LICENSE" to "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
            "README.md" to "e8751a029481521364265c7c95acf0394fa3580671f19e99c1e9ce51c74ba9d6",
        )
    }
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val voices: FloatArray
    private val tokens: Map<Char, Long>
    private var closed = false
    init {
        require(root.list()?.toSet() == hashes.keys) { "Unexpected asset set" }
        for ((name, hash) in hashes) {
            val digest = MessageDigest.getInstance("SHA-256")
            File(root, name).inputStream().use { stream ->
                val buffer = ByteArray(65536)
                while (true) { val n=stream.read(buffer); if(n<0) break; digest.update(buffer,0,n) }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == hash) { "Hash mismatch: $name" }
        }
        val bytes = File(root,"voices.bin").readBytes()
        require(bytes.size == 8*400*256*4)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        voices = FloatArray(buffer.remaining()); buffer.get(voices)
        require(voices.all { it.isFinite() })
        tokens = File(root,"tokens.txt").readLines().associate { line ->
            val split = line.lastIndexOf(' '); val symbol=line.substring(0,split)
            require(symbol.length == 1); symbol[0] to line.substring(split+1).toLong()
        }
        session = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
            env.createSession(File(root,"model.fp32.onnx").path, options)
        }
        try {
            val m = session.metadata.customMetadata
            require(m["version"] == "8" && m["sample_rate"] == "24000" && m["max_token_len"] == "400")
            require(m["speaker_names"]?.split(',')?.get(7) == "expr-voice-5-f")
            require(m["speaker_speed_priors"]?.split(',')?.get(7) == "0.8")
            require(m["start_id"] == "0" && m["end_id"] == "10" && m["pad_id"] == "0")
            require(m["style_dim"] == "400,256" && m["n_speakers"] == "8")
        } catch (e: Throwable) { session.close(); throw e }
    }
    override fun generate(chunks: List<String>, cancellation: Cancellation): FloatArray {
        check(!closed); require(chunks.isNotEmpty()); val parts = mutableListOf<FloatArray>()
        for (ipa in chunks) {
            cancellation.check(); require(ipa.isNotEmpty())
            val content = ipa.flatMap { c ->
                val id=tokens[c] ?: error("Unsupported phoneme: $c")
                if(c=='.') listOf(id,tokens.getValue(' ')) else listOf(id)
            }
            require(content.size+3<=400)
            val ids = longArrayOf(0, *content.toLongArray(), 10, 0)
            val row = minOf(content.size,399)
            val style = voices.copyOfRange((7*400+row)*256,(7*400+row+1)*256)
            OnnxTensor.createTensor(env,LongBuffer.wrap(ids),longArrayOf(1,ids.size.toLong())).use { input ->
                OnnxTensor.createTensor(env,FloatBuffer.wrap(style),longArrayOf(1,256)).use { embedding ->
                    OnnxTensor.createTensor(env,FloatBuffer.wrap(floatArrayOf(.8f*.8f)),longArrayOf(1)).use { speed ->
                        OrtSession.RunOptions().use { options ->
                            val terminate = { options.setTerminate(true) }
                            cancellation.attach(terminate)
                            try {
                                session.run(mapOf("input_ids" to input,"style" to embedding,"speed" to speed),setOf("waveform"),options).use { result ->
                                    cancellation.check()
                                    val y=result.get("waveform").get().value as FloatArray
                                    parts.add(AudioContract.shortenPauses(y))
                                }
                            } catch (e: OrtException) { cancellation.check(); throw e }
                            finally { cancellation.detach(terminate) }
                        }
                    }
                }
            }
        }
        cancellation.check()
        val output=FloatArray(parts.sumOf { it.size }); var offset=0
        for(part in parts) { part.copyInto(output,offset); offset+=part.size }
        return output
    }
    override fun close() { if(!closed) { closed=true; session.close() } }
}
