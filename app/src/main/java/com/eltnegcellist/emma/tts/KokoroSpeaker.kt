package com.eltnegcellist.emma.tts

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max

class KokoroSpeaker(
    private val context: Context,
    private val onDone: (Long, Long) -> Unit,
    private val onError: (String) -> Unit,
    private val onAmplitude: (Float) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val playbackWorker = Executors.newSingleThreadExecutor()
    @Volatile private var requestId: String? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var closed = false
    private var engine: Engine? = null

    fun speak(text: String, speed: Float = 1.0f): Boolean {
        if (closed || text.isBlank() || !speed.isFinite() || !KokoroModelStore.isInstalled(context)) return false
        val audioTestMode = consumeAudioTestMode()
        val speechText = if (audioTestMode != null) KOKORO_AUDIO_TEST_TEXT else text
        stop()
        val id = UUID.randomUUID().toString()
        requestId = id

        worker.execute {
            if (closed || requestId != id) return@execute
            val started = System.nanoTime()
            runCatching {
                val runtimeProfile = resolveRuntimeProfile()
                val currentEngine = engine
                val active = if (currentEngine == null || currentEngine.threadCount != runtimeProfile.threadCount) {
                    currentEngine?.close()
                    Engine(context, runtimeProfile.threadCount).also {
                        engine = it
                        DiagnosticStore.mark(
                            context,
                            "kokoro_runtime_profile",
                            "mode=${runtimeProfile.mode.savedValue} cores=${runtimeProfile.processorCount} " +
                                "ramMb=${runtimeProfile.totalRamMb} selectedThreads=${runtimeProfile.threadCount}",
                        )
                    }
                } else {
                    currentEngine
                }
                if (requestId != id) return@runCatching 0L to 0L

                val normalBabyMode = audioTestMode == null && isBabyAudienceMode()
                val babyDirected = audioTestMode != null || normalBabyMode
                val wholeUtteranceTest = when (audioTestMode) {
                    KokoroAudioTestMode.WHOLE_070_STREAM,
                    KokoroAudioTestMode.WHOLE_100_STREAM,
                    KokoroAudioTestMode.WHOLE_070_STATIC,
                    KokoroAudioTestMode.WHOLE_058_STATIC,
                    -> true
                    else -> false
                }
                val staticPlayback = when (audioTestMode) {
                    KokoroAudioTestMode.WHOLE_070_STATIC,
                    KokoroAudioTestMode.WHOLE_058_STATIC,
                    KokoroAudioTestMode.SPLIT_058_STATIC,
                    -> true
                    else -> false
                }
                val staticPauseMs = when {
                    staticPlayback && !wholeUtteranceTest && babyDirected -> BABY_PHRASE_PAUSE_MS
                    else -> 0
                }
                val effectiveSpeed = when (audioTestMode) {
                    KokoroAudioTestMode.PIPELINE_058,
                    KokoroAudioTestMode.FULL_BUFFER_058,
                    KokoroAudioTestMode.WHOLE_058_STATIC,
                    KokoroAudioTestMode.SPLIT_058_STATIC,
                    -> 0.58f
                    KokoroAudioTestMode.FULL_BUFFER_070,
                    KokoroAudioTestMode.WHOLE_070_STREAM,
                    KokoroAudioTestMode.WHOLE_070_STATIC,
                    -> 0.70f
                    KokoroAudioTestMode.WHOLE_100_STREAM -> 1.00f
                    null -> if (normalBabyMode) (speed * BABY_SENTENCE_SPEED_FACTOR).coerceIn(0.82f, 1.00f) else speed.coerceIn(0.70f, 1.10f)
                }
                val phrases = when {
                    wholeUtteranceTest -> listOf(speechText.trim())
                    normalBabyMode -> splitBabyPhrases(speechText)
                    audioTestMode != null -> splitBabyPhrases(speechText)
                    else -> splitParentPhrases(speechText)
                }
                require(phrases.isNotEmpty()) { "Kokoro received empty speech text." }

                val sampleRate = active.tts.sampleRate()
                require(sampleRate == 24000) { "Unexpected Kokoro sample rate: $sampleRate" }

                val initialLookaheadPhrases = when (audioTestMode) {
                    KokoroAudioTestMode.PIPELINE_058 -> 1
                    KokoroAudioTestMode.FULL_BUFFER_058,
                    KokoroAudioTestMode.FULL_BUFFER_070,
                    KokoroAudioTestMode.SPLIT_058_STATIC,
                    -> phrases.size
                    KokoroAudioTestMode.WHOLE_070_STREAM,
                    KokoroAudioTestMode.WHOLE_100_STREAM,
                    KokoroAudioTestMode.WHOLE_070_STATIC,
                    KokoroAudioTestMode.WHOLE_058_STATIC,
                    -> 1
                    null -> 1
                }
                val streamPauseMs = when {
                    normalBabyMode -> BABY_SENTENCE_PAUSE_MS
                    audioTestMode != null && !wholeUtteranceTest -> BABY_PHRASE_PAUSE_MS
                    else -> 0
                }

                val queue = ArrayBlockingQueue<StreamItem>(STREAM_QUEUE_CAPACITY)
                val playbackDone = CountDownLatch(1)
                val playbackResult = AtomicReference<PlaybackResult?>(null)
                val playbackError = AtomicReference<Throwable?>(null)

                playbackWorker.execute {
                    try {
                        playbackResult.set(
                            if (staticPlayback) {
                                playStaticBuffer(
                                    id = id,
                                    queue = queue,
                                    sampleRate = sampleRate,
                                    started = started,
                                    pauseBetweenChunksMs = staticPauseMs,
                                )
                            } else {
                                playStream(
                                    id = id,
                                    queue = queue,
                                    sampleRate = sampleRate,
                                    pauseBetweenChunksMs = streamPauseMs,
                                    started = started,
                                    initialLookaheadPhrases = initialLookaheadPhrases,
                                )
                            },
                        )
                    } catch (error: Throwable) {
                        playbackError.set(error)
                    } finally {
                        playbackDone.countDown()
                    }
                }

                var generatedPhraseCount = 0
                var generatedSamples = 0L
                var firstPhraseReadyMs = 0L
                var maxPeak = 0f
                val phraseGenerationMs = ArrayList<Long>()
                val generationStarted = System.nanoTime()

                // generateWithCallback() is intentionally not used on Android because that JNI
                // callback path crashed on real devices. Whole-utterance diagnostic modes call the
                // safe synchronous generate() once; normal and split-static modes generate tiny phrases.
                for (phrase in phrases) {
                    if (closed || requestId != id) break
                    val phraseStarted = System.nanoTime()
                    val generated = active.tts.generate(
                        text = phrase,
                        sid = KOKORO_SPEAKER_ID,
                        speed = effectiveSpeed,
                    )
                    val phraseMs = (System.nanoTime() - phraseStarted) / 1_000_000L
                    phraseGenerationMs += phraseMs
                    require(
                        generated.sampleRate == sampleRate &&
                            generated.samples.isNotEmpty() &&
                            generated.samples.all { it.isFinite() },
                    ) { "Kokoro returned invalid phrase audio." }

                    val smoothed = smoothPhraseEdges(generated.samples, sampleRate)
                    val peak = smoothed.maxOfOrNull { abs(it) } ?: 0f
                    if (peak > maxPeak) maxPeak = peak
                    val pcm = toPcm16(smoothed)
                    if (firstPhraseReadyMs == 0L) {
                        firstPhraseReadyMs = (System.nanoTime() - started) / 1_000_000L
                    }
                    generatedPhraseCount += 1
                    generatedSamples += pcm.size
                    if (!offerWhileActive(queue, StreamItem.Chunk(pcm), id)) break
                }
                val generationMs = (System.nanoTime() - generationStarted) / 1_000_000L

                if (requestId == id && !closed) {
                    offerWhileActive(queue, StreamItem.End, id)
                }

                val playbackPauseMs = streamPauseMs
                val pauseDurationMs = if (generatedPhraseCount > 1 && playbackPauseMs > 0) {
                    (generatedPhraseCount - 1).toLong() * playbackPauseMs
                } else {
                    0L
                }
                val audioDurationMs = generatedSamples * 1000L / sampleRate + pauseDurationMs
                val waitMs = (audioDurationMs + STREAM_COMPLETION_MARGIN_MS).coerceAtLeast(STREAM_COMPLETION_MIN_MS)
                check(playbackDone.await(waitMs, TimeUnit.MILLISECONDS)) {
                    "Kokoroフレーズ再生がタイムアウトしました。"
                }
                playbackError.get()?.let { throw it }
                val result = playbackResult.get() ?: PlaybackResult(
                    firstAudioMs = 0L,
                    totalMs = (System.nanoTime() - started) / 1_000_000L,
                    underruns = 0,
                    waitForChunksMs = 0L,
                    waitAfterStartMs = 0L,
                    playedSamples = 0L,
                    prefilled = 0,
                    prefetchedPhrases = 0,
                )
                val testName = audioTestMode?.savedValue ?: "NORMAL"
                val playbackKind = if (staticPlayback) "STATIC" else "STREAM"

                DiagnosticStore.mark(
                    context,
                    "kokoro_playback_health",
                    "test=$testName playback=$playbackKind threads=${active.threadCount} " +
                        "lookahead=$initialLookaheadPhrases prefetched=${result.prefetchedPhrases} " +
                        "underruns=${result.underruns} waitAfterStartMs=${result.waitAfterStartMs} " +
                        "prefilled=${result.prefilled} speed=$effectiveSpeed",
                )
                if (audioTestMode != null) {
                    DiagnosticStore.mark(
                        context,
                        "kokoro_audio_test",
                        "mode=$testName whole=$wholeUtteranceTest playback=$playbackKind speed=$effectiveSpeed " +
                            "textChars=${speechText.length} textHash=${speechText.hashCode()} generateCalls=$generatedPhraseCount " +
                            "pauseMs=$staticPauseMs threads=${active.threadCount} firstAudioMs=${result.firstAudioMs} " +
                            "generationMs=$generationMs underruns=${result.underruns} peak=$maxPeak",
                    )
                }
                DiagnosticStore.mark(
                    context,
                    "kokoro_phrase_pipeline",
                    "test=$testName babyDirected=$babyDirected segmentation=${if (audioTestMode == null) "SENTENCES" else "PHRASES"} speed=$effectiveSpeed phrases=$generatedPhraseCount " +
                        "threads=${active.threadCount} cpuMode=${runtimeProfile.mode.savedValue} " +
                        "firstPhraseReadyMs=$firstPhraseReadyMs generationMs=$generationMs " +
                        "phraseGenerationMs=${phraseGenerationMs.joinToString(",")} generatedSamples=$generatedSamples " +
                        "playedSamples=${result.playedSamples} peak=$maxPeak",
                )
                result.firstAudioMs to result.totalMs
            }.onSuccess { value ->
                main.post {
                    if (!closed && requestId == id) {
                        requestId = null
                        onAmplitude(0f)
                        onDone(value.first, value.second)
                    }
                }
            }.onFailure { error ->
                main.post {
                    if (!closed && requestId == id) {
                        requestId = null
                        onAmplitude(0f)
                        onError("Kokoro生成に失敗しました: ${error.message ?: error.javaClass.simpleName}")
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        requestId = null
        main.post { onAmplitude(0f) }
        track?.runCatching { pause(); flush() }
    }

    fun resetModel() {
        if (closed) return
        stop()
        worker.execute { engine?.close(); engine = null }
    }

    fun shutdown() {
        if (closed) return
        closed = true
        requestId = null
        track?.runCatching { pause(); flush() }
        worker.execute { engine?.close(); engine = null }
        worker.shutdown()
        playbackWorker.shutdown()
        main.removeCallbacksAndMessages(null)
    }

    private fun playStream(
        id: String,
        queue: ArrayBlockingQueue<StreamItem>,
        sampleRate: Int,
        pauseBetweenChunksMs: Int,
        started: Long,
        initialLookaheadPhrases: Int,
    ): PlaybackResult {
        val player = createStreamTrack(sampleRate)
        track = player
        var firstAudioMs = 0L
        var prefilled = 0
        var waitForChunksMs = 0L
        var waitAfterStartMs = 0L
        var totalScheduledSamples = 0L
        val segments = ArrayList<ScheduledSegment>()
        var lastMouthWindow = -1L
        val mouthWindowSamples = max(1, sampleRate / 20)
        val playbackDeadline = System.nanoTime() + STREAM_PLAYBACK_HARD_TIMEOUT_MS * 1_000_000L

        fun updateMouthFromPlayback() {
            if (player.playState != AudioTrack.PLAYSTATE_PLAYING) return
            val head = player.playbackHeadPosition.toLong() and 0xffffffffL
            if (head <= 0L) return
            val window = head / mouthWindowSamples
            if (window == lastMouthWindow) return
            lastMouthWindow = window
            val segment = segments.lastOrNull { head >= it.start && head < it.start + it.samples.size }
            val amplitude = if (segment == null) {
                0f
            } else {
                val local = (head - segment.start).toInt().coerceIn(0, segment.samples.lastIndex)
                val count = minOf(mouthWindowSamples, segment.samples.size - local)
                chunkAmplitude(segment.samples, local, count)
            }
            main.post {
                if (!closed && requestId == id) onAmplitude(amplitude)
            }
        }

        fun register(samples: ShortArray) {
            segments.add(ScheduledSegment(totalScheduledSamples, samples))
            totalScheduledSamples += samples.size
        }

        fun writeBlocking(samples: ShortArray, startOffset: Int = 0) {
            var offset = startOffset
            while (offset < samples.size && requestId == id && !closed) {
                check(System.nanoTime() < playbackDeadline) { "音声再生がタイムアウトしました。" }
                val requested = minOf(STREAM_WRITE_CHUNK_SAMPLES, samples.size - offset)
                val n = player.write(samples, offset, requested, AudioTrack.WRITE_BLOCKING)
                check(n > 0) { "音声出力エラー: $n" }
                offset += n
                updateMouthFromPlayback()
            }
        }

        fun awaitItem(): StreamItem? {
            val waitStarted = System.nanoTime()
            while (requestId == id && !closed) {
                check(System.nanoTime() < playbackDeadline) { "音声ストリーム待機がタイムアウトしました。" }
                val item = queue.poll(STREAM_QUEUE_POLL_MS, TimeUnit.MILLISECONDS)
                updateMouthFromPlayback()
                if (item != null) {
                    val waited = (System.nanoTime() - waitStarted) / 1_000_000L
                    waitForChunksMs += waited
                    if (firstAudioMs > 0L) waitAfterStartMs += waited
                    return item
                }
            }
            val waited = (System.nanoTime() - waitStarted) / 1_000_000L
            waitForChunksMs += waited
            if (firstAudioMs > 0L) waitAfterStartMs += waited
            return null
        }

        fun writePauseIfNeeded() {
            if (pauseBetweenChunksMs <= 0) return
            val pause = ShortArray((sampleRate * pauseBetweenChunksMs / 1000f).toInt())
            register(pause)
            writeBlocking(pause)
        }

        try {
            check(player.state == AudioTrack.STATE_INITIALIZED) { "音声出力を初期化できません。" }
            val prefetched = ArrayList<ShortArray>()
            var sourceEnded = false
            while (prefetched.size < initialLookaheadPhrases && !sourceEnded) {
                when (val item = awaitItem()) {
                    is StreamItem.Chunk -> prefetched += item.samples
                    StreamItem.End, null -> sourceEnded = true
                }
            }
            if (prefetched.isEmpty()) {
                return PlaybackResult(
                    firstAudioMs = 0L,
                    totalMs = (System.nanoTime() - started) / 1_000_000L,
                    underruns = 0,
                    waitForChunksMs = waitForChunksMs,
                    waitAfterStartMs = waitAfterStartMs,
                    playedSamples = 0L,
                    prefilled = 0,
                    prefetchedPhrases = 0,
                )
            }

            val firstSamples = prefetched.first()
            register(firstSamples)
            val prefillTarget = minOf(
                firstSamples.size,
                max(STREAM_WRITE_CHUNK_SAMPLES, sampleRate * INITIAL_PREFILL_MS / 1000),
            )
            prefilled = player.write(firstSamples, 0, prefillTarget, AudioTrack.WRITE_BLOCKING)
            check(prefilled > 0) { "音声の事前バッファリングに失敗しました: $prefilled" }
            if (requestId != id || closed) {
                return PlaybackResult(
                    firstAudioMs = 0L,
                    totalMs = (System.nanoTime() - started) / 1_000_000L,
                    underruns = 0,
                    waitForChunksMs = waitForChunksMs,
                    waitAfterStartMs = waitAfterStartMs,
                    playedSamples = totalScheduledSamples,
                    prefilled = prefilled,
                    prefetchedPhrases = prefetched.size,
                )
            }

            val underrunsBefore = player.underrunCount
            player.play()
            firstAudioMs = (System.nanoTime() - started) / 1_000_000L
            writeBlocking(firstSamples, prefilled)

            prefetched.drop(1).forEach { samples ->
                writePauseIfNeeded()
                register(samples)
                writeBlocking(samples)
            }

            var finished = sourceEnded
            while (!finished && requestId == id && !closed) {
                when (val item = awaitItem()) {
                    is StreamItem.Chunk -> {
                        writePauseIfNeeded()
                        register(item.samples)
                        writeBlocking(item.samples)
                    }
                    StreamItem.End, null -> finished = true
                }
            }

            while (
                requestId == id &&
                !closed &&
                (player.playbackHeadPosition.toLong() and 0xffffffffL) < totalScheduledSamples
            ) {
                check(System.nanoTime() < playbackDeadline) { "音声再生がタイムアウトしました。" }
                updateMouthFromPlayback()
                Thread.sleep(20)
            }

            val underruns = (player.underrunCount - underrunsBefore).coerceAtLeast(0)
            return PlaybackResult(
                firstAudioMs = firstAudioMs,
                totalMs = (System.nanoTime() - started) / 1_000_000L,
                underruns = underruns,
                waitForChunksMs = waitForChunksMs,
                waitAfterStartMs = waitAfterStartMs,
                playedSamples = totalScheduledSamples,
                prefilled = prefilled,
                prefetchedPhrases = prefetched.size,
            )
        } finally {
            if (track === player) track = null
            runCatching { player.stop() }
            player.release()
        }
    }

    private fun playStaticBuffer(
        id: String,
        queue: ArrayBlockingQueue<StreamItem>,
        sampleRate: Int,
        started: Long,
        pauseBetweenChunksMs: Int,
    ): PlaybackResult {
        val waitStarted = System.nanoTime()
        val chunks = ArrayList<ShortArray>()
        var ended = false
        while (!ended && requestId == id && !closed) {
            when (val item = queue.poll(STREAM_QUEUE_POLL_MS, TimeUnit.MILLISECONDS)) {
                is StreamItem.Chunk -> chunks += item.samples
                StreamItem.End -> ended = true
                null -> Unit
            }
            check((System.nanoTime() - started) / 1_000_000L < STREAM_PLAYBACK_HARD_TIMEOUT_MS) {
                "静的音声の生成待機がタイムアウトしました。"
            }
        }
        val waitForChunksMs = (System.nanoTime() - waitStarted) / 1_000_000L
        if (chunks.isEmpty() || requestId != id || closed) {
            return PlaybackResult(0L, waitForChunksMs, 0, waitForChunksMs, 0L, 0L, 0, chunks.size)
        }

        val pauseSamples = if (pauseBetweenChunksMs > 0 && chunks.size > 1) {
            (sampleRate * pauseBetweenChunksMs / 1000f).toInt().coerceAtLeast(0)
        } else {
            0
        }
        val pauseCount = (chunks.size - 1).coerceAtLeast(0)
        val totalSamplesLong = chunks.sumOf { it.size.toLong() } + pauseSamples.toLong() * pauseCount
        check(totalSamplesLong in 1..Int.MAX_VALUE.toLong()) { "静的音声バッファが大きすぎます。" }
        val combined = ShortArray(totalSamplesLong.toInt())
        var destination = 0
        chunks.forEachIndexed { index, chunk ->
            chunk.copyInto(combined, destination)
            destination += chunk.size
            if (pauseSamples > 0 && index < chunks.lastIndex) {
                destination += pauseSamples
            }
        }

        val staticAttempt = runCatching { createStaticTrack(sampleRate, combined.size) }
        val staticPlayer = staticAttempt.getOrNull()
        if (staticPlayer == null || staticPlayer.state != AudioTrack.STATE_INITIALIZED) {
            val state = staticPlayer?.state ?: -1
            val reason = staticAttempt.exceptionOrNull()?.javaClass?.simpleName ?: "state_$state"
            staticPlayer?.release()
            DiagnosticStore.mark(
                context,
                "kokoro_static_fallback",
                "reason=$reason samples=${combined.size} bytes=${combined.size.toLong() * 2L}",
            )
            val fallbackQueue = ArrayBlockingQueue<StreamItem>(2)
            check(fallbackQueue.offer(StreamItem.Chunk(combined)))
            check(fallbackQueue.offer(StreamItem.End))
            return playStream(
                id = id,
                queue = fallbackQueue,
                sampleRate = sampleRate,
                pauseBetweenChunksMs = 0,
                started = started,
                initialLookaheadPhrases = 1,
            )
        }

        val player = staticPlayer
        track = player
        val playbackDeadline = System.nanoTime() + STREAM_PLAYBACK_HARD_TIMEOUT_MS * 1_000_000L
        try {
            var written = 0
            while (written < combined.size && requestId == id && !closed) {
                val n = player.write(combined, written, combined.size - written, AudioTrack.WRITE_BLOCKING)
                check(n > 0) { "静的音声出力エラー: $n" }
                written += n
            }
            check(written == combined.size) { "静的音声の事前書き込みが完了しませんでした。" }
            if (requestId != id || closed) {
                return PlaybackResult(0L, (System.nanoTime() - started) / 1_000_000L, 0, waitForChunksMs, 0L, written.toLong(), written, chunks.size)
            }

            val underrunsBefore = player.underrunCount
            player.play()
            val firstAudioMs = (System.nanoTime() - started) / 1_000_000L
            val mouthWindowSamples = max(1, sampleRate / 20)
            var lastMouthWindow = -1L
            while (
                requestId == id &&
                !closed &&
                (player.playbackHeadPosition.toLong() and 0xffffffffL) < combined.size
            ) {
                check(System.nanoTime() < playbackDeadline) { "静的音声再生がタイムアウトしました。" }
                val head = player.playbackHeadPosition.toLong() and 0xffffffffL
                val window = head / mouthWindowSamples
                if (window != lastMouthWindow && head < combined.size) {
                    lastMouthWindow = window
                    val local = head.toInt().coerceIn(0, combined.lastIndex)
                    val count = minOf(mouthWindowSamples, combined.size - local)
                    val amplitude = chunkAmplitude(combined, local, count)
                    main.post { if (!closed && requestId == id) onAmplitude(amplitude) }
                }
                Thread.sleep(20)
            }
            val underruns = (player.underrunCount - underrunsBefore).coerceAtLeast(0)
            return PlaybackResult(
                firstAudioMs = firstAudioMs,
                totalMs = (System.nanoTime() - started) / 1_000_000L,
                underruns = underruns,
                waitForChunksMs = waitForChunksMs,
                waitAfterStartMs = 0L,
                playedSamples = combined.size.toLong(),
                prefilled = written,
                prefetchedPhrases = chunks.size,
            )
        } finally {
            if (track === player) track = null
            runCatching { player.stop() }
            player.release()
        }
    }

    private fun offerWhileActive(
        queue: ArrayBlockingQueue<StreamItem>,
        item: StreamItem,
        id: String,
    ): Boolean {
        while (!closed && requestId == id) {
            if (queue.offer(item, STREAM_QUEUE_POLL_MS, TimeUnit.MILLISECONDS)) return true
        }
        return false
    }

    private fun isBabyAudienceMode(): Boolean {
        val value = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(AUDIENCE_MODE_KEY, null)
        return value == null || value == BABY_AUDIENCE_VALUE
    }

    private fun consumeAudioTestMode(): KokoroAudioTestMode? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val saved = preferences.getString(KOKORO_AUDIO_TEST_ONCE_KEY, null)
        if (saved != null) preferences.edit().remove(KOKORO_AUDIO_TEST_ONCE_KEY).commit()
        return KokoroAudioTestMode.fromSaved(saved)
    }

    private fun resolveRuntimeProfile(): RuntimeProfile {
        val mode = KokoroCpuMode.fromSaved(
            context
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KOKORO_CPU_MODE_KEY, null),
        )
        val processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        val totalRamMb = if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem / MIB
        } else {
            0L
        }
        val threadCount = KokoroThreadPolicy.selectThreads(mode, processorCount, totalRamMb)
        return RuntimeProfile(mode, processorCount, totalRamMb, threadCount)
    }

    private fun splitBabyPhrases(text: String): List<String> =
        splitSentences(text, MAX_BABY_PHRASES)

    private fun splitParentPhrases(text: String): List<String> =
        splitSentences(text, MAX_PARENT_PHRASES)

    private fun splitSentences(text: String, maxSegments: Int): List<String> {
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isBlank()) return emptyList()
        val raw = cleaned
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (raw.size <= maxSegments) return raw
        return raw.take(maxSegments - 1) + raw.drop(maxSegments - 1).joinToString(" ")
    }

    private fun createStreamTrack(rate: Int) = AudioTrack.Builder()
        .setAudioAttributes(audioAttributes())
        .setAudioFormat(audioFormat(rate))
        .setBufferSizeInBytes(
            max(
                AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                rate,
            ),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private fun createStaticTrack(rate: Int, sampleCount: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(0)
        val requiredBytes = (sampleCount.toLong() * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return AudioTrack.Builder()
            .setAudioAttributes(audioAttributes())
            .setAudioFormat(audioFormat(rate))
            .setBufferSizeInBytes(max(minBuffer, requiredBytes))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }

    private fun audioAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun audioFormat(rate: Int) = AudioFormat.Builder()
        .setSampleRate(rate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()

    private class Engine(context: Context, val threadCount: Int) : AutoCloseable {
        private val dir = KokoroModelStore.directory(context)
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
        override fun close() { tts.release() }
    }

    private sealed class StreamItem {
        class Chunk(val samples: ShortArray) : StreamItem()
        object End : StreamItem()
    }

    private data class ScheduledSegment(
        val start: Long,
        val samples: ShortArray,
    )

    private data class PlaybackResult(
        val firstAudioMs: Long,
        val totalMs: Long,
        val underruns: Int,
        val waitForChunksMs: Long,
        val waitAfterStartMs: Long,
        val playedSamples: Long,
        val prefilled: Int,
        val prefetchedPhrases: Int,
    )

    private data class RuntimeProfile(
        val mode: KokoroCpuMode,
        val processorCount: Int,
        val totalRamMb: Long,
        val threadCount: Int,
    )

    private companion object {
        const val PREFERENCES_NAME = "emma_speech"
        const val AUDIENCE_MODE_KEY = "audience_mode"
        const val KOKORO_CPU_MODE_KEY = "kokoro_cpu_mode"
        const val BABY_AUDIENCE_VALUE = "BABY"
        const val KOKORO_SPEAKER_ID = 3 // af_heart in kokoro-multi-lang-v1_0
        const val BABY_SENTENCE_SPEED_FACTOR = 0.94f
        const val BABY_SENTENCE_PAUSE_MS = 780
        const val BABY_PHRASE_PAUSE_MS = 520
        const val MAX_BABY_PHRASES = 7
        const val MAX_PARENT_PHRASES = 5
        const val PHRASE_EDGE_FADE_MS = 6
        const val STREAM_QUEUE_CAPACITY = 8
        const val STREAM_QUEUE_POLL_MS = 20L
        const val STREAM_WRITE_CHUNK_SAMPLES = 2048
        const val INITIAL_PREFILL_MS = 400
        const val STREAM_COMPLETION_MIN_MS = 15_000L
        const val STREAM_COMPLETION_MARGIN_MS = 15_000L
        const val STREAM_PLAYBACK_HARD_TIMEOUT_MS = 60_000L
        const val MIB = 1024L * 1024L

        fun smoothPhraseEdges(samples: FloatArray, sampleRate: Int): FloatArray {
            val out = samples.copyOf()
            val peak = out.maxOfOrNull { abs(it) } ?: 0f
            if (peak > 0.98f) {
                val scale = 0.96f / peak
                for (i in out.indices) out[i] *= scale
            }

            val requestedFade = (sampleRate * PHRASE_EDGE_FADE_MS / 1000f).toInt().coerceAtLeast(1)
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

        fun toPcm16(s: FloatArray) = ShortArray(s.size) {
            (s[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }

        fun chunkAmplitude(samples: ShortArray, offset: Int, count: Int): Float {
            if (count <= 0) return 0f
            var peak = 0
            val end = minOf(samples.size, offset + count)
            for (i in offset until end) peak = max(peak, abs(samples[i].toInt()))
            return (peak / 12000f).coerceIn(0f, 1f)
        }
    }
}
