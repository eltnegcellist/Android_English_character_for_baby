package validation.tts

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Checks against independently enumerated stable source semantics, not a claim
// to execute the old Sherpa engine. Real Android/native tests are separate.
object ContractProbe {
 @JvmStatic fun main(args:Array<String>) {
  val worker=Executors.newSingleThreadExecutor();val queue=ConcurrentLinkedQueue<()->Unit>();val events=mutableListOf<String>()
  var installed=false;var done=0;var errors=0;var amplitude=0f;var created=0;var released=0
  val entered=CountDownLatch(1)
  val core=SpeakerCore(Planner {if(it=="bad") error("expected");listOf(it)},{
   created++;object:Backend {
    override fun generate(chunks:List<String>,cancellation:Cancellation):FloatArray {
     if(chunks.single()=="block") {entered.countDown();while(true){cancellation.check();Thread.sleep(2)}}
     return FloatArray(2400) {.1f}
    }
    override fun close(){released++}
   }
  },{object:Sink {
   override fun play(pcm:ShortArray,cancellation:Cancellation,amp:(Float)->Unit):Long {cancellation.check();amp(.3f);return 1}
   override fun close(){}
  }},Dispatcher {queue.add(it)},{installed},{_,_,_->done++},{errors++},{amplitude=it},worker)
  fun idle(){worker.submit {}.get(5,TimeUnit.SECONDS)}
  fun drain(){while(true)(queue.poll()?:break).invoke()}
  try {
   check(!core.speak("hello") && created==0);installed=true;check(!core.speak(" "));events.add("uninstalled_blank_reject_no_backend")
   check(core.speak("old"));idle();check(core.speak("new"));idle();drain();check(done==1 && errors==0 && amplitude==0f);events.add("superseded_pending_done_suppressed")
   check(core.speak("bad"));idle();core.stop();drain();check(errors==0 && done==1);events.add("stopped_pending_error_suppressed")
   check(core.speak("bad"));idle();drain();check(errors==1 && done==1);events.add("current_error_exactly_once")
   check(core.speak("block"));check(entered.await(5,TimeUnit.SECONDS));core.resetModel();idle();drain();check(done==1 && errors==1 && released==1);events.add("reset_cancels_generation_releases_model")
   check(core.speak("after_reset"));idle();drain();check(created==2 && done==2 && errors==1);events.add("reset_next_speak_recreates_model")
   check(core.speak("pending_shutdown"));idle();core.shutdown();check(worker.awaitTermination(5,TimeUnit.SECONDS));drain();check(done==2 && errors==1 && released==created && !core.speak("closed"));events.add("shutdown_suppresses_pending_releases_and_rejects")
   val json="""{"passed":true,"checks":${events.size},"events":[${events.joinToString(","){"\"$it\""}}],"expected_source":"KittenSpeaker.kt at 62f39abc095dd29a57d86a0efdef5400a2bb4293","reference_kind":"source-derived independent contract; old runtime not executed","integration_approved":false}"""
   File(args.single()).writeText(json);println(json)
  } finally {core.shutdown();worker.shutdownNow()}
 }
}
