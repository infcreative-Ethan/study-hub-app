package com.infcreative.studyhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infcreative.studyhub.AppState
import com.infcreative.studyhub.QuizItem
import com.infcreative.studyhub.Screen
import com.infcreative.studyhub.SegmentPlayer
import com.infcreative.studyhub.normalizeAnswer

@Composable
fun QuizScreen(app: AppState, page: Screen.Quiz) {
    val ref = page.ref
    val items = page.items
    val player = remember { SegmentPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var idx by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Header("${page.title}  ${(idx + 1).coerceAtMost(items.size)} / ${items.size}", { app.back() })
        LinearProgressIndicator(progress = { idx.toFloat() / items.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())

        if (items.isEmpty() || idx >= items.size) {
            LaunchedEffect(Unit) {
                if (!saved && items.isNotEmpty()) {
                    page.scoreKey?.let { app.progress?.saveScore(it, score, items.size) }
                    saved = true
                }
            }
            val pct = if (items.isEmpty()) 100 else score * 100 / items.size
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (pct >= 90) "🏆" else if (pct >= 60) "👍" else "💪", fontSize = 96.sp)
                Text("答对 $score / ${items.size} 题", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                SpacerH(8)
                Text(if (score < items.size) "做错的题已经放进错题本，可以在“习题试卷”里重做。" else "全部答对，太棒了！", fontSize = 18.sp, color = Color.Gray)
                SpacerH(24)
                BigButton("返回") { app.back() }
            }
            return@Column
        }

        val item = items[idx]
        val q = item.q
        var chosen by remember(idx) { mutableStateOf<Int?>(null) }
        var orderOk by remember(idx) { mutableStateOf<Boolean?>(null) }
        val picked = remember(idx) { mutableStateListOf<Int>() }
        val answered = if (q.type == "order") orderOk != null else chosen != null
        val correct = if (q.type == "order") orderOk == true else chosen == q.answer
        val audioFile = q.audio?.let { app.store.file(ref, it) }

        LaunchedEffect(idx) { audioFile?.let { player.play(it) } }

        fun record(ok: Boolean) {
            if (ok) {
                score++
                if (page.scoreKey == null) app.progress?.removeWrong(item.key)
            } else app.progress?.addWrong(item.key)
        }

        Row(Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
            // 左：题干
            Column(Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text(item.section, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                SpacerH(10)
                q.passage?.let {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                    ) { Text(it, fontSize = 18.sp, lineHeight = 28.sp) }
                    SpacerH(12)
                }
                if (audioFile != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpeakerButton(64, active = player.playing) { player.play(audioFile) }
                        Spacer(Modifier.width(12.dp))
                        Text(if (player.playing) "正在播放…" else "点喇叭再听一遍", fontSize = 17.sp, color = Color.Gray)
                    }
                    SpacerH(12)
                }
                Text(q.q, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
                if (answered) {
                    SpacerH(16)
                    Text(
                        if (correct) "✓ 答对了！" else "✗ 答错了",
                        fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                    if (q.type == "order" && !correct) Text("正确答案：${q.answerText}", fontSize = 20.sp)
                    q.explain?.let { Text("💡 $it", fontSize = 18.sp, color = Color.DarkGray) }
                }
            }
            Spacer(Modifier.width(24.dp))
            // 右：选项
            Column(Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (q.type == "order") {
                    // 已选的词
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp),
                    ) {
                        Text(
                            if (picked.isEmpty()) "按顺序点下面的词…" else picked.joinToString(" ") { q.tokens[it] },
                            fontSize = 24.sp, color = if (picked.isEmpty()) Color.Gray else Color.Black,
                        )
                    }
                    q.tokens.forEachIndexed { ti, tok ->
                        val used = ti in picked
                        OptionBox(tok, if (used) Color(0xFFE5E7EB) else Color.White, enabled = !used && !answered) { picked.add(ti) }
                    }
                    if (!answered) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SoftButton("⌫ 退一个") { if (picked.isNotEmpty()) picked.removeAt(picked.size - 1) }
                            BigButton("提交", enabled = picked.size == q.tokens.size) {
                                val ans = picked.joinToString(" ") { q.tokens[it] }
                                val ok = normalizeAnswer(ans) == normalizeAnswer(q.answerText)
                                orderOk = ok; record(ok)
                            }
                        }
                    }
                } else {
                    q.options.forEachIndexed { oi, opt ->
                        val bg = when {
                            !answered -> Color.White
                            oi == q.answer -> MaterialTheme.colorScheme.tertiaryContainer
                            oi == chosen -> MaterialTheme.colorScheme.errorContainer
                            else -> Color.White
                        }
                        OptionBox("${'A' + oi}.  $opt", bg, enabled = !answered) {
                            chosen = oi; record(oi == q.answer)
                        }
                    }
                }
                if (answered) {
                    SpacerH(8)
                    BigButton(if (idx < items.size - 1) "下一题 ›" else "看成绩", Modifier.fillMaxWidth()) {
                        player.pause(); idx++
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionBox(text: String, bg: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) { Text(text, fontSize = 22.sp) }
}
