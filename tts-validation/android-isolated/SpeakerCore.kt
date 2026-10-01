package validation.tts

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

fun interface Planner { fun plan(text: String): List<String> }
fun interface Dispatcher { fun post(action: () -> Unit) }
interface Sink : AutoCloseable {
    fun play(pcm: ShortArray, cancellation: Cancellation, amplitude: (Float) -> Unit): Long
}

// Isolated controller: an Android-capable G2P Planner is still REQUIRED.
// Test fixture plans are deliberately not supplied as a production fallback.
class SpeakerCore(
    private val planner: Planner,
    private val backendFactory: () -> Backend,
    private val sinkFactory: () -> Sink,
    private val dispatcher: Dispatcher,
    private val installed: () -> Boolean,
    private val onDone: (Long,Long,Long) -> Unit,
    private val onError: (String) -> Unit,
    private val onAmplitude: (Float) -> Unit = {},
    private val worker: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    private val lock = Any()
    private var request: Cancellation? = null
    private var closed=false
    private var backend: Backend?=null // worker-owned
    fun speak(text: String): Boolean = synchronized(lock) {
        if(closed || text.isBlank() || !installed()) return false
        stopLocked(); val token=Cancellation(); request=token
        worker.execute {
            val started=System.nanoTime()
            try {
                token.check()
                val active=backend ?: backendFactory().also { backend=it }
                val generationStart=System.nanoTime()
                val plans=planner.plan(text.trim()); token.check()
                val samples=active.generate(plans,token)
                val generationMs=(System.nanoTime()-generationStart)/1_000_000
                token.check()
                val firstAudioMs=sinkFactory().use { sink ->
                    token.check()
                    val terminate={ sink.close() }; token.attach(terminate)
                    try {
                        val beforePlay=(System.nanoTime()-started)/1_000_000
                        beforePlay+sink.play(AudioContract.pcm16(samples),token) { amplitude ->
                            postCurrent(token) { onAmplitude(amplitude) }
                        }
                    } finally { token.detach(terminate) }
                }
                token.check(); val totalMs=(System.nanoTime()-started)/1_000_000
                postCurrent(token) { request=null; onAmplitude(0f); onDone(firstAudioMs,generationMs,totalMs) }
            } catch (_: Cancelled) { /* stable contract: no terminal callback */ }
            catch(e: Throwable) {
                postCurrent(token) { request=null; onAmplitude(0f); onError("Kitten TTS Nano生成に失敗しました: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
        true
    }
    private fun postCurrent(token: Cancellation, action: () -> Unit) {
        dispatcher.post { synchronized(lock) { if(!closed && request===token) action() } }
    }
    private fun stopLocked() {
        request?.cancel(); request=null
        dispatcher.post { synchronized(lock) { if(!closed) onAmplitude(0f) } }
    }
    fun stop() = synchronized(lock) { stopLocked() }
    fun resetModel() = synchronized(lock) {
        if(!closed) { stopLocked(); worker.execute { backend?.close(); backend=null } }
    }
    fun shutdown() = synchronized(lock) {
        if(!closed) {
            closed=true; stopLocked()
            worker.execute { backend?.close(); backend=null }
            worker.shutdown()
        }
    }
}
