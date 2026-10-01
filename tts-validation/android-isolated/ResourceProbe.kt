package validation.tts

import java.io.File
import java.util.Base64
import java.util.concurrent.*

// Separate bounded native-resource probe. No Android playback or leak proof.
fun resourceProbe(args: Array<String>) {
    val decode=Base64.getDecoder()
    val plan=File(args[1]).readLines().first().split('\t').drop(1).map {String(decode.decode(it),Charsets.UTF_8)}
    val longPlan=listOf(plan.joinToString(" ").repeat(3))
    val rows=mutableListOf<String>(); val rss=mutableListOf<Long>();var maxCancelMs=0L
    val executor=Executors.newSingleThreadExecutor()
    OnnxBackend(File(args[0])).use {backend ->
        repeat(10) {backend.generate(plan,Cancellation())}
        System.gc()
        repeat(100) {i ->
            val token=Cancellation();val entered=CountDownLatch(1)
            val future=executor.submit<Boolean> {
                entered.countDown()
                try {backend.generate(longPlan,token);false} catch(_:Cancelled) {true}
            }
            check(entered.await(5,TimeUnit.SECONDS));Thread.sleep(30)
            val t=System.nanoTime();token.cancel();check(future.get(2,TimeUnit.SECONDS))
            maxCancelMs=maxOf(maxCancelMs,(System.nanoTime()-t)/1_000_000)
            check(backend.generate(plan,Cancellation()).isNotEmpty())
            if((i+1)%10==0) System.gc()
            val memory=File("/proc/self/status").readLines().first {it.startsWith("VmRSS:")}.substringAfter(':').trim().split(Regex("\\s+"))[0].toLong()
            val jvm=Runtime.getRuntime();val heap=jvm.totalMemory()-jvm.freeMemory()
            rss.add(memory);rows.add("{\"cycle\":$i,\"rss_kib\":$memory,\"jvm_used_heap_bytes\":$heap}")
        }
    }
    executor.shutdown();check(executor.awaitTermination(5,TimeUnit.SECONDS))
    File(args[2]).writeText("""{"cycles":100,"warmup_runs":10,"max_cancel_ms":$maxCancelMs,"rss_range_kib":[${rss.min()},${rss.max()}],"integration_approved":false,"limitations":["bounded Linux JVM observation","not an Android memory-leak proof"],"records":[${rows.joinToString(",")}]}""")
    println("Passed 100 resource-observed cancellation/regeneration cycles")
}

object ResourceProbe { @JvmStatic fun main(args: Array<String>) = resourceProbe(args) }
