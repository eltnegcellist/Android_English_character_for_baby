package com.eltnegcellist.emma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eltnegcellist.emma.ai.LiteTopicGuide
import kotlinx.coroutines.launch

/** Native, bundled help. The system Back button and the fixed header both return to settings. */
@Composable
internal fun LiteTopicGuideDialog(onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val contextIndex = 4 + LiteTopicGuide.topics.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("← 戻る") }
                    Text("話題の判定方法", style = MaterialTheme.typography.titleMedium)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "intro") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GuideHeading("話題の判定方法・話題一覧")
                            Text(LiteTopicGuide.INTRO)
                        }
                    }
                    item(key = "contents") {
                        val links = listOf(
                            "判定の仕組み" to 2, "話題一覧" to 3,
                            "話題の引き継ぎ" to contextIndex, "拾われにくいとき" to contextIndex + 1,
                        )
                        Column {
                            links.chunked(2).forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { (label, index) ->
                                        TextButton(
                                            onClick = { scope.launch { listState.animateScrollToItem(index) } },
                                            modifier = Modifier.weight(1f),
                                        ) { Text(label) }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "logic") { GuideSection(LiteTopicGuide.logic) }
                    item(key = "topics-heading") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GuideHeading("話題と呼びかけ例")
                            Text(LiteTopicGuide.TOPICS_INTRO)
                        }
                    }
                    items(LiteTopicGuide.topics, key = { "topic-${it.scene}" }) { topic ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                GuideHeading(topic.title)
                                Text(
                                    "手がかり：${topic.clues}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                topic.examples.forEach { Text("「$it」") }
                                if (topic.note.isNotBlank()) Text(topic.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item(key = "context") { GuideSection(LiteTopicGuide.context) }
                    item(key = "tips") { GuideSection(LiteTopicGuide.tips) }
                    item(key = "top") {
                        TextButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                            Text("先頭へ戻る")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun GuideSection(section: LiteTopicGuide.Section) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideHeading(section.title)
            section.paragraphs.forEach { Text(it) }
        }
    }
}
