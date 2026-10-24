# KP Astro — Handover (branch `deepseek`)

_Last updated: 2026-10-20. Supersedes `tools/reference/HANDOFF.md` for current state; that file still holds the detailed engine rationale._

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
  `KpTiming.kt` (exact DBA + luminary/Lagna event timing), `SweNative.kt`/`MiniJson.kt`
  (deps-free facade/parser).
- `app/src/main/assets/rules.json` — event rules read by the app (**keep identical** to
  `tools/reference/rules.json`).
- `tools/reference/` — Python reference (`kp_core.py`, `kp_rules.py`, `kp_daily.py`,
  `kp_timing.py`, `kp_hora.py`) + `rules.json`, `golden.json`, `gen_golden.py`,
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

### Session 4 (master): rules and pinning

Worked on `master` at the owner's request (supersedes the deepseek-only rule for this session). No build was run.

- `rules.json` (both copies): added `illness` and `recovery_from_illness` (+ `depends_on: illness`) with citations.
- `KpDaily`/`kp_daily.py`: money -> `partial`, health -> `verified`, with source comments.
- `KpRules.kt`/`kp_rules.py`: `transitPin` gained `ketu`; new `pinBodiesFor`, `pinPeriodAll` / `pin_bodies_for`, `pin_period_all`; `PinWindow.body`.
- Golden data is stale again (two new rules change the `life` section). Regenerate `golden.json` before `tools/host-check/run.sh`.
- Still open: A>B>C>D significator weighting in `KpDaily` (TODO 4), worked-chart backtests (TODO 5), diary UI (TODO 6), a UI hook for `pinPeriodAll`.

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

### Session 5 (master): birth→next-mahadasha outlook + plain-language rules

Worked on `master` at the owner's request. No build was run.

- `KpRules.kt`: new `LifeRange` + `lifeRange(ch, now)` = birth → end of the mahadasha *after* the one running now; `lifeReport(..., range)` scans that span (old `years` form kept for the golden check). `Rule` gained `plain`.
- `rules.json` (both copies): every rule has a `plain` field — a short everyday-language restatement of the cited Reader rule (no new astrology; wording derived from each rule's `source`).
- `KpViewModel` exposes `lifeSpan`; `LifeScreen` header shows the scanned span, windows are tagged past / running now, and the expanded card shows "In simple words" above the houses and Reader source.
- `tools/reference/kp_rules.py`: `life_range` + `life_report(..., span=)` mirror the Kotlin. Golden data unchanged by this (golden still uses `years=15`) but remains stale from Session 4.

### Session 6 (deepseek): more Reader-sourced events

No build was run. Event count is now 49 (15 new), all marked `verified` except the two
pre-existing `partial` rules (study_abroad, second_marriage).

- `rules.json` (both copies, kept identical):
  - `promotion` (10th CSL → 2,6,10,11; KP-E-Zine May 2015 ~l.49810-49826, Feb 2007-Dec
    2010 ~l.78933-78940; Reader 6 ~l.15428), soft-gated on `job`.
  - `leaving_job` (10th/6th CSL → 1,3,5,9; KP-E-Zine Feb 2007-Dec 2010 ~l.124818-124823),
    soft-gated on `job`.
  - `foreign_job` (12th CSL → 3,9,12, preferably 9, plus 6/10 as alt_support; same E-Zine
    ~l.124830-124833; Reader 6 ~l.14856), soft-gated on `job`.
  - `business_start` (10th CSL → 2,7,10; E-Zine May 2015 ~l.70504-70509) and
    `business_gain` (7th CSL → 2,10,11; ~l.71070-71076), the latter gated on the former.
  - `love_affair` (5th CSL in star of a significator of 7/11; E-Zine ~l.68428-68431;
    Reader 4 ~l.6404-6405, denial 4).
  - `college_admission` (4th CSL → 4,9,11; E-Zine ~l.141537-141545; end group 3,5,8 from
    Reader 3 ~l.16004-16028).
  - `visa_papers` (3rd/11th CSL → 3,11, with 9/12 linkage for foreign; E-Zine
    ~l.55633-55640).
  - `cinema_actor` (5th CSL → 5,7,10,11; E-Zine Feb 2007-Dec 2010 ~l.173169-173175).
  - `sports_victory` (1st/6th/11th CSL → 1,6,11; E-Zine ~l.71299-71307).
  - `election_win` (Asc CSL → 6,11 wins; 5,12 loses; E-Zine ~l.175644-175646; Reader 6
    ~l.15448).
  - `litigation_win` (6,11; Reader 6 ~l.15442-15443).
  - `lost_property_recovery` (2,6,11 vs 5,8,12; Reader 6 ~l.13145-13205; no cusp is given
    in the text, 11th used for the promise check).
  - `return_from_abroad` (3,9,11; Reader 4 ~l.12640-12651), soft-gated on `foreign_travel`.
  - `pet_animal` (6,11; Reader 6 ~l.15434).
  - Existing entries refined: `speculation_gain` gained alt_support 2,5,6,11 and
    `vehicle_purchase` gained alt_support 2,4,11, both from Reader 6 ~l.15419-15432.

Engine code did not change (rules are data-driven); LifeScreen picks the new rules up
automatically. `tools/reference/golden.json` is therefore stale again: regenerate it with
`python tools/reference/gen_golden.py > tools/reference/golden.json` (needs
`pip install pyswisseph`) before running `tools/host-check/run.sh`.

### Session 7 (deepseek): exact event timing (DBA + transits)

No build and no install were run. Python files were syntax-checked with `py_compile`; the
sensitive-point table was validated against the Reader examples with a stubbed `swisseph`
module (no ephemeris calls).

- `KpRules.kt`: extracted `dashaHits(ch, rule, start, end, depth)` (unmerged depth-3
  periods) from `scan()`; `scan()` now merges `dashaHits()` exactly as before, so existing
  golden windows are unchanged. `tools/reference/kp_rules.py` mirrors this as
  `scan_hits()` + `scan()`.
- New `engine/KpTiming.kt` (+ `tools/reference/kp_timing.py`):
  - `sensitivePoints(lords, depth=3)`: Reader 5 ~l.9970-10040. For A-B-C (MD-AD-PD), the
    sensitive points are the rows of the 249-sub table whose **sign/star/sub lords are
    exactly that group** (multiset, so repeated lords are respected). This is not a full
    permutation: e.g. Sun-Venus-Mercury gives the 3 points listed at ~l.10022-10038,
    Mercury-Rahu-Jupiter gives 2, Mars-Saturn-Mercury gives 4. Depth 2 falls back to
    "star of one + sub of the other" pairs (~l.2626), sign ignored.
  - `exactTimes(...)`: only runs when the cusp promise is `strong`/`promised`; returns the
    broad MD-AD windows plus `ExactWindow(start,end,precision,rank,body,point,chain,rule,
    strength,source)`. Funnel: Moon hour -> Asc minute; Sun day -> Moon hour -> Asc minute;
    other period lords day (Mars/Mercury/Venus) or month (Jupiter/Saturn/Rahu/Ketu).
    Ascendant needs `lat`/`lon` (`Swe.houses(...)[12]`; the reader says to use the place
    where the native actually is, ~l.10030).
  - `strength` = sum of each chain lord's significator level for the primary house
    (A=4,B=3,C=2,D=1; Reader 3 ~l.19160-19175); `bestExact()` sorts by rank then start.
  - Sampling: Moon 15 min, Sun 30 min, Lagna 1 min, fast lords 6 h, slow lords 12 h; caps
    24 periods / 120 windows. Edges are step-resolution approximations, not analytic
    crossings. Depth 2 over a 1-3 year AD is intentionally slower; default is depth 3.
- Not done yet:
  - No UI wiring (nothing calls `exactTimes` from `KpViewModel`/`LifeScreen`).
  - `golden.json` gained deterministic `sensitive` vectors (5 chains) and `GoldenCheck.kt`
    compares them; the refactor preserves `scan()` output. Transit-window vectors are not
    in golden yet (ephemeris-bound); add them later with a bounded sample if wanted.
  - Rahu/Ketu can be star or sub lords in the 249-sub table (never sign lords), so they
    appear naturally in the sensitive group. The separate node-agent rule at Reader 5
    ~l.10040-10070 (results through the natal conjunction/star/sign lord, and the day-lord
    / Rahu-Kalam ordering) is not modelled; the scan uses the node's own transit.

### Session 8 (master, owner-directed): exact Jupiter/Sun/Moon timing wired to the UI

No build, no install, no download (per the owner's instruction). Python was only parsed with
`ast.parse` (no bytecode written, no ephemeris calls).

- `KpTiming.kt`:
  - `UI_EXACT_CFG = ExactCfg(moonStepMin = 45, sunStepMin = 60, maxPeriods = 60)` - sampling
    preset for the on-device sweep. The narrowest 249-row zone is the Sun sub (~40'), which the
    Moon crosses in ~75 min, so a 45-min step cannot skip a zone and still resolves the book's
    "hour" precision (Reader 5 ~l.11170-11180). `EXACT_CFG` (engine default) is unchanged and
    stays in parity with `tools/reference/kp_timing.py` `DEFAULT_CFG`.
  - `exactTimes(..., near: Instant? = null)`: when `near` is given, the depth-3 periods kept are
    the ones closest to it instead of the earliest ones, so a birth-to-next-mahadasha scan still
    reports the times around today. `near = null` keeps the old behaviour bit for bit.
    Mirrored in `tools/reference/kp_timing.py` (`exact_times(..., near=None)`).
- `KpViewModel.kt`: `timings: StateFlow<Map<String, EventTiming>>` + `timingBusy: StateFlow<Set<String>>`,
  and `loadEventTiming(key)` which computes one event's timing lazily (depth 3, `UI_EXACT_CFG`,
  `near = now`, current place lat/lon for the Lagna minute per Reader 5 ~l.10002-10005). It runs on
  its own `timingMutex` so a long sweep never blocks `loadDay`/`loadLife` on `mutex`; results are
  cached per rule and cleared on `rebuild()` (profile change).
- `LifeScreen.kt`: the expanded event card now carries an "Exact times · Jupiter, Sun, Moon, Lagna"
  section - one row per window (body pill, start-end, dasha chain, sign/star/sub zone, precision,
  significator strength, past/running-now marker) with the tightest window marked "best"
  (`bestExact`). It states the rule in one line and cites Reader 5 ~l.9970-10040, ~l.2535-2538,
  ~l.11170-11180. Denial/mixed promises show "Exact timing needs a promised event".
- Not changed: no rule, no `rules.json` edit, no `scan()` change, so `golden.json` is unaffected by
  this session (it stays stale from Sessions 4/6 until the user regenerates it).
- Still open, now reachable in the UI but unimplemented: the E-Zine three-level transit form
  ("the dasa lord transited in the star of Mercury... the bhukthi lord transited in the star of
  Jupiter, anthara lord... the anthara lord Jupiter transited in the star of Mercury... **the Sun
  transited in the sign of dasa lord and Moon is transited in the star of Jupiter**", KP-E-Zine
  Sept 2019 ~l.615196-615199; and "The dasa lord Saturn is transiting in the star of Venus, 12
  significator... the anthara lord Rahu is transiting in the star of Jupiter, 3rd/4th lord... On
  9-2-2019 the luminary sun is transiting in the sign of dasa lord Saturn. The Moon... should be
  moved to the joint area of Mercury-Rahu... Moon enters this area on 15-2-2019 which tallies with
  the arrival of my granddaughter", ~l.583104-583117). Jupiter's own gate: "Jupiter should transit
  in the star of the lord of the 12th house to the 7th i.e. 6th house and the sub of the second or
  11th house" (Reader 3 ~l.13624-13630).
- Citation defects found in earlier sessions, still to correct:
  - `"slow planets give the month"` (KpTiming.kt `LORD_SOURCE`, kp_timing.py) is attributed to
    Reader 5 ~l.10026-10038, which does not state it; `tools/reference/HANDOFF.md` admits the rule
    could not be found in the Readers. Either find a source or reword it as an engine convention.
  - HANDOVER.md TODO 2 cites "Reader 3 ~l.20002-20004" for the three-level form; those lines are a
    house-signification chart ("Belt-7th house"), not the transit rule. Real sources are the two
    E-Zine passages quoted above.

### Session 9 (2026-10-24): Top-3 life timeline + first job / government job

No build and no install were run.

- `rules.json` (both copies, kept identical): added `first_job` (10th CSL, 2/6/10/11, denial
  1/5/9; Reader 3 ~l.11964-11965, Reader 6 ~l.18260-18262) and `first_government_job` (10th CSL,
  2/6/10/11, denial 8; KP-E-Zine Jun 2020 ~l.727284-727310 and Jun 2021 ~l.95631-95695). The
  existing `first_salary` now reads as first salary/first income (Reader 3 ~l.22345-22420).
  Event count is 51. `golden.json` is unchanged by this but remains stale from Sessions 4/6.
- `LifeScreen.kt`: the life list is now **Top 3 time ranges**, ranked by the Reader 3
  ~l.19169-19175 A > B > C > D significator order of the running dasha lords; events whose
  parent is not promised (or whose window falls outside the parent) are left out. Each top card
  shows only its best window (one date range) and its `plain` rule ("why") while collapsed; the
  three cards are displayed chronologically. The remaining events sit behind "Show all N events",
  so the default timeline is short.
- No engine, golden or daily-scoring change.

### Session 10 (2026-10-24): banking, private job, degrees, regular income

No build and no install were run. Work is on the `deepseek` branch.

- `rules.json` (both copies, identical): event count is now 56. Added `banking_job`
  (2nd CSL; 2/6/10/11 support, 1/5/9 denial, 8 alternative; KP-E-Zine Jan2015-Oct2020
  ~l.156435-156516, ~l.157606-157647), `first_private_job` (10th CSL; 6/7; KP-E-Zine
  Nov2020-Dec2021 ~l.95679-95681), `bachelor_degree` (4th CSL; any of 4/9/11; Jyotish
  Astro Secrets & KP Part5 ~l.3786-3843, ~l.4026-4052), `master_degree` (9th CSL; any of
  4/9/11; Part5 ~l.4474-4504) and `regular_income` (2nd CSL; any of 2/6/7/10/11;
  KP-E-Zine Feb2017 ~l.315600-315608, Feb2019 ~l.542323-542334).
- `KpRules.kt` and `tools/reference/kp_rules.py`: added `any_support`, which accepts one
  matching house instead of requiring every support house. Python compiles.
- `LifeScreen.kt`: `bestWindow` now picks the first window at or after age 18, so a degree
  shows its actual study window instead of a later repeat; past windows remain labelled
  "past". The top-3 list still uses the Reader 3 A>B>C>D order.
- Sample-chart outcome from the existing golden chart data (pure-Python scan, no Swiss
  Ephemeris needed): `bachelor_degree` strong (2018-09-20 to 2023-12-21), `master_degree`
  denied (9th CSL Mercury has 1/2/5, none of 4/9/11), `banking_job` mixed (2nd CSL Mercury
  has 2 but also denies 1/5), `first_private_job` mixed (10th CSL Sun has 6, not 7),
  `regular_income` strong (2023-12-21 to 2026-07-09; next 2038-09-08 to 2040-12-14).
- `golden.json` remains stale and must be regenerated; `pyswisseph` is missing locally
  (`ModuleNotFoundError: No module named 'swisseph'`), so the reference generator cannot run
  here.
