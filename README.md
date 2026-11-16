# Shunno Dictionary (শূন্য ডিকশনারি)

**Shunno Dictionary** is a fast, lightweight, ad-free English dictionary and vocabulary reference for Android, designed with a modern **Apple iOS / macOS Cupertino UI** and a **transparent, zero-dim pop-up lookup mode** for e-readers and web browsers.

---

## 📲 1-Click Direct Download for Android
Tap the link below to download and install the latest compiled APK directly on your phone:

- **[👉 Download ShunnoDictionary.apk (Latest Release)](https://github.com/asanaim/English-Dictionary/releases/latest/download/ShunnoDictionary.apk)**

---

## ✨ Key Features

### 1. Apple Cupertino UI & Custom "Shunno" Visual Identity
* **Clean, Distraction-Free Interface:** Built with an Apple-inspired palette (`#F2F2F7` grouped background, `#FFFFFF` elevated cards, `#007AFF` system blue accents, and crisp serif headword typography) alongside a true **OLED Dark Mode** (`#000000`).
* **Zero Bloatware & Zero Branding Clutter:** Completely stripped of legacy third-party branding, ads, and unnecessary background services.
* **Custom Shunno Squircle Emblem:** Features a Cobalt-to-Azure squircle icon combining the *Shunno* (`0` / Enso circle of clarity) loupe ring, minimalist open book pages, and an amber bookmark ribbon (`#FF9F0A`).

### 2. Transparent Reader & Browser Pop-up Mode (No Dark Screen)
* **Un-Dimmed Underlying Page:** When you highlight a word in any e-reader or browser, the floating Cupertino definition card appears at the bottom of the screen while keeping your book or webpage **100% bright, clear, and readable** behind it (`android:windowIsTranslucent="true"`, `android:backgroundDimEnabled="false"`, and `FLAG_DIM_BEHIND` cleared).
* **Universal E-Reader & Browser Integration:**
  * **Android Text Selection Menu (`ACTION_PROCESS_TEXT`):** Highlight any word in Chrome, Firefox, Brave, ReadEra, or system apps and tap **Shunno Dictionary**.
  * **Share Sheet (`ACTION_SEND`):** Share any selected word or phrase directly to the pop-up card.
  * **E-Reader Dictionary Protocol (`colordict.intent.action.SEARCH`):** Works seamlessly as an external pop-up dictionary for Moon+ Reader, Librera, Kobo, and GoldenDict-compatible readers.
* **Instant Outside-Tap Dismissal:** Tap anywhere outside the floating card to return immediately to your reading position.

### 3. Instant Prefix Search & Live Autocomplete Suggestions
* **Real-Time Suggestions Dropdown:** Displays a live dropdown list of matching words as you type, powered by local SQLite prefix indexing and live Wiktionary/Datamuse autocomplete.
* **Voice Search (`🎤`):** Look up words hands-free using built-in Android speech recognition.
* **Quick Clear & Instant Lookup:** One-tap query clearing and keyboard search action support.

### 4. Comprehensive Lexicon & Word Senses
* **Complete Word Entries:** Every entry provides the headword, **IPA phonetic transcription**, **parts of speech** (*noun*, *verb*, *adjective*, *adverb*), numbered definitions, and context-rich usage examples.
* **Synonyms, Antonyms & Cross-References:** Interactive chips for **Synonyms**, **Antonyms**, **Inflected Forms**, and **"See Also"** related terms—tap any chip to jump straight to that word's definition.
* **Etymology & Word Origins:** Dedicated origin section detailing the historical and linguistic roots of words.
* **Offline & Hybrid Architecture:** Supports instant local SQLite caching (`shunno_full.db`) alongside online fallback lookups.

### 5. Built-In Verb Conjugator & A–Z Words Index
* **English Verb Conjugator (`⚡`):** Generates complete conjugation tables across tenses and persons (*Present Simple*, *Past Simple*, *Continuous / Participle*, *Present Perfect*, and *Future Simple*). Verb entries also feature a direct **`Conjugate →`** shortcut.
* **Alphabetical A–Z Index (`🔤`):** Browse the dictionary lexicon systematically from **A to Z**.
* **Word of the Day (`☀`) & Random Word (`🔀`):** Discover a new featured word every day or tap **Random** for instant vocabulary exploration.

### 6. Personal Study Suite: Bookmarks, Categories, Notes & History
* **Categorized Bookmarks (`★`):** Save favorite words and filter them across organized study collections (*All*, *General*, *Favorites*, *Academic*, *GRE / IELTS*, *Literature*).
* **Personal Word Notes (`📝`):** Attach custom study notes, translations, or mnemonics directly to any word. Your saved note appears as a highlighted banner inside that word's definition card and in the dedicated **Notes** tab.
* **Lookup History:** Automatically logs your recent word searches with one-tap revisit or history clearing.

### 7. Multi-Accent Audio Pronunciation (TTS) & View Customization
* **US & UK Text-to-Speech (`🔊`):** Listen to natural offline word pronunciations in **American English (`en_US`)** or **British English (`en_GB`)** with adjustable speech rates (*Slow*, *Normal*, *Fast*).
* **Custom Typography & Layout (`⚙️`):** Adjust definition font sizes (*Small*, *Medium*, *Large*, *Very Large*), line spacing (*100%*, *150%*, *200%*), and Light/Dark appearance in real time.

---

## 🛠️ Project Structure & Cloud Build

```text
English-Dictionary/
├── .github/workflows/
│   └── main.yml                  # Automated GitHub Actions APK builder & Release publisher
├── app/
│   ├── build.gradle.kts          # Android SDK 34, Kotlin 1.9, Jetpack Compose Material 3
│   └── src/main/
│       ├── AndroidManifest.xml   # Main launcher + Zero-Dim ReaderPopupActivity intent filters
│       ├── java/com/shunno/dictionary/
│       │   ├── MainActivity.kt          # Full 6-tab Cupertino UI + Live Suggestions Dropdown
│       │   ├── ReaderPopupActivity.kt   # Zero-dim transparent floating card for e-readers/browsers
│       │   └── DictionaryRepository.kt  # SQLite lexicon, live autocomplete, notes, bookmarks, conjugator
│       └── res/
│           ├── drawable/         # Custom Apple-inspired Shunno vector adaptive icon
│           └── values/styles.xml # Theme.ShunnoDictionary & Theme.ShunnoDictionary.ReaderPopup
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 📄 License
Code is licensed under the **MIT License**. Dictionary lexical data is derived from **Wiktionary** under the [Creative Commons Attribution-ShareAlike 3.0 Unported License (CC BY-SA 3.0)](https://creativecommons.org/licenses/by-sa/3.0/).
