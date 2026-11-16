package com.shunno.dictionary

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class ShunnoTab(val label: String, val icon: String) {
    DICTIONARY("Dictionary", "📖"),
    INDEX("A–Z Index", "🔤"),
    CONJUGATOR("Conjugator", "⚡"),
    BOOKMARKS("Bookmarks", "★"),
    NOTES("Notes", "📝"),
    SETTINGS("Settings", "⚙️")
}

class MainActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private val repository by lazy { DictionaryRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        val initialWord = intent?.getStringExtra("INITIAL_WORD")

        setContent {
            var isDarkMode by remember { mutableStateOf(false) }
            var fontScale by remember { mutableStateOf(1.0f) }
            var lineSpacingMultiplier by remember { mutableStateOf(1.3f) }
            var useUkAccent by remember { mutableStateOf(false) }
            var speechRate by remember { mutableStateOf(1.0f) }

            val appleBg = if (isDarkMode) Color(0xFF000000) else Color(0xFFF2F2F7)
            val appleCard = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            val appleInputBg = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
            val appleBlue = Color(0xFF007AFF)
            val appleText = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF1C1C1E)
            val appleSecondary = Color(0xFF8E8E93)

            val scope = rememberCoroutineScope()
            val focusManager = LocalFocusManager.current

            var activeTab by remember { mutableStateOf(ShunnoTab.DICTIONARY) }
            var searchQuery by remember { mutableStateOf("") }
            var liveSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
            var showSuggestionsDropdown by remember { mutableStateOf(false) }
            var suggestionJob by remember { mutableStateOf<Job?>(null) }

            var currentEntry by remember { mutableStateOf<WordEntry?>(null) }
            var isLoading by remember { mutableStateOf(false) }
            var isBookmarked by remember { mutableStateOf(false) }
            var currentNote by remember { mutableStateOf<NoteItem?>(null) }
            var showNoteDialog by remember { mutableStateOf(false) }
            var noteDraft by remember { mutableStateOf("") }

            var selectedCategory by remember { mutableStateOf("All") }
            val categories by remember { mutableStateOf(repository.getCategories()) }
            var bookmarksList by remember { mutableStateOf(repository.getBookmarks()) }
            var notesList by remember { mutableStateOf(repository.getAllNotes()) }
            var historyList by remember { mutableStateOf(repository.getHistory()) }

            var selectedLetter by remember { mutableStateOf('A') }
            var indexWords by remember { mutableStateOf(repository.getWordsByLetter('A')) }
            var conjugatorInput by remember { mutableStateOf("abandon") }

            val wordOfTheDay = remember { repository.getWordOfTheDay() }

            fun selectAndLoadWord(word: String) {
                val target = word.trim()
                if (target.isEmpty()) return
                showSuggestionsDropdown = false
                searchQuery = target
                focusManager.clearFocus()
                activeTab = ShunnoTab.DICTIONARY
                scope.launch {
                    isLoading = true
                    val res = repository.lookupWord(target)
                    currentEntry = res
                    if (res != null) {
                        isBookmarked = repository.isBookmarked(res.word)
                        currentNote = repository.getNoteForWord(res.word)
                        historyList = repository.getHistory()
                    }
                    isLoading = false
                }
            }

            val voiceLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        selectAndLoadWord(spoken)
                    }
                }
            }

            LaunchedEffect(initialWord) {
                if (!initialWord.isNullOrBlank()) {
                    selectAndLoadWord(initialWord)
                } else {
                    selectAndLoadWord(wordOfTheDay)
                }
            }

            Scaffold(
                containerColor = appleBg,
                bottomBar = {
                    NavigationBar(
                        containerColor = appleCard,
                        tonalElevation = 0.dp
                    ) {
                        ShunnoTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    activeTab = tab
                                    showSuggestionsDropdown = false
                                    when (tab) {
                                        ShunnoTab.BOOKMARKS -> bookmarksList = repository.getBookmarks(selectedCategory)
                                        ShunnoTab.NOTES -> notesList = repository.getAllNotes()
                                        ShunnoTab.INDEX -> indexWords = repository.getWordsByLetter(selectedLetter)
                                        else -> {}
                                    }
                                },
                                icon = { Text(tab.icon, fontSize = 17.sp) },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = appleBlue,
                                    selectedTextColor = appleBlue,
                                    unselectedTextColor = appleSecondary,
                                    indicatorColor = Color(0x1A007AFF)
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 18.dp)
                ) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shunno Dictionary",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = appleText,
                            letterSpacing = (-0.4).sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { selectAndLoadWord(repository.getRandomWord()) },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = appleCard,
                                    contentColor = appleBlue
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("🔀 Random", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { newText ->
                                    searchQuery = newText
                                    suggestionJob?.cancel()
                                    if (newText.trim().isEmpty()) {
                                        liveSuggestions = emptyList()
                                        showSuggestionsDropdown = false
                                    } else {
                                        showSuggestionsDropdown = true
                                        suggestionJob = scope.launch {
                                            delay(120)
                                            liveSuggestions = repository.fetchLiveSuggestions(newText)
                                        }
                                    }
                                },
                                placeholder = {
                                    Text("Search English dictionary...", color = appleSecondary, fontSize = 15.sp)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = appleInputBg,
                                    unfocusedContainerColor = appleInputBg,
                                    focusedTextColor = appleText,
                                    unfocusedTextColor = appleText,
                                    focusedBorderColor = appleBlue,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = {
                                                searchQuery = ""
                                                liveSuggestions = emptyList()
                                                showSuggestionsDropdown = false
                                            }) {
                                                Text("✕", color = appleSecondary, fontSize = 15.sp)
                                            }
                                        }
                                        IconButton(onClick = {
                                            try {
                                                val vIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak an English word")
                                                }
                                                voiceLauncher.launch(vIntent)
                                            } catch (_: Exception) {
                                                Toast.makeText(this@MainActivity, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Text("🎤", fontSize = 16.sp)
                                        }
                                        TextButton(onClick = {
                                            if (searchQuery.isNotBlank()) selectAndLoadWord(searchQuery)
                                        }) {
                                            Text("Search", color = appleBlue, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (searchQuery.isNotBlank()) selectAndLoadWord(searchQuery)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (showSuggestionsDropdown && liveSuggestions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = appleCard),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                        items(liveSuggestions) { suggestion ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectAndLoadWord(suggestion) }
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("🔍", fontSize = 13.sp, color = appleSecondary)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = suggestion,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = appleText
                                                    )
                                                }
                                                Text("↗", fontSize = 14.sp, color = appleSecondary)
                                            }
                                            HorizontalDivider(color = appleInputBg, thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (activeTab) {
                        ShunnoTab.DICTIONARY -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = { selectAndLoadWord(wordOfTheDay) },
                                    label = { Text("☀ Word of the Day: $wordOfTheDay", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color(0x1A007AFF),
                                        labelColor = appleBlue
                                    ),
                                    border = null
                                )
                                historyList.take(6).forEach { (hWord, _, _) ->
                                    AssistChip(
                                        onClick = { selectAndLoadWord(hWord) },
                                        label = { Text(hWord, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = appleCard,
                                            labelColor = appleText
                                        ),
                                        border = null
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = appleBlue)
                                }
                            } else if (currentEntry != null) {
                                val entry = currentEntry!!
                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(containerColor = appleCard),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(20.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.word,
                                                    fontSize = (30 * fontScale).sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Serif,
                                                    color = appleText
                                                )
                                                if (entry.phonetic.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = entry.phonetic,
                                                        fontSize = (15 * fontScale).sp,
                                                        color = appleSecondary
                                                    )
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        tts?.language = if (useUkAccent) Locale.UK else Locale.US
                                                        tts?.setSpeechRate(speechRate)
                                                        tts?.speak(entry.word, TextToSpeech.QUEUE_FLUSH, null, "shunno_tts")
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0x1A007AFF))
                                                ) {
                                                    Text("🔊", fontSize = 16.sp)
                                                }
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        isBookmarked = repository.toggleBookmark(entry)
                                                        bookmarksList = repository.getBookmarks(selectedCategory)
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = if (isBookmarked) Color(0x26FF9F0A) else appleInputBg
                                                    )
                                                ) {
                                                    Text(
                                                        text = if (isBookmarked) "★" else "☆",
                                                        fontSize = 18.sp,
                                                        color = if (isBookmarked) Color(0xFFFF9F0A) else appleSecondary
                                                    )
                                                }
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        noteDraft = currentNote?.noteText.orEmpty()
                                                        showNoteDialog = true
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = appleInputBg)
                                                ) {
                                                    Text("📝", fontSize = 15.sp)
                                                }
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val textToCopy = "${entry.word} ${entry.phonetic}\n" +
                                                            entry.senses.joinToString("\n") { "• (${it.partOfSpeech}) ${it.definition}" }
                                                        cb.setPrimaryClip(ClipData.newPlainText("Shunno Dictionary", textToCopy))
                                                        Toast.makeText(this@MainActivity, "\"${entry.word}\" copied to clipboard.", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = appleInputBg)
                                                ) {
                                                    Text("📋", fontSize = 15.sp)
                                                }
                                            }
                                        }

                                        if (entry.inflections.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Inflected forms: " + entry.inflections.joinToString(", "),
                                                fontSize = (13 * fontScale).sp,
                                                fontStyle = FontStyle.Italic,
                                                color = appleSecondary
                                            )
                                        }

                                        if (currentNote != null) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Surface(
                                                color = Color(0x1AFF9F0A),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        noteDraft = currentNote?.noteText.orEmpty()
                                                        showNoteDialog = true
                                                    }
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("MY NOTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9F0A))
                                                    Text(currentNote!!.noteText, fontSize = (14 * fontScale).sp, color = appleText)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        HorizontalDivider(color = appleInputBg, thickness = 0.8.dp)
                                        Spacer(modifier = Modifier.height(14.dp))

                                        entry.senses.forEachIndexed { index, sense ->
                                            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        color = Color(0x14007AFF),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = sense.partOfSpeech,
                                                            fontSize = (12 * fontScale).sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontStyle = FontStyle.Italic,
                                                            color = appleBlue,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "${index + 1}.",
                                                        fontSize = (13 * fontScale).sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = appleSecondary
                                                    )
                                                    if (sense.partOfSpeech.equals("verb", ignoreCase = true)) {
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = "Conjugate →",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = appleBlue,
                                                            modifier = Modifier.clickable {
                                                                conjugatorInput = entry.word
                                                                activeTab = ShunnoTab.CONJUGATOR
                                                            }
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = sense.definition,
                                                    fontSize = (16 * fontScale).sp,
                                                    color = appleText,
                                                    lineHeight = (21 * fontScale * lineSpacingMultiplier).sp
                                                )
                                                if (!sense.example.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "“${sense.example}”",
                                                        fontSize = (14 * fontScale).sp,
                                                        fontStyle = FontStyle.Italic,
                                                        color = appleSecondary
                                                    )
                                                }
                                                if (sense.synonyms.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(
                                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Synonyms:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = appleSecondary)
                                                        sense.synonyms.forEach { syn ->
                                                            SuggestionChip(
                                                                onClick = { selectAndLoadWord(syn) },
                                                                label = { Text(syn, fontSize = 12.sp) }
                                                            )
                                                        }
                                                    }
                                                }
                                                if (sense.antonyms.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Antonyms:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = appleSecondary)
                                                        sense.antonyms.forEach { ant ->
                                                            SuggestionChip(
                                                                onClick = { selectAndLoadWord(ant) },
                                                                label = { Text(ant, fontSize = 12.sp) }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (!entry.etymology.isNullOrBlank()) {
                                            HorizontalDivider(color = appleInputBg, thickness = 0.8.dp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "ETYMOLOGY & ORIGIN",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appleSecondary,
                                                letterSpacing = 0.8.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = entry.etymology,
                                                fontSize = (14 * fontScale).sp,
                                                color = appleText,
                                                lineHeight = (19 * fontScale).sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }

                                        if (entry.seeAlso.isNotEmpty()) {
                                            HorizontalDivider(color = appleInputBg, thickness = 0.8.dp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "SEE ALSO",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appleSecondary,
                                                letterSpacing = 0.8.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                entry.seeAlso.forEach { related ->
                                                    AssistChip(
                                                        onClick = { selectAndLoadWord(related) },
                                                        label = { Text(related, fontSize = 13.sp, color = appleBlue) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Word not found, please check your spelling or try another word.",
                                        color = appleSecondary,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        ShunnoTab.INDEX -> {
                            Text("INDEX OF WORDS (A–Z)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = appleSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ('A'..'Z').forEach { letter ->
                                    val isSelected = selectedLetter == letter
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedLetter = letter
                                            indexWords = repository.getWordsByLetter(letter)
                                        },
                                        label = { Text(letter.toString(), fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = appleBlue,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(indexWords) { w ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = appleCard),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectAndLoadWord(w) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(w, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = appleText)
                                            Text("→", color = appleBlue, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        ShunnoTab.CONJUGATOR -> {
                            Text("ENGLISH VERB CONJUGATIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = appleSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = conjugatorInput,
                                onValueChange = { conjugatorInput = it },
                                label = { Text("Enter verb infinitive (e.g., abandon, write, speak)") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            val tables = repository.generateConjugations(conjugatorInput)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(tables.entries.toList()) { (tense, forms) ->
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = appleCard),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(tense, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = appleBlue)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            forms.forEach { (pronoun, conjugated) ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(pronoun, fontSize = 14.sp, color = appleSecondary)
                                                    Text(conjugated, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = appleText)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ShunnoTab.BOOKMARKS -> {
                            var showHistoryInstead by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !showHistoryInstead,
                                        onClick = { showHistoryInstead = false },
                                        label = { Text("Bookmarks (${bookmarksList.size})") }
                                    )
                                    FilterChip(
                                        selected = showHistoryInstead,
                                        onClick = {
                                            showHistoryInstead = true
                                            historyList = repository.getHistory()
                                        },
                                        label = { Text("History (${historyList.size})") }
                                    )
                                }
                                if (showHistoryInstead && historyList.isNotEmpty()) {
                                    TextButton(onClick = {
                                        repository.clearHistory()
                                        historyList = emptyList()
                                    }) {
                                        Text("Clear History", color = Color(0xFFFF3B30), fontSize = 13.sp)
                                    }
                                }
                            }

                            if (!showHistoryInstead) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    categories.forEach { cat ->
                                        FilterChip(
                                            selected = selectedCategory == cat,
                                            onClick = {
                                                selectedCategory = cat
                                                bookmarksList = repository.getBookmarks(cat)
                                            },
                                            label = { Text(cat, fontSize = 12.sp) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(bookmarksList) { b ->
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = appleCard),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectAndLoadWord(b.word) }
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(b.word, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = appleText)
                                                    Text(b.category, fontSize = 11.sp, color = appleBlue)
                                                }
                                                if (b.summary.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(b.summary, fontSize = 14.sp, color = appleSecondary, maxLines = 2)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(historyList) { (word, phonetic, summary) ->
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = appleCard),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectAndLoadWord(word) }
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(word, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = appleText)
                                                    if (phonetic.isNotBlank()) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(phonetic, fontSize = 13.sp, color = appleSecondary)
                                                    }
                                                }
                                                if (summary.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(summary, fontSize = 14.sp, color = appleSecondary, maxLines = 2)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ShunnoTab.NOTES -> {
                            Text("PERSONAL WORD NOTES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = appleSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (notesList.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Tap 📝 on any word definition to add a personal note.", color = appleSecondary)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(notesList) { note ->
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = appleCard),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectAndLoadWord(note.word) }
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(note.word, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = appleBlue)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(note.noteText, fontSize = 15.sp, color = appleText)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ShunnoTab.SETTINGS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = appleCard),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("DISPLAY & APPEARANCE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = appleSecondary)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Dark Mode", fontSize = 16.sp, color = appleText)
                                            Switch(checked = isDarkMode, onCheckedChange = { isDarkMode = it })
                                        }
                                        Text("Font Size", fontSize = 15.sp, color = appleText)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("Small" to 0.9f, "Medium" to 1.0f, "Large" to 1.2f, "Very Large" to 1.4f).forEach { (label, scale) ->
                                                FilterChip(
                                                    selected = fontScale == scale,
                                                    onClick = { fontScale = scale },
                                                    label = { Text(label, fontSize = 12.sp) }
                                                )
                                            }
                                        }
                                        Text("Line Spacing", fontSize = 15.sp, color = appleText)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("100%" to 1.15f, "150%" to 1.4f, "200%" to 1.7f).forEach { (label, mult) ->
                                                FilterChip(
                                                    selected = lineSpacingMultiplier == mult,
                                                    onClick = { lineSpacingMultiplier = mult },
                                                    label = { Text(label, fontSize = 12.sp) }
                                                )
                                            }
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = appleCard),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("PRONUNCIATION (TEXT-TO-SPEECH)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = appleSecondary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(
                                                selected = !useUkAccent,
                                                onClick = { useUkAccent = false },
                                                label = { Text("American English (US)") }
                                            )
                                            FilterChip(
                                                selected = useUkAccent,
                                                onClick = { useUkAccent = true },
                                                label = { Text("British English (UK)") }
                                            )
                                        }
                                        Text("Speech Rate", fontSize = 15.sp, color = appleText)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("Slow" to 0.75f, "Normal" to 1.0f, "Fast" to 1.3f).forEach { (label, rate) ->
                                                FilterChip(
                                                    selected = speechRate == rate,
                                                    onClick = { speechRate = rate },
                                                    label = { Text(label) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showNoteDialog && currentEntry != null) {
                    AlertDialog(
                        onDismissRequest = { showNoteDialog = false },
                        title = { Text("Note for \"${currentEntry!!.word}\"", fontWeight = FontWeight.Bold) },
                        text = {
                            OutlinedTextField(
                                value = noteDraft,
                                onValueChange = { noteDraft = it },
                                placeholder = { Text("Write your personal note or mnemonic...") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                repository.saveNote(currentEntry!!.word, noteDraft)
                                currentNote = repository.getNoteForWord(currentEntry!!.word)
                                notesList = repository.getAllNotes()
                                showNoteDialog = false
                            }) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNoteDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}
