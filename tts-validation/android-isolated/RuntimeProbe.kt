package validation.tts

import java.io.File
import java.util.Base64
import java.util.concurrent.*

private fun waitFor(condition: () -> Boolean) {
    val deadline=System.nanoTime()+5_000_000_000L
    while(!condition()) { check(System.nanoTime()<deadline) { "Timed out" }; Thread.sleep(1) }
}

private class QueueDispatcher : Dispatcher {
    val queue=ConcurrentLinkedQueue<()->Unit>()
    override fun post(action: ()->Unit) { queue.add(action) }
    fun drain() { while(true) (queue.poll() ?: break).invoke() }
}

private fun controllerProbe(): Int {
    val dispatch=QueueDispatcher(); val worker=Executors.newSingleThreadExecutor {r->Thread(r).apply {isDaemon=true}}
    var done=0; var errors=0; var created=0; var released=0; var played=0
    val plans=mutableListOf<String>(); val generationEntered=CountDownLatch(1)
    val releaseGeneration=CountDownLatch(1); var blockGeneration=true
    val backendFactory={
        created++
        object: Backend {
            override fun generate(chunks: List<String>,cancellation: Cancellation): FloatArray {
                plans.add(chunks.single())
                if(blockGeneration) { generationEntered.countDown(); check(releaseGeneration.await(5,TimeUnit.SECONDS)) }
                cancellation.check(); return floatArrayOf(.1f,-.1f)
            }
            override fun close() { released++ }
        }
    }
    var blockPlayback=false; var playbackEntered=CountDownLatch(1)
    var releasePlayback=CountDownLatch(1); var closedSinks=0
    val sinkFactory={ object: Sink {
        var closed=false
        override fun play(pcm: ShortArray,cancellation: Cancellation,amplitude: (Float)->Unit): Long {
            played++; amplitude(.25f)
            if(blockPlayback) { playbackEntered.countDown(); check(releasePlayback.await(5,TimeUnit.SECONDS)) }
            cancellation.check(); return 1
        }
        @Synchronized override fun close() { if(!closed) {closed=true;closedSinks++;releasePlayback.countDown()} }
    } }
    var installed=true
    val core=SpeakerCore(Planner { if(it=="bad") error("G2P unresolved") else listOf(it) },backendFactory,sinkFactory,dispatch,{installed},{a,b,c->check(a>=0 && b>=0 && c>=0);done++},{errors++},worker=worker)
    check(!core.speak(" ")); installed=false; check(!core.speak("one")); installed=true
    check(core.speak("old")); check(generationEntered.await(5,TimeUnit.SECONDS)); core.stop()
    check(core.speak("new")); blockGeneration=false; releaseGeneration.countDown()
    worker.submit {}.get(5,TimeUnit.SECONDS); dispatch.drain()
    check(done==1 && errors==0 && played==1 && plans==listOf("old","new"))
    // A finished request whose UI callback is queued must be suppressed by stop.
    check(core.speak("queued")); worker.submit {}.get(5,TimeUnit.SECONDS); core.stop(); dispatch.drain(); check(done==1)
    for(i in 0 until 100) {
        blockPlayback=true; playbackEntered=CountDownLatch(1); releasePlayback=CountDownLatch(1)
        check(core.speak("play-$i")); check(playbackEntered.await(5,TimeUnit.SECONDS)); core.stop()
        worker.submit {}.get(5,TimeUnit.SECONDS); dispatch.drain(); check(done==i+1 && errors==0)
        blockPlayback=false
        check(core.speak("restart-$i")); worker.submit {}.get(5,TimeUnit.SECONDS); dispatch.drain(); check(done==i+2)
    }
    check(core.speak("bad")); worker.submit {}.get(5,TimeUnit.SECONDS); dispatch.drain(); check(errors==1)
    val oldCreated=created; core.resetModel(); check(core.speak("after-reset")); worker.submit {}.get(5,TimeUnit.SECONDS);dispatch.drain()
    check(created==oldCreated+1 && released==1 && done==102)
    check(core.speak("before-shutdown")); worker.submit {}.get(5,TimeUnit.SECONDS); core.shutdown()
    check(worker.awaitTermination(5,TimeUnit.SECONDS)); dispatch.drain()
    check(done==102 && errors==1 && released==created && !core.speak("after-shutdown"))
    check(closedSinks==played)
    return 100
}

fun main(args: Array<String>) {
    check(args.size==3)
    // Boundary and float-to-PCM expectations are independent of runtime output.
    val internal4800=floatArrayOf(.1f)+FloatArray(4800)+floatArrayOf(.2f)
    check(AudioContract.shortenPauses(internal4800).size==962)
    check(AudioContract.shortenPauses(floatArrayOf(.1f)+FloatArray(4800)).size==4801)
    check(AudioContract.shortenPauses(floatArrayOf(.1f)+FloatArray(4801)).size==961)
    check(AudioContract.shortenPauses(FloatArray(100){.02f}).size==100)
    check(AudioContract.pcm16(floatArrayOf(1f,-1f,.5f,-.5f)).contentEquals(shortArrayOf(32767,-32767,16383,-16383)))
    check(runCatching {AudioContract.pcm16(floatArrayOf(Float.NaN))}.isFailure)
    val controllerCycles=controllerProbe()
    val decode=Base64.getDecoder()
    val plans=File(args[1]).readLines().map { line -> line.split('\t').drop(1).map { String(decode.decode(it),Charsets.UTF_8) } }
    val rows=mutableListOf<String>(); var cancelled=0; var regenerated=0; var maxCancelMs=0L
    val started=System.nanoTime()
    OnnxBackend(File(args[0])).use { backend ->
        for((i,plan) in plans.withIndex()) {
            val y=backend.generate(plan,Cancellation());check(y.isNotEmpty() && y.all {it.isFinite()})
            rows.add("{\"index\":$i,\"samples\":${y.size},\"duration_seconds\":${y.size/24000.0}}")
            if((i+1)%50==0) println("generated ${i+1}")
        }
        val executor=Executors.newSingleThreadExecutor()
        try {
            val longPlan=listOf(plans.first().joinToString(" ").repeat(3))
            for(i in 0 until 100) {
                val token=Cancellation();val entered=CountDownLatch(1)
                val future=executor.submit<Boolean> {
                    entered.countDown()
                    try {backend.generate(longPlan,token);false} catch(_:Cancelled) {true}
                }
                check(entered.await(5,TimeUnit.SECONDS)); Thread.sleep(30)
                val t=System.nanoTime();token.cancel();check(future.get(2,TimeUnit.SECONDS)) {"Generation finished before cancellation"}
                val elapsed=(System.nanoTime()-t)/1_000_000; maxCancelMs=maxOf(maxCancelMs,elapsed);cancelled++
                val y=backend.generate(plans[i%plans.size],Cancellation());check(y.isNotEmpty());regenerated++
            }
        } finally {executor.shutdownNow();check(executor.awaitTermination(5,TimeUnit.SECONDS))}
        val cancelledToken=Cancellation();cancelledToken.cancel()
        check(runCatching {backend.generate(plans.first(),cancelledToken)}.exceptionOrNull() is Cancelled)
        check(runCatching {backend.generate(listOf("☃"),Cancellation())}.isFailure)
        check(runCatching {backend.generate(listOf("a".repeat(400)),Cancellation())}.isFailure)
    }
    repeat(3) {OnnxBackend(File(args[0])).use {check(it.generate(plans.first(),Cancellation()).isNotEmpty())}}
    File(args[2]).writeText("""{"sample_rate":24000,"fixed_cases":${plans.size},"cancelled_runs":$cancelled,"regenerated_runs":$regenerated,"max_cancel_ms":$maxCancelMs,"controller_play_stop_restart_cycles":$controllerCycles,"session_close_reopen_cycles":3,"elapsed_seconds":${(System.nanoTime()-started)/1e9},"integration_approved":false,"limitations":["fixture plans, no Android G2P","Linux JVM ORT, not Android native","AudioTrack compile only, fake sink for controller"],"records":[${rows.joinToString(",")}]}""")
    println("Passed JVM runtime/controller probe")
}
