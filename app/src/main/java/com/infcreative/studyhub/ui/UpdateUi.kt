package com.infcreative.studyhub.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.infcreative.studyhub.UpdateInfo
import com.infcreative.studyhub.Updater
import kotlinx.coroutines.launch
import java.io.File

/** 发现新版本的弹窗：下载 → 安装 */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text("发现新版本") },
        text = {
            Column {
                Text("新版本：第 ${info.code} 版（当前第 ${Updater.currentCode(ctx)} 版）", fontSize = 16.sp)
                if (info.notes.isNotBlank()) Text(info.notes.take(200), fontSize = 14.sp)
                progress?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    Text("下载中 ${(it * 100).toInt()}%", fontSize = 14.sp)
                }
                msg?.let { Text(it, fontSize = 14.sp) }
                Text("更新后学习记录不会丢失。", fontSize = 13.sp)
            }
        },
        confirmButton = {
            val f = apk
            if (f != null) {
                TextButton(onClick = {
                    if (!Updater.install(ctx, f)) msg = "请在打开的设置页里允许“安装未知应用”，返回后再点“安装”。"
                }) { Text("安装") }
            } else {
                TextButton(enabled = progress == null, onClick = {
                    progress = 0f; msg = null
                    scope.launch {
                        try {
                            val file = Updater.download(ctx, info) { p -> scope.launch { progress = p } }
                            apk = file
                            if (!Updater.install(ctx, file)) msg = "请在打开的设置页里允许“安装未知应用”，返回后再点“安装”。"
                        } catch (e: Exception) {
                            msg = "下载没有成功，请检查网络后再试。"
                        }
                        progress = null
                    }
                }) { Text("立即更新") }
            }
        },
        dismissButton = { if (progress == null) TextButton(onClick = onDismiss) { Text("以后再说") } },
    )
}
