"""kp_rules.py - rules engine on top of kp_core: promise, dasha timing, daily outlook.

Procedure (as taught by KP schools):
 1. PROMISE : cusp sub-lord of the event house signifies the supporting houses, not the denial houses.
 2. TIMING  : running dasha / bhukti / antara lords each signify supporting houses (and their own
              sub-lords give 'permission'), together covering the primary house.
 3. DAILY   : transit Moon star/sub lord read through the NATAL significations.
The verdict thresholds are transparent heuristics, not canon - tune them in one place (promise()).
"""
from __future__ import annotations
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
import swisseph as swe
import kp_core as kp

RULES_PATH = Path(__file__).with_name("rules.json")


def load_rules(path: Path = RULES_PATH) -> dict:
    d = json.loads(Path(path).read_text())
    return {k: v for k, v in d.items() if not k.startswith("_")}


def _houses(ch: kp.Chart, planet: str) -> set[int]:
    return set(kp.planet_houses_signified(ch, planet))


# ------------------------------------------------------------------ 1. promise
def _group(rule: dict) -> set[int]:
    return set(rule["support"]) | set(rule.get("any_support", []))


def promise(ch: kp.Chart, rule: dict) -> dict:
    csl = ch.cusps[rule["cusp"] - 1].sub_lord
    hs = _houses(ch, csl)
    sup = sorted(hs & _group(rule))
    den = sorted(hs & set(rule["denial"]))
    if rule.get("any_support"):
        if not sup:
            verdict = "denied"
        elif not den:
            verdict = "strong"
        elif len(sup) > len(den):
            verdict = "promised"
        else:
            verdict = "mixed"
    elif len(sup) == len(rule["support"]) and not den:
        verdict = "strong"
    elif len(sup) >= 2 and len(sup) > len(den):
        verdict = "promised"
    elif sup:
        verdict = "mixed"
    else:
        verdict = "denied"
    return {"cusp_sub_lord": csl, "houses": sorted(hs), "support": sup, "denial": den, "verdict": verdict}


# ------------------------------------------------------------------ 2. timing
def lord_check(ch: kp.Chart, planet: str, rule: dict) -> dict:
    hs = _houses(ch, planet)
    group = _group(rule)
    sup, den = sorted(hs & group), sorted(hs & set(rule["denial"]))
    sub = ch.planets[planet].sub_lord
    shs = _houses(ch, sub)
    permission = len(shs & set(rule["denial"])) <= len(shs & group)
    return {"planet": planet, "support": sup, "denial": den, "sub_lord": sub, "permission": permission}


def period_check(ch: kp.Chart, rule: dict, lords: tuple[str, ...]) -> tuple[bool, list[dict]]:
    det = [lord_check(ch, l, rule) for l in lords]
    union = set().union(*[set(d["support"]) for d in det])
    need = 1 if rule.get("any_support") else min(2, len(rule["support"]))
    ok = (all(d["support"] and d["permission"] for d in det)
          and rule["primary"] in (union | set().union(*[_houses(ch, l) for l in lords]))
          and len(union) >= need)
    return ok, det


def scan_hits(ch: kp.Chart, rule: dict, start: datetime, end: datetime, depth: int = 2) -> list[kp.Period]:
    """Unmerged dasha periods in [start, end) whose chain fits the rule (depth 3 -> PD windows)."""
    start, end = start.astimezone(timezone.utc), end.astimezone(timezone.utc)
    hits: list[kp.Period] = []
    cache: dict[str, dict] = {}

    def lc(l: str) -> dict:
        if l not in cache:
            cache[l] = lord_check(ch, l, rule)
        return cache[l]

    def walk(p: kp.Period) -> None:
        if p.end <= start or p.start >= end:
            return
        # prune: every lord in the chain must individually support and permit
        if not all(lc(l)["support"] and lc(l)["permission"] for l in p.lords):
            return
        if len(p.lords) == depth:
            if period_check(ch, rule, p.lords)[0]:
                hits.append(p)
            return
        for s in kp.sub_periods(p):
            walk(s)

    for md in kp.mahadashas(ch):
        walk(md)
    return sorted(hits, key=lambda x: x.start)


def scan(ch: kp.Chart, rule: dict, start: datetime, end: datetime, depth: int = 2) -> list[dict]:
    """Windows in [start, end) where the dasha chain (depth 1=MD, 2=+AD, 3=+PD) fits the rule."""
    start, end = start.astimezone(timezone.utc), end.astimezone(timezone.utc)
    merged: list[dict] = []
    for p in scan_hits(ch, rule, start, end, depth):
        s, e = max(p.start, start), min(p.end, end)
        if merged and merged[-1]["end"] == s:           # merge only exactly-adjacent windows
            merged[-1]["end"] = e
            merged[-1]["lords"].append(p.lords)
        else:
            merged.append({"start": s, "end": e, "lords": [p.lords]})
    return merged


def _overlap(a: dict, b: dict) -> bool:
    return a["start"] < b["end"] and b["start"] < a["end"]


def life_range(ch: kp.Chart, now: datetime | None = None) -> dict:
    """Scan span for the life outlook: birth -> end of the NEXT mahadasha after the one running at `now`."""
    now = now or datetime.now(timezone.utc)
    mds = kp.mahadashas(ch)
    i = next((k for k, m in enumerate(mds) if m.start <= now < m.end), len(mds) - 1)
    nxt = mds[min(i + 1, len(mds) - 1)]
    return {"start": ch.dt_utc, "end": nxt.end, "current": mds[i].lords[0], "next": nxt.lords[0]}


def life_report(ch: kp.Chart, rules: dict, years: int = 20, now: datetime | None = None, depth: int = 2, span: dict | None = None) -> dict:
    """Long-term layer: promise + upcoming windows for every event.

    Events may declare `depends_on` (a parent event key) in rules.json. This is a SOFT gate:
    a child's windows are always kept, but tagged `parent_active` only when they nest inside a
    parent window, and `parents` lists the parent keys that window falls under. Pass 1 builds all
    promises/windows independently; pass 2 annotates, so the result does not depend on dict order.
    """
    now = now or datetime.now(timezone.utc)
    start = span["start"] if span else now
    end = span["end"] if span else now + timedelta(days=365.25 * years)
    base: dict[str, dict] = {}
    for name, rule in rules.items():
        pr = promise(ch, rule)
        wins = scan(ch, rule, start, end, depth) if pr["verdict"] in ("strong", "promised") else []
        base[name] = {"promise": pr, "windows": wins}
    for name, rule in rules.items():
        deps = list(rule.get("depends_on", []))
        gates = []
        for pk in deps:
            p = base.get(pk)
            if p is None:
                continue
            pv = p["promise"]["verdict"]
            gates.append({"key": pk, "verdict": pv, "promised": pv in ("strong", "promised")})
        for w in base[name]["windows"]:
            hits = [pk for pk in deps if any(_overlap(ow, w) for ow in base.get(pk, {}).get("windows", []))]
            w["parent_active"] = (not deps) or bool(hits)
            w["parents"] = hits
        base[name]["parents"] = gates
    return base


# ------------------------------------------------------------------ 3. daily
def _moon_lon(jd: float) -> float:
    swe.set_sid_mode(swe.SIDM_KRISHNAMURTI)
    xx, _ = swe.calc_ut(jd, swe.MOON, swe.FLG_MOSEPH | swe.FLG_SIDEREAL)
    return xx[0]


def moon_windows(day_start: datetime, step_min: int = 5) -> list[dict]:
    """Transit Moon star/sub-lord windows from day_start for 24h (day_start must be tz-aware)."""
    out: list[dict] = []
    t, end = day_start, day_start + timedelta(days=1)
    while t < end:
        u = t.astimezone(timezone.utc)
        jd = swe.julday(u.year, u.month, u.day, u.hour + u.minute / 60 + u.second / 3600)
        _, star, sub, _ = kp.lords_at(_moon_lon(jd))
        if out and out[-1]["star"] == star and out[-1]["sub"] == sub:
            out[-1]["end"] = t + timedelta(minutes=step_min)
        else:
            out.append({"start": t, "end": t + timedelta(minutes=step_min), "star": star, "sub": sub})
        t += timedelta(minutes=step_min)
    return out


def day_outlook(ch: kp.Chart, rules: dict, day_start: datetime, depth: int = 3) -> dict:
    """Daily layer (heuristic): dasha gate per area + transit-Moon windows read via natal significations."""
    noon = day_start + timedelta(hours=12)
    run = kp.periods_at(ch, noon, depth)
    areas = {}
    for name, rule in rules.items():
        pr = promise(ch, rule)
        ok, _ = period_check(ch, rule, run.lords)
        areas[name] = {"promise": pr["verdict"], "dasha_active": ok}
    wins = moon_windows(day_start)
    for w in wins:
        hs = _houses(ch, w["sub"])          # sub-lord decides; star lord alone is too permissive
        w["tags"] = {}
        for name, rule in rules.items():
            if areas[name]["promise"] not in ("strong", "promised"):
                continue
            s, d = len(hs & set(rule["support"])), len(hs & set(rule["denial"]))
            if s >= 2 and s > d and areas[name]["dasha_active"]:
                w["tags"][name] = "favourable"
            elif d >= 2 and d > s:
                w["tags"][name] = "caution"
    return {"dasha": run.lords, "areas": areas, "moon_windows": wins}


# ------------------------------------------------------------------ 4. confirmation: ruling planets + transits
def event_significators(ch: kp.Chart, rule: dict) -> set[str]:
    """Planets that signify the event: more support houses than denial houses, at least one support."""
    out = set()
    for p in ch.planets:
        hs = _houses(ch, p)
        s, d = len(hs & set(rule["support"])), len(hs & set(rule["denial"]))
        if s and s > d:
            out.add(p)
    return out


def ruling_match(ch: kp.Chart, rule: dict, at: datetime, lat: float, lon: float, chain: tuple[str, ...]) -> dict:
    """Ruling planets at a moment (AT THE CURRENT LOCATION) vs the event significators and dasha chain."""
    rp = kp.ruling_planets(at, lat, lon)
    ordered = rp["ordered"] + rp["derived"]
    sig = event_significators(ch, rule)
    return {"ruling": ordered,
            "event_hits": [p for p in ordered if p in sig],
            "chain_hits": [p for p in ordered if p in chain],
            "score": 2 * len([p for p in ordered if p in chain]) + len([p for p in ordered if p in sig])}


def _jd(dt: datetime) -> float:
    u = dt.astimezone(timezone.utc)
    return swe.julday(u.year, u.month, u.day, u.hour + u.minute / 60 + u.second / 3600)


def _lon(jd: float, body: int) -> float:
    swe.set_sid_mode(swe.SIDM_KRISHNAMURTI)
    xx, _ = swe.calc_ut(jd, body, swe.FLG_MOSEPH | swe.FLG_SIDEREAL)
    return xx[0]


def transit_score(chain: tuple[str, ...], day_noon: datetime, lat: float, lon: float) -> dict:
    """One-day confirmation score: slow transits (Sun/Jupiter/Saturn) whose star or sub lord is a
    dasha-chain lord, Moon windows where both star and sub are chain lords, and a chain-lord day lord."""
    jd = _jd(day_noon)
    hits: dict[str, list[str]] = {}
    for name, body in (("Sun", swe.SUN), ("Jupiter", swe.JUPITER), ("Saturn", swe.SATURN)):
        _, star, sub, _ = kp.lords_at(_lon(jd, body))
        h = [x for x in (star, sub) if x in chain]
        if h:
            hits[name] = h
    day0 = day_noon.replace(hour=0, minute=0, second=0, microsecond=0)
    moon = [(w["start"], w["end"]) for w in moon_windows(day0, 20) if w["star"] in chain and w["sub"] in chain]
    dl = kp.sunrise_day_lord(day_noon, lat, lon)
    score = len(hits) + (1 if moon else 0) + (1 if dl in chain else 0)
    return {"score": score, "slow_transits": hits, "moon_windows": moon, "day_lord": dl}


def rank_days(ch: kp.Chart, rule: dict, start: datetime, end: datetime, lat: float, lon: float,
              depth: int = 3, top: int = 10) -> list[dict]:
    """Narrow dasha windows (default to pratyantar level) to the best-confirmed days.
    lat/lon = where the person is now (sunrise/day lord); natal data comes from `ch`."""
    out = []
    for w in scan(ch, rule, start, end, depth):
        d = w["start"].astimezone(start.tzinfo).replace(hour=12, minute=0, second=0, microsecond=0)
        while d < w["end"].astimezone(start.tzinfo):
            if start <= d < end:
                run = kp.periods_at(ch, d, depth)
                if run and period_check(ch, rule, run.lords)[0]:
                    t = transit_score(run.lords, d, lat, lon)
                    t.update({"date": d.date(), "chain": run.lords})
                    out.append(t)
            d += timedelta(days=1)
    out.sort(key=lambda x: (-x["score"], x["date"]))
    return out[:top]


# ------------------------------------------------------------------ 5. transit pinning (Reader 5 ~l.2626)
def transit_pin(body: int, lord_a: str, lord_b: str, start: datetime, end: datetime,
                step_min: int = 60, ketu: bool = False) -> list[dict]:
    """Windows in [start, end) where `body` transits the star of one dasha/bhukti lord AND the sub of the
    other (star=A,sub=B or star=B,sub=A). Reader 5: Sun-Mars / Mars-Sun period -> the result is enjoyed
    when the Sun transits 'Mars star Sun sub or Sun star Mars sub'. Sun pins days, Moon pins hours."""
    want = {(lord_a, lord_b), (lord_b, lord_a)}
    out: list[dict] = []
    t = start
    while t < end:
        lon = _lon(_jd(t), body)
        if ketu:
            lon = (lon + 180.0) % 360.0
        _, star, sub, _ = kp.lords_at(lon)
        if (star, sub) in want:
            if out and out[-1]["end"] == t and (out[-1]["star"], out[-1]["sub"]) == (star, sub):
                out[-1]["end"] = t + timedelta(minutes=step_min)
            else:
                out.append({"start": t, "end": t + timedelta(minutes=step_min), "star": star, "sub": sub})
        t += timedelta(minutes=step_min)
    return out


def pin_period(ch: kp.Chart, rule: dict, start: datetime, end: datetime, body: int = swe.SUN) -> list[dict]:
    """For every antardasha window that passes the rule, return the exact transit windows of `body`
    (default Sun) pairing the dasha lord with the bhukti lord. Replaces the loose transit_score()."""
    res = []
    for w in scan(ch, rule, start, end, depth=2):
        for lords in w["lords"]:
            md, ad = lords[0], lords[1]
            for p in kp.sub_periods(next(m for m in kp.mahadashas(ch) if m.lords == (md,))):
                if p.lords == (md, ad) and p.end > start and p.start < end:
                    for x in transit_pin(body, md, ad, max(p.start, start), min(p.end, end)):
                        x.update({"chain": (md, ad)})
                        res.append(x)
    return res


# Reader 5 ~l.2535-2537 and ~l.2580-2583: with dasa lord X and bhukti lord Y the matters of both materialise 'when either of
# them or either of the luminaries transit in the zone' (star X sub Y or star Y sub X). Pinning bodies = the two period lords
# themselves + Sun (pins days) + Moon (pins hours, ~l.11165-11175). Rahu/Ketu = mean node / +180. Mirrors KpRules.pinPeriodAll.
_PIN_IDS = {"Sun": swe.SUN, "Moon": swe.MOON, "Mars": swe.MARS, "Mercury": swe.MERCURY, "Jupiter": swe.JUPITER,
            "Venus": swe.VENUS, "Saturn": swe.SATURN, "Rahu": swe.MEAN_NODE, "Ketu": swe.MEAN_NODE}


def pin_bodies_for(md: str, ad: str) -> list[dict]:
    names = list(dict.fromkeys([md, ad, "Sun", "Moon"]))
    return [{"name": n, "id": _PIN_IDS[n], "step_min": 5 if n == "Moon" else 60, "ketu": n == "Ketu"} for n in names]


def pin_period_all(ch: kp.Chart, rule: dict, start: datetime, end: datetime) -> list[dict]:
    res = []
    for w in scan(ch, rule, start, end, depth=2):
        for lords in w["lords"]:
            md, ad = lords[0], lords[1]
            for p in kp.sub_periods(next(m for m in kp.mahadashas(ch) if m.lords == (md,))):
                if p.lords == (md, ad) and p.end > start and p.start < end:
                    for pb in pin_bodies_for(md, ad):
                        for x in transit_pin(pb["id"], md, ad, max(p.start, start), min(p.end, end), pb["step_min"], pb["ketu"]):
                            x.update({"chain": (md, ad), "body": pb["name"]})
                            res.append(x)
    res.sort(key=lambda x: x["start"])
    return res


# ------------------------------------------------------------------ 6. personal lucky-time method (Reader 5 ~l.11770-11850)
def moon_sub_at(t: datetime) -> tuple[str, str]:
    _, star, sub, _ = kp.lords_at(_lon(_jd(t), swe.MOON))
    return star, sub


def learn_sub_scores(diary: list[tuple[datetime, datetime, float]], step_min: int = 10) -> dict[str, dict]:
    """Reader 5 method: the person logs spans of time with an outcome (+ gain / - loss). For each span we note
    which Moon SUB-lord was running and accumulate minutes and outcome. Result: per-sub average outcome and
    minutes of evidence. diary entries are (start, end, outcome), tz-aware."""
    acc: dict[str, dict] = {}
    for start, end, outcome in diary:
        t = start
        while t < end:
            _, sub = moon_sub_at(t)
            a = acc.setdefault(sub, {"minutes": 0, "sum": 0.0})
            a["minutes"] += step_min
            a["sum"] += outcome * step_min
            t += timedelta(minutes=step_min)
    return {k: {"minutes": v["minutes"], "avg": v["sum"] / v["minutes"]} for k, v in acc.items()}


def lucky_windows(scores: dict[str, dict], day_start: datetime, min_minutes: int = 120,
                  min_avg: float = 0.3) -> list[dict]:
    """Tag the day's Moon sub-lord windows as lucky/unlucky from the learned per-sub scores.
    Subs with too little evidence (min_minutes) are left untagged."""
    out = []
    for w in moon_windows(day_start, 10):
        sc = scores.get(w["sub"])
        tag = None
        if sc and sc["minutes"] >= min_minutes:
            tag = "lucky" if sc["avg"] >= min_avg else "unlucky" if sc["avg"] <= -min_avg else None
        out.append({**w, "tag": tag, "avg": sc["avg"] if sc else None})
    return out


# ------------------------------------------------------------------ 7. birth-time sensitivity
def birth_time_tolerance(dt: datetime, lat: float, lon: float, max_min: int = 120) -> dict:
    """For each cusp (and the planets) report how many minutes the birth time can move earlier/later
    before its SUB-lord changes. KP is sub-lord driven, so a verdict that flips within a minute or two
    is not trustworthy until the birth time is verified or rectified (Reader 1 / ruling planets)."""
    def subs(t: datetime) -> list[str]:
        c = kp.compute_chart(t, lat, lon)
        return [x.sub_lord for x in c.cusps] + [p.sub_lord for p in c.planets.values()]
    base = subs(dt)
    names = [f"H{i+1}" for i in range(12)] + list(kp.compute_chart(dt, lat, lon).planets)
    res = {n: {"sub": b, "before": None, "after": None} for n, b in zip(names, base)}
    for m in range(1, max_min + 1):
        for sign, key in ((-1, "before"), (1, "after")):
            cur = subs(dt + timedelta(minutes=sign * m))
            for n, b, c in zip(names, base, cur):
                if res[n][key] is None and c != b:
                    res[n][key] = m
    return res


# ------------------------------------------------------------------ demo
if __name__ == "__main__":
    from zoneinfo import ZoneInfo
    tz = ZoneInfo("Asia/Kolkata")
    ch = kp.compute_chart(datetime(1990, 5, 15, 10, 30, tzinfo=tz), 28.6139, 77.2090)
    rules = load_rules()
    now = datetime(2026, 10, 15, 9, 0, tzinfo=tz)
    rep = life_report(ch, rules, years=15, now=now)
    for name, r in rep.items():
        p = r["promise"]
        print(f"{name:17}{p['verdict']:9} CSL={p['cusp_sub_lord']:8} sup={p['support']} den={p['denial']}")
        for w in r["windows"][:3]:
            print(f"    {w['start']:%Y-%m-%d} -> {w['end']:%Y-%m-%d}  {w['lords'][0]}")
    d = day_outlook(ch, rules, datetime(2026, 10, 15, 0, 0, tzinfo=tz))
    print("Dasha today:", d["dasha"], d["areas"])
    for w in d["moon_windows"]:
        print(f"  {w['start']:%H:%M}-{w['end']:%H:%M} Moon {w['star']}/{w['sub']} {w['tags']}")
