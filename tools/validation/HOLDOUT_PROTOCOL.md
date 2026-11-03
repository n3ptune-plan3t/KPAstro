# Holdout validation protocol

## Purpose

Evaluate whether the frozen KPAstro implementation identifies the month of independently documented life events more often, and with less calendar coverage, than a simple baseline. This is retrospective validation, not proof that astrology works or a guarantee of future predictions.

## Prerequisites

Do not create or score the holdout until:
- the 56 event-rule audit is complete;
- partial rules have been corrected or excluded from claims;
- Reader 5 transit logic has regression coverage;
- the scoring code and event-date conventions are frozen;
- the development benchmark and all development outcomes are labelled and excluded from the holdout.

## Case inclusion

Each case must have:
- a public, independently checkable event date with day-level precision;
- a public birth time with source-quality rating and a primary-source description;
- birth location and historically correct local UTC offset;
- a clearly mapped event key from the canonical 56-rule catalogue;
- a direct source URL for birth data and a separate URL for the event date;
- no birth-time rectification using the event being scored.

Prefer Rodden AA (birth record/certificate) for the primary cohort. Keep A/B cases in separate sensitivity cohorts; exclude C/DD/X/unknown times from the primary month-timing score. If one person has multiple events, keep the person grouped in one partition to prevent leakage.

## Partitioning and sealing

- Development data: existing `celebrity-marriages.json` plus any new cases used while changing rules.
- Holdout data: entirely different people and event records. Never copy an event/person from development into holdout.
- Freeze code, event-rule asset, timing configuration, and scoring implementation before opening holdout scores.
- Store the holdout in a separate file and restrict casual edits; record its checksum and creation commit before evaluation.
- If holdout scores are inspected and then code/rules are changed, that holdout is spent. A new holdout is required for a confirmatory result.
- Where the event has a date sequence (e.g. application, selection, appointment), define the exact target event before calculating any chart.

## Scoring

Report all of the following, not only hit rate:
1. **Event-month hit rate:** fraction of all eligible cases whose actual event month is in the candidate set. Cases denied by the natal promise remain in the denominator and count as misses in the end-to-end score.
2. **Candidate coverage/density:** candidate months divided by all months in the forecast horizon. High coverage can make hit rate meaningless.
3. **Baseline:** compare with a simple uniform-month baseline using the same number of candidate months per case; optionally add a seasonal/month-of-year baseline if preregistered.
4. **Lead-time stratification:** report results by time horizon (under one month, one to twelve months, over one year) without changing the timing rules after seeing results.
5. **Event-type stratification:** report each event class separately, sample size, confidence intervals and source-quality cohort.
6. **Promise-stage and timing-stage scores:** report natal-promise classification separately from event-month timing. Do not hide promise failures by scoring only cases already deemed promised.

## Anti-leakage rules

- Do not tune house groups, significator weights, transit sampling, or event mappings on holdout outcomes.
- Do not select celebrity cases because their charts appear to fit the model.
- Do not choose the most favourable marriage/appointment/award date after viewing candidates.
- Do not revise birth times to improve a hit.
- Keep all eligible cases, including misses, and publish exclusions with reasons.
- Treat repeated events from one person as a grouped cluster, not independent observations.

## Interpretation

A hit rate without candidate density and a baseline is insufficient. A method that marks 70% of all months will naturally hit many event months. Even a strong retrospective result is not prospective predictive accuracy. Any scientific claim would require preregistered prospective testing, independent replication, and appropriate controls.
