package com.eltnegcellist.emma

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.util.Locale
import org.json.JSONArray

internal object AppLanguage {
    val supported = listOf("ja","de","fr","ko","th","en")
    val names = mapOf("ja" to "日本語","de" to "Deutsch","fr" to "Français","ko" to "한국어","th" to "ไทย","en" to "English · Multilingual")
    val languageState = mutableStateOf("ja")
    val choiceRequired = mutableStateOf(false)
    val language get() = languageState.value
    val productName get() = if (language == "ja") "みつことば" else "CooTrio"
    private var context: Context? = null
    private var catalogue = emptyMap<String,List<String>>()
    fun initialize(context: Context) {
        this.context=context.applicationContext
        val prefs=context.getSharedPreferences("emma_speech",Context.MODE_PRIVATE)
        val saved=prefs.getString("app_language_v1",null)
        val device=context.resources.configuration.locales
        val candidate=(0 until device.size()).map { device[it].language }.firstOrNull { it in supported }
        languageState.value=if(saved in supported) saved!! else candidate ?: "en"
        choiceRequired.value=saved !in supported && candidate==null
        if(saved !in supported && candidate!=null) prefs.edit().putString("app_language_v1",candidate).apply()
        val rows=JSONArray(context.assets.open("multilingual/ui-catalog.json").bufferedReader().use { it.readText() })
        catalogue=(0 until rows.length()).associate { i -> val row=rows.getJSONArray(i)
            row.getString(0) to (0 until row.length()).map { row.getString(it) } }
    }
    fun select(language: String) {
        require(language in supported)
        context?.getSharedPreferences("emma_speech",Context.MODE_PRIVATE)?.edit()?.putString("app_language_v1",language)?.apply()
        languageState.value=language;choiceRequired.value=false
    }
    fun translate(text: String): String {
        val index=mapOf("ja" to 0,"en" to 1,"de" to 2,"fr" to 3,"ko" to 4,"th" to 5)[language] ?: 0
        if(index==0)return text
        catalogue[text]?.getOrNull(index)?.let { return it }
        val step=Regex("^(\\d+ / 3)[\\s　]+(.+)$").matchEntire(text)
        if(step!=null)return step.groupValues[1]+" "+translate(step.groupValues[2])
        for((pattern,key) in listOf(
            "^選択した(\\d+)件の履歴を削除しますか？$" to "選択した{count}件の履歴を削除しますか？",
            "^(\\d+)件選択中$" to "{count}件選択中",
            "^(\\d+)件を削除$" to "{count}件を削除")) {
            val match=Regex(pattern).matchEntire(text)
            if(match!=null)return translate(key).replace("{count}",match.groupValues[1])
        }
        return text
    }
    fun selected(context: Context): String {
        val saved=context.getSharedPreferences("emma_speech",Context.MODE_PRIVATE).getString("app_language_v1",null)
        if(saved in supported)return saved!!
        val locales=context.resources.configuration.locales
        return (0 until locales.size()).map { locales[it].language }.firstOrNull { it in supported } ?: "en"
    }
}
