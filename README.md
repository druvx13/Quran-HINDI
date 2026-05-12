# Hinduism — Android App

Android WebView app that bundles the full static Hinduism website content from [druvx13/Hinduism](https://github.com/druvx13/Hinduism) inside `app/src/main/assets`.

## Features

- Offline bundled Hinduism website content
- Home navigation + WebView browsing
- Night mode toggle
- Adjustable text zoom
- Download handling for HTTP/HTTPS and blob downloads

## Project Structure

```text
Quran-HINDI/
├── app/
│   └── src/main/
│       ├── assets/                 # Full static site files from druvx13/Hinduism
│       ├── java/com/hinduism/      # Android app source
│       ├── res/                    # Layouts, strings, themes, icons
│       └── AndroidManifest.xml
├── .github/workflows/build-apk.yml
├── build.gradle
├── settings.gradle
└── gradlew
```

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## GitHub Actions

Use **Actions → Build APK** and choose `debug` or `release`.

## License

Android application code is licensed under Apache 2.0.
Content copyright/license belongs to its original Hinduism source materials.
