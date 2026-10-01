package validation.tts

import java.io.File
import java.util.Base64
import java.util.concurrent.*

object NativeMemoryProbe {
    @JvmStatic fun main(args: Array<String>) {
        val texts=File(args[1]).readLines().map {String(Base64.getDecoder().decode(it),Charsets.UTF_8)}
        val planner=NativeLitePlanner(File(args[0]),texts)
        val rows=mutableListOf<String>();val rss=mutableListOf<Long>();val heaps=mutableListOf<Long>()
        val executor=Executors.newSingleThreadExecutor()
        OnnxBackend(File(args[2])).use {backend ->
            fun generate(i: Int,token: Cancellation)=backend.generate(planner.plan(texts[i%texts.size]),token)
            repeat(10) {generate(it,Cancellation())};System.gc()
            try {
                repeat(100) {i->
                    val token=Cancellation();val entered=CountDownLatch(1)
                    val future=executor.submit<Boolean> {
                        entered.countDown();try {generate(i,token);false} catch(_:Cancelled) {true}
                    }
                    check(entered.await(5,TimeUnit.SECONDS));Thread.sleep(30);token.cancel();check(future.get(5,TimeUnit.SECONDS))
                    check(generate(i+1,Cancellation()).isNotEmpty())
                    if((i+1)%10==0) {
                        System.gc()
                        val memory=File("/proc/self/status").readLines().first {it.startsWith("VmRSS:")}.substringAfter(':').trim().split(Regex("\\s+"))[0].toLong()
                        val vm=Runtime.getRuntime();val used=vm.totalMemory()-vm.freeMemory();rss.add(memory);heaps.add(used)
                        rows.add("{\"cycle\":${i+1},\"rss_kib\":$memory,\"used_heap_bytes\":$used}")
                    }
                }
            } finally {executor.shutdownNow();check(executor.awaitTermination(5,TimeUnit.SECONDS))}
        }
        File(args[3]).writeText("""{"cycles":100,"warmup":10,"measurement":"after explicit GC every ten cycles","rss_range_kib":[${rss.min()},${rss.max()}],"heap_range_bytes":[${heaps.min()},${heaps.max()}],"integration_approved":false,"scope":"Bounded JVM observation, not long-term Android leak proof","records":[${rows.joinToString(",")}]}""")
        println("Completed native text memory probe")
    }
}
