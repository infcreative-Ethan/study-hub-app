package com.infcreative.studyhub.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infcreative.studyhub.AppState
import com.infcreative.studyhub.Screen
import com.infcreative.studyhub.SegmentPlayer

@Composable
fun LessonScreen(app: AppState, page: Screen.LessonPage) {
    val ref = page.ref
    val unit = page.unit
    val lesson = unit.lessons[page.index]
    val audio = app.store.file(ref, lesson.audio)
    val player = remember(lesson.id) { SegmentPlayer() }
    DisposableEffect(lesson.id) { onDispose { player.release() } }

    val timed = lesson.sentences.isNotEmpty() && lesson.sentences.all { it.start != null && it.end != null }
    var showCn by remember { mutableStateOf(true) }
    var loop by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    var playingAll by remember { mutableStateOf(false) }
    var active by remember(lesson.id) { mutableIntStateOf(-1) }
    var bigImage by remember { mutableStateOf(false) }
    var calibrating by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val marks = remember { mutableStateListOf<Double>() }
    val listState = rememberLazyListState()
    val prefix = ref.path ?: ""

    player.speed = if (slow) 0.75f else 1.0f

    fun stopAll() { player.pause(); playingAll = false }

    fun playSentence(i: Int) {
        val s = lesson.sentences[i]
        playingAll = false
        active = i
        if (s.start == null || s.end == null) { player.play(audio); return }
        player.play(audio, s.start, s.end) {
            if (loop && active == i) playSentence(i)
        }
    }

    fun playAll() {
        playingAll = true
        if (!timed) {
            player.play(audio) { playingAll = false; app.progress?.markLesson(prefix + lesson.id) }
            return
        }
        val first = lesson.sentences.first().start!!
        val last = lesson.sentences.last().end!!
        player.play(audio, (first - 0.3).coerceAtLeast(0.0), last + 0.3) {
            playingAll = false
            app.progress?.markLesson(prefix + lesson.id)
        }
    }

    // 整段播放时，根据播放位置高亮当前句子
    val hl: Int = if (playingAll && timed && !calibrating) {
        val pos = player.position
        lesson.sentences.indexOfLast { (it.start ?: 0.0) <= pos + 0.05 }
    } else active
    LaunchedEffect(hl) { if (hl >= 0) listState.animateScrollToItem(hl) }

    val curPage = lesson.sentences.getOrNull(hl)?.page ?: lesson.sentences.firstOrNull()?.page ?: lesson.page
    val img = rememberBitmap(app.store.file(ref, "pages/p$curPage.jpg"))

    Column(Modifier.fillMaxSize()) {
        Header("P${lesson.page}  ${lesson.title}", { app.back() }) {
            HeaderChip(if (player.playing && playingAll) "⏸ 停止" else "▶ 整段播放") {
                if (player.playing) stopAll() else playAll()
            }
            if (lesson.sentences.isNotEmpty()) {
                HeaderChip("中文", selected = showCn) { showCn = !showCn }
                if (timed) HeaderChip("🔁 单句循环", selected = loop) { loop = !loop }
            }
            HeaderChip("🐢 慢速", selected = slow) { slow = !slow }
            if (timed) HeaderChip("⋯") { menu = true }
        }

        if (lesson.approx && !calibrating) {
            Text(
                "这一段有背景音乐，分句时间是自动估算的，可能不太准。家长可以点右上角“⋯”校准。",
                fontSize = 14.sp, color = Color(0xFFB45309),
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        Row(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            // 左：课本原页
            Box(
                Modifier
                    .weight(if (bigImage) 1f else 0.42f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable { bigImage = !bigImage },
                contentAlignment = Alignment.Center,
            ) {
                if (img != null) Image(img, contentDescription = "课本第${curPage}页", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else Text("课本图片缺失", color = Color.Gray)
            }
            if (!bigImage) {
                Spacer(Modifier.width(12.dp))
                // 右：句子列表
                Column(Modifier.weight(0.58f).fillMaxHeight()) {
                    lesson.note?.let {
                        Text("💡 $it", fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (lesson.sentences.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎧", fontSize = 64.sp)
                            SpacerH(12)
                            BigButton(if (player.playing) "⏸ 暂停" else "▶ 播放录音") { if (player.playing) stopAll() else playAll() }
                            SpacerH(16)
                            val d = player.duration
                            if (d > 0) {
                                LinearProgressIndicator(progress = { (player.position / d).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text("%.0f / %.0f 秒".format(player.position, d), fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        itemsIndexed(lesson.sentences) { i, s ->
                            val on = i == hl
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.White)
                                    .border(
                                        if (on) 2.dp else 1.dp,
                                        if (on) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0),
                                        RoundedCornerShape(14.dp),
                                    )
                                    .clickable(enabled = !calibrating) { playSentence(i) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.en, fontSize = 24.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                                    app.progress?.score(prefix + "read/" + lesson.id + "/" + i)?.let { sc ->
                                        val st = com.infcreative.studyhub.stars(sc.first)
                                        Text("★".repeat(st) + "☆".repeat(3 - st), fontSize = 14.sp, color = Color(0xFFF59E0B))
                                    }
                                }
                                if (showCn && s.cn.isNotEmpty()) Text(s.cn, fontSize = 17.sp, color = Color.DarkGray)
                            }
                        }
                    }
                    val readIdx = active
                    if (!calibrating && timed && readIdx in lesson.sentences.indices) {
                        Spacer(Modifier.padding(4.dp))
                        ReadPanel(
                            app, prefix + "read/" + lesson.id + "/" + readIdx, lesson.sentences[readIdx],
                            playOriginal = { playSentence(readIdx) }, player = player,
                        )
                    }
                    if (calibrating) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(12.dp),
                        ) {
                            val n = lesson.sentences.size
                            Text(
                                when {
                                    marks.isEmpty() -> "校准：点“开始”播放录音，听到第 1 句开始时马上点“这一句开始了”。"
                                    marks.size < n -> "已标记 ${marks.size}/$n 句。听到下一句开始时点按钮。"
                                    else -> "全部标好了。最后一句读完时点“最后一句结束”。"
                                },
                                fontSize = 16.sp,
                            )
                            SpacerH(8)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (marks.isEmpty() && !player.playing) {
                                    BigButton("开始") { active = -1; player.play(audio) }
                                } else if (marks.size < n) {
                                    BigButton("这一句开始了 (${marks.size + 1})", color = MaterialTheme.colorScheme.secondary) {
                                        marks.add(player.currentSeconds()); active = marks.size - 1
                                    }
                                } else {
                                    BigButton("最后一句结束", color = MaterialTheme.colorScheme.tertiary) {
                                        val endT = player.currentSeconds()
                                        player.pause()
                                        val times = marks.mapIndexed { i, st ->
                                            val e = if (i < marks.size - 1) marks[i + 1] - 0.05 else endT
                                            (st - 0.15).coerceAtLeast(0.0) to e
                                        }
                                        app.store.saveTiming(ref, lesson.id, times)
                                        calibrating = false
                                        app.replaceTop(page.copy(unit = app.store.loadUnit(ref)))
                                    }
                                }
                                SoftButton("取消") { player.pause(); marks.clear(); calibrating = false }
                            }
                        }
                    }
                }
            }
        }

        // 底部：上一课 / 下一课
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SoftButton("‹ 上一个", Modifier) {
                if (page.index > 0) app.replaceTop(page.copy(index = page.index - 1))
            }
            Spacer(Modifier.weight(1f))
            Text("${page.index + 1} / ${unit.lessons.size}", fontSize = 15.sp, color = Color.Gray)
            Spacer(Modifier.weight(1f))
            SoftButton("学完了 ✓") { app.progress?.markLesson(prefix + lesson.id) }
            Spacer(Modifier.width(10.dp))
            SoftButton("下一个 ›") {
                app.progress?.markLesson(prefix + lesson.id)
                if (page.index < unit.lessons.size - 1) app.replaceTop(page.copy(index = page.index + 1))
            }
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("分句时间校准（家长）") },
            text = { Text("如果点句子时播放的不是这一句，可以重新校准：边听录音，边在每一句开始时点按钮。校准结果只保存在这台设备上。") },
            confirmButton = {
                TextButton(onClick = { menu = false; stopAll(); marks.clear(); calibrating = true; bigImage = false }) { Text("开始校准") }
            },
            dismissButton = {
                TextButton(onClick = {
                    menu = false
                    app.store.clearTiming(ref, lesson.id)
                    app.replaceTop(page.copy(unit = app.store.loadUnit(ref)))
                }) { Text("恢复默认时间") }
            },
        )
    }
}
