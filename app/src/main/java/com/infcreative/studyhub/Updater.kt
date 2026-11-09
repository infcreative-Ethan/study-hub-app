package com.infcreative.studyhub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val code: Int, val name: String, val url: String, val notes: String)

/**
 * 应用内更新：先查资源服务器 app/latest.json（国内快），查不到再查 GitHub Releases。
 */
object Updater {
    private const val GITHUB_API = "https://api.github.com/repos/infcreative-Ethan/study-hub-app/releases/latest"

    fun currentCode(ctx: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    } catch (e: Exception) { 0L }

    fun currentName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** 返回比当前新的版本；没有新版本或查不到返回 null */
    suspend fun check(ctx: Context, server: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val info: UpdateInfo? = try {
            val o = JSONObject(get(server + "app/latest.json"))
            val apk = o.getString("apk")
            UpdateInfo(o.getInt("versionCode"), o.optString("versionName"),
                if (apk.startsWith("http")) apk else server + "app/" + apk, o.optString("notes"))
        } catch (e: Exception) {
            try {
                val o = JSONObject(get(GITHUB_API))
                val tag = o.getString("tag_name")             // build-23
                val code = tag.substringAfter("build-").toIntOrNull() ?: 0
                val assets = o.getJSONArray("assets")
                var url = ""
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
                }
                if (url.isEmpty()) null else UpdateInfo(code, tag, url, o.optString("body"))
            } catch (e2: Exception) { null }
        }
        if (info != null && info.code > currentCode(ctx)) info else null
    }

    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "apk").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "StudyHub-${info.code}.apk")
        fun open(u: String): HttpURLConnection = (URL(u).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 60000
        }
        var c = open(info.url)
        var redirects = 0
        while (c.responseCode in 300..399 && redirects < 5) {   // GitHub 下载会跳转到别的域名
            val next = c.getHeaderField("Location"); c.disconnect(); c = open(next); redirects++
        }
        if (c.responseCode != 200) { val code = c.responseCode; c.disconnect(); throw RuntimeException("下载失败（$code）") }
        try {
            val total = c.contentLengthLong.coerceAtLeast(1L)
            var done = 0L
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n); done += n
                        onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } finally { c.disconnect() }
        out
    }

    /** 能直接安装返回 true；需要先去设置里允许“安装未知应用”返回 false（会自动打开设置页） */
    fun install(ctx: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
