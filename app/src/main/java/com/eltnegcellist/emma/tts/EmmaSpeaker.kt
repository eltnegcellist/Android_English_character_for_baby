package com.eltnegcellist.emma.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class EmmaSpeaker(
    context: Context,
    onDone: () -> Unit,
    onError: (String) -> Unit,
    private val onVoicesReady: (List<String>) -> Unit = {},
) : TextToSpeech.OnInitListener {
    private val doneCallback: () -> Unit = onDone
    private val errorCallback: (String) -> Unit = onError
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var activeUtterance: String? = null
    @Volatile private var closed = false
    @Volatile private var ready: Boolean = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        handler.post { if (!closed) initializeVoice(status) }
    }

    private fun initializeVoice(status: Int) {
        if (closed) return
        if (status != TextToSpeech.SUCCESS) {
            errorCallback("English TTS could not be initialized.")
            return
        }

        val languageResult: Int = tts.setLanguage(Locale.US)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            errorCallback("An English (US) TTS voice is not installed on this device.")
            return
        }

        val preferredVoice = chooseStableEnglishVoice()
        if (preferredVoice == null) {
            errorCallback("Androidの英語音声を利用できません。端末の音声読み上げ設定で英語音声を追加してください。")
            return
        }

        tts.voice = preferredVoice
        tts.setSpeechRate(0.75f)
        tts.setPitch(1.0f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = complete(utteranceId, null)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = complete(utteranceId, "Emma TTS playback failed.")

            override fun onError(utteranceId: String?, errorCode: Int) =
                complete(utteranceId, "Emma TTS playback failed ($errorCode).")

            override fun onStop(utteranceId: String?, interrupted: Boolean) =
                complete(utteranceId, "音声の再生が中断されました。")
        })
        ready = true
        onVoicesReady(availableEnglishVoices().map { it.name })
    }

    fun isReady(): Boolean = ready && !closed

    fun speak(text: String, rate: Float = 0.75f, voiceName: String = ""): Boolean {
        if (closed) return false
        if (!ready) {
            errorCallback("Android English TTS is still starting.")
            return false
        }

        val voices = availableEnglishVoices()
        val voice = voices.firstOrNull { it.name == voiceName } ?: voices.firstOrNull()
        if (voice == null || tts.setVoice(voice) == TextToSpeech.ERROR ||
            tts.setSpeechRate(rate.coerceIn(0.55f, 1.05f)) == TextToSpeech.ERROR) {
            errorCallback("音声または読み上げ速度を設定できませんでした。")
            return false
        }
        val params = Bundle()
        val id = java.util.UUID.randomUUID().toString()
        activeUtterance = id
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result == TextToSpeech.ERROR) {
            activeUtterance = null
            errorCallback("Emma TTS request failed.")
            return false
        }
        return true
    }

    private fun complete(id: String?, error: String?) {
        // Check identity when the main thread consumes the event, not on the TTS binder thread.
        handler.post {
            if (!closed && id != null && id == activeUtterance) {
                activeUtterance = null
                if (error == null) doneCallback() else errorCallback(error)
            }
        }
    }

    fun stop() {
        activeUtterance = null
        tts.stop()
    }

    fun shutdown() {
        closed = true
        ready = false
        stop()
        handler.removeCallbacksAndMessages(null)
        tts.shutdown()
    }

    private fun chooseStableEnglishVoice(): Voice? {
        val voices = availableEnglishVoices()
        return voices.firstOrNull { !it.isNetworkConnectionRequired } ?: voices.firstOrNull()
    }

    private fun availableEnglishVoices(): List<Voice> = tts.voices.orEmpty()
        .filter { it.locale.language == Locale.ENGLISH.language && it.locale.country == Locale.US.country }
        .sortedWith(
            compareBy<Voice> { it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
                .thenBy { it.latency }
                .thenBy { it.name },
        )
}

