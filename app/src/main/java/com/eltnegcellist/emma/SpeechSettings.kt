package com.eltnegcellist.emma

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eltnegcellist.emma.ai.BabyGender
import com.eltnegcellist.emma.ai.BabyNamePronunciation
import com.eltnegcellist.emma.ai.EnglishLevel
import com.eltnegcellist.emma.tts.KOKORO_AUDIO_TEST_ONCE_KEY
import com.eltnegcellist.emma.tts.KOKORO_AUDIO_TEST_TEXT
import com.eltnegcellist.emma.tts.KokoroAudioTestMode
import com.eltnegcellist.emma.tts.KokoroCpuMode
import com.eltnegcellist.emma.tts.KokoroModelStore
import com.eltnegcellist.emma.tts.KokoroThreadPolicy
import com.eltnegcellist.emma.tts.KokoroWavDiagnostic
import java.util.Locale

@Composable
internal fun SpeechSettings(
    level: EnglishLevel,
    rate: Float,
    voiceName: String,
    voices: List<String>,
    enabled: Boolean,
    previewing: Boolean,
    onLevel: (EnglishLevel) -> Unit,
    onRate: (Float) -> Unit,
    onVoice: (String) -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emma_speech", Context.MODE_PRIVATE) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var babyName by remember { mutableStateOf(preferences.getString("baby_name", "").orEmpty()) }
    var spokenBabyName by remember { mutableStateOf(preferences.getString("baby_spoken_name", "").orEmpty()) }
    var babyGender by remember { mutableStateOf(BabyGender.fromSaved(preferences.getString("baby_gender", null))) }
    var kokoroCpuMode by remember {
        mutableStateOf(KokoroCpuMode.fromSaved(preferences.getString("kokoro_cpu_mode", null)))
    }
    var audioTestMode by remember { mutableStateOf(KokoroAudioTestMode.SPLIT_058_STATIC) }
    var voiceExpanded by remember { mutableStateOf(false) }
    var cpuExpanded by remember { mutableStateOf(false) }
    var audioTestExpanded by remember { mutableStateOf(false) }
    var wavCaptureRunning by remember { mutableStateOf(false) }
    var wavCaptureMessage by remember { mutableStateOf("") }
    val resolvedSpokenName = BabyNamePronunciation.toSpokenEnglish(babyName, spokenBabyName)
    val processorCount = remember { Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }
    val totalRamMb = remember {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        if (manager != null) {
            manager.getMemoryInfo(info)
            info.totalMem / (1024L * 1024L)
        } else {
            0L
        }
    }
    val selectedThreads = KokoroThreadPolicy.selectThreads(kokoroCpuMode, processorCount, totalRamMb)
    val kokoroInstalled = KokoroModelStore.isInstalled(context)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("会話・読み上げ設定", style = MaterialTheme.typography.titleMedium)

            Text("赤ちゃんの名前（任意）", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = babyName,
                onValueChange = { value ->
                    val sanitized = value.filterNot { it == '\n' || it == '\r' || it == '\t' }.take(30)
                    babyName = sanitized
                    preferences.edit().putString("baby_name", sanitized).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = { Text("赤ちゃんの名前") },
                placeholder = { Text("例：はな / ハナ / Hana") },
                supportingText = {
                    Text("ひらがな・カタカナ・英字を使えます。かなの名前はEmmaが英語で呼べる形へ自動変換します。")
                },
            )

            OutlinedTextField(
                value = spokenBabyName,
                onValueChange = { value ->
                    val sanitized = value
                        .filterNot { it == '\n' || it == '\r' || it == '\t' }
                        .filter { it.isLetter() || it == '\'' || it == '’' || it == '-' || it == ' ' }
                        .take(40)
                    spokenBabyName = sanitized
                    preferences.edit().putString("baby_spoken_name", sanitized).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = { Text("英語で呼ぶ名前（必要な場合だけ）") },
                placeholder = { Text("例：Hana") },
                supportingText = {
                    Text("漢字名・特殊な読み・発音を調整したい場合だけ英字で指定します。かなの名前なら通常は空欄で大丈夫です。")
                },
            )
            Text(
                if (babyName.isBlank()) {
                    "Emmaが呼ぶ名前：未設定"
                } else if (resolvedSpokenName.isNotBlank()) {
                    "Emmaが呼ぶ名前：$resolvedSpokenName"
                } else {
                    "Emmaが呼ぶ名前：読み方未設定（漢字名などは上の欄に英字で入力してください）"
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Text("赤ちゃんの性別", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BabyGender.entries.forEach { option ->
                    if (babyGender == option) {
                        Button(onClick = {}, enabled = enabled, modifier = Modifier.weight(1f)) {
                            Text(option.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                babyGender = option
                                preferences.edit().putString("baby_gender", option.name).apply()
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(option.label)
                        }
                    }
                }
            }
            Text(
                "「指定しない」では、Emmaは名前や会話内容から性別を推測しません。",
                style = MaterialTheme.typography.bodySmall,
            )

            EnglishLevel.entries.forEach { option ->
                Row {
                    RadioButton(selected = level == option, onClick = { onLevel(option) }, enabled = enabled)
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(option.label)
                        Text(option.example, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("初期設定は「ふつう」です。", style = MaterialTheme.typography.bodySmall)

            Text("親モードの読み上げ速度：${String.format(Locale.US, "%.2f", rate)}×")
            Slider(value = rate, onValueChange = onRate, valueRange = 0.70f..1.10f, steps = 7, enabled = enabled)
            Text(
                "「赤ちゃんへ」では各単語を1.00×の自然な速度で個別生成し、単語間を約0.35秒あけます。単語内部を引き延ばさず、一語ずつ明瞭に聞かせます。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("「親へ」では上のスライダーで声の速さを変更します。", style = MaterialTheme.typography.bodySmall)

            Text("KokoroのCPU設定", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { cpuExpanded = true }, enabled = enabled) {
                Text(kokoroCpuMode.label)
            }
            DropdownMenu(expanded = cpuExpanded && enabled, onDismissRequest = { cpuExpanded = false }) {
                KokoroCpuMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            kokoroCpuMode = mode
                            preferences.edit().putString("kokoro_cpu_mode", mode.savedValue).apply()
                            cpuExpanded = false
                        },
                    )
                }
            }
            Text(kokoroCpuMode.description, style = MaterialTheme.typography.bodySmall)
            Text(
                "この端末：CPU ${processorCount}コア / RAM 約${totalRamMb / 1024}GB → ${selectedThreads}スレッド。自動モードは端末ごとに判定します。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "別のスマホでも同じAPKを使えます。低性能端末ではスレッド数を抑え、高性能端末だけ6スレッドまで使います。比較したい場合は2・4・6を手動で切り替えられます。",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Kokoro音質診断 A〜H", style = MaterialTheme.typography.titleSmall)
            Text(
                "すべての診断モードで、下の固定英文を必ず使います。今回の本命Hは0.58×のまま短句別に生成し、全PCMを結合してMODE_STATICで再生します。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("固定英文：$KOKORO_AUDIO_TEST_TEXT", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { audioTestExpanded = true }, enabled = enabled && kokoroInstalled) {
                Text(audioTestMode.label)
            }
            DropdownMenu(
                expanded = audioTestExpanded && enabled,
                onDismissRequest = { audioTestExpanded = false },
            ) {
                KokoroAudioTestMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            audioTestMode = mode
                            audioTestExpanded = false
                        },
                    )
                }
            }
            Text(audioTestMode.description, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = if (previewing) {
                    onStopPreview
                } else {
                    {
                        preferences.edit()
                            .putString(KOKORO_AUDIO_TEST_ONCE_KEY, audioTestMode.savedValue)
                            .commit()
                        onPreview()
                    }
                },
                enabled = previewing || (enabled && kokoroInstalled && !wavCaptureRunning),
            ) {
                Text(if (previewing) "診断試聴を停止" else "この条件を1回だけ再生")
            }
            Text(
                "Hでも『Hello little one, look at you』までしか聞こえなかったため、次は生成PCMそのものをWAVで比較します。",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Kokoro生成WAVの速度比較", style = MaterialTheme.typography.titleSmall)
            Text(
                "0.58 / 0.60 / 0.65 / 0.70 / 1.00×で同じ4短句を生成します。各generate()の生PCMを20個、実際のH相当処理で結合したWAVを5個保存します。再生処理は通しません。",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    wavCaptureRunning = true
                    wavCaptureMessage = "WAVを生成しています…"
                    EmmaWorkQueue.execute {
                        val result = KokoroWavDiagnostic.capture(context.applicationContext, selectedThreads)
                        mainHandler.post {
                            wavCaptureRunning = false
                            wavCaptureMessage = result.fold(
                                onSuccess = { summary ->
                                    "WAV ${summary.wavFiles}個を保存しました（${String.format(Locale.US, "%.1f", summary.elapsedMs / 1000.0)}秒）。画面下の「クラッシュ詳細を書き出す」でZIPを保存してください。"
                                },
                                onFailure = { error ->
                                    "WAV生成に失敗しました：${error.message ?: error.javaClass.simpleName}"
                                },
                            )
                        }
                    }
                },
                enabled = enabled && kokoroInstalled && !previewing && !wavCaptureRunning,
            ) {
                Text(if (wavCaptureRunning) "WAV生成中…" else "5速度のWAVをまとめて生成")
            }
            if (wavCaptureMessage.isNotBlank()) {
                Text(wavCaptureMessage, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "詳細診断ZIPのaudio/には各短句のraw WAVと、420msの間を入れたcombined-playback WAV、manifest.txtが入ります。ZIPをこのチャットに添付すれば、生成段階でどこまで音が存在するかを直接確認できます。",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedButton(onClick = { voiceExpanded = true }, enabled = enabled && voices.isNotEmpty()) {
                Text(if (voiceName in voices) "予備音声：$voiceName" else "予備音声：自動選択")
            }
            DropdownMenu(expanded = voiceExpanded && enabled, onDismissRequest = { voiceExpanded = false }) {
                DropdownMenuItem(text = { Text("自動選択") }, onClick = { onVoice(""); voiceExpanded = false })
                voices.forEachIndexed { index, name ->
                    DropdownMenuItem(text = { Text("${index + 1}. $name") }, onClick = { onVoice(name); voiceExpanded = false })
                }
            }
            Button(
                onClick = if (previewing) {
                    onStopPreview
                } else {
                    {
                        preferences.edit().remove(KOKORO_AUDIO_TEST_ONCE_KEY).commit()
                        onPreview()
                    }
                },
                enabled = previewing || (enabled && !wavCaptureRunning),
            ) {
                Text(if (previewing) "試聴を停止" else "通常設定で試聴")
            }
            Text("設定はセッションを停止して変更できます。", style = MaterialTheme.typography.bodySmall)
            Text("予備音声の選択は、Kokoroが使えない場合のAndroidオフライン音声にだけ適用されます。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
