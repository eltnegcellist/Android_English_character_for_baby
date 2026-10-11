package com.eltnegcellist.emma.audio

/** Endpoint policy only; the recurrent model and captured PCM are separate. */
internal class SileroEndpointPolicy(private val silenceMillis: Int = 1_100) {
    private var inSpeech = false
    private var onset = 0
    private var voiced = 0
    private var silence = 0

    fun reset() { inSpeech = false; onset = 0; voiced = 0; silence = 0 }

    fun process(probability: Float, sampleCount: Int = 512): VoiceActivityEvent? {
        require(probability.isFinite() && probability in 0f..1f && sampleCount > 0)
        val isVoice = probability >= if (inSpeech) 0.35f else 0.5f
        if (!inSpeech) {
            onset = if (isVoice) onset + sampleCount else 0
            if (onset >= 160 * 16) {
                inSpeech = true; voiced = onset; onset = 0; silence = 0
                return VoiceActivityEvent.SpeechStarted
            }
        } else if (isVoice) {
            voiced += sampleCount; silence = 0
        } else {
            silence += sampleCount
            if (voiced < 450 * 16 && silence >= 700 * 16) {
                reset()
            } else if (voiced >= 450 * 16 && silence >= silenceMillis * 16) {
                val event = VoiceActivityEvent.Endpoint(voiced.toLong() / 16, silence.toLong() / 16)
                reset()
                return event
            }
        }
        return null
    }
}
