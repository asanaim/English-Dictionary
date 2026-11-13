package com.shunno.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SenseItem(
    val partOfSpeech: String,
    val definition: String,
    val example: String? = null
)

data class WordEntry(
    val word: String,
    val phonetic: String,
    val senses: List<SenseItem>,
    val etymology: String? = null
)

class DictionaryRepository(context: Context) : SQLiteOpenHelper(context, "shunno_dict.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cache (word TEXT PRIMARY KEY COLLATE NOCASE, json TEXT, timestamp INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS bookmarks (word TEXT PRIMARY KEY COLLATE NOCASE, phonetic TEXT, summary TEXT, timestamp INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS history (word TEXT PRIMARY KEY COLLATE NOCASE, phonetic TEXT, summary TEXT, timestamp INTEGER)"
        )
        seedCoreOfflineVocabulary(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun seedCoreOfflineVocabulary(db: SQLiteDatabase) {
        val builtIn = listOf(
            WordEntry(
                word = "shunno",
                phonetic = "/ˈʃʊn.oʊ/ • শূন্য (śūnya)",
                senses = listOf(
                    SenseItem("noun", "Zero; nothingness; the mathematical concept of naught and philosophical openness or clarity.", "From Sanskrit śūnya, the conceptual foundation of modern zero."),
                    SenseItem("adjective", "Empty, clear, unburdened by clutter; minimalist and pure in form.")
                ),
                etymology = "From Bengali শূন্য (shunno) and Sanskrit শূন্য (śūnya, 'void, zero')."
            ),
            WordEntry(
                word = "serendipity",
                phonetic = "/ˌsɛɹ.ənˈdɪp.ɪ.ti/",
                senses = listOf(
                    SenseItem("noun", "The occurrence and development of events by chance in a happy or beneficial way.", "A fortunate stroke of serendipity brought the two researchers together.")
                ),
                etymology = "Coined by Horace Walpole in 1754 from the Persian fairy tale 'The Three Princes of Serendip'."
            ),
            WordEntry(
                word = "ephemeral",
                phonetic = "/ɪˈfɛm.əɹ.əl/",
                senses = listOf(
                    SenseItem("adjective", "Lasting for a very short time; transitory, fleeting.", "The ephemeral beauty of cherry blossoms in spring.")
                ),
                etymology = "From Greek ephēmeros ('lasting only one day')."
            ),
            WordEntry(
                word = "eloquent",
                phonetic = "/ˈɛl.ə.kwənt/",
                senses = listOf(
                    SenseItem("adjective", "Fluent, forceful, and persuasive in speaking or writing.", "She delivered an eloquent defense of human liberty.")
                ),
                etymology = "From Latin ēloquēns, present participle of ēloquī ('to speak out')."
            ),
            WordEntry(
                word = "ubiquitous",
                phonetic = "/juːˈbɪk.wɪ.təs/",
                senses = listOf(
                    SenseItem("adjective", "Present, appearing, or found everywhere at the same time; omnipresent.", "Smartphones have become ubiquitous in modern daily life.")
                ),
                etymology = "From Latin ubīque ('everywhere')."
            ),
            WordEntry(
                word = "resilience",
                phonetic = "/ɹɪˈzɪl.jəns/",
                senses = listOf(
                    SenseItem("noun", "The capacity to recover quickly from difficulties; toughness and adaptability.", "Community resilience following the monsoon floods.")
                ),
                etymology = "From Latin resiliēns, from resilīre ('to rebound, recoil')."
            ),
            WordEntry(
                word = "lucid",
                phonetic = "/ˈluː.sɪd/",
                senses = listOf(
                    SenseItem("adjective", "Expressed clearly; easy to understand; bright or luminous.", "A lucid explanation of quantum mechanics.")
                ),
                etymology = "From Latin lūcidus ('light, bright, clear')."
            ),
            WordEntry(
                word = "paradigm",
                phonetic = "/ˈpæɹ.ə.daɪm/",
                senses = listOf(
                    SenseItem("noun", "A typical example, pattern, or model of something; a worldview underlying a science.", "A new paradigm in minimalist interface design.")
                ),
                etymology = "From Greek parádeigma ('pattern, example')."
            )
        )
        for (entry in builtIn) {
            saveToCache(db, entry)
        }
    }

    private fun saveToCache(db: SQLiteDatabase, entry: WordEntry) {
        val obj = JSONObject().apply {
            put("word", entry.word)
            put("phonetic", entry.phonetic)
            put("etymology", entry.etymology ?: "")
            val arr = JSONArray()
            for (s in entry.senses) {
                arr.put(JSONObject().apply {
                    put("pos", s.partOfSpeech)
                    put("def", s.definition)
                    put("ex", s.example ?: "")
                })
            }
            put("senses", arr)
        }
        db.execSQL(
            "INSERT OR REPLACE INTO cache (word, json, timestamp) VALUES (?, ?, ?)",
            arrayOf(entry.word.lowercase(), obj.toString(), System.currentTimeMillis())
        )
    }

    private fun parseCachedJson(jsonStr: String): WordEntry {
        val obj = JSONObject(jsonStr)
        val arr = obj.optJSONArray("senses") ?: JSONArray()
        val list = mutableListOf<SenseItem>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            list.add(
                SenseItem(
                    partOfSpeech = item.optString("pos", "definition"),
                    definition = item.optString("def", ""),
                    example = item.optString("ex", "").takeIf { it.isNotBlank() }
                )
            )
        }
        return WordEntry(
            word = obj.optString("word"),
            phonetic = obj.optString("phonetic", ""),
            senses = list,
            etymology = obj.optString("etymology", "").takeIf { it.isNotBlank() }
        )
    }

    suspend fun lookupWord(rawQuery: String): WordEntry? = withContext(Dispatchers.IO) {
        val clean = rawQuery.trim().lowercase()
        if (clean.isEmpty()) return@withContext null

        // 1. Check instant local SQLite cache first (0ms offline lookup)
        readableDatabase.rawQuery("SELECT json FROM cache WHERE word = ? LIMIT 1", arrayOf(clean)).use { cursor ->
            if (cursor.moveToFirst()) {
                val entry = parseCachedJson(cursor.getString(0))
                recordHistory(entry)
                return@withContext entry
            }
        }

        // 2. Fallback to Wiktionary / Free Dictionary REST API and cache locally for offline use
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val rootArray = JSONArray(body)
                if (rootArray.length() > 0) {
                    val first = rootArray.getJSONObject(0)
                    val wordTitle = first.optString("word", clean)
                    var phonetic = first.optString("phonetic", "")
                    if (phonetic.isBlank()) {
                        val phoneticsArr = first.optJSONArray("phonetics")
                        if (phoneticsArr != null) {
                            for (i in 0 until phoneticsArr.length()) {
                                val t = phoneticsArr.getJSONObject(i).optString("text", "")
                                if (t.isNotBlank()) {
                                    phonetic = t
                                    break
                                }
                            }
                        }
                    }
                    val meanings = first.optJSONArray("meanings") ?: JSONArray()
                    val senses = mutableListOf<SenseItem>()
                    for (i in 0 until meanings.length()) {
                        val m = meanings.getJSONObject(i)
                        val pos = m.optString("partOfSpeech", "term")
                        val defs = m.optJSONArray("definitions") ?: JSONArray()
                        for (j in 0 until minOf(defs.length(), 4)) {
                            val d = defs.getJSONObject(j)
                            val defText = d.optString("definition", "")
                            val exText = d.optString("example", "").takeIf { it.isNotBlank() }
                            if (defText.isNotBlank()) {
                                senses.add(SenseItem(pos, defText, exText))
                            }
                        }
                    }
                    if (senses.isNotEmpty()) {
                        val entry = WordEntry(
                            word = wordTitle,
                            phonetic = phonetic,
                            senses = senses,
                            etymology = "Source: Wiktionary (CC BY-SA)"
                        )
                        saveToCache(writableDatabase, entry)
                        recordHistory(entry)
                        return@withContext entry
                    }
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    fun searchPrefixSuggestions(prefix: String): List<String> {
        val q = prefix.trim().lowercase()
        val results = mutableListOf<String>()
        val sql = if (q.isEmpty()) {
            "SELECT word FROM cache ORDER BY word ASC LIMIT 30"
        } else {
            "SELECT word FROM cache WHERE word LIKE ? ORDER BY word ASC LIMIT 20"
        }
        val args = if (q.isEmpty()) emptyArray() else arrayOf("$q%")
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                results.add(c.getString(0))
            }
        }
        return results
    }

    private fun recordHistory(entry: WordEntry) {
        val summary = entry.senses.firstOrNull()?.definition.orEmpty()
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO history (word, phonetic, summary, timestamp) VALUES (?, ?, ?, ?)",
            arrayOf(entry.word, entry.phonetic, summary, System.currentTimeMillis())
        )
    }

    fun toggleBookmark(entry: WordEntry): Boolean {
        val isCurrentlySaved = isBookmarked(entry.word)
        if (isCurrentlySaved) {
            writableDatabase.execSQL("DELETE FROM bookmarks WHERE word = ?", arrayOf(entry.word))
            return false
        } else {
            val summary = entry.senses.firstOrNull()?.definition.orEmpty()
            writableDatabase.execSQL(
                "INSERT OR REPLACE INTO bookmarks (word, phonetic, summary, timestamp) VALUES (?, ?, ?, ?)",
                arrayOf(entry.word, entry.phonetic, summary, System.currentTimeMillis())
            )
            return true
        }
    }

    fun isBookmarked(word: String): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE word = ? LIMIT 1", arrayOf(word)).use {
            return it.moveToFirst()
        }
    }

    fun getBookmarks(): List<Triple<String, String, String>> {
        val list = mutableListOf<Triple<String, String, String>>()
        readableDatabase.rawQuery("SELECT word, phonetic, summary FROM bookmarks ORDER BY timestamp DESC", null).use { c ->
            while (c.moveToNext()) {
                list.add(Triple(c.getString(0), c.getString(1), c.getString(2)))
            }
        }
        return list
    }

    fun getHistory(): List<Triple<String, String, String>> {
        val list = mutableListOf<Triple<String, String, String>>()
        readableDatabase.rawQuery("SELECT word, phonetic, summary FROM history ORDER BY timestamp DESC LIMIT 100", null).use { c ->
            while (c.moveToNext()) {
                list.add(Triple(c.getString(0), c.getString(1), c.getString(2)))
            }
        }
        return list
    }
}
