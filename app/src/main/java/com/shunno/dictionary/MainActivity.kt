package com.shunno.dictionary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

enum class AppleTab(val label: String) {
    SEARCH("Dictionary"),
    BOOKMARKS("Saved"),
    HISTORY("Recents")
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

        val initialWord = intent?.getStringExtra("INITIAL_WORD") ?: "shunno"

        setContent {
            val appleBg = Color(0xFFF2F2F7)
            val appleCard = Color(0xFFFFFFFF)
            val appleBlue = Color(0xFF007AFF)
            val appleText = Color(0xFF1C1C1E)
            val appleSecondary = Color(0xFF8E8E93)

            val scope = rememberCoroutineScope()
            var activeTab by remember { mutableStateOf(AppleTab.SEARCH) }
            var searchQuery by remember { mutableStateOf("") }
            var currentEntry by remember { mutableStateOf<WordEntry?>(null) }
            var isLoading by remember { mutableStateOf(false) }
            var isBookmarked by remember { mutableStateOf(false) }
            var suggestions by remember { mutableStateOf(repository.searchPrefixSuggestions("")) }
            var bookmarksList by remember { mutableStateOf(repository.getBookmarks()) }
            var historyList by remember { mutableStateOf(repository.getHistory()) }

            fun loadWord(word: String) {
                scope.launch {
                    isLoading = true
                    val res = repository.lookupWord(word)
                    currentEntry = res
                    if (res != null) {
                        isBookmarked = repository.isBookmarked(res.word)
                        historyList = repository.getHistory()
                    }
                    isLoading = false
                }
            }

            LaunchedEffect(initialWord) {
                loadWord(initialWord)
            }

            Scaffold(
                containerColor = appleBg,
                bottomBar = {
                    NavigationBar(
                        containerColor = Color(0xF5FFFFFF),
                        tonalElevation = 0.dp
                    ) {
                        AppleTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    activeTab = tab
                                    if (tab == AppleTab.BOOKMARKS) bookmarksList = repository.getBookmarks()
                                    if (tab == AppleTab.HISTORY) historyList = repository.getHistory()
                                },
                                icon = {
                                    val symbol = when (tab) {
                                        AppleTab.SEARCH -> "📖"
                                        AppleTab.BOOKMARKS -> "★"
                                        AppleTab.HISTORY -> "🕒"
                                    }
                                    Text(symbol, fontSize = 18.sp)
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = appleBlue,
                                    selectedTextColor = appleBlue,
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
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Apple iOS Large Navigation Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Shunno",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = appleText,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Clean Wiktionary Reference • Zero-Dim Reader Pop-up",
                                fontSize = 13.sp,
                                color = appleSecondary
                            )
                        }
                        // Demo button to preview the transparent Reader Pop-up right inside the app
                        TextButton(
                            onClick = {
                                val popupIntent = Intent(this@MainActivity, ReaderPopupActivity::class.java).apply {
                                    putExtra("EXTRA_QUERY", currentEntry?.word ?: "shunno")
                                }
                                startActivity(popupIntent)
                            }
                        ) {
                            Text("Test Pop-up", color = appleBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (activeTab) {
                        AppleTab.SEARCH -> {
                            // Apple iOS Rounded Spotlight Search Bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    suggestions = repository.searchPrefixSuggestions(it)
                                },
                                placeholder = { Text("Search English words...", color = appleSecondary, fontSize = 15.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFFE5E5EA),
                                    unfocusedContainerColor = Color(0xFFE5E5EA),
                                    focusedBorderColor = appleBlue,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = {
                                                searchQuery = ""
                                                suggestions = repository.searchPrefixSuggestions("")
                                            }) {
                                                Text("✕", color = appleSecondary, fontSize = 14.sp)
                                            }
                                        }
                                        TextButton(onClick = {
                                            if (searchQuery.isNotBlank()) loadWord(searchQuery)
                                        }) {
                                            Text("Go", color = appleBlue, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (searchQuery.isNotBlank()) loadWord(searchQuery)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Quick Word Pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                suggestions.take(4).forEach { sug ->
                                    AssistChip(
                                        onClick = {
                                            searchQuery = sug
                                            loadWord(sug)
                                        },
                                        label = { Text(sug, fontSize = 13.sp) },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = appleCard,
                                            labelColor = appleText
                                        ),
                                        border = null
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Apple Dictionary Definition Card
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
                                            .padding(22.dp)
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
                                                    fontSize = 32.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Serif,
                                                    color = appleText
                                                )
                                                if (entry.phonetic.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = entry.phonetic,
                                                        fontSize = 15.sp,
                                                        color = appleSecondary
                                                    )
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        tts?.speak(entry.word, TextToSpeech.QUEUE_FLUSH, null, "shunno_main_tts")
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = Color(0x1A007AFF)
                                                    )
                                                ) {
                                                    Text("🔊", fontSize = 17.sp)
                                                }
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        isBookmarked = repository.toggleBookmark(entry)
                                                        bookmarksList = repository.getBookmarks()
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = if (isBookmarked) Color(0x26FF9F0A) else appleBg
                                                    )
                                                ) {
                                                    Text(
                                                        text = if (isBookmarked) "★" else "☆",
                                                        fontSize = 19.sp,
                                                        color = if (isBookmarked) Color(0xFFFF9F0A) else appleSecondary
                                                    )
                                                }
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val formatted = "${entry.word} ${entry.phonetic}\n" +
                                                            entry.senses.joinToString("\n") { "• (${it.partOfSpeech}) ${it.definition}" }
                                                        cb.setPrimaryClip(ClipData.newPlainText("Shunno Dictionary", formatted))
                                                        Toast.makeText(this@MainActivity, "Definition copied", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = appleBg
                                                    )
                                                ) {
                                                    Text("📋", fontSize = 16.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(color = Color(0xFFE5E5EA), thickness = 0.8.dp)
                                        Spacer(modifier = Modifier.height(16.dp))

                                        entry.senses.forEachIndexed { index, sense ->
                                            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        color = Color(0x14007AFF),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = sense.partOfSpeech,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontStyle = FontStyle.Italic,
                                                            color = appleBlue,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "${index + 1}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = appleSecondary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = sense.definition,
                                                    fontSize = 16.sp,
                                                    color = appleText,
                                                    lineHeight = 22.sp
                                                )
                                                if (!sense.example.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "“${sense.example}”",
                                                        fontSize = 14.sp,
                                                        fontStyle = FontStyle.Italic,
                                                        color = Color(0xFF636366)
                                                    )
                                                }
                                            }
                                        }

                                        if (!entry.etymology.isNullOrBlank()) {
                                            HorizontalDivider(color = Color(0xFFE5E5EA), thickness = 0.8.dp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "ORIGIN & NOTES",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appleSecondary,
                                                letterSpacing = 0.8.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = entry.etymology,
                                                fontSize = 13.sp,
                                                color = Color(0xFF636366),
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Word not found. Check spelling or try another term.", color = appleSecondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        AppleTab.BOOKMARKS -> {
                            WordListSection(
                                items = bookmarksList,
                                emptyMessage = "No saved words yet. Tap ★ on any definition to bookmark it.",
                                onSelectWord = { word ->
                                    activeTab = AppleTab.SEARCH
                                    loadWord(word)
                                }
                            )
                        }

                        AppleTab.HISTORY -> {
                            WordListSection(
                                items = historyList,
                                emptyMessage = "Your recent word lookups will appear here.",
                                onSelectWord = { word ->
                                    activeTab = AppleTab.SEARCH
                                    loadWord(word)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}

@Composable
fun WordListSection(
    items: List<Triple<String, String, String>>,
    emptyMessage: String,
    onSelectWord: (String) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = Color(0xFF8E8E93), fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { (word, phonetic, summary) ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectWord(word) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = word,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                color = Color(0xFF1C1C1E)
                            )
                            if (phonetic.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(phonetic, fontSize = 13.sp, color = Color(0xFF8E8E93))
                            }
                        }
                        if (summary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = summary,
                                fontSize = 14.sp,
                                color = Color(0xFF3A3A3C),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
