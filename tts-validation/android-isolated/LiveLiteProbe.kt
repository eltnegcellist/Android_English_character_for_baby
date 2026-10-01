package com.eltnegcellist.emma.ai

import validation.tts.*
import java.io.File
import java.util.Base64

object LiveLiteProbe {
 @JvmStatic fun main(args:Array<String>) {
  val catalogue=File(args[1]).readLines().map {String(Base64.getDecoder().decode(it),Charsets.UTF_8)}
  val planner=NativeLitePlanner(File(args[0]),catalogue)
  val prompts=listOf("今日はどうかな","お風呂","ミルク","ねんね","おはよう","おむつ","着替え","抱っこ","おてて","あんよ","にこにこ","泣いている","おしゃべり","げっぷ","おもちゃ","お散歩","雨","太陽","離乳食","絵本","音楽")
  val rows=mutableListOf<String>();var failed=0;var total=0
  fun q(s:String)="\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\""
  for(name in listOf("","Hana","Hana-chan","Aoi-chan","Haruto-chan","Tsumugi-chan")) {
   val engine=LiteResponseEngine()
   for(prompt in prompts)repeat(20) {turn->
    val text=engine.respond(prompt,name).english;total++
    // Explicit name metadata grants the candidate its best supported path.
    val tagged=name.takeIf {it.isNotBlank() && (text.startsWith("$it! ") || text.startsWith("$it, "))}?.let(::JapaneseRomajiName)
    val result=runCatching {planner.planNamed(text,tagged)}
    if(result.isFailure){failed++;rows.add("{\"name\":${q(name)},\"prompt\":${q(prompt)},\"turn\":$turn,\"text\":${q(text)},\"error\":${q(result.exceptionOrNull().toString())}}")}
   }
  }
  val intro=AiCharacterName.introduction(AiCharacterName.DEFAULT)
  val introAccepted=runCatching {planner.plan(intro)}.isSuccess
  val json="""{"runtime_lite_cases":$total,"failed":$failed,"default_introduction":${q(intro)},"introduction_accepted":$introAccepted,"production_source_executed":true,"production_source_changed":false,"integration_approved":false,"failures":[${rows.joinToString(",")}]}"""
  File(args[2]).writeText(json);println("Actual Lite outputs: $total; rejected: $failed; default introduction accepted: $introAccepted")
 }
}
