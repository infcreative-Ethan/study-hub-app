package com.infcreative.studyhub.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.infcreative.studyhub.AppState
import com.infcreative.studyhub.ReadResult
import com.infcreative.studyhub.Recorder
import com.infcreative.studyhub.SegmentPlayer
import com.infcreative.studyhub.Sentence
import com.infcreative.studyhub.Speech
import com.infcreative.studyhub.stars
import kotlinx.coroutines.launch
import java.io.File

/** 跟读评分面板：听原声 → 录音 → 逐词打分 → 回放自己的录音 */
@Composable
fun ReadPanel(app: AppState, scoreKey: String, sentence: Sentence, playOriginal: () -> Unit, player: SegmentPlayer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { Recorder() }
    val wav = remember(scoreKey) { File(ctx.cacheDir, "read_${scoreKey.hashCode()}.wav") }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember(scoreKey) { mutableStateOf<ReadResult?>(null) }
    var installed by remember { mutableStateOf(Speech.installed(ctx)) }
    var dlProgress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var best by remember(scoreKey) { mutableStateOf(app.progress?.score(scoreKey)?.first) }

    fun start() {
        error = null
        player.pause()
        recording = true
        busy = true
        val dur = ((sentence.end ?: 0.0) - (sentence.start ?: 0.0)).coerceAtLeast(1.0)
        val maxSec = (dur * 2.2 + 2.0).coerceIn(3.0, 15.0)
        scope.launch {
            try {
                val model = Speech.load(ctx)
                val r = recorder.recordAndScore(model, sentence.en, maxSec, wav)
                result = r
                val old = app.progress?.score(scoreKey)?.first ?: -1
                if (r.score > old) { app.progress?.saveScore(scoreKey, r.score, 100); best = r.score }
            } catch (e: Exception) {
                error = "录音失败：${e.message ?: "未知错误"}"
            }
            recording = false
            busy = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else error = "需要允许使用麦克风才能跟读评分"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(12.dp),
    ) {
        if (!installed) {
            Text("跟读评分第一次使用需要下载语音识别模型（约 40MB），下载后不用网络也能用。", fontSize = 15.sp)
            Spacer(Modifier.padding(4.dp))
            val p = dlProgress
            if (p != null) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                Text("下载中 ${(p * 100).toInt()}%", fontSize = 14.sp)
            } else {
                BigButton("下载语音模型", color = MaterialTheme.colorScheme.tertiary) {
                    dlProgress = 0f; error = null
                    scope.launch {
                        try {
                            Speech.install(ctx, app.store.server) { f -> scope.launch { dlProgress = f } }
                            installed = Speech.installed(ctx)
                        } catch (e: Exception) {
                            error = "下载没有成功，请检查网络后再试。"
                        }
                        dlProgress = null
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("🔊 原声") { playOriginal() }
            BigButton(
                if (recording) "■ 读完了" else "🎤 跟读",
                color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                enabled = recording || !busy,
            ) {
                if (recording) recorder.requestStop()
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            SoftButton("▶ 我的录音") { if (wav.exists() && wav.length() > 44 && !recording) player.play(wav) }
            Spacer(Modifier.weight(1f))
            best?.let { Text("最好 ${"★".repeat(stars(it))}${"☆".repeat(3 - stars(it))}", fontSize = 15.sp, color = Color(0xFFB45309)) }
        }
        if (recording) {
            Text("正在听…读完这句后点“读完了”", fontSize = 15.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        }
        result?.let { r ->
            Spacer(Modifier.padding(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("★".repeat(stars(r.score)) + "☆".repeat(3 - stars(r.score)), fontSize = 26.sp, color = Color(0xFFF59E0B))
                Spacer(Modifier.width(8.dp))
                Text("${r.score} 分", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        r.score >= 85 -> "读得真棒！"
                        r.score >= 60 -> "不错，红色的词再练练"
                        else -> "先听原声，再慢慢读一遍"
                    },
                    fontSize = 15.sp,
                )
            }
            val text = buildAnnotatedString {
                r.marks.forEach { m ->
                    val c = when (m.state) {
                        2 -> Color(0xFF059669)
                        1 -> Color(0xFFD97706)
                        0 -> Color(0xFFDC2626)
                        else -> Color.Gray
                    }
                    withStyle(SpanStyle(color = c, fontWeight = FontWeight.Bold)) { append(m.word) }
                    append(" ")
                }
            }
            Text(text, fontSize = 24.sp)
            Text("绿色：读得好　橙色：还可以　红色：没听清", fontSize = 12.sp, color = Color.Gray)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
    }
}
