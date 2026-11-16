# Shunno Dictionary — Complete Modding & Modern App Kit

This archive includes two complete solutions for **Shunno Dictionary**:

1. **`1_Livio_APK_Patcher/` (Patch Your Existing Offline Wiktionary APK):**
   - Strips all **Livio** logos (`ab_icon.png`, `ic_launcher.png`, and hidden `en-rGB` / `en-rUS` locale overrides).
   - Replaces the branding and icon with the new Apple-inspired **Shunno Dictionary** logo.
   - Injects `Theme.Shunno.Popup.NoDim` (`android:windowIsTranslucent="true"`, `android:windowBackground="@android:color/transparent"`, `android:backgroundDimEnabled="false"`) so that when the dictionary pop-up opens on any e-reader or browser, **the background stays the reader page with zero dark screen**.

2. **`2_Shunno_Dictionary_GitHub_Project/` (Clean, Fast, Lightweight Standalone Android App):**
   - Built with Kotlin & Jetpack Compose in a modern **Apple iOS / macOS Dictionary** aesthetic.
   - Includes `ReaderPopupActivity` hooked into Android's `ACTION_PROCESS_TEXT`, `ACTION_SEND`, and `colordict.intent.action.SEARCH` with `FLAG_DIM_BEHIND` cleared so highlighting a word in any e-reader or browser pops up a sleek floating Cupertino card while keeping the reader completely bright and visible behind it.
