package com.infcreative.studyhub.ui

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.key
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
import com.infcreative.studyhub.Screen
import com.infcreative.studyhub.SegmentPlayer
import com.infcreative.studyhub.Word
import kotlin.random.Random

// ================= 学新词 =================
@Composable
fun WordLearnScreen(app: AppState, page: Screen.WordLearn) {
    val ref = page.ref
    val words = page.words
    val player = remember { SegmentPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var i by remember { mutableIntStateOf(page.start.coerceIn(0, (words.size - 1).coerceAtLeast(0))) }
    val prefix = ref.path ?: ""

    if (words.isEmpty()) { Text("没有新词了"); return }
    val w = words[i]
    LaunchedEffect(i) { playWord(app, ref, w, player) }

    Column(Modifier.fillMaxSize()) {
        Header(if (page.browse) "单词  ${i + 1} / ${words.size}" else "学新词  ${i + 1} / ${words.size}", { app.back() })
        LinearProgressIndicator(progress = { (i + 1f) / words.size }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 左：单词卡
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(2.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(w.w, fontSize = 52.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(w.ph, fontSize = 20.sp, color = Color.Gray)
                SpacerH(6)
                Text(w.cn, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                SpacerH(12)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { com.infcreative.studyhub.Tts.speak(w.ex) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("例句：${w.ex}", fontSize = 17.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("🔈", fontSize = 17.sp)
                }
            }
            Spacer(Modifier.width(20.dp))
            // 右：听和跟读
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpeakerButton(72) { playWord(app, ref, w, player) }
                    Spacer(Modifier.width(12.dp))
                    Text("再听一遍", fontSize = 17.sp)
                }
                key(w.w) {
                    ReadPanel(
                        app, prefix + "read/word/" + w.w,
                        com.infcreative.studyhub.Sentence(w.w, w.cn, 0.0, 1.0, null),
                        playOriginal = { playWord(app, ref, w, player) }, player = player,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SoftButton("‹ 上一个") { if (i > 0) i-- }
            Spacer(Modifier.weight(1f))
            if (page.browse) {
                if (i < words.size - 1) BigButton("下一个 ›") { i++ }
                else BigButton("返回单词表") { app.back() }
            } else if (i < words.size - 1) {
                BigButton("记住了，下一个 ›") { app.progress?.learned(prefix + w.w); i++ }
            } else {
                BigButton("学完了！做个小测验", color = MaterialTheme.colorScheme.tertiary) {
                    words.forEach { app.progress?.learned(prefix + it.w) }
                    app.replaceTop(Screen.WordReview(ref, page.unit, words, "新词小测验", srs = false))
                }
            }
        }
    }
}

// ================= 复习 / 练习 =================
private enum class Mode(val label: String) { SPELL("看中文，拼英文"), MEANING("看英文，想中文"), DICTATE("听发音，拼单词") }

private data class Task(val word: Word, val mode: Mode, val retry: Boolean)

@Composable
fun WordReviewScreen(app: AppState, page: Screen.WordReview) {
    val ref = page.ref
    val prefix = ref.path ?: ""
    val player = remember { SegmentPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val queue = remember {
        mutableStateListOf<Task>().apply {
            page.words.shuffled().forEach { add(Task(it, Mode.values()[Random.nextInt(3)], false)) }
        }
    }
    var idx by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    val firstTotal = page.words.size

    fun finish(task: Task, ok: Boolean) {
        if (!task.retry) {
            if (ok) right++ else wrong++
            if (page.srs) app.progress?.reviewed(prefix + task.word.w, ok)
        }
        // 答错的词换一种方式，排到后面再考一次
        if (!ok) queue.add(Task(task.word, Mode.values()[Random.nextInt(3)], true))
        idx++
    }

    Column(Modifier.fillMaxSize()) {
        Header("${page.title}  ${(idx + 1).coerceAtMost(queue.size)} / ${queue.size}", { app.back() })
        LinearProgressIndicator(progress = { idx.toFloat() / queue.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        if (idx >= queue.size) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (wrong == 0) "🎉" else "💪", fontSize = 96.sp)
                Text("完成！第一次就答对 $right / $firstTotal 个", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                if (page.srs) Text("系统已按遗忘曲线安排好下次复习时间", fontSize = 18.sp, color = Color.Gray)
                SpacerH(24)
                BigButton("返回") { app.back() }
            }
            return@Column
        }
        val task = queue[idx]
        Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            when (task.mode) {
                Mode.MEANING -> MeaningCard(app, ref, task, player, idx) { finish(task, it) }
                else -> SpellCard(app, ref, task, player, idx) { finish(task, it) }
            }
        }
    }
}

@Composable
private fun ModeTitle(t: Task) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Tag(t.mode.label, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        if (t.retry) {
            Spacer(Modifier.width(8.dp))
            Tag("再来一次", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        }
    }
}

/** 看英文想中文：不用输入，自己判断记没记住 */
@Composable
private fun MeaningCard(app: AppState, ref: com.infcreative.studyhub.UnitRef, t: Task, player: SegmentPlayer, key: Int, done: (Boolean) -> Unit) {
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { playWord(app, ref, t.word, player) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ModeTitle(t)
        SpacerH(20)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.word.w, fontSize = 80.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            SpeakerButton(64) { playWord(app, ref, t.word, player) }
        }
        SpacerH(20)
        if (!shown) {
            Text("先在心里想一想它的中文意思", fontSize = 20.sp, color = Color.Gray)
            SpacerH(20)
            BigButton("看答案") { shown = true }
        } else {
            Text(t.word.cn, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            SpacerH(24)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BigButton("✓ 我想对了", color = MaterialTheme.colorScheme.tertiary) { done(true) }
                BigButton("✗ 没想起来", color = MaterialTheme.colorScheme.error) { done(false) }
            }
        }
    }
}

/** 拼写（看中文拼英文 / 听音拼写）：点字母块，不用键盘 */
@Composable
private fun SpellCard(app: AppState, ref: com.infcreative.studyhub.UnitRef, t: Task, player: SegmentPlayer, key: Int, done: (Boolean) -> Unit) {
    val target = t.word.w.lowercase()
    val letters = remember(key) {
        val base = target.filter { it.isLetter() }.toList()
        val extra = ('a'..'z').filter { it !in base }.shuffled().take(2)
        (base + extra).shuffled()
    }
    val picked = remember(key) { mutableStateListOf<Int>() }   // letters 下标
    var result by remember(key) { mutableStateOf<Boolean?>(null) }
    val need = target.count { it.isLetter() }

    LaunchedEffect(key) { if (t.mode == Mode.DICTATE) playWord(app, ref, t.word, player) }

    fun check() {
        val ans = picked.joinToString("") { letters[it].toString() }
        val ok = ans == target.filter { it.isLetter() }
        result = ok
        playWord(app, ref, t.word, player)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ModeTitle(t)
        SpacerH(16)
        if (t.mode == Mode.SPELL) {
            Text(t.word.cn, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        } else {
            SpeakerButton(88) { playWord(app, ref, t.word, player) }
            SpacerH(6)
            Text("听一听，拼出这个单词", fontSize = 18.sp, color = Color.Gray)
        }
        SpacerH(20)
        // 答案格子
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in 0 until need) {
                val ch = picked.getOrNull(k)?.let { letters[it].toString() } ?: ""
                val color = when (result) {
                    true -> MaterialTheme.colorScheme.tertiaryContainer
                    false -> MaterialTheme.colorScheme.errorContainer
                    null -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .border(2.dp, if (k == picked.size && result == null) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(ch, fontSize = 34.sp, fontWeight = FontWeight.Bold) }
            }
        }
        SpacerH(20)
        if (result == null) {
            // 字母块
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 900.dp)) {
                letters.forEachIndexed { li, c ->
                    val used = li in picked
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (used) Color(0xFFE5E7EB) else MaterialTheme.colorScheme.primary)
                            .clickable(enabled = !used) {
                                picked.add(li)
                                if (picked.size == need) check()
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (used) "" else c.toString(), fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            SpacerH(16)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SoftButton("⌫ 退一格") { if (picked.isNotEmpty()) picked.removeAt(picked.size - 1) }
                SoftButton("清空") { picked.clear() }
                SoftButton("不会，看答案") { result = false; playWord(app, ref, t.word, player) }
            }
        } else {
            Text(
                if (result == true) "✓ 拼对了！" else "正确答案：${t.word.w}",
                fontSize = 30.sp, fontWeight = FontWeight.Bold,
                color = if (result == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            )
            Text("${t.word.w}  ${t.word.ph}  ${t.word.cn}", fontSize = 20.sp, color = Color.DarkGray)
            SpacerH(16)
            BigButton("下一个 ›") { done(result == true) }
        }
    }
}
