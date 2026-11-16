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

class DictionaryRepository(context: Context) : SQLiteOpenHelper(context, "shunno_full.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cache (word TEXT PRIMARY KEY COLLATE NOCASE, json TEXT, timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (word TEXT PRIMARY KEY COLLATE NOCASE, phonetic TEXT, summary TEXT, category TEXT DEFAULT 'General', timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS history (word TEXT PRIMARY KEY COLLATE NOCASE, phonetic TEXT, summary TEXT, timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS notes (word TEXT PRIMARY KEY COLLATE NOCASE, content TEXT, category TEXT DEFAULT 'General', timestamp INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (name TEXT PRIMARY KEY COLLATE NOCASE)")
        db.execSQL("INSERT OR IGNORE INTO categories (name) VALUES ('General'), ('Favorites'), ('Academic'), ('GRE / IELTS'), ('Literature')")
        seedCoreLexicon(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    private fun seedCoreLexicon(db: SQLiteDatabase) {
        val seedEntries = listOf(
            WordEntry(
                word = "abandon",
                phonetic = "/əˈbæn.dən/",
                senses = listOf(
                    SenseItem("verb", "To give up completely; to desert or leave behind.", "They had to abandon the old ship during the storm.", listOf("forsake", "desert", "relinquish"), listOf("retain", "keep")),
                    SenseItem("noun", "Complete lack of inhibition or restraint.", "She danced with reckless abandon.", listOf("unrestraint", "exuberance"))
                ),
                etymology = "From Old French abandoner ('to surrender, give up'), from a bandon ('at one's disposal').",
                inflections = listOf("abandons", "abandoning", "abandoned"),
                seeAlso = listOf("relinquish", "forsake", "renounce")
            ),
            WordEntry(
                word = "benevolent",
                phonetic = "/bəˈnɛv.ə.lənt/",
                senses = listOf(
                    SenseItem("adjective", "Well-meaning, kindly, and charitable toward others.", "A benevolent donor funded the entire community library.", listOf("kindhearted", "charitable", "altruistic"), listOf("malevolent", "unkind"))
                ),
                etymology = "From Latin benevolēns, from bene ('well') + volēns ('wishing').",
                inflections = listOf("more benevolent", "most benevolent", "benevolently"),
                seeAlso = listOf("altruistic", "philanthropic", "magnanimous")
            ),
            WordEntry(
                word = "candid",
                phonetic = "/ˈkæn.dɪd/",
                senses = listOf(
                    SenseItem("adjective", "Truthful and straightforward; frank and sincere.", "She gave a candid interview about the challenges of leadership.", listOf("frank", "outspoken", "forthright"), listOf("guarded", "secretive"))
                ),
                etymology = "From Latin candidus ('shining white, clear, sincere').",
                inflections = listOf("more candid", "most candid", "candidly"),
                seeAlso = listOf("frank", "sincere", "impartial")
            ),
            WordEntry(
                word = "diligent",
                phonetic = "/ˈdɪl.ɪ.dʒənt/",
                senses = listOf(
                    SenseItem("adjective", "Having or showing care and conscientiousness in one's work or duties.", "After diligent research, the team discovered the root cause.", listOf("industrious", "assiduous", "conscientious"), listOf("negligent", "lazy"))
                ),
                etymology = "From Latin dīligēns ('careful, attentive'), from dīligere ('to value, esteem').",
                inflections = listOf("more diligent", "most diligent", "diligently"),
                seeAlso = listOf("assiduous", "meticulous", "sedulous")
            ),
            WordEntry(
                word = "eloquent",
                phonetic = "/ˈɛl.ə.kwənt/",
                senses = listOf(
                    SenseItem("adjective", "Fluent, forceful, and persuasive in speaking or writing.", "An eloquent speech that moved the entire assembly.", listOf("articulate", "expressive", "silver-tongued"), listOf("inarticulate"))
                ),
                etymology = "From Latin ēloquēns, present participle of ēloquī ('to speak out').",
                inflections = listOf("more eloquent", "most eloquent", "eloquently"),
                seeAlso = listOf("articulate", "rhetoric", "fluent")
            ),
            WordEntry(
                word = "ephemeral",
                phonetic = "/ɪˈfɛm.əɹ.əl/",
                senses = listOf(
                    SenseItem("adjective", "Lasting for a very short time; transitory and fleeting.", "The ephemeral beauty of cherry blossoms in spring.", listOf("fleeting", "transient", "short-lived"), listOf("permanent", "enduring"))
                ),
                etymology = "From Greek ephēmeros ('lasting only one day').",
                inflections = listOf("ephemerally"),
                seeAlso = listOf("transient", "evanescent", "fleeting")
            ),
            WordEntry(
                word = "fortitude",
                phonetic = "/ˈfɔː.tɪ.tjuːd/",
                senses = listOf(
                    SenseItem("noun", "Courage and mental strength in facing pain, danger, or adversity.", "She endured the long recovery with remarkable fortitude.", listOf("courage", "grit", "endurance", "resilience"), listOf("cowardice", "timidity"))
                ),
                etymology = "From Latin fortitūdō ('strength, bravery'), from fortis ('strong').",
                seeAlso = listOf("resilience", "mettle", "perseverance")
            ),
            WordEntry(
                word = "gregarious",
                phonetic = "/ɡɹɪˈɡɛə.ɹi.əs/",
                senses = listOf(
                    SenseItem("adjective", "Fond of company; sociable and outgoing.", "He was a popular and gregarious host.", listOf("sociable", "convivial", "extroverted"), listOf("reclusive", "unsociable"))
                ),
                etymology = "From Latin gregārius ('belonging to a flock'), from grex ('flock, herd').",
                seeAlso = listOf("convivial", "affable", "amiable")
            ),
            WordEntry(
                word = "harbinger",
                phonetic = "/ˈhɑː.bɪn.dʒə/",
                senses = listOf(
                    SenseItem("noun", "A person or thing that announces or signals the approach of another; a forerunner.", "The first robin is a harbinger of spring.", listOf("herald", "forerunner", "precursor", "omen"))
                ),
                etymology = "From Middle English herbergere ('one who provides lodging ahead of an army').",
                seeAlso = listOf("precursor", "herald", "portent")
            ),
            WordEntry(
                word = "inevitable",
                phonetic = "/ɪnˈɛv.ɪ.tə.bəl/",
                senses = listOf(
                    SenseItem("adjective", "Certain to happen; unavoidable.", "Technological change is inevitable in a connected world.", listOf("unavoidable", "inescapable", "inexorable"), listOf("avoidable", "uncertain"))
                ),
                etymology = "From Latin inevitābilis ('unavoidable'), from in- ('not') + evitābilis ('avoidable').",
                seeAlso = listOf("inexorable", "imminent", "destined")
            ),
            WordEntry(
                word = "juxtapose",
                phonetic = "/ˌdʒʌk.stəˈpəʊz/",
                senses = listOf(
                    SenseItem("verb", "To place or deal with close together for contrasting effect.", "The exhibition juxtaposes classical oil paintings with digital art.", listOf("contrast", "compare", "collocate"))
                ),
                etymology = "From French juxtaposer, from Latin iuxtā ('next to') + French poser ('to place').",
                inflections = listOf("juxtaposes", "juxtaposing", "juxtaposed", "juxtaposition"),
                seeAlso = listOf("contrast", "dichotomy")
            ),
            WordEntry(
                word = "kinetic",
                phonetic = "/kɪˈnɛt.ɪk/",
                senses = listOf(
                    SenseItem("adjective", "Relating to or resulting from motion.", "Wind turbines convert kinetic energy into electrical power.", listOf("dynamic", "active", "motile"), listOf("static", "potential"))
                ),
                etymology = "From Greek kinētikos ('of motion'), from kinein ('to move').",
                seeAlso = listOf("dynamic", "velocity", "momentum")
            ),
            WordEntry(
                word = "lucid",
                phonetic = "/ˈluː.sɪd/",
                senses = listOf(
                    SenseItem("adjective", "Expressed clearly; easy to understand; bright or luminous.", "A lucid explanation of complex legal principles.", listOf("clear", "intelligible", "cogent", "coherent"), listOf("confusing", "obscure"))
                ),
                etymology = "From Latin lūcidus ('light, bright, clear'), from lūx ('light').",
                inflections = listOf("more lucid", "most lucid", "lucidly", "lucidity"),
                seeAlso = listOf("pellucid", "cogent", "perspicuous")
            ),
            WordEntry(
                word = "meticulous",
                phonetic = "/məˈtɪk.jʊ.ləs/",
                senses = listOf(
                    SenseItem("adjective", "Showing great attention to detail; very careful and precise.", "He kept meticulous notes on every experiment.", listOf("scrupulous", "punctilious", "thorough", "exacting"), listOf("careless", "sloppy"))
                ),
                etymology = "From Latin meticulōsus ('fearful, timid, careful'), from metus ('fear').",
                inflections = listOf("more meticulous", "most meticulous", "meticulously"),
                seeAlso = listOf("fastidious", "scrupulous", "thorough")
            ),
            WordEntry(
                word = "nostalgia",
                phonetic = "/nɒˈstæl.dʒə/",
                senses = listOf(
                    SenseItem("noun", "A sentimental longing or wistful affection for the past.", "Listening to old vinyl records filled him with nostalgia.", listOf("wistfulness", "longing", "reminiscence"))
                ),
                etymology = "From Greek nostos ('return home') + algos ('pain, longing').",
                seeAlso = listOf("reminiscence", "melancholy", "yearning")
            ),
            WordEntry(
                word = "obsolete",
                phonetic = "/ˈɒb.sə.liːt/",
                senses = listOf(
                    SenseItem("adjective", "No longer produced or used; out of date.", "Floppy disks have become completely obsolete.", listOf("outdated", "outmoded", "archaic", "defunct"), listOf("modern", "current", "contemporary"))
                ),
                etymology = "From Latin obsolētus ('worn out, grown old').",
                seeAlso = listOf("archaic", "anachronistic", "antiquated")
            ),
            WordEntry(
                word = "paradigm",
                phonetic = "/ˈpæɹ.ə.daɪm/",
                senses = listOf(
                    SenseItem("noun", "A typical example, pattern, or model of something; a philosophical and theoretical framework.", "A new paradigm in minimalist software design.", listOf("model", "archetype", "exemplar", "standard"))
                ),
                etymology = "From Greek parádeigma ('pattern, example'), from paradeiknynai ('to show side by side').",
                seeAlso = listOf("archetype", "prototype", "epitome")
            ),
            WordEntry(
                word = "quintessential",
                phonetic = "/ˌkwɪn.tɪˈsɛn.ʃəl/",
                senses = listOf(
                    SenseItem("adjective", "Representing the most perfect or typical example of a quality or class.", "She is the quintessential professional.", listOf("archetypal", "classic", "definitive", "ideal"))
                ),
                etymology = "From medieval Latin quīnta essentia ('fifth essence').",
                seeAlso = listOf("epitome", "paragon", "exemplar")
            ),
            WordEntry(
                word = "resilience",
                phonetic = "/ɹɪˈzɪl.jəns/",
                senses = listOf(
                    SenseItem("noun", "The capacity to recover quickly from difficulties; toughness and elasticity.", "The remarkable resilience of coastal ecosystems.", listOf("toughness", "adaptability", "fortitude", "grit"), listOf("fragility", "vulnerability"))
                ),
                etymology = "From Latin resiliēns, present participle of resilīre ('to rebound, recoil').",
                seeAlso = listOf("fortitude", "tenacity", "endurance")
            ),
            WordEntry(
                word = "serendipity",
                phonetic = "/ˌsɛɹ.ənˈdɪp.ɪ.ti/",
                senses = listOf(
                    SenseItem("noun", "The occurrence and development of events by chance in a happy or beneficial way.", "Penicillin was one of science's greatest moments of serendipity.", listOf("fluke", "blessing", "fortune", "providence"), listOf("misfortune", "calamity"))
                ),
                etymology = "Coined by Horace Walpole in 1754 from the Persian fairy tale 'The Three Princes of Serendip'.",
                seeAlso = listOf("fortuitous", "providence", "synchronicity")
            ),
            WordEntry(
                word = "tenacious",
                phonetic = "/təˈneɪ.ʃəs/",
                senses = listOf(
                    SenseItem("adjective", "Tending to keep a firm hold of something; persistent and determined.", "A tenacious investigator who refused to drop the case.", listOf("persistent", "resolute", "dogged", "steadfast"), listOf("irresolute", "yielding"))
                ),
                etymology = "From Latin tenāx ('holding fast'), from tenēre ('to hold').",
                inflections = listOf("more tenacious", "most tenacious", "tenaciously", "tenacity"),
                seeAlso = listOf("steadfast", "persevering", "resolute")
            ),
            WordEntry(
                word = "ubiquitous",
                phonetic = "/juːˈbɪk.wɪ.təs/",
                senses = listOf(
                    SenseItem("adjective", "Present, appearing, or found everywhere at the same time.", "Coffee shops have become ubiquitous in the city center.", listOf("omnipresent", "pervasive", "universal"), listOf("rare", "scarce"))
                ),
                etymology = "From Latin ubīque ('everywhere').",
                inflections = listOf("ubiquitously", "ubiquity"),
                seeAlso = listOf("pervasive", "omnipresent", "universal")
            ),
            WordEntry(
                word = "vivid",
                phonetic = "/ˈvɪv.ɪd/",
                senses = listOf(
                    SenseItem("adjective", "Producing powerful feelings or strong, clear images in the mind.", "I still have vivid memories of that summer.", listOf("graphic", "striking", "bright", "distinct"), listOf("vague", "dull", "faint"))
                ),
                etymology = "From Latin vīvidus ('spirited, lively'), from vīvus ('alive').",
                inflections = listOf("more vivid", "most vivid", "vividly"),
                seeAlso = listOf("lucid", "vibrant", "graphic")
            ),
            WordEntry(
                word = "wanderlust",
                phonetic = "/ˈwɒn.də.lʌst/",
                senses = listOf(
                    SenseItem("noun", "A strong desire to travel and explore the world.", "Driven by wanderlust, she backpacked across four continents.", listOf("itchy feet", "restlessness"))
                ),
                etymology = "Borrowed from German Wanderlust, from wandern ('to hike, roam') + Lust ('desire').",
                seeAlso = listOf("nomadic", "wayfarer", "sojourn")
            ),
            WordEntry(
                word = "zealous",
                phonetic = "/ˈzɛl.əs/",
                senses = listOf(
                    SenseItem("adjective", "Having or showing great energy, passion, or enthusiasm in pursuit of a cause or objective.", "A zealous advocate for open-source privacy tools.", listOf("fervent", "ardent", "passionate", "devoted"), listOf("apathetic", "indifferent"))
                ),
                etymology = "From Late Latin zēlōsus, from Greek zēlos ('zeal, ardor').",
                inflections = listOf("more zealous", "most zealous", "zealously", "zeal"),
                seeAlso = listOf("fervent", "ardent", "enthusiastic")
            )
        )
        for (entry in seedEntries) {
            saveToCache(db, entry)
        }
    }

    private fun saveToCache(db: SQLiteDatabase, entry: WordEntry) {
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
        db.execSQL(
            "INSERT OR REPLACE INTO cache (word, json, timestamp) VALUES (?, ?, ?)",
            arrayOf(entry.word.lowercase(), obj.toString(), System.currentTimeMillis())
        )
    }

    private fun parseCachedJson(jsonStr: String): WordEntry {
        val obj = JSONObject(jsonStr)
        val arr = obj.optJSONArray("senses") ?: JSONArray()
        val senses = mutableListOf<SenseItem>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val synArr = item.optJSONArray("syn") ?: JSONArray()
            val antArr = item.optJSONArray("ant") ?: JSONArray()
            senses.add(
                SenseItem(
                    partOfSpeech = item.optString("pos", "definition"),
                    definition = item.optString("def", ""),
                    example = item.optString("ex", "").takeIf { it.isNotBlank() },
                    synonyms = (0 until synArr.length()).map { synArr.getString(it) },
                    antonyms = (0 until antArr.length()).map { antArr.getString(it) }
                )
            )
        }
        val inflArr = obj.optJSONArray("inflections") ?: JSONArray()
        val seeArr = obj.optJSONArray("seeAlso") ?: JSONArray()
        return WordEntry(
            word = obj.optString("word"),
            phonetic = obj.optString("phonetic", ""),
            senses = senses,
            etymology = obj.optString("etymology", "").takeIf { it.isNotBlank() },
            inflections = (0 until inflArr.length()).map { inflArr.getString(it) },
            seeAlso = (0 until seeArr.length()).map { seeArr.getString(it) }
        )
    }

    suspend fun fetchLiveSuggestions(prefix: String): List<String> = withContext(Dispatchers.IO) {
        val q = prefix.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()

        val combined = linkedSetOf<String>()
        readableDatabase.rawQuery(
            "SELECT word FROM cache WHERE word LIKE ? ORDER BY word ASC LIMIT 12",
            arrayOf("$q%")
        ).use { c ->
            while (c.moveToNext()) {
                combined.add(c.getString(0))
            }
        }

        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val url = URL("https://api.datamuse.com/sug?s=$encoded&max=12")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
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

        combined.take(12)
    }

    fun getWordsByLetter(letter: Char): List<String> {
        val list = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT word FROM cache WHERE word LIKE ? ORDER BY word COLLATE NOCASE ASC",
            arrayOf("${letter.lowercaseChar()}%")
        ).use { c ->
            while (c.moveToNext()) list.add(c.getString(0))
        }
        return list
    }

    fun getAllCachedWords(): List<String> {
        val list = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT word FROM cache ORDER BY word COLLATE NOCASE ASC", null).use { c ->
            while (c.moveToNext()) list.add(c.getString(0))
        }
        return list
    }

    fun getWordOfTheDay(): String {
        val all = getAllCachedWords()
        if (all.isEmpty()) return "serendipity"
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return all[dayOfYear % all.size]
    }

    fun getRandomWord(): String {
        val all = getAllCachedWords()
        return if (all.isNotEmpty()) all.random() else "eloquent"
    }

    suspend fun lookupWord(rawQuery: String): WordEntry? = withContext(Dispatchers.IO) {
        val clean = rawQuery.trim().lowercase()
        if (clean.isEmpty()) return@withContext null

        readableDatabase.rawQuery("SELECT json FROM cache WHERE word = ? LIMIT 1", arrayOf(clean)).use { cursor ->
            if (cursor.moveToFirst()) {
                val entry = parseCachedJson(cursor.getString(0))
                recordHistory(entry)
                return@withContext entry
            }
        }

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

    fun addCategory(name: String) {
        if (name.isNotBlank()) {
            writableDatabase.execSQL("INSERT OR IGNORE INTO categories (name) VALUES (?)", arrayOf(name.trim()))
        }
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
