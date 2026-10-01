package validation.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.abs
import kotlin.math.max

// Compile-tested adapter only. Real Android playback still needs instrumentation.
class AndroidSink : Sink {
    private var track: AudioTrack?=null
    private var closed=false
    override fun play(pcm: ShortArray,cancellation: Cancellation,amplitude: (Float)->Unit): Long {
        val started=System.nanoTime()
        cancellation.check()
        val player=synchronized(this) {
            check(!closed)
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(max(AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT),48000))
                .setTransferMode(AudioTrack.MODE_STREAM).build().also { track=it }
        }
        check(player.state==AudioTrack.STATE_INITIALIZED)
        var first=-1L; var offset=0
        try {
            player.play()
            while(offset<pcm.size) {
                cancellation.check()
                val n=player.write(pcm,offset,minOf(2048,pcm.size-offset),AudioTrack.WRITE_BLOCKING)
                cancellation.check(); check(n>0) { "AudioTrack.write: $n" }
                if(first<0) first=(System.nanoTime()-started)/1_000_000
                var peak=0; for(i in offset until offset+n) peak=max(peak,abs(pcm[i].toInt()))
                amplitude((peak/12000f).coerceIn(0f,1f)); offset+=n
            }
            val deadline=System.nanoTime()+60_000_000_000L
            while((player.playbackHeadPosition.toLong() and 0xffffffffL)<pcm.size) {
                cancellation.check(); check(System.nanoTime()<deadline) { "Playback timed out" }; Thread.sleep(20)
            }
            return first
        } finally { close() }
    }
    @Synchronized override fun close() {
        if(closed) return; closed=true
        track?.let { runCatching { it.pause() }; runCatching { it.flush() }; runCatching { it.release() } }; track=null
    }
}
