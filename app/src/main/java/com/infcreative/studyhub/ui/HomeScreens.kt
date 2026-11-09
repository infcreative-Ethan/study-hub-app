package com.infcreative.studyhub.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infcreative.studyhub.AppState
import com.infcreative.studyhub.Book
import com.infcreative.studyhub.Kid
import com.infcreative.studyhub.Screen
import kotlinx.coroutines.launch

private val AVATARS = listOf("🐼", "🐯", "🐰", "🦊", "🐻", "🐨", "🦁", "🐸", "🐵", "🐧", "🦄", "🐳")

// ================= 选择孩子 =================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KidsScreen(app: AppState) {
    var kids by remember { mutableStateOf(app.profiles.kids()) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Kid?>(null) }
    var settings by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<com.infcreative.studyhub.UpdateInfo?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!app.updateChecked) {
            app.updateChecked = true
            update = com.infcreative.studyhub.Updater.check(ctx, app.store.server)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header("全科学习 · 谁来学习？", null) { HeaderChip("⚙ 设置") { settings = true } }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(kids) { k ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(2.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .combinedClickable(onClick = { app.selectKid(k) }, onLongClick = { deleting = k })
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(k.avatar, fontSize = 64.sp)
                    SpacerH(8)
                    Text(k.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { adding = true }
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("➕", fontSize = 64.sp)
                    SpacerH(8)
                    Text("添加学生", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var avatar by remember { mutableStateOf(AVATARS[0]) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("添加学生") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(10) }, label = { Text("名字") }, singleLine = true)
                    SpacerH(12)
                    Text("选一个头像")
                    SpacerH(6)
                    AVATARS.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { a ->
                                Box(
                                    Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(if (a == avatar) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                        .clickable { avatar = a },
                                    contentAlignment = Alignment.Center,
                                ) { Text(a, fontSize = 28.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    app.profiles.addKid(name.trim(), avatar)
                    kids = app.profiles.kids(); adding = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } },
        )
    }

    deleting?.let { k ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除 ${k.name}？") },
            text = { Text("${k.name} 的全部学习记录都会被删除，不能恢复。") },
            confirmButton = {
                TextButton(onClick = { app.profiles.removeKid(k.id); kids = app.profiles.kids(); deleting = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }

    update?.let { UpdateDialog(it) { update = null } }

    if (settings) {
        var url by remember { mutableStateOf(app.store.server) }
        var taps by remember { mutableIntStateOf(0) }
        var checking by remember { mutableStateOf(false) }
        var info by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("设置") },
            text = {
                Column {
                    Text(
                        "全科学习 · 第 ${com.infcreative.studyhub.Updater.currentCode(ctx)} 版",
                        fontSize = 16.sp,
                        modifier = Modifier.clickable { taps++ },
                    )
                    SpacerH(8)
                    SoftButton(if (checking) "正在检查…" else "检查更新") {
                        if (!checking) {
                            checking = true; info = null
                            scope.launch {
                                val u = com.infcreative.studyhub.Updater.check(ctx, app.store.server)
                                checking = false
                                if (u != null) { update = u; settings = false } else info = "已经是最新版本了"
                            }
                        }
                    }
                    info?.let { Text(it, fontSize = 14.sp, color = Color.Gray) }
                    SpacerH(8)
                    Text("长按学生头像可以删除学生。", fontSize = 13.sp, color = Color.Gray)
                    // 资源服务器地址默认隐藏：连续点版本号 7 次才显示（家长/管理员用）
                    if (taps >= 7) {
                        SpacerH(8)
                        Text("资源服务器地址（管理员）", fontSize = 13.sp)
                        OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (taps >= 7) app.store.server = url; settings = false }) { Text("好的") } },
        )
    }
}

// ================= 单元列表（下载） =================
@Composable
fun BookScreen(app: AppState, page: Screen.BookPage) {
    val scope = rememberCoroutineScope()
    val book: Book = page.book
    val progress = remember { mutableStateMapOf<String, Float>() }
    var errors by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Header("${page.subject.name} · ${book.name}（${book.publisher}）", { app.back() })
        errors?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SpacerH(8) }
            items(book.units) { u ->
                val refreshKey = tick
                val ready = app.store.isReady(u)
                val update = app.store.hasUpdate(u)
                val p = progress[u.id]
                val prefix = u.path ?: ""
                val due = if (ready) app.progress?.dueCount(prefix) ?: 0 else 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                        .clickable(enabled = ready && p == null) { app.go(Screen.UnitPage(book, u)) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(u.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.titleCn, fontSize = 16.sp, color = Color.DarkGray)
                            if (due > 0) {
                                Spacer(Modifier.width(10.dp))
                                Tag("今天要复习 $due 个单词", MaterialTheme.colorScheme.secondaryContainer, Color(0xFFB45309))
                            }
                        }
                        if (p != null) {
                            SpacerH(8)
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    when {
                        u.path == null -> Text("准备中", color = Color.Gray, fontSize = 17.sp)
                        p != null -> Text("下载中 ${(p * 100).toInt()}%", fontSize = 17.sp)
                        !ready || update -> BigButton(if (update) "更新" else "下载 ${"%.1f".format(u.size / 1048576.0)}MB") {
                            progress[u.id] = 0f; errors = null
                            scope.launch {
                                try {
                                    app.store.download(u) { f -> scope.launch { progress[u.id] = f } }
                                } catch (e: Exception) {
                                    errors = "下载没有成功，请检查网络后再试。"
                                }
                                progress.remove(u.id); tick++
                            }
                        }
                        else -> Text("进入 ›", fontSize = 19.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Text(
                    "资源在第一次使用时从云端下载，下载后不用网络也能学习。",
                    fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

