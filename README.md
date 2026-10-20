# KP Astro (Android, Kotlin + Jetpack Compose)

Offline KP (Krishnamurti Paddhati) app. The logic is a faithful port of `kp_core.py`, `kp_rules.py`, `kp_daily.py` (see `tools/reference/`).
Swiss Ephemeris (Moshier, KP ayanamsa) is built from the vendored C sources via NDK/JNI (AGPL-3.0, app is open source). No INTERNET permission.

## Build
1. Open the folder in Android Studio (Ladybug or newer). Install NDK + CMake 3.22.1 from SDK Manager if prompted.
2. Let it sync (it fetches Gradle 8.9 from `gradle-wrapper.properties`; run `gradle wrapper` once if you want `./gradlew`).
3. Run on a device/emulator (minSdk 26).

## Layout
- `engine/` pure Kotlin port (`KpCore`, `KpRules`, `KpDaily`, `KpTiming`), `SweNative` JNI facade, `MiniJson` (no dependencies)
- `cpp/` JNI bridge + vendored Swiss Ephemeris
- `ui/` Compose screens: Today, Life, Chart (diamond chart, planets, cusps, dasha, birth-time sensitivity), Setup
- `data/SampleProfile.kt` pre-fills the setup form (from `profile.json`); delete it before publishing if you like
- `assets/rules.json` editable event rules (same file as the Python engine)

## Validate the port against the Python reference
`tools/host-check/run.sh` builds `libkpswe.so` for the host, compiles the engine with `kotlinc` and compares ~355 values
(charts, cusp/planet lords, dashas, ruling planets, life windows, daily scores/windows, birth-time tolerance)
with `tools/reference/golden.json`, which `gen_golden.py` produces from the Python engine (`pip install pyswisseph`).
Needs a JDK with headers (`openjdk-*-jdk-headless`) and `kotlinc` on PATH. Golden also
carries five deterministic `sensitivePoints` chains; exact transit windows are not in
golden yet.

## Added from the Readers (Reader 3/4 Q&A chapters)
- `rules.json` now holds 49 events (each with a line reference): engagement, separation, divorce, second marriage, partnership start/end, first salary, reinstatement, suspension, termination of service, transfer, deputation with gain, selection for a post, loan borrowing/repayment, windfall, speculation, imprisonment, accident on journey, PhD thesis, vehicle purchase, tenant arrival/vacate, promotion, leaving a job, foreign job, business start/gain, love affair, college admission, visa papers, cinema career, sports victory, election win, litigation win, lost-property recovery, return from abroad and pet animals. Childbirth is now verified (denial 1-4-10, Reader 3 ~l.25788).
- `engine/KpHora.kt` (+ `tools/reference/kp_hora.py`): planetary hours from Reader 3 ~l.30440-30580, sub-horas, the Dasha/Bhukti pair rule and the muhurta pairs; shown on the Today screen. Afternoon/night continuation is an assumption.
- `rules.json` holds 49 events. `illness` (6,8,12 vs cure 1,5,11) and `recovery_from_illness` (1,5,11 vs 6,12; soft-gated on `illness`) are cited to Reader 3 ~l.17665, 10818-10822 and the KP-E-Zines.
- Reader 6's compact house table (~l.15419-15448) is now used directly: 2-6-10-11 promotion, 6-11 litigation/pet animals, 6-11 election, 2-4-11 car, 2-5-6-11 speculation. KP-E-Zine articles supply promotion, business start/gain, love affair, college admission, visa, cinema, sports, leaving/foreign job and lost-property rules. Each new rule carries support/denial groups, a `plain` restatement and a soft `depends_on` gate where the event presupposes another.
- Daily areas: `health` is now `verified` (cure 1,5,11 / disease 6,8,12) and `money` is `partial` (2,6,10,11 and 12 verified; denial 8 is ambiguous in Reader 3 ~l.12388).
- `KpRules.pinPeriodAll` (+ `kp_rules.pin_period_all`): Reader 5 ~l.2535, 2580 pins results when either period lord or either luminary transits star X sub Y / star Y sub X. Sun pins days, Moon pins hours. Opt-in; not wired into the UI.
- `engine/KpTiming.kt` (+ `tools/reference/kp_timing.py`): exact event timing from Reader 5 ~l.9970-10040. For a promised Dasa–Bhukti–Antara (A-B-C), the sensitive points are the rows of the 249-sub table whose sign/star/sub lords are exactly that group. Transits by the Moon give the hour, the Sun the day, the period lords the month, and the Lagna the minute (needs the place latitude/longitude). Rahu/Ketu can be star or sub lords in the table (never sign lords). Windows are ranked by the Reader 3 ~l.19160-19175 significator order A > B > C > D. Engine-only; not wired into the UI. The sensitive-point table is covered by `golden.json`; transit windows are not yet.
- Exact-timing window edges are sampling approximations (Moon 15 min, Sun 30 min, Lagna 1 min, fast lords 6 h, slow lords 12 h), not analytic star/sub crossings.
- Not added on purpose: Parashari gochar/ashtakavarga/dasha-lord-in-house rules (Reader 3 ~l.26929-27440, ~l.29109); Reader 5 rejects traditional transit rules.

## Known deviations from the Python reference
- Adjacent dasha windows are merged when they touch within 1 s (Python required exact datetime equality).
- `transit_score`, `rank_days`, `day_outlook` are not ported (the handoff marks them superseded).
- Lucky-planet ties are broken deterministically (Python iterated a set).
- Diary (`learnSubScores`/`luckyWindows`) is ported in the engine but has no UI yet.
