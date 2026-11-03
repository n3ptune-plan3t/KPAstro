# KP Astro tools

The application and calculation engine are Kotlin-only. This directory contains the host regression harness and supporting test data.

## Host regression check

Run:

```sh
sh tools/host-check/run.sh
```

Requirements: a JDK with native headers, `gcc`, and `kotlinc` on PATH.

The script:
1. Builds the host JNI library from the vendored Swiss Ephemeris C sources.
2. Compiles the exact Kotlin engine sources used by the Android app.
3. Runs `GoldenCheck.kt` against the checked-in `tools/host-check/golden.json` fixture and the canonical `app/src/main/assets/rules.json`.

The fixture is a reviewed regression baseline, not an auto-generated CI artifact. Change it only when a behaviour change is intentional, supported by the KP source texts, and accompanied by updated tests.

