# KP Astro — Handover (branch `deepseek`)

_Last updated: 2026-10-16. Supersedes `tools/reference/HANDOFF.md` for current state; that file still holds the detailed engine rationale._

## What this project is

Android-only, fully offline, open-source KP (Krishnamurti Paddhati) astrology app.
The astrology engine is a faithful port of a Python reference (`tools/reference/kp_core.py`,
`kp_rules.py`, `kp_daily.py`) to Kotlin (`app/src/main/java/dev/plawan/kp/engine/`).
Swiss Ephemeris (Moshier, KP ayanamsa) is vendored in C and exposed via JNI (`cpp/`).

## Hard rules (also in `AGENTS.md`)

1. **All astrology rules/logic MUST come from `kp-readers/`** (KP Readers 1-6, KP E-Zine
   compilations, KP Navaratnamala, KP Sublord SPK, Astrology for Beginners, Jyotish Astro
   Secrets & KP). Never invent or guess a rule. Cite the book + OCR line(s).
2. **Work on the `deepseek` branch only.** Never touch `main`/`master`.
3. **Do NOT build or compile here.** The user builds via GitHub Actions.
4. **Do NOT install/download anything** without asking the user first.

## Repo layout

- `app/src/main/java/dev/plawan/kp/engine/` — `KpCore.kt` (ephemeris, houses, star/sub/sub-sub
  lords, significators, node agency, Vimshottari, sunrise day-lord, ruling planets),
  `KpRules.kt` (promise, dasha-window scan, Moon windows, transit pinning, diary, birth-time
  tolerance), `KpDaily.kt` (per-30-min slot scoring, lucky/avoid windows, day report),
  `SweNative.kt`/`MiniJson.kt` (deps-free facade/parser).
- `app/src/main/assets/rules.json` — event rules read by the app (**keep identical** to
  `tools/reference/rules.json`).
- `tools/reference/` — Python reference + `rules.json`, `golden.json`, `gen_golden.py`,
  `HANDOFF.md` (engine rationale), `profile.json`.
- `tools/host-check/` — `GoldenCheck.kt` + `run.sh`; compares the Kotlin port to
  `golden.json` (~355 values). Needs JDK headers, `kotlinc`, `pyswisseph` to regen.
- `kp-readers/` — the reference texts (**untracked in git**).

## Session progress (2026-10-16)

Audited and corrected `rules.json` (both copies) against the readers. No engine code changed.

- **childbirth**: denial `[1,4,10]` sourced (Reader 3 ~l.25783-25789) → status `partial`→`verified`.
- **foreign_travel**: support `[9,12]`→`[3,9,12]`, denial `[2,4,11]`→`[2,4,8,11]`
  (KP-E-Zine Jul 2009 "Rules for Foreign Going, Settlement and Return to Motherland"
  ~l.226664-226682; KP-E-Zine Jan 2011 ~l.224031-224037) → status `partial`→`verified`.
- marriage, job, retirement, higher_education, study_abroad, property_purchase/sale: groups
  confirmed; `source` citations tightened to exact OCR lines. `study_abroad` stays `partial`
  (its denial `[3,5,8]` is inferred from the education-end rule only).

Also created `AGENTS.md`.

### Session 2 (2026-10-16): parent-event dependencies (soft gate)

Events that logically follow another event are now flagged, never dropped. `rules.json`
(both copies) gained `depends_on` + `depends_on_source`:

- `childbirth` ← `marriage` (Reader 6 ~l.16491-16496; Reader 4 ~l.12805-12806, 12827-12829)
- `retirement` ← `job` (KP-E-Zine Nov2020-Dec2021 ~l.125368-125369; Reader 3 ~l.11961-11965)
- `study_abroad` ← `higher_education` (Reader 3 ~l.19020-19030, 19158-19166)
- `property_sale` ← `property_purchase` (Reader 3 ~l.14651-14654)

Engine: `KpRules.lifeReport` now runs two passes (promise/windows for all rules, then annotate),
adding `EventReport.parents` (`ParentGate` list) and per-window `DashaWindow.parentActive` /
`parents` (overlap with a parent window). Python `kp_rules.life_report` mirrors it exactly.
No window is filtered and no numeric daily score changes — the flag is advisory. `LifeScreen`
shows "after <parent>" pills and marks windows that fall outside the parent event's window.

### Session 3 (2026-10-16): UI refresh (modern, decluttered)

UI-only; no engine, rules, or golden data changed. Reworked the Compose layer:

- `Theme.kt`: cohesive dark/light palette built on the M3 surfaceContainer ramp, shared
  `Shapes` (10/16/22dp rounding); `KpColors` semantic accents retained.
- `Components.kt`: `KpCard` now a quiet hairline-bordered surface (no elevation); smaller tonal
  `Pill`s; `Dot`, `KeyValue`; custom dependency-free `KpNavIcon` (Canvas line icons).
- `MainActivity.kt`: navigation bar uses the drawn icons + M3 selected indicator (no emoji).
- `TodayScreen.kt`: compact hero, `DayStrip` + two window rows, timing details behind a toggle,
  and life areas as tappable rows (dot + score + bar) that expand to the drivers/windows — the
  single biggest declutter; guidance and transits split into two cards.
- `LifeScreen.kt`: event rows with status dot, chevron and expandable rule/source.
- `ChartScreen.kt`, `SettingsScreen.kt`: hairline table separators, grouped fields.

No build run here (per AGENTS.md); no test vectors or golden regeneration affected.

### ⚠️ Consequence: `golden.json` is now stale

`foreign_travel` groups feed the daily "Travel" area, so `tools/reference/golden.json` no
longer matches. It must be regenerated by the user:
`pip install pyswisseph` then `python tools/reference/gen_golden.py > tools/reference/golden.json`.
The `life` section format also changed this session (added `parents` and per-window
`parent_active` / `parents`), so the regeneration is required before `tools/host-check/run.sh`
will pass.

## Regression anchors (must not change unless a bug is proven)

- 1990-05-15 10:30 IST, Delhi (28.6139 N, 77.2090 E): KP ayanamsa 23.6257; Asc 99.9815
  (Cancer, Saturn/Venus); Moon 269.9391 (Sun/Rahu/Saturn); Rahu 287.7256; 7th cusp sub-lord Venus.
- 2000-11-21 18:12 IST, Nalbari (26.4446 N, 91.4373 E): Asc 01d47'40" Gemini
  (Mercury/Mars/Mercury/Saturn); Moon 12d29'40" Virgo; mean node Rahu 24d03'37" Gemini H1;
  running Rahu-Ketu-Sun on 2026-10-15.

## Open TODOs (priority order)

1. Regenerate `golden.json` after the `rules.json` change, then run `tools/host-check/run.sh`.
2. Extend transit pinning (`KpRules.transitPin`, currently Sun only) to **Moon (hours)** and
   **Jupiter/Saturn (months)** per Reader 3 ~l.1980-2043 and the 3-level form at Reader 3
   ~l.20002-20004 (Sun in sign of dasa lord + star of bhukti lord + sub of antara lord).
3. Replace the remaining `unverified` daily groups (money, health) with cited rules — e.g.
   recovery = 1,5,11 vs illness = 6,8,12 (KP-E-Zine), speculation gain = 5,6,11 vs 8,12
   (Reader 3 ~l.25776-25779).
4. Consider reworking `KpDaily` scoring to weight significator strength A>B>C>D
   (Reader 3 ~l.19169-19174 describes the hierarchy).
5. Validate against worked charts in Reader 1 (casting) and Reader 6 (horary); add a
   backtesting harness (known event dates → does a window contain it?).
6. Diary layer (`learnSubScores`/`luckyWindows`) is ported but has no UI.

## Paste-able prompt for the next agent

```
You are continuing the KP Astro project (Android, Kotlin + Jetpack Compose) on the
`deepseek` branch. First read `AGENTS.md` and `HANDOVER.md` at the repo root, then
`tools/reference/HANDOFF.md`.

Non-negotiable rules:
- All astrology rules/logic MUST come from the reference texts in `kp-readers/`.
  Never invent or guess a rule; cite the book and OCR line(s) for every rule you add.
- Edit the `deepseek` branch only. Never touch `main`/`master`.
- Do NOT compile or build anything; the user builds via GitHub Actions.
- Do NOT install or download any program/package without asking the user first.

Current state: the engine lives in app/src/main/java/dev/plawan/kp/engine/
(KpCore.kt, KpRules.kt, KpDaily.kt). Event rules live in app/src/main/assets/rules.json
and MUST stay identical to tools/reference/rules.json. I just audited rules.json against
the readers (see HANDOVER.md); as a result tools/reference/golden.json is stale and needs
regenerating by the user (tools/reference/gen_golden.py).

Your task: [DESCRIBE THE TASK HERE].

Before editing: read the relevant reader passages and quote them in your plan. After
editing: list the files changed and state which test vectors / golden data (if any) the
user must regenerate or re-run.
```
