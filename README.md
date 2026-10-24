# LatLong Trivia 🌍📍

**LatLong Trivia** is a 100% offline geography trivia game built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**.

Test your geographical instincts across 240 countries and territories with interactive vector map navigation, localized interior coordinates, flag identification, and continent challenges.

---

## Features

- **🗺️ Map Pin Mode**:
  - The map frames an interior coordinate with a fixed screen-space pulsing radar pin.
  - Guess the country from 4 regional choices (1 correct + 3 regional distractors).
  - Post-answer reveals highlight the true country in green (`#22C55E`) and incorrect guesses in red (`#EF4444`).
- **🚩 Flag Quiz Mode**:
  - Guess the country from a centered flag card rendered with `ContentScale.Fit` (accommodates square flags like Switzerland and non-rectangular flags like Nepal).
  - Background map remains zoomed out globally with no pin until answered, then smoothly animates to frame and highlight the correct country.
- **🌐 Continent Filters**:
  - Filter questions and distractors by region: `All World`, `Europe`, `Asia`, `Africa`, `Americas` (merging North and South America), and `Oceania`.
- **🔥 Local Streak Tracking**:
  - Persistent all-time best streaks tracked per `(GameMode, ContinentFilter)` combination, surviving app restarts.
- **⚡ 100% Offline**:
  - Pre-processed Natural Earth 1:50m Admin-0 GeoJSON dataset (< 801 KB).
  - 236 embedded crisp country flag assets. Zero network permissions required at runtime.
- **🎨 Modern Android Design**:
  - Dynamic slate UI with hardware-accelerated Compose Canvas paths.
  - Custom adaptive icon and Android 13+ themed monochrome icon support.

---

## Tech Stack & Architecture

- **Platform**: Kotlin Multiplatform + Compose Multiplatform
- **UI & Graphics**: Jetpack Compose, Skia Canvas transforms, Equirectangular projection
- **Data & Serialization**: `kotlinx.serialization` (JSON)
- **Local Storage**: `StreakStorage` (`expect`/`actual` backed by Android `SharedPreferences`)
- **Data Pipeline**: Python + Shapely pipeline (`scripts/build_dataset.py`) for polygon simplification and interior point sampling.

---

## Building & Running

### Prerequisites
- JDK 17 or higher
- Android SDK (API 35+)

### Commands
```bash
# Run unit tests
./gradlew :shared:allTests

# Assemble debug APK
./gradlew :androidApp:assembleDebug

# Install to connected device or emulator
./gradlew :androidApp:installDebug
```

The output APK is generated at:
`androidApp/build/outputs/apk/debug/androidApp-debug.apk`
