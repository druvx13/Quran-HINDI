# Quran Hindi — Android App

A production-ready Android application that displays the Holy Quran with:
- **Arabic text** (Uthmani script)
- **Transliteration** (Roman/Unicode)
- **Hindi Translation** (multiple translators)
- **Hindi Tafsir** (Mokhtasar)
- **Audio recitation** by Mishary Alafasy (streamed over HTTPS)

---

## Features

| Feature | Details |
|---|---|
| 📖 Content | 114 Surahs, all offline (bundled HTML) |
| 🔊 Audio | Inline per-ayah audio player (requires internet) |
| 🌙 Night Mode | Toggle dark/light theme |
| 🔠 Font Size | Adjustable A− / A+ controls |
| 🔎 Navigation | Surah dropdown + built-in HTML search page |
| 💾 Last Position | Automatically saved and restored |
| 📱 Compatibility | Android 5.0+ (API 21) |

---

## Project Structure

```
Quran-HINDI/
├── app/
│   └── src/main/
│       ├── assets/          ← 114 Surah HTML files + index/search pages
│       ├── java/com/quran/hindi/
│       │   ├── SplashActivity.kt   ← 2-second branded launch screen
│       │   ├── MainActivity.kt     ← WebView host with navigation & settings
│       │   └── AudioService.kt     ← Background audio playback service
│       ├── res/
│       │   ├── layout/      ← activity_splash.xml, activity_main.xml
│       │   ├── menu/        ← main_menu.xml (Home, Night Mode)
│       │   ├── values/      ← strings, colors, themes
│       │   └── drawable/    ← vector icons
│       └── AndroidManifest.xml
├── .github/workflows/build-apk.yml   ← Manual CI workflow
├── build.gradle
├── settings.gradle
└── gradlew
```

---

## Building the APK

### Prerequisites

- **JDK 17** (OpenJDK / Temurin recommended)
- **Android SDK** with:
  - Platform: `android-34`
  - Build-tools: `34.0.0` or newer
- Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) environment variable

### Debug build (recommended for testing)

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

### Release build (unsigned)

```bash
./gradlew assembleRelease
# APK output: app/build/outputs/apk/release/app-release-unsigned.apk
```

### Signing the release APK

1. Generate a keystore (one time):
   ```bash
   keytool -genkey -v -keystore quran-hindi.jks \
     -alias quran_hindi_key -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Sign the APK:
   ```bash
   apksigner sign \
     --ks quran-hindi.jks \
     --ks-key-alias quran_hindi_key \
     --out app-release-signed.apk \
     app/build/outputs/apk/release/app-release-unsigned.apk
   ```

3. Verify:
   ```bash
   apksigner verify app-release-signed.apk
   ```

---

## GitHub Actions — Manual APK Build

A manual workflow is available under **Actions → Build APK**.  
Select `debug` or `release` and trigger it.  
The resulting APK is uploaded as a workflow artifact (retained 30 days).

---

## Running on a Device / Emulator

```bash
# Install debug APK directly via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or drag-and-drop the APK onto a running emulator.

---

## Privacy Policy

This app:
- **Does not collect** any personal data.
- **Does not use** analytics, ads, or crash-reporting SDKs.
- **Does not transmit** reading history, preferences, or device identifiers.
- **Stores** only the last-read Surah URL and UI preferences (font size, night mode) in `SharedPreferences` on the device — never uploaded.
- **Streams audio** from `https://druvx13-quran-audio-alafasy.hf.space` solely to play recitations you request. No user data is sent to this server.

All Quranic text and translations are bundled locally and work fully offline.

---

## Ethical Guidelines

- No advertisements are displayed on any screen showing Quranic text.
- External navigation from within the WebView is blocked (only local asset pages and HTTPS audio sources are allowed).

---

## License

HTML content and translations are sourced from open Islamic resources.  
Android application code is released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
