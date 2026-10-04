package com.eltnegcellist.emma

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal data class PlayTopic(val id: String, val label: String, val phrases: List<String>)
internal fun loadPlayTopics(runtime: ConversationController): List<PlayTopic> {
    val data = JSONObject(runtime.context.assets.open("play-topics.json").bufferedReader().use { it.readText() }).getJSONArray("topics")
    return (0 until data.length()).map { i -> data.getJSONObject(i).let { topic ->
        val phrases = topic.getJSONArray("phrases")
        PlayTopic(topic.getString("id"),topic.getString("label"),(0 until phrases.length()).map { phrases.getString(it) })
    } }
}
@Composable
internal fun FeatureSettings(runtime: ConversationController, onHistory: () -> Unit) {
    var screenOff by runtime.continueScreenOffState
    var history by runtime.historyEnabledState
    val historyError by runtime.history.errorState
    val prefs = runtime.context.getSharedPreferences("emma_speech", 0)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("画面を閉じても会話を続ける", Modifier.weight(1f))
                Switch(screenOff, { screenOff = it; prefs.edit().putBoolean("continue_screen_off",it).apply() })
            }
            Text("開始した会話だけを継続します。通知の許可は不要です。アプリの「会話を止める」で終了できます。通知をオンにしている場合は通知からも停止できます。Androidの動作中アプリの表示は残ります。", style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("端末内に会話履歴を残す", Modifier.weight(1f))
                Switch(history, { history = it; prefs.edit().putBoolean("history_enabled",it).apply() })
            }
            Text("再生が始まった応答の日本語・英語を最大1,000件保存します。録音は保存しません。オフにしても以前の履歴は残ります。", style=MaterialTheme.typography.bodySmall)
            if(historyError!=null) Text(historyError!!,color=MaterialTheme.colorScheme.error)
            OutlinedButton(onHistory, Modifier.fillMaxWidth()) { Text("会話履歴を見る・聞く") }
        }
    }
}
@Composable
internal fun HistoryScreen(runtime: ConversationController, onExit: () -> Unit) {
    val historyError by runtime.history.errorState
    var entries by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var remove by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var speaking by runtime.statusState
    fun refresh() = runtime.history.list { entries = it }
    fun exit() { runtime.stopSession(); onExit() }
    BackHandler { exit() }
    LaunchedEffect(Unit) { refresh() }
    if (confirm) AlertDialog(onDismissRequest={confirm=false}, title={Text(if(remove==null) "すべての履歴を削除しますか？" else "この履歴を削除しますか？")},
        confirmButton={TextButton({ runtime.stopSession(); runtime.history.delete(remove) { refresh() }; confirm=false }) { Text("削除") }},
        dismissButton={TextButton({confirm=false}) {Text("戻る")}})
    Scaffold(topBar={Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
        TextButton({exit()}) {Text("← 戻る")}; Text("会話履歴",Modifier.weight(1f)); TextButton({remove=null;confirm=true},enabled=entries.isNotEmpty()) {Text("全件削除")}
    }}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(historyError!=null) item { Text(historyError!!,color=MaterialTheme.colorScheme.error) }
            if(entries.isEmpty()) item { Text("履歴はまだありません。会話の音声が再生を始めると、ここに残ります。") }
            items(entries,key={it.id}) { entry -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(entry.createdAt,style=MaterialTheme.typography.bodySmall)
                Text(entry.japaneseText); Text(entry.englishText,style=MaterialTheme.typography.titleMedium)
                Row { Button({runtime.replay(entry.englishText)},enabled=speaking!=ProductionEmmaStatus.SPEAKING) { Text("もう一度聞く") }
                    TextButton({runtime.stopSession()}) {Text("停止")}; TextButton({remove=entry.id;confirm=true}) {Text("削除")}
                }
            } } }
        }
    }
}
@Composable
internal fun PlayScreen(runtime: ConversationController, onExit: () -> Unit) {
    val topics = remember { loadPlayTopics(runtime) }
    var selected by remember { mutableStateOf<PlayTopic?>(null) }
    var ready by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf<String?>(null) }
    var status by runtime.statusState
    var text by runtime.latestEmmaTextState
    var exitConfirm by remember { mutableStateOf(false) }
    fun exit() { runtime.stopSession(); onExit() }
    BackHandler { exitConfirm=true }
    if(exitConfirm) AlertDialog(onDismissRequest={exitConfirm=false},title={Text("おとなの方へ")},text={Text("遊びを終えてメイン画面に戻りますか？会話は自動で始まりません。")},
        confirmButton={TextButton({exit()}){Text("遊びを終える")}},dismissButton={TextButton({exitConfirm=false}){Text("遊びを続ける")}})
    Scaffold(topBar={Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
        TextButton({exitConfirm=true}){Text("おとな用・戻る")}; Text("押して聞く",Modifier.weight(1f))
        if(selected!=null) TextButton({runtime.stopSession();selected=null;previous=null}){Text("話題を選ぶ")}
    }}) { padding ->
        if(selected==null) LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { Text("親子で一緒に聞いて、まねして、交互に押して遊びましょう。押すのは赤ちゃんでも、おとなでもかまいません。マイクは使いません。学習効果が実証された機能ではありません。") }
            items(topics) { topic -> Button({selected=topic; previous=null; ready=false; text="声を準備しています…";runtime.kitten.prepare(topic.phrases) { ok -> if (selected?.id == topic.id) { ready=ok; text=if(ok) "押すと短い英語が流れます" else "準備に失敗しました。話題を選び直してください。" } }},Modifier.fillMaxWidth().heightIn(min=64.dp)) {Text(topic.label)} }
        } else Button(onClick={
            val topic=selected!!
            if(ready && status!=ProductionEmmaStatus.SPEAKING) {
                val phrase=nextPlayPhrase(topic.phrases,previous); previous=phrase
                runtime.replay(phrase)
            }
        },enabled=ready,modifier=Modifier.fillMaxSize().padding(padding).padding(16.dp),shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp)) {
                com.eltnegcellist.emma.ui.CompactEmmaAvatar(state=if(status==ProductionEmmaStatus.SPEAKING) com.eltnegcellist.emma.ui.EmmaVisualState.SPEAKING else com.eltnegcellist.emma.ui.EmmaVisualState.IDLE, mouthLevel=runtime.mouthLevel, modifier=Modifier.fillMaxWidth().heightIn(max=240.dp))
                Text(selected!!.label,style=MaterialTheme.typography.headlineSmall)
                Text(if(status==ProductionEmmaStatus.SPEAKING) "一緒に聞こう" else "押して聞く",style=MaterialTheme.typography.headlineMedium)
                Text(text,style=MaterialTheme.typography.titleLarge)
                Text("一緒にまねする・交互に押す",style=MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
