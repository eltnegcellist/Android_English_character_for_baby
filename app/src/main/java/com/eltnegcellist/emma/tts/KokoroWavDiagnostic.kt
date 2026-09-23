package com.eltnegcellist.emma.tts

import android.content.Context
import com.eltnegcellist.emma.ai.GemmaEmmaClient
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.util.Locale
import kotlin.math.abs

internal object KokoroWavDiagnostic {
    data class SpeedScore(
        val speed: Float,
        val wordErrors: Int,
        val referenceWords: Int,
        val exactPhrases: Int,
        val phrases: Int,
    ) {
        val wer: Float
            get() = if (referenceWords <= 0) 1f else wordErrors.toFloat() / referenceWords.toFloat()
    }

    data class Summary(
        val wavFiles: Int,
        val elapsedMs: Long,
        val asrChecks: Int,
        val asrAvailable: Boolean,
        val speedScores: List<SpeedScore>,
    )

    private val speeds = listOf(0.58f, 0.60f, 0.65f, 0.70f, 1.00f)
    private const val SAMPLE_RATE = 24_000
    private const val PAUSE_MS = 420
    private const val EDGE_FADE_MS = 6

    fun capture(context: Context, threadCount: Int): Result<Summary> = runCatching {
        require(threadCount > 0) { "threadCount must be positive" }
        val started = System.nanoTime()
        val phrases = KOKORO_AUDIO_TEST_TEXT
            .trim()
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        require(phrases.isNotEmpty()) { "diagnostic text is empty" }

        val gemma = GemmaEmmaClient.currentReady()
        val selfAsrReport = StringBuilder().apply {
            appendLine("Kokoro -> Gemma English ASR self diagnostic")
            appendLine("text=$KOKORO_AUDIO_TEST_TEXT")
            appendLine("speeds=${speeds.joinToString(",")}")
            appendLine("gemmaReady=${gemma != null}")
            appendLine("WER is diagnostic only; Gemma ASR is not a human-listening ground truth.")
            appendLine()
        }
        val speedScores = ArrayList<SpeedScore>()
        var asrChecks = 0

        DiagnosticStore.clearAudioCaptures(context)
        DiagnosticStore.mark(
            context,
            "kokoro_wav_sweep_start",
            "threads=$threadCount speeds=${speeds.joinToString(",")} phrases=${phrases.size} gemmaSelfAsr=${gemma != null}",
        )

        val dir = KokoroModelStore.directory(context)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = File(dir, "model.onnx").path,
                    voices = File(dir, "voices.bin").path,
                    tokens = File(dir, "tokens.txt").path,
                    dataDir = File(dir, "espeak-ng-data").path,
                    lexicon = File(dir, "lexicon-us-en.txt").path,
                    lang = "en-us",
                ),
                numThreads = threadCount,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )

        val tts = OfflineTts(assetManager = null, config = config)
        var files = 0
        try {
            val sampleRate = tts.sampleRate()
            require(sampleRate == SAMPLE_RATE) { "Unexpected Kokoro sample rate: $sampleRate" }

            for (speed in speeds) {
                val speedTag = speedTag(speed)
                val playbackChunks = ArrayList<ShortArray>(phrases.size)
                var speedWordErrors = 0
                var speedReferenceWords = 0
                var speedExactPhrases = 0
                var scoredPhrases = 0

                phrases.forEachIndexed { index, phrase ->
                    val phraseStarted = System.nanoTime()
                    val generated = tts.generate(text = phrase, sid = 3, speed = speed)
                    val generationMs = (System.nanoTime() - phraseStarted) / 1_000_000L
                    require(
                        generated.sampleRate == sampleRate &&
                            generated.samples.isNotEmpty() &&
                            generated.samples.all { it.isFinite() },
                    ) { "Kokoro returned invalid diagnostic audio" }

                    val rawPcm = toPcm16(generated.samples)
                    val phraseFile = "speed-$speedTag-p${(index + 1).toString().padStart(2, '0')}-raw"
                    DiagnosticStore.saveAudioCapture(
                        context = context,
                        fileName = phraseFile,
                        samples = rawPcm,
                        sampleRate = sampleRate,
                        detail = "kind=raw speed=$speed phrase=${index + 1}/${phrases.size} generationMs=$generationMs text=$phrase",
                    )
                    files += 1

                    if (gemma != null) {
                        val transcriptResult = gemma.transcribeDiagnosticEnglish(PcmWav.encodeMono16(rawPcm, sampleRate))
                        val transcript = transcriptResult.getOrElse { error ->
                            "[ASR_ERROR:${error.javaClass.simpleName}]"
                        }
                        if (transcriptResult.isSuccess) {
                            val score = compareWords(phrase, transcript)
                            speedWordErrors += score.errors
                            speedReferenceWords += score.referenceWords
                            speedExactPhrases += if (score.errors == 0) 1 else 0
                            scoredPhrases += 1
                            asrChecks += 1
                            selfAsrReport.appendLine(
                                "speed=${formatSpeed(speed)} phrase=${index + 1} errors=${score.errors}/${score.referenceWords} expected=$phrase | transcript=$transcript",
                            )
                            DiagnosticStore.mark(
                                context,
                                "kokoro_self_asr_result",
                                "speed=$speed phrase=${index + 1} errors=${score.errors}/${score.referenceWords} transcript=${transcript.replace(Regex("[\\r\\n]"), " ").take(120)}",
                            )
                        } else {
                            selfAsrReport.appendLine(
                                "speed=${formatSpeed(speed)} phrase=${index + 1} ERROR expected=$phrase | transcript=$transcript",
                            )
                        }
                    }

                    val processed = smoothPhraseEdges(generated.samples, sampleRate)
                    playbackChunks += toPcm16(processed)
                }

                val combined = combineWithPause(playbackChunks, sampleRate, PAUSE_MS)
                DiagnosticStore.saveAudioCapture(
                    context = context,
                    fileName = "speed-$speedTag-combined-playback",
                    samples = combined,
                    sampleRate = sampleRate,
                    detail = "kind=combined speed=$speed phrases=${phrases.size} pauseMs=$PAUSE_MS text=$KOKORO_AUDIO_TEST_TEXT",
                )
                files += 1

                if (gemma != null && scoredPhrases > 0) {
                    val score = SpeedScore(
                        speed = speed,
                        wordErrors = speedWordErrors,
                        referenceWords = speedReferenceWords,
                        exactPhrases = speedExactPhrases,
                        phrases = scoredPhrases,
                    )
                    speedScores += score
                    selfAsrReport.appendLine(
                        "SUMMARY speed=${formatSpeed(speed)} WER=${String.format(Locale.US, "%.3f", score.wer)} exact=${score.exactPhrases}/${score.phrases}",
                    )
                    selfAsrReport.appendLine()
                }
            }
        } finally {
            tts.release()
        }

        DiagnosticStore.saveAudioTextReport(context, "gemma-asr-results.txt", selfAsrReport.toString())
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        DiagnosticStore.mark(
            context,
            "kokoro_wav_sweep_done",
            "threads=$threadCount wavFiles=$files elapsedMs=$elapsedMs asrChecks=$asrChecks gemmaSelfAsr=${gemma != null}",
        )
        Summary(
            wavFiles = files,
            elapsedMs = elapsedMs,
            asrChecks = asrChecks,
            asrAvailable = gemma != null,
            speedScores = speedScores,
        )
    }

    private data class WordScore(val errors: Int, val referenceWords: Int)

    private fun compareWords(expected: String, transcript: String): WordScore {
        val reference = words(expected)
        val hypothesis = words(transcript)
        if (reference.isEmpty()) return WordScore(hypothesis.size, 0)
        val previous = IntArray(hypothesis.size + 1) { it }
        val current = IntArray(hypothesis.size + 1)
        for (i in 1..reference.size) {
            current[0] = i
            for (j in 1..hypothesis.size) {
                val substitution = previous[j - 1] + if (reference[i - 1] == hypothesis[j - 1]) 0 else 1
                val deletion = previous[j] + 1
                val insertion = current[j - 1] + 1
                current[j] = minOf(substitution, deletion, insertion)
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return WordScore(previous[hypothesis.size], reference.size)
    }

    private fun words(text: String): List<String> =
        Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
            .findAll(text.lowercase(Locale.US))
            .map { it.value.replace('’', '\'') }
            .toList()

    private fun formatSpeed(speed: Float): String = String.format(Locale.US, "%.2f", speed)

    private fun speedTag(speed: Float): String =
        (speed * 100f).toInt().toString().padStart(3, '0')

    private fun combineWithPause(chunks: List<ShortArray>, sampleRate: Int, pauseMs: Int): ShortArray {
        require(chunks.isNotEmpty()) { "chunks are empty" }
        val pauseSamples = (sampleRate * pauseMs / 1000f).toInt().coerceAtLeast(0)
        val total = chunks.sumOf { it.size.toLong() } + pauseSamples.toLong() * (chunks.size - 1)
        require(total in 1..Int.MAX_VALUE.toLong()) { "combined diagnostic audio is too large" }
        val combined = ShortArray(total.toInt())
        var offset = 0
        chunks.forEachIndexed { index, chunk ->
            chunk.copyInto(combined, offset)
            offset += chunk.size
            if (index < chunks.lastIndex) offset += pauseSamples
        }
        return combined
    }

    private fun smoothPhraseEdges(samples: FloatArray, sampleRate: Int): FloatArray {
        val out = samples.copyOf()
        val peak = out.maxOfOrNull { abs(it) } ?: 0f
        if (peak > 0.98f) {
            val scale = 0.96f / peak
            for (i in out.indices) out[i] *= scale
        }
        val requestedFade = (sampleRate * EDGE_FADE_MS / 1000f).toInt().coerceAtLeast(1)
        val fade = minOf(requestedFade, out.size / 4)
        if (fade > 1) {
            for (i in 0 until fade) {
                val gain = i.toFloat() / (fade - 1).toFloat()
                out[i] *= gain
                out[out.lastIndex - i] *= gain
            }
        }
        return out
    }

    private fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { index ->
        (samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
    }
}
