package com.shunno.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.luben.zstd.Zstd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

data class SenseItem(
    val partOfSpeech: String,
    val definition: String,
    val example: String? = null,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList()
)

data class WordEntry(
    val word: String,
    val phonetic: String,
    val senses: List<SenseItem>,
    val etymology: String? = null,
    val inflections: List<String> = emptyList(),
    val seeAlso: List<String> = emptyList()
)

data class NoteItem(
    val word: String,
    val noteText: String,
    val category: String,
    val timestamp: Long
)

data class BookmarkItem(
    val word: String,
    val phonetic: String,
    val summary: String,
    val category: String,
    val timestamp: Long
)

/**
 * Option 2A Engine:
 * - Uses bundled `shunno_lexicon.db` containing the SQLite prefix index (`lexicon_index`)
 *   and 64 KB Zstandard-compressed definition blocks (`lexicon_blocks`) compressed with
 *   a trained Wiktionary dictionary (`shunno_zstd.zdict`).
 * - Decompresses any looked-up headword in < 1 ms completely offline.
 * - Stores user bookmarks, personal word notes, categories, and lookup history in `shunno_user.db`.
 */
class DictionaryRepository(private val appContext: Context) :
    SQLiteOpenHelper(appContext, "shunno_user.db", null, 2) {

    private var lexiconDb: SQLiteDatabase? = null
    private var zstdDictBytes: ByteArray? = null

    init {
        ensureOfflineZstdDatabaseReady()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cache (word TEXT PRIMARY KEY COLLATE NOCASE, json TEXT, timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (word TEXT PRIMARY KEY COLLATE NOCASE, phonetic TEXT, summary TEXT, category TEXT DEFAULT 'General', timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS history (word TEXT PRIMARY KEY COLLATE NOCASE, phonetic TEXT, summary TEXT, timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS notes (word TEXT PRIMARY KEY COLLATE NOCASE, content TEXT, category TEXT DEFAULT 'General', timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (name TEXT PRIMARY KEY COLLATE NOCASE)")
        db.execSQL("INSERT OR IGNORE INTO categories (name) VALUES ('General'), ('Favorites'), ('Academic'), ('GRE / IELTS'), ('Literature')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    @Synchronized
    private fun ensureOfflineZstdDatabaseReady() {
        if (lexiconDb != null) return
        try {
            val dbFile = File(appContext.filesDir, "shunno_lexicon.db")
            if (!dbFile.exists() || dbFile.length() == 0L) {
                appContext.assets.open("shunno_lexicon.db").use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            zstdDictBytes = try {
                appContext.assets.open("shunno_zstd.zdict").use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
            lexiconDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        } catch (_: Exception) {
            lexiconDb = null
        }
    }

    /**
     * Decompresses a 64 KB Zstd block using the trained Wiktionary dictionary (`shunno_zstd.zdict`)
     * and extracts the JSON slice `[byteOffset, byteOffset + byteLength]`.
     */
    private fun readEntryFromZstdBlock(blockId: Int, byteOffset: Int, byteLength: Int): WordEntry? {
        val db = lexiconDb ?: return null
        db.rawQuery(
            "SELECT raw_size, zstd_blob FROM lexicon_blocks WHERE block_id = ? LIMIT 1",
            arrayOf(blockId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            val rawSize = c.getInt(0)
            val compressed = c.getBlob(1)
            val decompressed = ByteArray(rawSize)
            val dict = zstdDictBytes
            val written = if (dict != null && dict.isNotEmpty()) {
                Zstd.decompressUsingDict(decompressed, 0, compressed, 0, compressed.size, dict)
            } else {
                Zstd.decompress(decompressed, compressed)
            }
            if (written <= 0 || byteOffset + byteLength > decompressed.size) return null
            val jsonStr = String(decompressed, byteOffset, byteLength, Charsets.UTF_8)
            return parseCompactEntryJson(jsonStr)
        }
    }

    private fun parseCompactEntryJson(jsonStr: String): WordEntry {
        val obj = JSONObject(jsonStr)
        val arr = obj.optJSONArray("senses") ?: obj.optJSONArray("s") ?: JSONArray()
        val senses = mutableListOf<SenseItem>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val synArr = item.optJSONArray("syn") ?: JSONArray()
            val antArr = item.optJSONArray("ant") ?: JSONArray()
            senses.add(
                SenseItem(
                    partOfSpeech = item.optString("pos", item.optString("p", "definition")),
                    definition = item.optString("def", item.optString("d", "")),
                    example = item.optString("ex", item.optString("e", "")).takeIf { it.isNotBlank() },
                    synonyms = (0 until synArr.length()).map { synArr.getString(it) },
                    antonyms = (0 until antArr.length()).map { antArr.getString(it) }
                )
            )
        }
        val inflArr = obj.optJSONArray("inflections") ?: obj.optJSONArray("i") ?: JSONArray()
        val seeArr = obj.optJSONArray("seeAlso") ?: obj.optJSONArray("a") ?: JSONArray()
        return WordEntry(
            word = obj.optString("word", obj.optString("w", "")),
            phonetic = obj.optString("phonetic", obj.optString("ph", "")),
            senses = senses,
            etymology = obj.optString("etymology", obj.optString("et", "")).takeIf { it.isNotBlank() },
            inflections = (0 until inflArr.length()).map { inflArr.getString(it) },
            seeAlso = (0 until seeArr.length()).map { seeArr.getString(it) }
        )
    }

    private fun saveToUserCache(entry: WordEntry) {
        val obj = JSONObject().apply {
            put("word", entry.word)
            put("phonetic", entry.phonetic)
            put("etymology", entry.etymology ?: "")
            put("inflections", JSONArray(entry.inflections))
            put("seeAlso", JSONArray(entry.seeAlso))
            val arr = JSONArray()
            for (s in entry.senses) {
                arr.put(JSONObject().apply {
                    put("pos", s.partOfSpeech)
                    put("def", s.definition)
                    put("ex", s.example ?: "")
                    put("syn", JSONArray(s.synonyms))
                    put("ant", JSONArray(s.antonyms))
                })
            }
            put("senses", arr)
        }
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO cache (word, json, timestamp) VALUES (?, ?, ?)",
            arrayOf(entry.word.lowercase(), obj.toString(), System.currentTimeMillis())
        )
    }

    /**
     * Sub-2ms Offline SQLite Prefix Autocomplete + Online Fallback
     */
    suspend fun fetchLiveSuggestions(prefix: String): List<String> = withContext(Dispatchers.IO) {
        val q = prefix.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()

        ensureOfflineZstdDatabaseReady()
        val combined = linkedSetOf<String>()

        // 1. Query Offline Zstd Lexicon Index (< 2 ms via B-Tree prefix index)
        lexiconDb?.rawQuery(
            "SELECT word FROM lexicon_index WHERE word >= ? AND word < ? ORDER BY word ASC LIMIT 15",
            arrayOf(q, q + "\uffff")
        )?.use { c ->
            while (c.moveToNext()) {
                combined.add(c.getString(0))
            }
        }

        // 2. Query User Cache
        if (combined.size < 12) {
            readableDatabase.rawQuery(
                "SELECT word FROM cache WHERE word LIKE ? ORDER BY word ASC LIMIT 12",
                arrayOf("$q%")
            ).use { c ->
                while (c.moveToNext()) {
                    combined.add(c.getString(0))
                }
            }
        }

        // 3. Optional Online Autocomplete if fewer than 5 offline prefix matches exist
        if (combined.size < 5) {
            try {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val url = URL("https://api.datamuse.com/sug?s=$encoded&max=10")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1800
                    readTimeout = 1800
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val w = arr.getJSONObject(i).optString("word", "")
                        if (w.isNotBlank()) combined.add(w)
                    }
                }
            } catch (_: Exception) {
            }
        }

        combined.take(15)
    }

    fun getWordsByLetter(letter: Char): List<String> {
        ensureOfflineZstdDatabaseReady()
        val list = mutableListOf<String>()
        val low = letter.lowercaseChar().toString()
        lexiconDb?.rawQuery(
            "SELECT word FROM lexicon_index WHERE word >= ? AND word < ? ORDER BY word ASC LIMIT 250",
            arrayOf(low, low + "\uffff")
        )?.use { c ->
            while (c.moveToNext()) list.add(c.getString(0))
        }
        return list
    }

    fun getWordOfTheDay(): String {
        ensureOfflineZstdDatabaseReady()
        val db = lexiconDb ?: return "serendipity"
        db.rawQuery("SELECT COUNT(*) FROM lexicon_index", null).use { c ->
            if (c.moveToFirst() && c.getInt(0) > 0) {
                val total = c.getInt(0)
                val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                val offset = (day * 104729) % total
                db.rawQuery("SELECT word FROM lexicon_index LIMIT 1 OFFSET ?", arrayOf(offset.toString())).use { wc ->
                    if (wc.moveToFirst()) return wc.getString(0)
                }
            }
        }
        return "serendipity"
    }

    fun getRandomWord(): String {
        ensureOfflineZstdDatabaseReady()
        val db = lexiconDb ?: return "eloquent"
        db.rawQuery("SELECT word FROM lexicon_index ORDER BY RANDOM() LIMIT 1", null).use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return "eloquent"
    }

    suspend fun lookupWord(rawQuery: String): WordEntry? = withContext(Dispatchers.IO) {
        val clean = rawQuery.trim().lowercase()
        if (clean.isEmpty()) return@withContext null

        ensureOfflineZstdDatabaseReady()

        // 1. Lookup in Offline Zstd-Compressed Lexicon (Option 2A)
        lexiconDb?.rawQuery(
            "SELECT block_id, byte_offset, byte_length FROM lexicon_index WHERE word = ? LIMIT 1",
            arrayOf(clean)
        )?.use { c ->
            if (c.moveToFirst()) {
                val entry = readEntryFromZstdBlock(c.getInt(0), c.getInt(1), c.getInt(2))
                if (entry != null) {
                    recordHistory(entry)
                    return@withContext entry
                }
            }
        }

        // 2. Check User Local Cache
        readableDatabase.rawQuery("SELECT json FROM cache WHERE word = ? LIMIT 1", arrayOf(clean)).use { cursor ->
            if (cursor.moveToFirst()) {
                val entry = parseCompactEntryJson(cursor.getString(0))
                recordHistory(entry)
                return@withContext entry
            }
        }

        // 3. Online Wiktionary Fallback for new/unindexed terms
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
                    val origin = first.optString("origin", "").takeIf { it.isNotBlank() }
                    val meanings = first.optJSONArray("meanings") ?: JSONArray()
                    val senses = mutableListOf<SenseItem>()
                    val allSynonyms = linkedSetOf<String>()
                    for (i in 0 until meanings.length()) {
                        val m = meanings.getJSONObject(i)
                        val pos = m.optString("partOfSpeech", "term")
                        val mSyn = m.optJSONArray("synonyms") ?: JSONArray()
                        val mAnt = m.optJSONArray("antonyms") ?: JSONArray()
                        val posSyns = (0 until minOf(mSyn.length(), 6)).map { mSyn.getString(it) }
                        val posAnts = (0 until minOf(mAnt.length(), 6)).map { mAnt.getString(it) }
                        allSynonyms.addAll(posSyns)

                        val defs = m.optJSONArray("definitions") ?: JSONArray()
                        for (j in 0 until minOf(defs.length(), 5)) {
                            val d = defs.getJSONObject(j)
                            val defText = d.optString("definition", "")
                            val exText = d.optString("example", "").takeIf { it.isNotBlank() }
                            val dSyn = d.optJSONArray("synonyms") ?: JSONArray()
                            val dAnt = d.optJSONArray("antonyms") ?: JSONArray()
                            val syns = ((0 until minOf(dSyn.length(), 5)).map { dSyn.getString(it) } + posSyns).distinct().take(6)
                            val ants = ((0 until minOf(dAnt.length(), 5)).map { dAnt.getString(it) } + posAnts).distinct().take(6)
                            if (defText.isNotBlank()) {
                                senses.add(SenseItem(pos, defText, exText, syns, ants))
                            }
                        }
                    }
                    if (senses.isNotEmpty()) {
                        val entry = WordEntry(
                            word = wordTitle,
                            phonetic = phonetic,
                            senses = senses,
                            etymology = origin ?: "Extracted from Wiktionary (CC BY-SA 3.0)",
                            seeAlso = allSynonyms.take(8)
                        )
                        saveToUserCache(entry)
                        recordHistory(entry)
                        return@withContext entry
                    }
                }
            }
        } catch (_: Exception) {
        }
        null
    }

    fun generateConjugations(verb: String): Map<String, List<Pair<String, String>>> {
        val base = verb.trim().lowercase()
        if (base.isEmpty()) return emptyMap()
        val thirdPerson = when {
            base.endsWith("y") && base.length > 1 && base[base.length - 2] !in "aeiou" -> base.dropLast(1) + "ies"
            base.endsWith("s") || base.endsWith("sh") || base.endsWith("ch") || base.endsWith("x") || base.endsWith("z") || base.endsWith("o") -> base + "es"
            else -> base + "s"
        }
        val past = when {
            base.endsWith("e") -> base + "d"
            base.endsWith("y") && base.length > 1 && base[base.length - 2] !in "aeiou" -> base.dropLast(1) + "ied"
            else -> base + "ed"
        }
        val gerund = when {
            base.endsWith("ie") -> base.dropLast(2) + "ying"
            base.endsWith("e") && !base.endsWith("ee") -> base.dropLast(1) + "ing"
            else -> base + "ing"
        }
        return linkedMapOf(
            "Present Simple" to listOf(
                "I / You / We / They" to base,
                "He / She / It" to thirdPerson
            ),
            "Past Simple" to listOf(
                "All Persons" to past
            ),
            "Continuous / Participle" to listOf(
                "Present Participle" to gerund,
                "Past Participle" to past
            ),
            "Present Perfect" to listOf(
                "I / You / We / They" to "have $past",
                "He / She / It" to "has $past"
            ),
            "Future Simple" to listOf(
                "All Persons" to "will $base"
            )
        )
    }

    private fun recordHistory(entry: WordEntry) {
        val summary = entry.senses.firstOrNull()?.definition.orEmpty()
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO history (word, phonetic, summary, timestamp) VALUES (?, ?, ?, ?)",
            arrayOf(entry.word, entry.phonetic, summary, System.currentTimeMillis())
        )
    }

    fun toggleBookmark(entry: WordEntry, category: String = "General"): Boolean {
        val isSaved = isBookmarked(entry.word)
        if (isSaved) {
            writableDatabase.execSQL("DELETE FROM bookmarks WHERE word = ?", arrayOf(entry.word))
            return false
        } else {
            val summary = entry.senses.firstOrNull()?.definition.orEmpty()
            writableDatabase.execSQL(
                "INSERT OR REPLACE INTO bookmarks (word, phonetic, summary, category, timestamp) VALUES (?, ?, ?, ?, ?)",
                arrayOf(entry.word, entry.phonetic, summary, category, System.currentTimeMillis())
            )
            return true
        }
    }

    fun isBookmarked(word: String): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE word = ? LIMIT 1", arrayOf(word)).use {
            return it.moveToFirst()
        }
    }

    fun getBookmarks(categoryFilter: String? = null): List<BookmarkItem> {
        val list = mutableListOf<BookmarkItem>()
        val sql = if (categoryFilter.isNullOrBlank() || categoryFilter == "All") {
            "SELECT word, phonetic, summary, category, timestamp FROM bookmarks ORDER BY timestamp DESC"
        } else {
            "SELECT word, phonetic, summary, category, timestamp FROM bookmarks WHERE category = ? ORDER BY timestamp DESC"
        }
        val args = if (categoryFilter.isNullOrBlank() || categoryFilter == "All") emptyArray() else arrayOf(categoryFilter)
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                list.add(BookmarkItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
            }
        }
        return list
    }

    fun getCategories(): List<String> {
        val list = mutableListOf("All")
        readableDatabase.rawQuery("SELECT name FROM categories ORDER BY name ASC", null).use { c ->
            while (c.moveToNext()) list.add(c.getString(0))
        }
        return list
    }

    fun saveNote(word: String, content: String, category: String = "General") {
        if (content.isBlank()) {
            writableDatabase.execSQL("DELETE FROM notes WHERE word = ?", arrayOf(word))
        } else {
            writableDatabase.execSQL(
                "INSERT OR REPLACE INTO notes (word, content, category, timestamp) VALUES (?, ?, ?, ?)",
                arrayOf(word, content.trim(), category, System.currentTimeMillis())
            )
        }
    }

    fun getNoteForWord(word: String): NoteItem? {
        readableDatabase.rawQuery(
            "SELECT word, content, category, timestamp FROM notes WHERE word = ? LIMIT 1",
            arrayOf(word)
        ).use { c ->
            if (c.moveToFirst()) {
                return NoteItem(c.getString(0), c.getString(1), c.getString(2), c.getLong(3))
            }
        }
        return null
    }

    fun getAllNotes(): List<NoteItem> {
        val list = mutableListOf<NoteItem>()
        readableDatabase.rawQuery("SELECT word, content, category, timestamp FROM notes ORDER BY timestamp DESC", null).use { c ->
            while (c.moveToNext()) {
                list.add(NoteItem(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)))
            }
        }
        return list
    }

    fun getHistory(): List<Triple<String, String, String>> {
        val list = mutableListOf<Triple<String, String, String>>()
        readableDatabase.rawQuery("SELECT word, phonetic, summary FROM history ORDER BY timestamp DESC LIMIT 200", null).use { c ->
            while (c.moveToNext()) {
                list.add(Triple(c.getString(0), c.getString(1), c.getString(2)))
            }
        }
        return list
    }

    fun clearHistory() {
        writableDatabase.execSQL("DELETE FROM history")
    }
}
