# KP Astro — Agent Notes

## Source of truth

All astrology rules and logic MUST come from the reference texts in `kp-readers/`
(e.g. KP Reader 1-6, KP E-Zine compilations, KP Navaratnamala, KP Sublord SPK,
Astrology for Beginners, Jyotish Astro Secrets & KP). Do not invent, guess, or
improvise astrological rules. When implementing a rule, cite the source book/line.

## Branching

Work happens on the `deepseek` branch only. Never edit `main`/`master`.

## No local builds

Do NOT compile or build in this environment. Only edit code. All compiling and
building is done by the user via GitHub Actions.

## No installs

Do NOT install or download any programs, packages, or apps without asking first.
If a tool is needed, ask the user for permission.


## Engine and regression policy

The Kotlin implementation under `app/src/main/java/dev/plawan/kp/engine/` is the sole engine implementation. Do not add or restore a Python reference engine, Python golden generator, or Python runtime dependency. Keep regression fixtures in `tools/host-check/golden.json`; host-check must compile and test Kotlin directly against the app's canonical `app/src/main/assets/rules.json`. Do not regenerate or rewrite fixtures automatically in CI.
