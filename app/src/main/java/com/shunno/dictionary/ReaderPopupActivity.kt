package com.shunno.dictionary

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Floating Pop-up Dictionary Activity for E-Readers & Browsers.
 * Keeps the underlying reader or browser 100% visible without darkening the screen!
 */
class ReaderPopupActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private val repository by lazy { DictionaryRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CRITICAL: Explicitly strip FLAG_DIM_BEHIND and make window background 100% transparent
        // so the e-reader or browser page underneath stays completely bright and readable.
        window.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            attributes = attributes.apply {
                dimAmount = 0.0f
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.BOTTOM
            }
        }

        val selectedWord = extractWordFromIntent(intent)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        setContent {
            var entry by remember { mutableStateOf<WordEntry?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            var isSaved by remember { mutableStateOf(false) }

            LaunchedEffect(selectedWord) {
                isLoading = true
                val res = repository.lookupWord(selectedWord)
                entry = res
                if (res != null) {
                    isSaved = repository.isBookmarked(res.word)
                }
                isLoading = false
            }

            // Full-screen completely transparent touch catcher:
            // Tapping anywhere outside the card dismisses the popup immediately back to the reader.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { finish() },
                contentAlignment = Alignment.BottomCenter
            ) {
                // Apple iOS Lookup Sheet Card floating over the un-dimmed reader page
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                        .shadow(24.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x33000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* Consume taps inside the card */ },
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFAFFFFFF), // Translucent Apple Cupertino card surface
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        // iOS Grabber Pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(38.dp)
                                .height(4.5.dp)
                                .background(Color(0xFFD1D1D6), CircleShape)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Header Bar: Brand Pill + Open in Full App + Close
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SHUNNO DICTIONARY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF007AFF),
                                letterSpacing = 0.8.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        val fullIntent = Intent(this@ReaderPopupActivity, MainActivity::class.java).apply {
                                            putExtra("INITIAL_WORD", selectedWord)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        startActivity(fullIntent)
                                        finish()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("Open App", fontSize = 13.sp, color = Color(0xFF007AFF), fontWeight = FontWeight.SemiBold)
                                }
                                Surface(
                                    onClick = { finish() },
                                    shape = CircleShape,
                                    color = Color(0xFFE5E5EA),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("✕", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3A3C))
                                    }
                                }
                            }
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF007AFF), strokeWidth = 2.5.dp)
                            }
                        } else if (entry == null) {
                            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                Text(
                                    text = selectedWord.ifBlank { "No word selected" },
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = Color(0xFF1C1C1E)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No offline or Wiktionary definition found for this selection.",
                                    fontSize = 14.sp,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        } else {
                            val current = entry!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = current.word,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFF1C1C1E)
                                    )
                                    if (current.phonetic.isNotBlank()) {
                                        Text(
                                            text = current.phonetic,
                                            fontSize = 14.sp,
                                            color = Color(0xFF8E8E93)
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            tts?.speak(current.word, TextToSpeech.QUEUE_FLUSH, null, "shunno_popup_tts")
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = Color(0x1A007AFF)
                                        )
                                    ) {
                                        Text("🔊", fontSize = 16.sp)
                                    }
                                    FilledTonalIconButton(
                                        onClick = {
                                            isSaved = repository.toggleBookmark(current)
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (isSaved) Color(0x26FF9F0A) else Color(0xFFF2F2F7)
                                        )
                                    ) {
                                        Text(if (isSaved) "★" else "☆", fontSize = 18.sp, color = if (isSaved) Color(0xFFFF9F0A) else Color(0xFF8E8E93))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFE5E5EA), thickness = 0.7.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(
                                modifier = Modifier
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                current.senses.forEachIndexed { idx, sense ->
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = Color(0x14007AFF),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = sense.partOfSpeech,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontStyle = FontStyle.Italic,
                                                    color = Color(0xFF007AFF),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${idx + 1}.",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF8E8E93)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = sense.definition,
                                            fontSize = 15.sp,
                                            color = Color(0xFF1C1C1E),
                                            lineHeight = 20.sp
                                        )
                                        if (!sense.example.isNullOrBlank()) {
                                            Text(
                                                text = "“${sense.example}”",
                                                fontSize = 13.sp,
                                                fontStyle = FontStyle.Italic,
                                                color = Color(0xFF636366),
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractWordFromIntent(intent: Intent?): String {
        if (intent == null) return ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!processText.isNullOrBlank()) return cleanSelectedPhrase(processText)
        }
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedText.isNullOrBlank()) return cleanSelectedPhrase(sharedText)

        val queryText = intent.getStringExtra("EXTRA_QUERY") ?: intent.getStringExtra("query")
        if (!queryText.isNullOrBlank()) return cleanSelectedPhrase(queryText)

        return ""
    }

    private fun cleanSelectedPhrase(raw: String): String {
        return raw.trim()
            .trim('.', ',', ';', ':', '!', '?', '"', '\'', '“', '”', '(', ')', '[', ']')
            .lines()
            .firstOrNull()
            .orEmpty()
            .take(45)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}
