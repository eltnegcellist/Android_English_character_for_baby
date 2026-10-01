package validation.tts

import android.app.Instrumentation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// Evaluation harness only; never shipped as the application.
class LabInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {super.onCreate(arguments);start()}
    override fun onStart() {
        val context=targetContext
        val events=mutableListOf<String>();val root=File(context.filesDir,"assets").apply {mkdirs()}
        val handler=Handler(Looper.getMainLooper())
        fun idle() {val l=CountDownLatch(1);handler.post {l.countDown()};check(l.await(30,TimeUnit.SECONDS))}
        try {
            for(name in listOf("model.fp32.onnx","voices.bin","tokens.txt","LICENSE","README.md","cmudict.dict","catalogue.txt")) {
                context.assets.open(name).use {input->File(root,name).outputStream().use {input.copyTo(it)}}
            }
            val catalogue=File(root,"catalogue.txt").readLines().map {String(Base64.getDecoder().decode(it),Charsets.UTF_8)}
            val planner=NativeLitePlanner(File(root,"cmudict.dict"),catalogue)
            val done=AtomicInteger();val errors=AtomicInteger();val active=AtomicInteger();val created=AtomicInteger();val released=AtomicInteger()
            var terminal=CountDownLatch(1);var audible=CountDownLatch(1)
            val core=SpeakerCore(planner,{
                created.incrementAndGet();val backend=OnnxBackend(root)
                object: Backend {override fun generate(chunks: List<String>,c: Cancellation)=backend.generate(chunks,c)
                    override fun close() {backend.close();released.incrementAndGet()}}
            },{AndroidSink()},Dispatcher {handler.post(it)},{true},{a,g,t->check(a>=0 && g>=0 && t>=0);done.incrementAndGet();terminal.countDown()},{errors.incrementAndGet();terminal.countDown()},{amp->check(amp in 0f..1f);active.set(if(amp>0) 1 else 0);if(amp>0)audible.countDown()})
            try {
                check(!core.speak(" "));events.add("blank_rejected")
                check(core.speak("Pitter-patter!"));check(terminal.await(180,TimeUnit.SECONDS));idle();check(done.get()==1 && errors.get()==0);events.add("real_onnx_audio_done")
                repeat(10) {i->
                    terminal=CountDownLatch(1);audible=CountDownLatch(1)
                    check(core.speak(catalogue.first()));check(audible.await(180,TimeUnit.SECONDS));core.stop();idle();Thread.sleep(100);idle()
                    check(done.get()==i+1 && errors.get()==0 && active.get()==0)
                    terminal=CountDownLatch(1);check(core.speak(catalogue.minBy {it.length}));check(terminal.await(180,TimeUnit.SECONDS));idle();check(done.get()==i+2 && errors.get()==0)
                    android.util.Log.i("TTS_LAB","playback stop/restart ${i+1}")
                }
                events.add("playback_cancel_restart_10")
                terminal=CountDownLatch(1);check(core.speak("Please bring the bass."));check(terminal.await(30,TimeUnit.SECONDS));idle();check(errors.get()==1);events.add("unsupported_text_error")
                core.resetModel();terminal=CountDownLatch(1);check(core.speak(catalogue.minBy {it.length}));check(terminal.await(180,TimeUnit.SECONDS));idle();check(done.get()==12 && errors.get()==1 && created.get()==2 && released.get()==1);events.add("reset_recreates_backend")
                core.shutdown();Thread.sleep(500);idle();check(!core.speak(catalogue.minBy {it.length}));check(released.get()==2);events.add("shutdown_rejects_and_releases")
            } finally {core.shutdown()}
            val result="""{"passed":true,"android_api":${android.os.Build.VERSION.SDK_INT},"abi":"${android.os.Build.SUPPORTED_ABIS.first()}","real_onnx":true,"real_audio_track":true,"playback_stop_restart_cycles":10,"done":${done.get()},"expected_errors":${errors.get()},"backend_creations":${created.get()},"backend_releases":${released.get()},"events":[${events.joinToString(",") {"\"$it\""}}],"device":"software-emulated API28 x86_64","integration_approved":false,"limitations":["not Galaxy S25 or arm64","no audible listening or naturalness proof","Full G2P unsupported"]}"""
            File(context.filesDir,"result.json").writeText(result);android.util.Log.i("TTS_LAB",result)
            finish(0,Bundle().apply {putString("result",result)})
        } catch(t: Throwable) {
            val error=t.stackTraceToString();File(context.filesDir,"error.txt").writeText(error);android.util.Log.e("TTS_LAB",error)
            finish(1,Bundle().apply {putString("error",error)})
        }
    }
}
