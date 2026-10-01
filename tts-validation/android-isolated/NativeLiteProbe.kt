package validation.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

private fun quote(text: String)="\""+text.replace("\\","\\\\").replace("\"","\\\"")+"\""

object NativeLiteProbe {
    @JvmStatic fun main(args: Array<String>) {
        // dictionary, catalogue (text only), cases TSV, JSON report,
        // optional assets / waveform directory / normal|length
        require(args.size==4 || args.size==7)
        val decode=Base64.getDecoder()
        fun string(encoded: String)=String(decode.decode(encoded),Charsets.UTF_8)
        val catalogue=File(args[1]).readLines().map(::string)
        val length=args.size==7 && args[6].startsWith("length")
        val compound=args.size==7 && args[6].contains("compound")
        val planner=NativeLitePlanner(File(args[0]),catalogue,length,compound)
        val rows=mutableListOf<String>();var failed=0
        val backend=if(args.size==7) OnnxBackend(File(args[4])) else null
        val started=System.nanoTime()
        try {
            for((i,line) in File(args[2]).readLines().withIndex()) {
                val fields=line.split('\t');val text=string(fields[0]);val name=fields[1].takeIf {it!="-"}?.let {JapaneseRomajiName(string(it))}
                val index=fields.getOrNull(2)?.toInt() ?: i
                try {
                    val analysis=planner.analyze(text,name);val chunks=planner.planNamed(text,name)
                    val words=analysis.words.joinToString(",") {"{\"text\":${quote(it.text)},\"ipa\":${quote(it.ipa)}}"}
                    var waveform=""
                    if(backend!=null) {
                        val y=backend.generate(chunks,Cancellation());check(y.isNotEmpty() && y.all {it.isFinite()})
                        val root=File(args[5]);root.mkdirs()
                        val buffer=ByteBuffer.allocate(y.size*4).order(ByteOrder.LITTLE_ENDIAN)
                        y.forEach {buffer.putFloat(it)};File(root,"%04d.f32".format(index)).writeBytes(buffer.array())
                        waveform=",\"samples\":${y.size},\"sample_rate\":24000,\"duration_seconds\":${y.size/24000.0}"
                    }
                    rows.add("{\"index\":$index,\"text\":${quote(text)},\"name\":${if(name==null) "null" else quote(name.text)},\"ipa\":${quote(analysis.ipa)},\"chunks\":${chunks.size},\"words\":[$words],\"valid\":true$waveform}")
                } catch(e: Exception) {
                    failed++;rows.add("{\"index\":$i,\"text\":${quote(text)},\"valid\":false,\"error\":${quote(e.toString())}}")
                }
                if((i+1)%50==0) println("completed ${i+1}, failed $failed")
            }
            // Unknown text, untagged name, foreign name, injected IPA all reject.
            val rejected=listOf("Hello quuxzz.","Please bring the bass.","John! ${catalogue.first()}","[hello](/həloʊ/)","${catalogue.first()}\n")
            val negatives=rejected.count {runCatching {planner.plan(it)}.isFailure}
            check(negatives==rejected.size)
            check(runCatching {planner.planNamed("John! ${catalogue.first()}",JapaneseRomajiName("John"))}.isFailure)
            File(args[3]).writeText("""{"total":${rows.size},"failed":$failed,"negative_cases":6,"length_variant":$length,"compound_variant":$compound,"elapsed_seconds":${(System.nanoTime()-started)/1e9},"integration_approved":false,"scope":"Independent native CMU fixed-Lite alternative; unrestricted Full unsupported","records":[${rows.joinToString(",")}]}""")
        } finally {backend?.close()}
        check(failed==0) {"Native cases failed: $failed"}
    }
}
