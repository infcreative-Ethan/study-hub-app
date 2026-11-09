package com.infcreative.studyhub

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** 离线英语语音识别（Vosk 小模型），用于朗读评分 */
object Speech {
    const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    private var model: Model? = null

    private fun root(ctx: Context) = File(ctx.filesDir, "vosk")
    private fun modelDir(ctx: Context) = File(root(ctx), MODEL_NAME)
    fun installed(ctx: Context) = File(modelDir(ctx), "am/final.mdl").exists()

    /** 从资源服务器下载并解压模型（约 40MB，只需一次） */
    suspend fun install(ctx: Context, server: String, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val c = URL(server + "models/$MODEL_NAME.zip").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 60000
        try {
            if (c.responseCode != 200) throw RuntimeException("服务器上还没有放语音模型（HTTP ${c.responseCode}）")
            val total = c.contentLengthLong.coerceAtLeast(1L)
            val tmp = File(ctx.cacheDir, "model.zip")
            var done = 0L
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress((done.toFloat() / total * 0.9f).coerceAtMost(0.9f))
                    }
                }
            }
            val dest = root(ctx)
            dest.deleteRecursively()
            dest.mkdirs()
            ZipInputStream(tmp.inputStream().buffered()).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val f = File(dest, e.name)
                    if (!f.canonicalPath.startsWith(dest.canonicalPath)) continue
                    if (e.isDirectory) f.mkdirs() else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { zin.copyTo(it) }
                    }
                }
            }
            tmp.delete()
            onProgress(1f)
        } finally {
            c.disconnect()
        }
    }

    suspend fun load(ctx: Context): Model = withContext(Dispatchers.IO) {
        model ?: Model(modelDir(ctx).path).also { model = it }
    }
}

/** 一次朗读的评分结果：每个词的状态 + 总分 */
data class WordMark(val word: String, val state: Int)   // 2 好 1 一般 0 没读对 -1 不计分（人名等）
data class ReadResult(val marks: List<WordMark>, val score: Int, val heard: String)

/** 课文里的词 → 用于评分的小写词表 */
fun readingTokens(text: String): List<Pair<String, String>> {
    val nums = mapOf("1" to "one", "2" to "two", "3" to "three", "4" to "four", "5" to "five",
        "6" to "six", "7" to "seven", "8" to "eight", "9" to "nine", "10" to "ten")
    return text.split(Regex("[\\s]+")).mapNotNull { raw ->
        if (raw.contains("/")) return@mapNotNull null
        val w = raw.replace("’", "'").trim { !it.isLetterOrDigit() && it != '\'' }
        if (w.isEmpty()) return@mapNotNull null
        val key = nums[w] ?: w.lowercase()
        if (!key.all { it.isLetter() || it == '\'' }) return@mapNotNull null
        w to key
    }
}

class Recorder {
    @Volatile private var stop = false
    fun requestStop() { stop = true }

    /**
     * 录音并识别。maxSec 秒后自动停止。录音同时存成 wav，方便回放。
     */
    @SuppressLint("MissingPermission")
    suspend fun recordAndScore(model: Model, sentence: String, maxSec: Double, wav: File): ReadResult = withContext(Dispatchers.IO) {
        stop = false
        val rate = 16000
        val tokens = readingTokens(sentence)
        val vocab = tokens.map { it.second }.distinct()
        val grammar = JSONArray().apply { vocab.forEach { put(it) }; put("[unk]") }.toString()
        val rec = Recognizer(model, rate.toFloat(), grammar)
        rec.setWords(true)
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val ar = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8192))
        val raf = RandomAccessFile(wav, "rw")
        raf.setLength(0)
        raf.write(ByteArray(44))
        var bytes = 0
        try {
            ar.startRecording()
            val buf = ByteArray(4096)
            val maxBytes = (maxSec * rate * 2).toInt()
            while (!stop && bytes < maxBytes) {
                val n = ar.read(buf, 0, buf.size)
                if (n <= 0) continue
                raf.write(buf, 0, n)
                bytes += n
                rec.acceptWaveForm(buf, n)
            }
        } finally {
            try { ar.stop() } catch (e: Exception) { }
            ar.release()
            writeWavHeader(raf, bytes, rate)
            raf.close()
        }
        val result = JSONObject(rec.finalResult)
        rec.close()
        score(tokens, result)
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataLen: Int, rate: Int) {
        fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        raf.seek(0)
        raf.write("RIFF".toByteArray()); raf.write(le32(36 + dataLen)); raf.write("WAVE".toByteArray())
        raf.write("fmt ".toByteArray()); raf.write(le32(16)); raf.write(le16(1)); raf.write(le16(1))
        raf.write(le32(rate)); raf.write(le32(rate * 2)); raf.write(le16(2)); raf.write(le16(16))
        raf.write("data".toByteArray()); raf.write(le32(dataLen))
    }

    /** 课文词和识别出的词按顺序对齐，再按置信度给每个词打分 */
    private fun score(tokens: List<Pair<String, String>>, res: JSONObject): ReadResult {
        val arr = res.optJSONArray("result") ?: JSONArray()
        val heard = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("word") != "[unk]" }
            .map { it.optString("word") to it.optDouble("conf", 0.0) }
        val n = tokens.size
        val m = heard.size
        // 最长公共子序列对齐
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) for (j in 1..m) {
            dp[i][j] = if (tokens[i - 1].second == heard[j - 1].first) dp[i - 1][j - 1] + 1 else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
        val conf = DoubleArray(n) { -1.0 }
        var i = n; var j = m
        while (i > 0 && j > 0) {
            if (tokens[i - 1].second == heard[j - 1].first) { conf[i - 1] = heard[j - 1].second; i--; j-- }
            else if (dp[i - 1][j] >= dp[i][j - 1]) i-- else j--
        }
        val marks = tokens.mapIndexed { k, (word, _) ->
            val c = conf[k]
            // 句中大写开头的词多是人名，识别模型可能不认识，没读到时不扣分
            val isName = k > 0 && word.first().isUpperCase() && word != "I" && !word.startsWith("I'")
            val state = when {
                c >= 0.75 -> 2
                c >= 0.4 -> 1
                c >= 0 -> 0
                isName -> -1
                else -> 0
            }
            WordMark(word, state)
        }
        val counted = marks.filter { it.state >= 0 }
        val score = if (counted.isEmpty()) 0 else (counted.sumOf { if (it.state == 2) 1.0 else if (it.state == 1) 0.6 else 0.0 } * 100 / counted.size).toInt()
        return ReadResult(marks, score, heard.joinToString(" ") { it.first })
    }
}

fun stars(score: Int) = when {
    score >= 85 -> 3
    score >= 60 -> 2
    score >= 30 -> 1
    else -> 0
}
