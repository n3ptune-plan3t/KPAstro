# KP Astrology engine - handoff prompt (paste everything below the line into the new LLM, and attach the files)

---

You are continuing a project I started with another assistant. Read this fully, then ask me for any missing files before coding.

## Goal
An **Android-only, fully offline, open-source** app based on KP (Krishnamurti Paddhati) astrology, giving daily and long-term indications. **Logic comes first, app later.** Plan: (1) finish and validate the logic in a Python reference engine; (2) generate test vectors from it; (3) port to Kotlin (Swiss Ephemeris via NDK/JNI, AGPL is acceptable because the app is open source); (4) Jetpack Compose UI. Be honest in wording: output is "indications per KP rules", not guarantees; astrology predictions are not scientifically validated, but the astronomy and rule application must be exact and testable.

## Files I attach
- `kp_core.py` - ephemeris (pyswisseph, Moshier, KP ayanamsa SIDM_KRISHNAMURTI), Placidus cusps, star/sub/sub-sub lord table, 4-level significators, Rahu/Ketu agency, Vimshottari (MD/AD/PD...) with 365.25-day year, sunrise-based day lord, ruling planets.
- `kp_rules.py` + `rules.json` - promise (cusp sub-lord), dasha-window scan, daily Moon-window outlook, `ruling_match()`, `transit_score()`, `rank_days()`.
- Reference books (my zip `kp-readers.zip`, txt/djvu OCR): KP Readers 1-6, KP Ezines, Navratnamala, etc. **These are the authority. Grep them; do not rely on memory or web blogs when they disagree.** OCR is noisy.

## Decisions already made (keep unless a Reader contradicts them)
- KP ayanamsa, Placidus houses, mean node for Rahu (configurable), dasha year 365.25 days (JHora and KP Astro default; 360 is a minority convention).
- Day lord changes at local **sunrise**, not midnight (Hindu rising: disc centre, no refraction).
- Rahu/Ketu agency order: conjunction (3 deg 20' orb) > (optional aspect, off) > star lord > sign lord.
- Ruling-planet strength order: Lagna sub > Lagna star > Lagna sign > Moon sub > Moon star > Moon sign > day lord.
- Location: birth place drives the natal chart; **current** place drives ruling planets and sunrise. Planet longitudes, Moon lords and dasha dates are location-independent.
- Event rules live in `rules.json` (support/denial house groups), never hard-coded.

## Regression anchor (must not change unless a bug is proven)
Chart: 1990-05-15 10:30 IST (Asia/Kolkata), Delhi 28.6139 N 77.2090 E -> KP ayanamsa 23.6257; Asc 99.9815 (Cancer, Saturn/Venus); Moon 269.9391 (Sun/Rahu/Saturn); Rahu 287.7256; 7th cusp sub-lord Venus. Use this as the first Kotlin test vector.

## Second regression anchor (user's own chart; coordinates approximate)
21-11-2000 18:12 IST, Nalbari (26.4446 N, 91.4373 E): KP ayanamsa 23.7727; Asc 01d47'40" Gemini (Mercury/Mars/Mercury/Saturn); Moon 12d29'40" Virgo (Mercury/Moon/Rahu/Saturn, star Moon = Hasta); Sun 05d44'34" Scorpio; Rahu (mean) 24d03'37" Gemini H1 (Mercury/Jupiter/Mercury); Moon dasha began 08-01-1999; running Rahu-Ketu-Sun on 2026-10-15. Independent check with `ephem` (VSOP87): all planets within 0.006 deg, Asc within 0.005 deg. Cusp sub-lords flip within -1..+9 min of birth time (H10 Sun: -1/+3 min) -> add a birth-time sensitivity report to the app. Rahu node: Reader 6 ~l.11900 says Rahu's motion is always retrograde (true of the mean node only); mean vs true changes this chart's Rahu/Ketu sub-lord (Mercury vs Saturn). Mean node kept.

## Verified against the Readers so far
- Reader 4: significators whose sub-lord connects with 2, 7, 11 are useful; if the sub is lord of 1, 6, 10 and signifies 12, marriage cannot take place (so marriage denial = 1, 6, 10, 12 now in rules.json); ruling planets at the moment of judgement must agree.
- Reader 5 (transits), ~line 2626: for a running Dasha-Bhukti pair (e.g. Sun-Mars), the result is enjoyed when the Sun transits **star of one lord and sub of the other** (Mars star Sun sub, or Sun star Mars sub), which pins a date inside the period. **My current `transit_score()` counts star-or-sub membership separately and is therefore too loose - replace it with this exact star/sub pairing rule.**

## Progress since the first handoff (done by previous assistant)
- `rules.json` rebuilt from the Readers: 9 events (marriage, childbirth, job, retirement, higher_education, study_abroad, foreign_travel, property_purchase, property_sale), each with `status` (verified/partial/unverified) and a `source` line reference into the OCR txt files. Verified: marriage 2-7-11 / denial 1-6-10-12 (R4 ~l.7160); job 2-6-10(-11) / end of service 1-5-9 (R3 ~l.11964); education 4-9-11 / ends 3-5-8 (R3 ~l.16004); property purchase 4-11-12 (alt 2-4-11) / disposal 3-5-10 (R3 ~l.14535-14650); children 2-5-11 (R4 ~l.13229; 11th sub-lord for male child, 5th for female). Unverified: childbirth denial 1-4-10, foreign_travel denial 2-4-11.
- `kp_rules.py` now has `transit_pin()` and `pin_period()` implementing Reader 5 ~l.2626 (Sun/Moon star-of-one-lord + sub-of-the-other pairing). Demo: job, Rahu-Sun antara -> Sun in Rahu star Sun sub on 2026-11-04 (16h) and Sun star Rahu sub on 2027-01-14..16. `transit_score()` is kept only for comparison; prefer `pin_period()`.
- KP ayanamsa sanity check: engine gives 22d53'39" for 1938-01-01, consistent with Reader 1 ~l.4415 (any ayanamsa below 22d44' for 1938 gives wrong results).
- Reader map: R1 casting the horoscope (time conversion, ayanamsa, worked charts); R2 fundamentals; R3 predictive (house/conveyance l.14082-14830, education l.16000, study abroad l.19011-19170, profession, business); R4 marriage/children (l.7160, 12827-13240); R5 transits (l.1174-1186 aspect 16-degree rule, l.2626 Sun pinning, l.3914 slow planets); R6 horary.

- NEW `learn_sub_scores()` / `lucky_windows()` in kp_rules.py: Reader 5 ~l.11770-11850 "lucky time" method. The person keeps a diary of timed wins/losses; for each span we record the running Moon SUB-lord; per-sub averages then tag future Moon-sub windows lucky/unlucky. This is the KP-sanctioned, personalised **daily layer** (fully offline, learns on-device) and should replace the heuristic `day_outlook()` tags. Tested for plumbing only with a synthetic diary (not evidence for astrology).
- Reader 5 ~l.1700-1775 explicitly rejects traditional gochar (sign-based transit) rules as useless; do NOT add Parashari transit rules (Jupiter 5/9 from lord etc.) to the KP engine. Reader 3 ~l.13611: Jupiter must transit the star of the relevant house lord and the sub of the 2nd/11th house (use for slow-planet gating once the exact wording is confirmed).
- Moon sub durations: Reader 5 divides the Moon's actual star-transit time in Vimshottari proportion (12 min per dasha-year per 24h); our longitude-based windows are equivalent.

## TODO, in priority order
1. (Sun pairing done.) Now check Reader 5 for the equivalent Moon (hours) and Jupiter/Saturn (months) rules and the "16 degrees" aspect-duration rule near line 1174. Replace additive scoring with a hierarchical funnel: dasha chain -> speed/portion of period (verify the "slow planets give later portion" rule in the Readers; I could not find it in Reader 3) -> slow-transit gate -> Sun (days) -> Moon (hours) -> ruling-planet check. No invented numeric weights.
2. Ruling planets belong to the moment of judgement: add a **snapshot** feature (store RP when the user asks, later check that they repeat at fulfilment) plus a minute-by-minute sweep for a chosen day to find times when the Lagna/Moon lords match.
3. Validate the engine against **worked example charts in Reader 1 (casting the horoscope) and Reader 6 (horary)**: positions, cusps, sub-lords, dasha balances. Fix any mismatch (check sub table, ayanamsa, node type, birth-time/timezone handling).
4. Replace from-memory event groups (foreign travel, property, higher education, childbirth, job) in `rules.json` with Reader-verified support/denial groups; add more events (Reader 4 covers marriage and children).
5. Rework the daily layer to follow KP practice (Moon star/sub lords, ruling planets) instead of my heuristic tags.
6. Build a **backtesting harness**: charts with known event dates -> does the engine's window contain the date? Report hit rate and window width.
7. Generate golden test vectors (JSON) for the Kotlin port, then port module by module.

## Working rules
- Quote the Reader page/line for every rule you add. If a rule is uncertain, put it behind a config flag and say so.
- Keep functions small and pure so they port to Kotlin easily; no network; no global state beyond constants.
- Don't state astrological claims as facts in user-facing text.
- Run the demos (`python kp_core.py`, `python kp_rules.py`, needs `pip install pyswisseph`) after every change and compare with the regression anchor.

First, confirm you can read the files and the Readers, then do TODO 1 and 3 and show me the diffs and test results.
