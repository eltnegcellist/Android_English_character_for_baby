package validation.tts

import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object NativeControllerProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size==4)
        val catalogue=File(args[1]).readLines().map {String(Base64.getDecoder().decode(it),Charsets.UTF_8)}
        val planner=NativeLitePlanner(File(args[0]),catalogue)
        val queue=ConcurrentLinkedQueue<()->Unit>()
        val worker=Executors.newSingleThreadExecutor {r->Thread(r).apply {isDaemon=true}}
        var done=0;var errors=0;var played=0;var closedSinks=0;var created=0;var released=0
        fun drain() {while(true) (queue.poll() ?: break).invoke()}
        val rss=mutableListOf<Long>()
        val core=SpeakerCore(planner,{
            created++;val native=OnnxBackend(File(args[2]))
            object: Backend {
                override fun generate(chunks: List<String>,cancellation: Cancellation)=native.generate(chunks,cancellation)
                override fun close() {native.close();released++}
            }
        },{
            object: Sink {
                var closed=false
                override fun play(pcm: ShortArray,cancellation: Cancellation,amplitude: (Float)->Unit): Long {
                    cancellation.check();check(pcm.isNotEmpty());played++;amplitude(.2f);return 1
                }
                override fun close() {if(!closed) {closed=true;closedSinks++}}
            }
        },Dispatcher {queue.add(it)},{true},{_,_,_->done++},{errors++},worker=worker)
        val started=System.nanoTime();var maxStopMs=0L
        try {
            check(core.speak(catalogue.first()));worker.submit {}.get(10,TimeUnit.SECONDS);drain();check(done==1 && errors==0)
            repeat(100) {i->
                check(core.speak(catalogue[i%catalogue.size]));Thread.sleep(30)
                val t=System.nanoTime();core.stop();worker.submit {}.get(5,TimeUnit.SECONDS);drain()
                maxStopMs=maxOf(maxStopMs,(System.nanoTime()-t)/1_000_000)
                check(done==i+1 && errors==0) {"Cancelled terminal callback"}
                check(core.speak(catalogue[(i+1)%catalogue.size]));worker.submit {}.get(5,TimeUnit.SECONDS);drain()
                check(done==i+2 && errors==0)
                val memory=File("/proc/self/status").readLines().first {it.startsWith("VmRSS:")}.substringAfter(':').trim().split(Regex("\\s+"))[0].toLong();rss.add(memory)
            }
            check(core.speak("Please bring the bass."));worker.submit {}.get(5,TimeUnit.SECONDS);drain();check(errors==1)
            core.resetModel();check(core.speak(catalogue.last()));worker.submit {}.get(10,TimeUnit.SECONDS);drain();check(done==102 && created==2 && released==1)
            core.shutdown();check(worker.awaitTermination(5,TimeUnit.SECONDS));drain()
            check(released==created && closedSinks==played && !core.speak(catalogue.first()))
            File(args[3]).writeText("""{"actual_text_frontend":true,"stop_restart_cycles":100,"completed_requests":$done,"expected_unsupported_text_errors":$errors,"cancelled_terminal_callbacks":0,"max_stop_to_idle_ms":$maxStopMs,"backend_creations":$created,"backend_closes":$released,"rss_range_kib":[${rss.min()},${rss.max()}],"elapsed_seconds":${(System.nanoTime()-started)/1e9},"integration_approved":false,"scope":"Native fixed-Lite G2P + real JVM ONNX + fake audio sink; Android playback and Full unqualified"}""")
            println("Passed 100 text-to-G2P-to-ONNX controller stop/restart cycles")
        } finally {core.shutdown();worker.shutdownNow()}
    }
}
