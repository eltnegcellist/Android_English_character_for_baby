package com.eltnegcellist.emma

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    var playSentenceCount by remember { mutableIntStateOf(if (prefs.getInt("play_sentence_count", 1) == 3) 3 else 1) }
    var offerNotifications by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("画面を閉じても会話を続ける", Modifier.weight(1f))
                Switch(screenOff, { runtime.applyContinueScreenOff(it); if (it) offerNotifications=true })
            }
            Text("初期設定はオフです。オンにすると、開始した会話だけを画面オフ中や他のアプリの使用中も継続し、周囲の会話に応答することがあります。アプリの「会話を止める」で終了できます。", style=MaterialTheme.typography.bodySmall)
            ConversationNotificationSettings(runtime, offerNotifications, { offerNotifications=false })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("端末内に会話履歴を残す", Modifier.weight(1f))
                Switch(history, { history = it; prefs.edit().putBoolean("history_enabled",it).apply() })
            }
            Text("再生が始まった応答の日本語・英語を最大1,000件保存します。録音は保存しません。オフにしても以前の履歴は残ります。", style=MaterialTheme.typography.bodySmall)
            if(historyError!=null) Text(historyError!!,color=MaterialTheme.colorScheme.error)
            OutlinedButton(onHistory, Modifier.fillMaxWidth()) { Text("会話履歴を見る・聞く") }
            HorizontalDivider()
            Text("押して聞く", style = MaterialTheme.typography.titleSmall)
            Text("1回に流す文の数", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = playSentenceCount == 1,
                    onClick = {
                        playSentenceCount = 1
                        prefs.edit().putInt("play_sentence_count", 1).apply()
                    },
                    label = { Text("1文（標準）") },
                )
                FilterChip(
                    selected = playSentenceCount == 3,
                    onClick = {
                        playSentenceCount = 3
                        prefs.edit().putInt("play_sentence_count", 3).apply()
                    },
                    label = { Text("3文") },
                )
            }
            Text(
                "1文では短い一言も含めて1つだけ再生します。3文では、これまでのように短い文をまとめて再生します。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
@Composable
private fun ConversationNotificationSettings(runtime: ConversationController, offerNotifications: Boolean, onOfferHandled: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(NotificationManager::class.java) }
    val prefs = remember(context) { context.getSharedPreferences("emma_speech", 0) }
    fun notificationsVisible(): Boolean = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel("conversation")?.importance != NotificationManager.IMPORTANCE_NONE
    var allowed by remember { mutableStateOf(notificationsVisible()) }
    var controls by remember { mutableStateOf(allowed && prefs.getBoolean("conversation_notification_controls", prefs.getBoolean("conversation_notification_permission_requested", false))) }
    var awaitingSettings by remember { mutableStateOf(false) }
    val serviceActive by runtime.conversationServiceActiveState
    fun saveControls(value: Boolean) {
        controls = value
        prefs.edit().putBoolean("conversation_notification_controls", value).apply()
        runtime.onNotificationChanged?.invoke()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = notificationsVisible()
        saveControls(granted && allowed)
    }
    fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    fun enableControls() {
        allowed = notificationsVisible()
        if (allowed) { saveControls(true); return }
        val permissionNeeded = Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (permissionNeeded && !prefs.getBoolean("conversation_notification_permission_requested", false)) {
            prefs.edit().putBoolean("conversation_notification_permission_requested", true).apply()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            awaitingSettings = true
            openSettings()
        }
    }
    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = notificationsVisible()
                if (awaitingSettings) { awaitingSettings=false; saveControls(allowed) }
                else if (!allowed && controls) saveControls(false)
                runtime.onNotificationChanged?.invoke()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { saveControls(controls) }
    LaunchedEffect(offerNotifications, controls, allowed) {
        if (offerNotifications && controls && allowed) onOfferHandled()
    }
    if (offerNotifications && (!controls || !allowed)) AlertDialog(
        onDismissRequest=onOfferHandled,
        title={Text("通知から会話を停止できるようにしますか？")},
        text={Text("「はい」を選ぶと、必要な場合に通知の許可を求めます。開始した会話の通知に「会話を止める」を表示します。通知を使わなくても会話は続けられます。")},
        confirmButton={TextButton({onOfferHandled(); enableControls()}) {Text("はい")}},
        dismissButton={TextButton(onOfferHandled) {Text("いいえ")}},
    )
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text("通知から会話を止める", Modifier.weight(1f))
        Switch(controls, { if (it) enableControls() else saveControls(false) })
    }
    Text(when {
        !controls -> if (allowed) "停止ボタンの表示：オフ" else "停止ボタンの表示：オフ（Androidの通知許可なし）"
        !allowed -> "Androidで通知が許可されていないため、表示できません。"
        !serviceActive -> "待機中：バックグラウンド会話を始めると通知に停止ボタンを表示します。"
        else -> "会話中：通知に停止ボタンを表示しています。"
    }, style=MaterialTheme.typography.bodySmall)
    Text("この設定はアプリ内でオン・オフできます。オフの場合はアプリ内で会話を止めてください。Androidが要求する動作中の通知・表示は残ることがあります。", style=MaterialTheme.typography.bodySmall)
    if (!allowed) TextButton({openSettings()}) { Text("Androidの通知許可を確認") }
}

@Composable
internal fun HistoryScreen(runtime: ConversationController, onExit: () -> Unit) {
    val historyError by runtime.history.errorState
    var entries by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var remove by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var speaking by runtime.statusState
    val zone = ZoneId.systemDefault()
    val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPAN)
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.JAPAN)
    val grouped = entries.groupBy { entry ->
        runCatching { dateFormat.format(Instant.parse(entry.createdAt).atZone(zone)) }.getOrDefault("日付不明")
    }
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
            grouped.forEach { (date, dayEntries) ->
                item(key="date-$date") { Text(date, style=MaterialTheme.typography.titleMedium) }
                items(dayEntries,key={it.id}) { entry -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(runCatching { timeFormat.format(Instant.parse(entry.createdAt).atZone(zone)) }.getOrDefault(entry.createdAt),style=MaterialTheme.typography.bodySmall)
                    Text("聞き取った言葉", style=MaterialTheme.typography.labelSmall)
                    Text(entry.japaneseText)
                    Text("再生した英語", style=MaterialTheme.typography.labelSmall)
                    Text(entry.englishText,style=MaterialTheme.typography.titleMedium)
                    Row { Button({runtime.replay(entry.englishText)},enabled=speaking!=ProductionEmmaStatus.SPEAKING) { Text("もう一度聞く") }
                        TextButton({runtime.stopSession()}) {Text("停止")}; TextButton({remove=entry.id;confirm=true}) {Text("削除")}
                    }
                } } }
            }
        }
    }
}
@Composable
internal fun PlayScreen(runtime: ConversationController, onExit: () -> Unit) {
    val topics = remember { loadPlayTopics(runtime) }
    val allTopics = remember(topics) { PlayTopic("all", "すべての話題", topics.flatMap { it.phrases }.distinct()) }
    val prefs = remember(runtime.context) { runtime.context.getSharedPreferences("emma_speech", 0) }
    var playSentenceCount by remember {
        mutableIntStateOf(if (prefs.getInt("play_sentence_count", 1) == 3) 3 else 1)
    }
    var selected by remember { mutableStateOf(allTopics) }
    var choosingTopic by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf<String?>(null) }
    var status by runtime.statusState
    var text by runtime.latestEmmaTextState
    var exitConfirm by remember { mutableStateOf(false) }
    val isSpeaking = status == ProductionEmmaStatus.SPEAKING
    val playContainerColor by animateColorAsState(
        targetValue = if (isSpeaking) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.White,
        label = "play-container-color",
    )
    fun exit() { runtime.stopSession(); onExit() }
    LaunchedEffect(Unit) { text="押すと短い英語が流れます" }
    BackHandler { if (choosingTopic) choosingTopic=false else exitConfirm=true }
    if(exitConfirm) AlertDialog(onDismissRequest={exitConfirm=false},title={Text("おとなの方へ")},text={Text("遊びを終えてメイン画面に戻りますか？会話は自動で始まりません。")},
        confirmButton={TextButton({exit()}){Text("遊びを終える")}},dismissButton={TextButton({exitConfirm=false}){Text("遊びを続ける")}})
    Scaffold(topBar={Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
        TextButton({exitConfirm=true}){Text("おとな用・戻る")}; Text("押して聞く",Modifier.weight(1f))
        TextButton({runtime.stopSession(); choosingTopic=!choosingTopic}){Text(if(choosingTopic) "遊びに戻る" else "話題を変更")}
    }}) { padding ->
        if(choosingTopic) LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { Text("親子で一緒に聞いて、まねして、交互に押して遊びましょう。押すのは赤ちゃんでも、おとなでもかまいません。マイクは使いません。学習効果が実証された機能ではありません。") }
            items(listOf(allTopics) + topics, key={it.id}) { topic ->
                Button({selected=topic; previous=null; choosingTopic=false; text="押すと短い英語が流れます"},Modifier.fillMaxWidth().heightIn(min=64.dp)) {Text(topic.label)}
            }
        } else Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedButton(onClick={
                val topic=selected
                if(runtime.modelReady && status!=ProductionEmmaStatus.SPEAKING) {
                    val candidates = playPhrasesForSentenceCount(topic.phrases, playSentenceCount)
                    val phrase=nextPlayPhrase(candidates,previous); previous=phrase
                    runtime.replay(phrase)
                }
            },enabled=runtime.modelReady,modifier=Modifier.fillMaxSize(),
                colors=ButtonDefaults.outlinedButtonColors(containerColor=playContainerColor, contentColor=Color(0xFF342F32)),
                border=BorderStroke(2.dp,Color(0xFFDED6DE)), contentPadding=PaddingValues(16.dp), shape=MaterialTheme.shapes.extraLarge) {
                Column(Modifier.verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.sizeIn(maxWidth=260.dp).fillMaxWidth().padding(8.dp)) {
                        com.eltnegcellist.emma.ui.CompactEmmaAvatar(state=if(isSpeaking) com.eltnegcellist.emma.ui.EmmaVisualState.SPEAKING else com.eltnegcellist.emma.ui.EmmaVisualState.IDLE, mouthLevel=runtime.mouthLevel, modifier=Modifier.fillMaxWidth())
                    }
                    Text(selected.label,style=MaterialTheme.typography.headlineSmall)
                    Text(if(isSpeaking) "一緒に聞こう" else "押して聞く",style=MaterialTheme.typography.headlineMedium)
                    Text(text,style=MaterialTheme.typography.titleLarge)
                    Text("親子で一緒に聞く・まねする・交互に押す",style=MaterialTheme.typography.bodyLarge)
                }
            }
            Row(
                modifier=Modifier.align(Alignment.TopEnd).padding(top=12.dp,end=12.dp),
                horizontalArrangement=Arrangement.spacedBy(6.dp),
                verticalAlignment=Alignment.CenterVertically,
            ) {
                Text("1回", style=MaterialTheme.typography.bodySmall, color=Color(0xFF6E666B))
                FilterChip(
                    selected=playSentenceCount==1,
                    onClick={
                        playSentenceCount=1
                        prefs.edit().putInt("play_sentence_count",1).apply()
                    },
                    label={Text("1文")},
                )
                FilterChip(
                    selected=playSentenceCount==3,
                    onClick={
                        playSentenceCount=3
                        prefs.edit().putInt("play_sentence_count",3).apply()
                    },
                    label={Text("3文")},
                )
            }
        }
    }
}
