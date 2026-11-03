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
- `assets/rules.json` — the single canonical event-rule catalogue used by the app and regression tests.
- `cpp/` — JNI bridge and vendored Swiss Ephemeris (Moshier).

## Network and privacy boundary

The calculation engine does not require network access. The app requests internet access only for the user-triggered rule-catalogue update in Profile. The update downloads the catalogue over HTTPS from this repository's `deepseek` branch, validates the event set, house numbers, status values and source citations, then caches the validated JSON for offline use. Invalid or unavailable downloads are rejected and the bundled/cached rules remain usable. No birth details or profile data are sent.

The Profile screen can also request Android's runtime coarse/fine location permission and read the device's last-known location (up to 24 hours old) to fill the current calculation location. This lookup does not replace birth coordinates and does not make a network request. Manual coordinates remain available. The engine continues to calculate locally with the bundled Moshier ephemeris; no ephemeris download/update mechanism is needed.

## Build

1. Open the folder in Android Studio (Ladybug or newer). Install NDK + CMake 3.22.1 from SDK Manager if prompted.
2. Let it sync (it fetches Gradle 8.9 from `gradle-wrapper.properties`; run `gradle wrapper` once if you want `./gradlew`).
3. Run on a device/emulator (minSdk 26).

## Kotlin regression checks

Run `sh tools/host-check/run.sh` with a JDK that includes native headers, `gcc`, and `kotlinc` on PATH. The script builds the host JNI library, compiles the Kotlin engine, and compares chart, dasha, promise, month-level timing, daily, hora and birth-time-sensitivity outputs against the checked-in fixture at `tools/host-check/golden.json`. The fixture is a reviewed regression baseline; CI does not regenerate it automatically. Update it only alongside an explicitly reviewed change to expected engine behaviour.


## Reader-sourced rule catalogue

The rule catalogue currently contains 56 events. Every astrology rule must remain traceable to `kp-readers/` with a book and OCR line reference. Do not add or alter rule logic without checking the source passages. The app's rules asset is the only canonical rule catalogue. Regression tests read it directly.
