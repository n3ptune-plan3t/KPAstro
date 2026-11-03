# KP Astro tools handover

The current project handover is maintained in the repository-root `HANDOVER.md`.

The Python reference implementation and its generator have been removed. The Kotlin engine under the root `app/` directory is the sole implementation. Use `tools/host-check/run.sh` to run the Kotlin regression suite against `tools/host-check/golden.json` and the canonical `app/src/main/assets/rules.json`.

Do not recreate a Python engine or Python-based CI workflow. Follow the root `AGENTS.md` and `HANDOVER.md`.
