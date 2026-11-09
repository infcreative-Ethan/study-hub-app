package com.infcreative.studyhub.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.infcreative.studyhub.Test
import com.infcreative.studyhub.SegmentPlayer
import com.infcreative.studyhub.Tts
import com.infcreative.studyhub.UnitData
import com.infcreative.studyhub.UnitRef
import com.infcreative.studyhub.today

private val TABS = listOf("📖 课文", "🔤 单词", "💡 知识点", "📝 习题试卷")

fun kindIcon(kind: String) = when (kind) {
    "talk" -> "💬"; "learn" -> "🧩"; "chant" -> "🥁"; "song" -> "🎵"
    "read" -> "📖"; "phonics" -> "🔠"; "listen" -> "🎧"; else -> "📄"
}

@Composable
fun UnitScreen(app: AppState, page: Screen.UnitPage) {
    val ref = page.ref
    var loadError by remember { mutableStateOf<String?>(null) }
    val unit: UnitData? = remember(ref.id) {
        try { app.store.loadUnit(ref) } catch (e: Exception) { loadError = e.toString(); null }
    }
    var tab by remember { mutableIntStateOf(page.tab) }

    Column(Modifier.fillMaxSize()) {
        Header("${ref.title}  ${ref.titleCn}", { app.back() }) {
            TABS.forEachIndexed { i, t -> HeaderChip(t, selected = tab == i) { tab = i; app.replaceTop(page.copy(tab = i)) } }
        }
        if (unit == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("内容读取失败，请返回后重新下载这个单元。\n${loadError ?: ""}", fontSize = 18.sp)
            }
            return@Column
        }
        when (tab) {
            0 -> LessonsTab(app, ref, unit)
            1 -> WordsTab(app, ref, unit)
            2 -> KnowledgeTab(app, ref, unit)
            else -> TestsTab(app, ref, unit)
        }
    }
}

// ---------------- 课文 ----------------
@Composable
private fun LessonsTab(app: AppState, ref: UnitRef, unit: UnitData) {
    val prefix = ref.path ?: ""
    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(unit.lessons) { i, l ->
            val done = app.progress?.lessonDone(prefix + l.id) == true
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .clickable { app.go(Screen.LessonPage(ref, unit, i)) }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag("P${l.page}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    if (l.part.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Tag("Part ${l.part}", MaterialTheme.colorScheme.secondaryContainer, Color(0xFFB45309))
                    }
                    Spacer(Modifier.weight(1f))
                    if (done) Text("✅", fontSize = 18.sp)
                }
                SpacerH(8)
                Text("${kindIcon(l.kind)} ${l.title}", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                if (l.sentences.isNotEmpty()) Text("${l.sentences.size} 句", fontSize = 14.sp, color = Color.Gray)
                else Text("听力练习", fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

// ---------------- 单词 ----------------
@Composable
private fun WordsTab(app: AppState, ref: UnitRef, unit: UnitData) {
    val prefix = ref.path ?: ""
    val prog = app.progress
    val player = remember { SegmentPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val states = unit.words.map { prog?.word(prefix + it.w) }
    val newWords = unit.words.filterIndexed { i, _ -> states[i] == null }
    val dueWords = unit.words.filterIndexed { i, _ -> states[i]?.let { it.due <= today() } == true }
    val learned = states.count { it != null }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("共 ${unit.words.size} 个 · 已学 $learned 个 · 今天待复习 ${dueWords.size} 个", fontSize = 18.sp, modifier = Modifier.weight(1f))
            BigButton("学新词 (${newWords.size})", enabled = newWords.isNotEmpty()) {
                app.go(Screen.WordLearn(ref, unit, newWords))
            }
            BigButton("今日复习 (${dueWords.size})", color = MaterialTheme.colorScheme.secondary, enabled = dueWords.isNotEmpty()) {
                app.go(Screen.WordReview(ref, unit, dueWords, "今日复习"))
            }
            SoftButton("全部练一遍") { app.go(Screen.WordReview(ref, unit, unit.words, "全部单词练习", srs = false)) }
        }
        SpacerH(12)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(unit.words) { i, w ->
                val st = states[i]
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .clickable { app.go(Screen.WordLearn(ref, unit, unit.words, i, browse = true)) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeakerButton(44) { playWord(app, ref, w, player) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${w.w}  ${w.ph}", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(w.cn, fontSize = 15.sp, color = Color.DarkGray)
                    }
                    prog?.score(prefix + "read/word/" + w.w)?.first?.let { sc ->
                        val n = com.infcreative.studyhub.stars(sc)
                        Text("🎤" + "★".repeat(n) + "☆".repeat(3 - n) + "  ", fontSize = 13.sp, color = Color(0xFFB45309))
                    }
                    Text(
                        when {
                            st == null -> "未学"
                            st.stage >= 7 -> "已掌握 ⭐"
                            else -> "★".repeat(st.stage) + "☆".repeat(6 - st.stage.coerceAtMost(6))
                        },
                        fontSize = 14.sp, color = if (st == null) Color.Gray else Color(0xFFF59E0B),
                    )
                }
            }
        }
    }
}

fun playWord(app: AppState, ref: UnitRef, w: com.infcreative.studyhub.Word, player: SegmentPlayer) {
    val f = w.audio?.let { app.store.file(ref, it) }
    if (f != null && f.exists()) player.play(f) else Tts.speak(w.w)
}

// ---------------- 知识点 ----------------
@Composable
private fun KnowledgeTab(app: AppState, ref: UnitRef, unit: UnitData) {
    val prefix = ref.path ?: ""
    val score = app.progress?.score(prefix + "kp")
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SpacerH(12)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("点英文句子可以听发音", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                score?.let { Text("上次练习：${it.first}/${it.second}  ", fontSize = 16.sp) }
                BigButton("✏ 知识点练习 (${unit.kpractice.size} 题)", color = MaterialTheme.colorScheme.tertiary) {
                    app.go(
                        Screen.Quiz(
                            ref, "知识点练习", prefix + "kp",
                            unit.kpractice.mapIndexed { i, q -> QuizItem(prefix + "kp/$i", "知识点练习", q) },
                        ),
                    )
                }
            }
        }
        items(unit.knowledge) { k ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text("💡 ${k.title}", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                SpacerH(8)
                k.items.forEach {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { Tts.speak(it.en.trimStart('—')) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(it.en, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(it.cn + (it.ex?.let { e -> "（例：$e）" } ?: ""), fontSize = 16.sp, color = Color.DarkGray, modifier = Modifier.weight(1f))
                    }
                }
                k.tip?.let {
                    SpacerH(8)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(12.dp),
                    ) { Text("📌 $it", fontSize = 16.sp) }
                }
            }
        }
        item { SpacerH(24) }
    }
}

// ---------------- 习题试卷 ----------------
@Composable
private fun TestsTab(app: AppState, ref: UnitRef, unit: UnitData) {
    val prefix = ref.path ?: ""
    val prog = app.progress
    val wrongKeys = prog?.wrongKeys(prefix) ?: emptyList()

    // 所有题目按 key 建索引，错题本用
    val all = remember(unit.id) {
        val m = LinkedHashMap<String, QuizItem>()
        unit.kpractice.forEachIndexed { i, q -> m[prefix + "kp/$i"] = QuizItem(prefix + "kp/$i", "知识点练习", q) }
        (unit.tests + unit.practice).forEach { t ->
            t.sections.forEachIndexed { si, s ->
                s.qs.forEachIndexed { qi, q ->
                    val k = prefix + "${t.id}/$si/$qi"
                    m[k] = QuizItem(k, "${t.title} · ${s.title}", q)
                }
            }
        }
        m
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (unit.practice.isNotEmpty() && unit.tests.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("单元测试", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
        gridItems(unit.tests) { t -> TestCard(app, ref, prefix, t, "📝") }
        if (unit.practice.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("同步练习（按课时）", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
            gridItems(unit.practice) { t -> TestCard(app, ref, prefix, t, "✏️") }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable(enabled = wrongKeys.isNotEmpty()) {
                        val items = wrongKeys.mapNotNull { all[it] }
                        app.go(Screen.Quiz(ref, "错题重做", null, items))
                    }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("📕 错题本", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (wrongKeys.isEmpty()) "暂无错题，真棒！" else "${wrongKeys.size} 道错题，点这里重做（做对就移出错题本）",
                    fontSize = 16.sp,
                )
            }
        }
    }
}


@Composable
private fun TestCard(app: AppState, ref: UnitRef, prefix: String, t: Test, icon: String) {
    val prog = app.progress
            val sc = prog?.score(prefix + t.id)
            val total = t.sections.sumOf { it.qs.size }
            val listen = t.sections.filter { it.listen }.sumOf { it.qs.size }
            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                    .clickable {
                        val items = t.sections.flatMapIndexed { si, s ->
                            s.qs.mapIndexed { qi, q -> QuizItem(prefix + "${t.id}/$si/$qi", s.title, q) }
                        }
                        app.go(Screen.Quiz(ref, t.title, prefix + t.id, items))
                    }
                    .padding(18.dp),
            ) {
                Text("$icon ${t.title}", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                t.sub?.let { Text(it, fontSize = 15.sp, color = Color.Gray, maxLines = 1) }
                SpacerH(6)
                Text(if (listen > 0) "共 $total 题，其中听力 $listen 题" else "共 $total 题", fontSize = 16.sp, color = Color.DarkGray)
                SpacerH(6)
                Text(
                    sc?.let { "上次成绩：${it.first} / ${it.second}" } ?: "还没做过",
                    fontSize = 16.sp, color = if (sc != null) MaterialTheme.colorScheme.tertiary else Color.Gray,
                )
            }
        }
