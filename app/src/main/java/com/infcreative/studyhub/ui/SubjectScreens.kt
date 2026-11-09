package com.infcreative.studyhub.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infcreative.studyhub.AppState
import com.infcreative.studyhub.R
import com.infcreative.studyhub.Screen
import com.infcreative.studyhub.Subject
import kotlinx.coroutines.launch

private data class Banner(val id: String, val name: String, val res: Int)

private val BANNERS = listOf(
    Banner("english", "英语", R.drawable.banner_english),
    Banner("chinese", "语文", R.drawable.banner_chinese),
    Banner("math", "数学", R.drawable.banner_math),
)

// ================= 首页：三个学科横幅 =================
@Composable
fun SubjectsScreen(app: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf(app.catalog) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true; error = null
        scope.launch {
            try {
                val c = app.store.fetchCatalog(); app.catalog = c; catalog = c
            } catch (e: Exception) {
                error = "暂时连不上资源服务器，已下载的内容仍然可以使用。"
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val kid = app.kid
    Column(Modifier.fillMaxSize()) {
        Header("${kid?.avatar ?: ""} ${kid?.name ?: ""} · 全科学习", { app.back() }) {
            HeaderChip(if (loading) "刷新中…" else "⟳ 刷新") { if (!loading) refresh() }
            HeaderChip("👤 我的") { app.go(Screen.My) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { SpacerH(6) }
            items(BANNERS) { b ->
                val subject = catalog?.firstOrNull { it.id == b.id }
                Image(
                    painter = painterResource(b.res),
                    contentDescription = b.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 1100.dp)
                        .fillMaxWidth()
                        .aspectRatio(4f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            if (subject != null && subject.books.isNotEmpty()) app.go(Screen.Shelf(subject))
                            else Toast.makeText(ctx, "${b.name}课程即将上线，敬请期待！", Toast.LENGTH_SHORT).show()
                        },
                )
            }
            item { SpacerH(16) }
        }
    }
}

// ================= 书架：课本封面 =================
@Composable
fun ShelfScreen(app: AppState, page: Screen.Shelf) {
    val subject: Subject = page.subject
    Column(Modifier.fillMaxSize()) {
        Header("${subject.name} · 选择课本", { app.back() }) {
            HeaderChip("👤 我的") { app.go(Screen.My) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            items(subject.books) { b ->
                Column(
                    Modifier.clickable { app.go(Screen.BookPage(subject, b)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Cover(app, b.cover, b.name)
                    SpacerH(10)
                    Text(b.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(b.publisher, fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun Cover(app: AppState, path: String?, name: String) {
    var bmp by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path != null) {
            val f = app.store.fetchCover(path)
            bmp = f?.let { try { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } catch (e: Exception) { null } }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.71f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("📗\n$name", fontSize = 22.sp)
    }
}

// ================= 我的：学习任务 + 学习情况 =================
private data class Task(val title: String, val tag: String, val done: Boolean)

@Composable
fun MyScreen(app: AppState) {
    val prog = app.progress
    val kid = app.kid
    val st = remember { prog?.stats() }
    val u1 = "english/pep3a/u1/"
    val tasks = remember {
        if (prog == null) emptyList() else listOf(
            Task("学完 Unit 1 的新单词", "英语 · 单词", prog.wordCount(u1) >= 18),
            Task("跟读 Unit 1 Let's talk（P4）", "英语 · 朗读", prog.hasKeyPrefix("quiz", u1 + "read/p4_talk")),
            Task("复习今天的单词", "英语 · 复习", (st?.learned ?: 0) > 0 && (st?.due ?: 1) == 0),
            Task("完成 Unit 1 知识点练习", "英语 · 练习", prog.score(u1 + "kp") != null),
            Task("完成 Unit 1 单元测试（一）", "英语 · 试卷", prog.score(u1 + "t1") != null),
        )
    }
    Column(Modifier.fillMaxSize()) {
        Header("👤 我的", { app.back() }) {
            HeaderChip("切换孩子") { app.stack.clear(); app.stack.add(Screen.Kids) }
        }
        Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // 左：个人信息 + 学习任务
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(kid?.avatar ?: "🙂", fontSize = 56.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(kid?.name ?: "", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("三年级 · 英语 人教PEP版", fontSize = 15.sp, color = Color.DarkGray)
                    }
                }
                SpacerH(16)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📋 学习任务", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Tag("演示：以后由老师布置", MaterialTheme.colorScheme.secondaryContainer, Color(0xFFB45309))
                }
                SpacerH(8)
                Text("已完成 ${tasks.count { it.done }} / ${tasks.size}", fontSize = 15.sp, color = Color.Gray)
                SpacerH(8)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(tasks) { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (t.done) "✅" else "⬜", fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = if (t.done) Color.Gray else Color.Black)
                                Text(t.tag, fontSize = 13.sp, color = Color.Gray)
                            }
                            Text(if (t.done) "已完成" else "待完成", fontSize = 14.sp,
                                color = if (t.done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            // 右：学习情况汇总
            Column(Modifier.weight(1f)) {
                Text("📊 学习情况", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                SpacerH(10)
                if (st == null) { Text("还没有学习记录"); return@Column }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("已学单词", "${st.learned}", "个", Modifier.weight(1f))
                    StatBox("已掌握", "${st.mastered}", "个", Modifier.weight(1f))
                    StatBox("今天待复习", "${st.due}", "个", Modifier.weight(1f))
                }
                SpacerH(12)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("学完课文", "${st.lessons}", "段", Modifier.weight(1f))
                    StatBox("跟读句子", "${st.reads}", "句", Modifier.weight(1f))
                    StatBox("跟读平均", "${st.readAvg}", "分", Modifier.weight(1f))
                }
                SpacerH(12)
                StatBox("错题本", "${st.wrong}", "道待订正", Modifier.fillMaxWidth())
                SpacerH(14)
                Text("练习与试卷成绩", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                SpacerH(6)
                if (st.quizzes.isEmpty()) Text("还没做过练习或试卷", color = Color.Gray)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(st.quizzes) { (k, sc, total) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(quizName(k), fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text("$sc / $total", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (total > 0 && sc * 100 / total >= 80) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

private fun quizName(key: String): String {
    val parts = key.trimEnd('/').split("/")
    val unit = parts.getOrNull(parts.size - 2)?.let { if (it.startsWith("u")) "Unit " + it.drop(1) else if (it == "rev") "Revision" else it } ?: ""
    val what = when (parts.lastOrNull()) {
        "kp" -> "知识点练习"; "t1" -> "单元测试（一）"; "t2" -> "单元测试（二）"; else -> parts.lastOrNull() ?: ""
    }
    return "英语 $unit · $what"
}

@Composable
private fun StatBox(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}
