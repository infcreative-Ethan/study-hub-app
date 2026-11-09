package com.infcreative.studyhub

import org.json.JSONArray
import org.json.JSONObject

// ---------- JSON helpers ----------
fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
fun JSONObject.int(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
fun JSONObject.arr(k: String): JSONArray = if (has(k) && !isNull(k)) getJSONArray(k) else JSONArray()
inline fun <T> JSONArray.mapObj(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

// ---------- catalog ----------
data class UnitRef(
    val id: String, val title: String, val titleCn: String,
    val path: String?, val size: Long, val version: Int,
)

data class Book(val id: String, val name: String, val publisher: String, val units: List<UnitRef>)
data class Subject(val id: String, val name: String, val icon: String, val books: List<Book>)

fun parseCatalog(o: JSONObject): List<Subject> = o.arr("subjects").mapObj { s ->
    Subject(
        s.getString("id"), s.getString("name"), s.str("icon") ?: "📘",
        s.arr("books").mapObj { b ->
            Book(
                b.getString("id"), b.getString("name"), b.str("publisher") ?: "",
                b.arr("units").mapObj { u ->
                    UnitRef(
                        u.getString("id"), u.getString("title"), u.str("titleCn") ?: "",
                        u.str("path"), u.optLong("size", 0L), u.optInt("version", 1),
                    )
                },
            )
        },
    )
}

// ---------- unit content ----------
data class Sentence(val en: String, val cn: String, val start: Double?, val end: Double?, val page: Int?)

data class Lesson(
    val id: String, val page: Int, val part: String, val title: String, val kind: String,
    val audio: String, val image: String, val sentences: List<Sentence>,
    val approx: Boolean, val note: String?,
)

data class Word(val w: String, val ph: String, val cn: String, val ex: String, val audio: String?)
data class KItem(val en: String, val cn: String, val ex: String?)
data class KCard(val title: String, val items: List<KItem>, val tip: String?)

data class Question(
    val type: String,          // choice | order
    val q: String,
    val options: List<String>,
    val answer: Int,
    val explain: String?,
    val audio: String?,
    val tokens: List<String>,
    val answerText: String,
    val passage: String?,
)

data class Section(val title: String, val listen: Boolean, val qs: List<Question>)
data class Test(val id: String, val title: String, val sections: List<Section>)

data class UnitData(
    val id: String, val title: String, val titleCn: String,
    val lessons: List<Lesson>, val words: List<Word>,
    val knowledge: List<KCard>, val kpractice: List<Question>, val tests: List<Test>,
)

fun parseQuestion(o: JSONObject): Question {
    val type = o.str("t") ?: "choice"
    val au = if (o.has("au") && !o.isNull("au")) o.getJSONObject("au").str("f") else null
    return Question(
        type = type,
        q = o.str("q") ?: "",
        options = o.arr("o").strings(),
        answer = if (type == "order") 0 else o.optInt("a", 0),
        explain = o.str("x"),
        audio = au,
        tokens = o.arr("w").strings(),
        answerText = if (type == "order") o.optString("a", "") else "",
        passage = o.str("passage"),
    )
}

fun parseUnit(o: JSONObject): UnitData = UnitData(
    id = o.getString("id"),
    title = o.str("title") ?: "",
    titleCn = o.str("titleCn") ?: "",
    lessons = o.arr("lessons").mapObj { l ->
        Lesson(
            id = l.getString("id"), page = l.optInt("page", 0), part = l.str("part") ?: "",
            title = l.str("title") ?: "", kind = l.str("kind") ?: "talk",
            audio = l.str("audio") ?: "", image = l.str("image") ?: "",
            sentences = l.arr("sentences").mapObj { s ->
                Sentence(s.str("en") ?: "", s.str("cn") ?: "", s.dbl("start"), s.dbl("end"), s.int("page"))
            },
            approx = l.optBoolean("approx", false), note = l.str("note"),
        )
    },
    words = o.arr("words").mapObj { w ->
        Word(w.getString("w"), w.str("ph") ?: "", w.str("cn") ?: "", w.str("ex") ?: "", w.str("audio"))
    },
    knowledge = o.arr("knowledge").mapObj { k ->
        KCard(k.str("title") ?: "", k.arr("items").mapObj { KItem(it.str("en") ?: "", it.str("cn") ?: "", it.str("ex")) }, k.str("tip"))
    },
    kpractice = o.arr("kpractice").mapObj { parseQuestion(it) },
    tests = o.arr("tests").mapObj { t ->
        Test(
            t.getString("id"), t.str("title") ?: "",
            t.arr("sections").mapObj { s ->
                Section(s.str("title") ?: "", s.optBoolean("listen", false), s.arr("qs").mapObj { parseQuestion(it) })
            },
        )
    },
)

/** 比较连词成句答案：忽略大小写、标点和多余空格 */
fun normalizeAnswer(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
