# KP Astro (Android, Kotlin + Jetpack Compose)

KP (Krishnamurti Paddhati) app. The KP engine and its rule catalogue remain on-device; optional network-backed capabilities can be added behind repository interfaces without making chart calculation depend on a connection.

## App workflow

1. **Profile** — enter birth date, local birth time, birth time zone and birth-place coordinates. The calculation location can be the birth place or a separate current place.
2. **Today** — see the daily overview, favourable/caution windows, hora and life-area summary.
3. **Forecast** — explore life-event promises, periods and exact timing. Expand an event to inspect its rule and Reader source.
4. **Chart** — inspect the natal chart, planets, cusps, Vimshottari periods and birth-time sensitivity.
5. **Profile** — edit the saved birth details or calculation location at any time.

A first-time user is taken directly to Profile. After saving, the app opens Today. Existing users retain access to all four destinations from the bottom navigation.

## Code architecture

- `ui/` — Compose screens and reusable visual components. Screens render state and send user actions to the ViewModel.
- `KpViewModel.kt` — screen state, coroutine dispatch and coordination. It does not load assets or calculate KP results directly.
- `domain/KpAnalysis.kt` — calculation use-case boundary. It orchestrates existing engine operations without redefining astrology.
- `data/KpRepository.kt` — profile and rule-catalogue data boundary. The current implementation reads SharedPreferences and the bundled rules asset; remote sources can later be introduced here.
- `data/ProfileStore.kt` — local profile persistence.
- `engine/` — pure Kotlin KP calculation port (`KpCore`, `KpRules`, `KpDaily`, `KpTiming`, and related files). This is the calculation source of truth.
- `assets/rules.json` — event rules, kept identical to `tools/reference/rules.json`.
- `cpp/` — JNI bridge and vendored Swiss Ephemeris (Moshier).

## Network and privacy boundary

The calculation engine does not require network access. Any future geocoding, place search, remote rule-catalogue check or sync must be optional, use an explicit repository implementation, and leave manual coordinates and bundled rules available as fallbacks. Birth details should not be transmitted unless a user-initiated feature clearly requires it and the user has been told what is sent.

## Build

1. Open the folder in Android Studio (Ladybug or newer). Install NDK + CMake 3.22.1 from SDK Manager if prompted.
2. Let it sync (it fetches Gradle 8.9 from `gradle-wrapper.properties`; run `gradle wrapper` once if you want `./gradlew`).
3. Run on a device/emulator (minSdk 26).

## Validate the port against the Python reference

`tools/host-check/run.sh` builds `libkpswe.so` for the host, compiles the engine with `kotlinc` and compares ~355 values with `tools/reference/golden.json`, which `gen_golden.py` produces from the Python engine (`pip install pyswisseph`). Needs a JDK with headers and `kotlinc` on PATH. Golden also carries five deterministic `sensitivePoints` chains; exact transit windows are not in golden yet.

## Reader-sourced rule catalogue

The rule catalogue currently contains 56 events. Every astrology rule must remain traceable to `kp-readers/` with a book and OCR line reference. Do not add or alter rule logic without checking the source passages. The app's rules asset and Python reference copy must remain byte-for-byte identical.
