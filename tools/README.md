# KP Astro (Android, Kotlin + Jetpack Compose)

Offline KP (Krishnamurti Paddhati) app. The logic is a faithful port of `kp_core.py`, `kp_rules.py`, `kp_daily.py` (see `tools/reference/`).
Swiss Ephemeris (Moshier, KP ayanamsa) is built from the vendored C sources via NDK/JNI (AGPL-3.0, app is open source). No INTERNET permission.

## Build
1. Open the folder in Android Studio (Ladybug or newer). Install NDK + CMake 3.22.1 from SDK Manager if prompted.
2. Let it sync (it fetches Gradle 8.9 from `gradle-wrapper.properties`; run `gradle wrapper` once if you want `./gradlew`).
3. Run on a device/emulator (minSdk 26).

## Layout
- `engine/` pure Kotlin port (`KpCore`, `KpRules`, `KpDaily`), `SweNative` JNI facade, `MiniJson` (no dependencies)
- `cpp/` JNI bridge + vendored Swiss Ephemeris
- `ui/` Compose screens: Today, Life, Chart (diamond chart, planets, cusps, dasha, birth-time sensitivity), Setup
- `data/SampleProfile.kt` pre-fills the setup form (from `profile.json`); delete it before publishing if you like
- `assets/rules.json` editable event rules (same file as the Python engine)

## Validate the port against the Python reference
`tools/host-check/run.sh` builds `libkpswe.so` for the host, compiles the engine with `kotlinc` and compares ~355 values
(charts, cusp/planet lords, dashas, ruling planets, life windows, daily scores/windows, birth-time tolerance)
with `tools/reference/golden.json`, which `gen_golden.py` produces from the Python engine (`pip install pyswisseph`).
Needs a JDK with headers (`openjdk-*-jdk-headless`) and `kotlinc` on PATH.

## Added from the Readers (Reader 3/4 Q&A chapters)
- `rules.json` now holds 32 events (23 new, each with a line reference): engagement, separation, divorce, second marriage, partnership start/end, first salary, reinstatement, suspension, termination of service, transfer, deputation with gain, selection for a post, loan borrowing/repayment, windfall, speculation, imprisonment, accident on journey, PhD thesis, vehicle purchase, tenant arrival/vacate. Childbirth is now verified (denial 1-4-10, Reader 3 ~l.25788).
- `engine/KpHora.kt` (+ `tools/reference/kp_hora.py`): planetary hours from Reader 3 ~l.30440-30580, sub-horas, the Dasha/Bhukti pair rule and the muhurta pairs; shown on the Today screen. Afternoon/night continuation is an assumption.
- Not added on purpose: Parashari gochar/ashtakavarga/dasha-lord-in-house rules (Reader 3 ~l.26929-27440, ~l.29109); Reader 5 rejects traditional transit rules.

## Known deviations from the Python reference
- Adjacent dasha windows are merged when they touch within 1 s (Python required exact datetime equality).
- `transit_score`, `rank_days`, `day_outlook` are not ported (the handoff marks them superseded).
- Lucky-planet ties are broken deterministically (Python iterated a set).
- Diary (`learnSubScores`/`luckyWindows`) is ported in the engine but has no UI yet.
