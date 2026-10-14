package com.eltnegcellist.emma.asr

import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriberOption
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eltnegcellist.emma.audio.WavMono16
import com.eltnegcellist.emma.ai.RuriSemanticClassifier
import com.eltnegcellist.emma.ai.RuriSemanticModelStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Manual-only Galaxy S25 research instrument in androidTest, not the released APK.
 * WAV+UTF8 reference pairs are private to the app. No real speech is uploaded.
 */
@RunWith(AndroidJUnit4::class)
class MoonshineAsrBiasReplayTest {
    private data class Variant(val id:String,val rate:Double,val boost:Double,val terms:List<String>)
    private val existing=listOf(
        "沐浴", "お風呂", "ミルク", "母乳", "おっぱい", "哺乳瓶", "授乳",
        "ねんね", "おやすみ", "昼寝", "おむつ", "うんち", "おしっこ",
        "着替え", "抱っこ", "おてて", "あんよ", "にこにこ", "泣く",
        "ぐずぐず", "喃語", "クーイング", "げっぷ", "吐き戻し", "おもちゃ",
        "お散歩", "ベビーカー", "離乳食", "絵本", "音楽"
    )
    private val variants=listOf(
        Variant("as_is_6p5_boost2",6.5,2.0,existing),
        Variant("jp13_boost2",13.0,2.0,existing),
        Variant("jp13_no_keyterms",13.0,0.0,emptyList()),
        Variant("jp13_boost1",13.0,1.0,existing),
        Variant("jp13_boost3",13.0,3.0,existing),
        Variant("jp13_boost2_new_terms",13.0,2.0,existing+listOf("しゃっくり","ご機嫌","くしゃみ","あくび")),
    )
    private fun normalized(value:String)=
        value.lowercase(Locale.ROOT).replace(Regex("[\\s、。！？!?,.・「」『』（）()]"),"")
    private fun distance(expected:String,actual:String):Int {
        val a=expected.codePoints().toArray()
        val b=actual.codePoints().toArray()
        var previous=IntArray(b.size+1){it}
        for(i in a.indices){
            val next=IntArray(b.size+1)
            next[0]=i+1
            for(j in b.indices){
                next[j+1]=minOf(next[j]+1,previous[j+1]+1,previous[j]+if(a[i]==b[j])0 else 1)
            }
            previous=next
        }
        return previous[b.size]
    }
    @Test fun replaySameAudioAcrossMoonshineSettings() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val input=File(context.filesDir,"asr-bias-lab/inputs")
        val wavs=input.listFiles()?.filter{it.extension.equals("wav",true)}?.sortedBy{it.name}.orEmpty()
        assertTrue("Place 16-kHz mono PCM16 .wav and matching .txt references in "+input.absolutePath,wavs.isNotEmpty())
        val model=MoonshineAsrModel.SMALL
        assertTrue("Download Moonshine Small in the main app first",MoonshineModelStore.isInstalled(context,model))
        val clips=wavs.map { wav ->
            val ref=File(input,wav.nameWithoutExtension+".txt")
            require(ref.isFile){"Missing reference: "+ref.name}
            Triple(wav.name,WavMono16.decode(wav.readBytes()),ref.readText().trim())
        }
        val output=JSONArray()
        val ruriAvailable=RuriSemanticModelStore.isInstalled(context)
        val ruri=if(ruriAvailable) RuriSemanticClassifier(context) else null
        try {
        for(variant in variants){
            val options=listOf(
                TranscriberOption("max_tokens_per_second",variant.rate.toString()),
                TranscriberOption("keyterm_boost",variant.boost.toString())
            )
            val transcriber=Transcriber(options)
            try{
                transcriber.loadFromFiles(MoonshineModelStore.directory(context,model).absolutePath+File.separator,model.arch)
                if(variant.terms.isNotEmpty()) transcriber.setKeyterms(variant.terms)
                for((filename,clip,reference) in clips){
                    val start=System.nanoTime()
                    val transcript=transcriber.transcribeWithoutStreaming(clip.samples,clip.sampleRate)?.text().orEmpty().trim()
                    val latencyMs=(System.nanoTime()-start)/1_000_000L
                    val expected=normalized(reference)
                    val actual=normalized(transcript)
                    val predicted=ruri?.predict(transcript)?.getOrNull()
                    val acceptedTopic=predicted?.topic?.takeIf {
                        it!="generic" && predicted.probability>=0.50 && predicted.margin>=0.05
                    }
                    val acceptedInteraction=predicted?.interaction?.takeIf {
                        it!="none" && predicted.interactionProbability>=0.50
                    }
                    output.put(JSONObject()
                        .put("file",filename).put("variant",variant.id)
                        .put("expected",reference).put("transcript",transcript)
                        .put("exact",actual==expected)
                        .put("char_errors",distance(expected,actual))
                        .put("reference_chars",expected.codePointCount(0,expected.length))
                        .put("elapsed_ms",latencyMs)
                        .put("ruri_topic",acceptedTopic ?: JSONObject.NULL)
                        .put("ruri_topic_probability",predicted?.probability ?: JSONObject.NULL)
                        .put("ruri_interaction",acceptedInteraction ?: JSONObject.NULL)
                        .put("ruri_interaction_probability",predicted?.interactionProbability ?: JSONObject.NULL)
                        .put("both_generic",if(ruriAvailable) acceptedTopic==null&&acceptedInteraction==null else JSONObject.NULL))
                }
            }finally{transcriber.close()}
        }
        } finally {ruri?.close()}
        val result=File(context.filesDir,"asr-bias-lab/results.json")
        result.writeText(JSONObject()
            .put("status","RESEARCH_ONLY_ANDROID_DEVICE_AUDIO_REPLAY")
            .put("note","Fixed audio replay only; no microphone, VAD or field CER measured")
            .put("model",model.modelName)
            .put("ruri_available",ruriAvailable)
            .put("variants",JSONArray(variants.map{it.id}))
            .put("results",output).toString(2))
        println("ASR_BIAS_REPLAY_DONE path="+result.absolutePath+" clips="+clips.size+" variants="+variants.size)
    }
}
