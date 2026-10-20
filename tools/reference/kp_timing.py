"""kp_timing.py - exact event timing on top of kp_rules.

Reader 5 ~l.9970-10040: when a promised Dasa-Bhukti-Antara (A-B-C) runs, the sensitive
points are the sign/star/sub positions governed by A, B and C. A transit of the period
lords, the luminaries or the Lagna over one of those points triggers the event.
Reader 5 ~l.2626: the two-level fallback pins only the star of one lord + sub of the other.
Reader 5 ~l.11170-11180 / ~l.2535-2538: the Moon gives the hour, the Sun the day, the
Lagna the minute; slow planets give the month.
Reader 3 ~l.19160-19175: strength order for picking the best window is A > B > C > D
(planet in the star of the occupant > occupant > star of the house lord > house lord).
"""
from __future__ import annotations
from collections import Counter
from datetime import datetime, timedelta, timezone
import swisseph as swe
import kp_core as kp
import kp_rules as kr

SLOW_LORDS = {"Jupiter", "Saturn", "Rahu", "Ketu"}
NODE_BODY = swe.MEAN_NODE if kp.RAHU_MEAN else swe.TRUE_NODE

DEFAULT_CFG = {
    "moon_step_min": 15, "sun_step_min": 30, "asc_step_min": 1,
    "fast_step_min": 360, "slow_step_min": 720,
    "max_periods": 24, "max_hits": 120,
}

THREE_LEVEL_SOURCE = (
    "Reader 5 ~l.9970-10040: when A-B-C runs, the sign/star/sub positions governed by the "
    "three are the sensitive points; their transit by the period lords, the luminaries or "
    "the Lagna triggers the event."
)
TWO_LEVEL_SOURCE = (
    "Reader 5 ~l.2626: the result is felt when one luminary transits the star of one lord "
    "and the sub of the other."
)
MOON_SOURCE = (
    "Reader 5 ~l.11170-11180 / ~l.2626: the Moon gives the hour; it crosses a sensitive "
    "point once in about 27 days."
)
SUN_SOURCE = "Reader 5 ~l.2535-2538: the Sun transits the zone on the day, fixing the date."
ASC_SOURCE = "Reader 5 ~l.11170-11180: within the Moon hour the Lagna gives the exact minute."
LORD_SOURCE = (
    "Reader 5 ~l.10026-10038: the period lords themselves transiting the sensitive point "
    "also trigger the result; slow planets give the month."
)


# ---------------------------------------------------------------- sensitive points
def sensitive_points(lords, depth: int = 3):
    """depth 3 -> rows of the 249-sub table ruled by exactly the same group of lords;
    depth 2 -> ('*', star, sub) pairs, sign ignored (Reader 5 ~l.2626).
    Rahu/Ketu can be star or sub lords in the table, but never sign lords."""
    use = list(lords)[: 3 if depth >= 3 else 2]
    if not use:
        return []
    out = []
    if len(use) >= 3:
        need = Counter(use)
        for start, _end, star, sub in kp.SUB_TABLE:
            sign = kp.SIGN_LORDS[int(start // 30)]
            if Counter((sign, star, sub)) == need:
                pt = (sign, star, sub)
                if pt not in out:
                    out.append(pt)
    elif len(use) == 2:
        for pt in (("*", use[0], use[1]), ("*", use[1], use[0])):
            if pt not in out:
                out.append(pt)
    else:
        out.append(("*", use[0], use[0]))
    return out


def _matches(pt, lords) -> bool:
    sign, star, sub = pt
    return (sign == "*" or sign == lords[0]) and star == lords[1] and sub == lords[2]


def _sig_rank(ch: kp.Chart, planet: str, house: int) -> int:
    sig = ch.significators.get(house, {})
    for level, rank in (("A", 4), ("B", 3), ("C", 2), ("D", 1)):
        if planet in sig.get(level, []):
            return rank
    return 0


# ---------------------------------------------------------------- ephemeris helpers
def _jd(t: datetime) -> float:
    u = t.astimezone(timezone.utc)
    return swe.julday(u.year, u.month, u.day,
                      u.hour + u.minute / 60 + u.second / 3600 + u.microsecond / 3.6e9)


def _body_lords(body: str, jd: float):
    flags = swe.FLG_MOSEPH | swe.FLG_SIDEREAL
    if body == "Rahu":
        lon = swe.calc_ut(jd, NODE_BODY, flags)[0][0]
    elif body == "Ketu":
        lon = swe.calc_ut(jd, NODE_BODY, flags)[0][0] + 180.0
    else:
        lon = swe.calc_ut(jd, kp.BODIES[body], flags)[0][0]
    return kp.lords_at(lon % 360.0)[:3]


def asc_lords(t: datetime, lat: float, lon: float):
    swe.set_sid_mode(swe.SIDM_KRISHNAMURTI)
    _, ascmc = swe.houses_ex(_jd(t), lat, lon, b"P", swe.FLG_SIDEREAL)
    return kp.lords_at(ascmc[0])[:3]


# ---------------------------------------------------------------- scanning
def _scan_lords(body, targets, start, end, step_min, precision, rank, chain, strength,
                rule_key, source, lords_of):
    if end <= start or not targets:
        return []
    if step_min <= 0:
        raise ValueError("step_min must be positive")
    step = timedelta(minutes=step_min)
    out, cur, t = [], None, start
    while t < end:
        lords = lords_of(t)
        nxt = min(t + step, end)
        hit = next((pt for pt in targets if _matches(pt, lords)), None)
        if hit is not None:
            if cur is not None and cur["point"] == hit and cur["end"] == t:
                cur["end"] = nxt
            else:
                if cur is not None:
                    out.append(cur)
                cur = {"start": t, "end": nxt, "precision": precision, "rank": rank,
                       "body": body, "point": hit, "chain": tuple(chain),
                       "rule": rule_key, "strength": strength, "source": source}
        else:
            if cur is not None:
                out.append(cur)
            cur = None
        t += step
    if cur is not None:
        out.append(cur)
    return out


def _refine_asc(base, targets, lat, lon, cfg):
    if lat is None or lon is None:
        return [base]
    out = _scan_lords("Asc", targets, base["start"], base["end"], cfg["asc_step_min"],
                      "minute", 4, base["chain"], base["strength"], base["rule"], ASC_SOURCE,
                      lambda t: asc_lords(t, lat, lon))
    return out or [base]


# ---------------------------------------------------------------- public API
def exact_times(ch: kp.Chart, rule: dict, start: datetime, end: datetime,
                lat: float | None = None, lon: float | None = None,
                depth: int = 3, cfg: dict | None = None, near: datetime | None = None) -> dict:
    cfg = {**DEFAULT_CFG, **(cfg or {})}
    start, end = start.astimezone(timezone.utc), end.astimezone(timezone.utc)
    pr = kr.promise(ch, rule)
    if pr["verdict"] not in ("strong", "promised"):
        return {"rule": rule["key"], "promise": pr, "broad": [], "exact": []}
    broad = kr.scan(ch, rule, start, end, 2)
    use_depth = 3 if depth >= 3 else 2
    all_hits = kr.scan_hits(ch, rule, start, end, use_depth)
    # `near` keeps the periods closest to that instant instead of the earliest ones.
    if near is None:
        hits = all_hits[: cfg["max_periods"]]
    else:
        near = near.astimezone(timezone.utc)
        hits = sorted(all_hits, key=lambda p: abs((p.start - near).total_seconds()))[: cfg["max_periods"]]
        hits.sort(key=lambda p: p.start)
    out = []
    for p in hits:
        chain = tuple(p.lords[:use_depth])
        targets = sensitive_points(chain, use_depth)
        if not targets:
            continue
        strength = sum(_sig_rank(ch, l, rule["primary"]) for l in chain)
        s, e = max(p.start, start), min(p.end, end)
        source = (THREE_LEVEL_SOURCE if use_depth >= 3 else TWO_LEVEL_SOURCE) + " " + LORD_SOURCE

        for m in _scan_lords("Moon", targets, s, e, cfg["moon_step_min"], "hour", 3,
                             chain, strength, rule["key"], MOON_SOURCE,
                             lambda t: _body_lords("Moon", _jd(t))):
            out.extend(_refine_asc(m, targets, lat, lon, cfg))

        for d in _scan_lords("Sun", targets, s, e, cfg["sun_step_min"], "day", 2,
                             chain, strength, rule["key"], SUN_SOURCE,
                             lambda t: _body_lords("Sun", _jd(t))):
            inner = _scan_lords("Moon", targets, d["start"], d["end"], cfg["moon_step_min"],
                                "hour", 3, chain, strength, rule["key"], MOON_SOURCE,
                                lambda t: _body_lords("Moon", _jd(t)))
            if not inner:
                out.extend(_refine_asc(d, targets, lat, lon, cfg))
            else:
                for m in inner:
                    out.extend(_refine_asc(m, targets, lat, lon, cfg))

        for lord in dict.fromkeys(chain):
            if lord in ("Sun", "Moon"):
                continue
            slow = lord in SLOW_LORDS
            step = cfg["slow_step_min"] if slow else cfg["fast_step_min"]
            precision = "month" if slow else "day"
            rank = 1 if slow else 2
            out.extend(_scan_lords(lord, targets, s, e, step, precision, rank, chain,
                                   strength, rule["key"], source,
                                   lambda t, l=lord: _body_lords(l, _jd(t))))

    seen, uniq = set(), []
    for w in out:
        key = (w["start"], w["end"], w["body"], w["point"])
        if key not in seen:
            seen.add(key)
            uniq.append(w)
    uniq.sort(key=lambda w: (w["start"], -w["rank"], w["body"]))
    if near is None:
        exact = uniq[: cfg["max_hits"]]
    else:
        exact = sorted(uniq, key=lambda w: abs((w["start"] - near).total_seconds()))[: cfg["max_hits"]]
        exact.sort(key=lambda w: w["start"])
    return {"rule": rule["key"], "promise": pr, "broad": broad, "exact": exact}


def best_exact(result: dict):
    return sorted(result["exact"], key=lambda w: (-w["rank"], w["start"]))[0] if result["exact"] else None


def next_exact(result: dict, after: datetime):
    fut = [w for w in result["exact"] if w["end"] > after]
    return sorted(fut, key=lambda w: (-w["rank"], w["start"]))[0] if fut else None


if __name__ == "__main__":
    from zoneinfo import ZoneInfo
    IST = ZoneInfo("Asia/Kolkata")
    ch = kp.compute_chart(datetime(2000, 11, 21, 18, 12, tzinfo=IST), 26.4446, 91.4373)
    rules = kr.load_rules()
    now = datetime(2026, 10, 15, 9, 0, tzinfo=IST)
    res = exact_times(ch, rules["job"], now, now + timedelta(days=90),
                      lat=26.4446, lon=91.4373, depth=3, cfg={"max_hits": 8})
    print(res["rule"], res["promise"]["verdict"], "broad", len(res["broad"]), "exact", len(res["exact"]))
    for w in res["exact"]:
        print(w["start"].astimezone(IST).strftime("%Y-%m-%d %H:%M"), w["precision"], w["body"],
              w["point"], w["chain"], "strength", w["strength"])
