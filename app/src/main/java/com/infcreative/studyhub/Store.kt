package com.infcreative.studyhub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

const val DEFAULT_SERVER = "http://101.35.43.66:8686/res/"

/** 资源下载、本地缓存与设置 */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val root: File = File(ctx.filesDir, "packs").apply { mkdirs() }

    var server: String
        get() = prefs.getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(v) {
            val s = v.trim().let { if (it.endsWith("/")) it else "$it/" }
            prefs.edit().putString("server", s).apply()
        }

    private fun httpGet(url: String, dest: File? = null): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}: $url")
            if (dest != null) {
                dest.parentFile?.mkdirs()
                val tmp = File(dest.path + ".part")
                c.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
                return null
            }
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    // ----- catalog -----
    private val catalogFile = File(ctx.filesDir, "catalog.json")

    fun cachedCatalog(): List<Subject>? = try {
        if (catalogFile.exists()) parseCatalog(JSONObject(catalogFile.readText())) else null
    } catch (e: Exception) { null }

    suspend fun fetchCatalog(): List<Subject> = withContext(Dispatchers.IO) {
        val bytes = httpGet(server + "catalog.json")!!
        val text = String(bytes, Charsets.UTF_8)
        val list = parseCatalog(JSONObject(text))
        catalogFile.writeText(text)
        list
    }

    // ----- unit packs -----
    fun unitDir(ref: UnitRef): File = File(root, ref.path!!.trimEnd('/'))

    fun localVersion(ref: UnitRef): Int {
        if (ref.path == null) return 0
        val f = File(unitDir(ref), ".version")
        return if (f.exists() && File(unitDir(ref), "unit.json").exists()) f.readText().trim().toIntOrNull() ?: 0 else 0
    }

    fun isReady(ref: UnitRef) = ref.path != null && localVersion(ref) > 0
    fun hasUpdate(ref: UnitRef) = isReady(ref) && localVersion(ref) < ref.version

    suspend fun download(ref: UnitRef, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val base = server + ref.path!!.trimEnd('/') + "/"
        val dir = unitDir(ref).apply { mkdirs() }
        val manifest = JSONObject(String(httpGet(base + "manifest.json")!!, Charsets.UTF_8))
        val files = manifest.arr("files")
        var total = 0L
        for (i in 0 until files.length()) total += files.getJSONObject(i).optLong("s", 0)
        var done = 0L
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val rel = f.getString("p")
            val size = f.optLong("s", 0)
            val local = File(dir, rel)
            // unit.json 每次都重新下载，媒体文件大小一致则跳过
            if (rel == "unit.json" || !local.exists() || local.length() != size) {
                httpGet(base + rel.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }, local)
            }
            done += size
            onProgress(if (total > 0) done.toFloat() / total else 1f)
        }
        File(dir, ".version").writeText(manifest.optInt("version", ref.version).toString())
    }

    fun deleteUnit(ref: UnitRef) { if (ref.path != null) unitDir(ref).deleteRecursively() }

    fun loadUnit(ref: UnitRef): UnitData {
        val dir = unitDir(ref)
        val u = parseUnit(JSONObject(File(dir, "unit.json").readText()))
        // 应用家长校准过的时间轴
        val lessons = u.lessons.map { l ->
            val ov = File(dir, "timing_${l.id}.json")
            if (!ov.exists()) l else try {
                val arr = JSONArray(ov.readText())
                val ss = l.sentences.mapIndexed { i, s ->
                    if (i < arr.length()) {
                        val t = arr.getJSONArray(i)
                        s.copy(start = t.getDouble(0), end = t.getDouble(1))
                    } else s
                }
                l.copy(sentences = ss, approx = false)
            } catch (e: Exception) { l }
        }
        return u.copy(lessons = lessons)
    }

    fun saveTiming(ref: UnitRef, lessonId: String, times: List<Pair<Double, Double>>) {
        val arr = JSONArray()
        times.forEach { arr.put(JSONArray().put(it.first).put(it.second)) }
        File(unitDir(ref), "timing_$lessonId.json").writeText(arr.toString())
    }

    fun clearTiming(ref: UnitRef, lessonId: String) { File(unitDir(ref), "timing_$lessonId.json").delete() }

    fun file(ref: UnitRef, rel: String): File = File(unitDir(ref), rel)

    /** 课本封面：先用本地缓存，没有再从服务器下载 */
    fun cachedCover(path: String): File = File(ctx.filesDir, "covers/" + path.replace("/", "_"))

    suspend fun fetchCover(path: String): File? = withContext(Dispatchers.IO) {
        val f = cachedCover(path)
        if (f.exists() && f.length() > 0) return@withContext f
        try { httpGet(server + path, f); f } catch (e: Exception) { null }
    }
}

// ================= 孩子档案与学习记录 =================

data class Kid(val id: String, val name: String, val avatar: String)

/** 艾宾浩斯复习间隔（天）：第 n 级掌握后，隔多少天再复习 */
val REVIEW_DAYS = intArrayOf(0, 1, 2, 4, 7, 15, 30, 60)
const val MAX_STAGE = 7

data class WordState(val stage: Int, val due: Long, val wrong: Int)

fun today(): Long = LocalDate.now().toEpochDay()

class Profiles(ctx: Context) {
    private val dir = File(ctx.filesDir, "kids").apply { mkdirs() }
    private val listFile = File(dir, "kids.json")

    fun kids(): List<Kid> = try {
        if (!listFile.exists()) emptyList()
        else JSONArray(listFile.readText()).mapObj { Kid(it.getString("id"), it.getString("name"), it.str("avatar") ?: "🙂") }
    } catch (e: Exception) { emptyList() }

    private fun saveKids(list: List<Kid>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar)) }
        listFile.writeText(a.toString())
    }

    fun addKid(name: String, avatar: String): Kid {
        val k = Kid("k" + System.currentTimeMillis(), name, avatar)
        saveKids(kids() + k)
        return k
    }

    fun removeKid(id: String) {
        saveKids(kids().filter { it.id != id })
        File(dir, "$id.json").delete()
    }

    fun progress(kid: Kid) = Progress(File(dir, "${kid.id}.json"))
}

/** 单个孩子的学习进度，保存在一个 JSON 文件里 */
class Progress(private val file: File) {
    private val data: JSONObject = try {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    } catch (e: Exception) { JSONObject() }

    private fun obj(k: String): JSONObject {
        if (!data.has(k)) data.put(k, JSONObject())
        return data.getJSONObject(k)
    }

    private fun save() = file.writeText(data.toString())

    // ---- 单词 ----
    fun word(key: String): WordState? {
        val w = obj("words")
        if (!w.has(key)) return null
        val o = w.getJSONObject(key)
        return WordState(o.optInt("stage"), o.optLong("due"), o.optInt("wrong"))
    }

    private fun putWord(key: String, s: WordState) {
        obj("words").put(key, JSONObject().put("stage", s.stage).put("due", s.due).put("wrong", s.wrong))
        save()
    }

    /** 学完新词：进入第 1 级，明天复习 */
    fun learned(key: String) {
        if (word(key) == null) putWord(key, WordState(1, today() + REVIEW_DAYS[1], 0))
    }

    /** 复习结果 */
    fun reviewed(key: String, correct: Boolean) {
        val s = word(key) ?: WordState(1, today(), 0)
        val n = if (correct) {
            val st = (s.stage + 1).coerceAtMost(MAX_STAGE)
            WordState(st, today() + REVIEW_DAYS[st], s.wrong)
        } else {
            WordState(1, today() + 1, s.wrong + 1)
        }
        putWord(key, n)
    }

    fun dueCount(prefix: String): Int {
        val w = obj("words"); val t = today(); var n = 0
        w.keys().forEach { k -> if (k.startsWith(prefix) && w.getJSONObject(k).optLong("due") <= t) n++ }
        return n
    }

    // ---- 汇总统计（“我的”页面） ----
    data class Stats(
        val learned: Int, val mastered: Int, val due: Int, val lessons: Int,
        val reads: Int, val readAvg: Int, val quizzes: List<Triple<String, Int, Int>>, val wrong: Int,
    )

    fun stats(): Stats {
        val w = obj("words"); val t = today()
        var learned = 0; var mastered = 0; var due = 0
        w.keys().forEach { k ->
            val o = w.getJSONObject(k); learned++
            if (o.optInt("stage") >= MAX_STAGE) mastered++
            if (o.optLong("due") <= t) due++
        }
        val l = obj("lessons"); var lessons = 0
        l.keys().forEach { if (l.optBoolean(it)) lessons++ }
        val q = obj("quiz"); var reads = 0; var readSum = 0
        val quizzes = mutableListOf<Triple<String, Int, Int>>()
        q.keys().forEach { k ->
            val o = q.getJSONObject(k)
            if (k.contains("/read/")) { reads++; readSum += o.optInt("score") }
            else quizzes.add(Triple(k, o.optInt("score"), o.optInt("total")))
        }
        return Stats(learned, mastered, due, lessons, reads, if (reads > 0) readSum / reads else 0,
            quizzes.sortedBy { it.first }, obj("wrong").length())
    }

    fun hasKeyPrefix(map: String, prefix: String): Boolean {
        val o = obj(map); var found = false
        o.keys().forEach { if (it.startsWith(prefix)) found = true }
        return found
    }

    fun wordCount(prefix: String): Int {
        val o = obj("words"); var n = 0
        o.keys().forEach { if (it.startsWith(prefix)) n++ }
        return n
    }

    // ---- 课文 ----
    fun lessonDone(key: String) = obj("lessons").optBoolean(key, false)
    fun markLesson(key: String) { obj("lessons").put(key, true); save() }

    // ---- 练习/试卷 ----
    fun score(key: String): Pair<Int, Int>? {
        val q = obj("quiz")
        if (!q.has(key)) return null
        val o = q.getJSONObject(key)
        return o.optInt("score") to o.optInt("total")
    }

    fun saveScore(key: String, score: Int, total: Int) {
        obj("quiz").put(key, JSONObject().put("score", score).put("total", total).put("time", System.currentTimeMillis()))
        save()
    }

    // ---- 错题本：key -> 错误次数 ----
    fun addWrong(key: String) { val w = obj("wrong"); w.put(key, w.optInt(key, 0) + 1); save() }
    fun removeWrong(key: String) { obj("wrong").remove(key); save() }
    fun wrongKeys(prefix: String): List<String> {
        val w = obj("wrong"); val r = mutableListOf<String>()
        w.keys().forEach { if (it.startsWith(prefix)) r.add(it) }
        return r.sorted()
    }
}
