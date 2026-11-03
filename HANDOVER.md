# KP Astro — Handover (branch `deepseek`)

_Last updated: 2026-11-03._

## Project state

Android-only, open-source KP (Krishnamurti Paddhati) app. **Kotlin is the sole calculation-engine implementation.** Swiss Ephemeris Moshier is vendored in C and exposed through JNI. The Android app and host regression suite execute the same Kotlin engine.

## Non-negotiable rules

1. Astrology rules and logic MUST come from the reference texts in `kp-readers/`. Cite book and OCR lines for every rule change. Do not invent or improvise astrology rules.
2. Work on the `deepseek` branch only. Never edit `main` or `master`.
3. Do NOT build or compile in this environment. The owner validates through GitHub Actions.
4. Do NOT install or download programs or packages without asking first.
5. Do not reintroduce Python engine files, a Python golden generator, or Python-based CI dependencies.

## Architecture

- `app/src/main/java/dev/plawan/kp/engine/` — Kotlin calculation engine:
  - `KpCore.kt`: ephemeris, houses, star/sub/sub-sub lords, significators, node agency, Vimshottari, ruling planets.
  - `KpRules.kt`: event promises, dasha-window scans, transit pinning, parent-event dependencies and birth-time tolerance.
  - `KpDaily.kt`: daily scoring and windows.
  - `KpTiming.kt`: exact timing windows using DBA and sensitive transit zones.
  - `KpHora.kt`: planetary hours and muhurta.
  - `SweNative.kt`, `MiniJson.kt`: JNI facade and dependency-free JSON parser.
- `app/src/main/assets/rules.json` — the single canonical event-rule catalogue. Do not create a second copy.
- `app/src/main/cpp/` — JNI bridge and vendored Swiss Ephemeris Moshier sources.
- `ui/`, `KpViewModel.kt`, `data/`, `domain/` — Compose UI and app orchestration.
- `tools/host-check/` — Kotlin host regression harness:
  - `GoldenCheck.kt` tests the production Kotlin engine.
  - `golden.json` is a checked-in expected-output fixture.
  - `run.sh` compiles the engine and runs the fixture tests.
- `.github/workflows/build.yml` — one workflow with independent APK build/release and Kotlin host-check jobs.

## Validation

Run `sh tools/host-check/run.sh` with a JDK including native headers, `gcc`, and `kotlinc`. GitHub Actions runs the same Kotlin-only check. The golden fixture is a reviewed baseline; CI does not regenerate or commit it automatically. Update it only when a changed expected result is supported by the source texts and the test update is reviewed.

## Current rule/model changes

### Significator hierarchy

Reader 3 ~l.19169-19190 describes the A>B>C>D hierarchy. `KpDaily` now retains the strongest level for each planet-house link instead of flattening all links to equal presence. The 4/3/2/1 values are an order-preserving encoding for a comparative index, not book-defined cardinal weights or probabilities.

### Event promise corrections

- `first_job`: 6th cusp; its sub-lord's star-lord must signify 10 and 11 (Reader 6 ~l.18260-18262).
- `first_private_job`: 10th cusp sub-lord's star-lord signifies 6 and its sub-lord signifies 7 (KP-E-Zine Nov2020-Dec2021 ~l.95679-95681).
- `love_affair`: 5th cusp sub-lord's star-lord signifies 7 and 11 (KP-E-Zine Jan2015-Oct2020 ~l.68428-68431).
- `recovery_from_illness`: 1st cusp sub-lord's star-lord signifies 1, 5 and 11; star-level 6/12 connections deny recovery (KP-E-Zine Jan2015-Oct2020 ~l.699160 and ~l.752258).
- Marriage's specific period veto: the relevant sub-lord owns 1/6/10 and signifies 12 (Reader 4 ~l.7160-7168).
- `first_government_job` remains marked `partial`; the Sun/Moon connection in its source has not yet been implemented as a separate gate.

The former dasha-chain support-count heuristic has been removed. Every lord in a candidate Dasa–Bhukti–Anthra must itself signify at least one event house. This is the implementation of the Readers' 'joint period of significators' requirement; event-specific promise conditions still require continued source-by-source audit.

## Recent commits

- `e3a096a`: apply the A>B>C>D significator hierarchy to daily scoring.
- `8dc65a6`: source-specific promise gates and single GitHub Actions workflow.
- `5a802eb`: fix the significator hierarchy regression assertion.
- `91b6f0c`: remove the Python reference engine and make regression checks Kotlin-only.

## Prediction redesign status (2026-11-03)

- The chart/ephemeris/Vimshottari calculation engine and Compose UI are retained.
- Event promise scoring no longer uses the old arbitrary 'two houses' or support-versus-denial count. Outcomes distinguish supported, mixed, denied and insufficient evidence.
- Life forecast periods now scan to Dasa–Bhukti–Anthra depth.
- Month candidates are produced from the Reader 5 sensitive sign/star/sub zones during a qualifying three-lord period. The Sun and the running period lords are scanned; exact day/hour/minute event claims have been removed from the forecast UI.
- Reader 5 OCR lines 9900–10070 describes the 249 zones and the three-lord transit method. Reader 3 OCR lines 22850–22950 contains a worked event example that narrows from Anthra/Shookshma periods to a transit month/date.
- This is still an astrological method, not a scientifically validated guarantee. The remaining 56 event definitions need individual source audits and independent worked-chart backtests before the app can label every event rule verified.

## Next work

1. Review all event promise definitions against their exact Reader passages; encode any conjunctions and exceptions explicitly.
2. Add worked-example regression cases from the Readers and dated event charts with known outcomes.
3. Validate month-candidate coverage and performance in GitHub Actions and on-device testing.

