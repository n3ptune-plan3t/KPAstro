"""kp_daily.py - daily KP (Krishnamurti Paddhati) outlook: scores, lucky/unlucky windows, predictions.

Layer on top of kp_core.py + kp_rules.py. Pure functions, no network, no global state besides CONFIG.

KP "gochar" (transit) here means: every transiting body is read through its STAR lord and SUB lord, and
those lords are read through what they signify in the NATAL chart (4-level significators). Sign-based
Parashari gochar (Jupiter 5/9 from Moon etc.) is deliberately NOT used - Reader 5 ~l.1700-1775 rejects it.

Pipeline per 30-minute slot, per life area (career, money, relationships, health, study, travel):
  D  dasha      MD-AD-PD-SD lords: houses signified (natal), sub-lord of each lord gives/denies permission
  M  moon       transit Moon star lord (topic) + sub lord (yes/no, double weight) -> natal houses
  T  transit    Sun, Mars, Mercury, Venus, Jupiter, Saturn, Rahu, Ketu star/sub lords -> natal houses
  R  ruling     Moon sub/star/sign lords + day lord (sunrise-based) -> natal houses
  pins          Reader 5 ~l.2626: Moon (hours) / Sun (days) in star of one running lord and sub of the next
                -> the running period's result is felt in that window (boost, sign follows the dasha)
Dasha is the promise; transits only time it: a transit contribution that contradicts the dasha is halved.

THE 0-100 SCORE IS A PRESENTATION LAYER with transparent heuristic weights (CONFIG). The Readers do not
define a numeric score. Output = "indications per KP rules", not guarantees.
"""
from __future__ import annotations
import argparse
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo
import swisseph as swe
import kp_core as kp
import kp_rules as kr

# ------------------------------------------------------------------ CONFIG (all heuristics live here)
SLOT_MIN = 30
USABLE_HOURS = (5, 23)                       # hours shown as lucky/avoid windows
CHAIN_DEPTH = 4                              # MD, AD, PD, SD
CHAIN_W = (1, 2, 3, 4)                       # deeper = closer in time = heavier
COMP_W = {"dasha": 0.35, "moon": 0.30, "transit": 0.20, "ruling": 0.15}
STAR_W, SUB_W = 1, 2                         # star = topic, sub = yes/no (kp_rules.day_outlook: "sub decides")
PIN_BOOST = 0.5                              # Reader 5 pairing rule, sign follows the dasha net
REF_DAYS, REF_STEP = 60, 3                   # reference sample for the percentile score (+-60 days, every 3rd)
MIN_SPREAD = 4.0                             # min slot-score range in a day before windows are called
SCORE_GAIN = 1.8                             # stretches the 0-100 scale; pure presentation
TRANSIT_BODIES = {                           # name: (swe body, weight); slow bodies weigh more
    "Sun": (swe.SUN, 1), "Mars": (swe.MARS, 1), "Mercury": (swe.MERCURY, 1), "Venus": (swe.VENUS, 1),
    "Jupiter": (swe.JUPITER, 2), "Saturn": (swe.SATURN, 2),
    "Rahu": (swe.MEAN_NODE, 2),
}                                            # Ketu = Rahu + 180, added separately
RP_W = {"moon_sub": 4, "moon_star": 3, "moon_sign": 2, "day": 1}   # Lagna lords omitted: they flip every few
                                                                    # minutes -> use the snapshot feature instead

# Area -> (rules.json event or explicit groups). status tells how well it is sourced.
AREAS = {
    "career":        {"rule": "job",              "label": "Career & work"},
    "money":         {"support": [2, 6, 10, 11], "denial": [8, 12], "status": "partial",
                      "label": "Money & gains"},   # 2-6-10-11 = R3 ~l.12380-12392, 12930-12938; 12 = expenses (same lines); 8 'loss or unexpected gains' (R3 ~l.12388) ambiguous -> partial
    "relationships": {"rule": "marriage",         "label": "Relationships"},
    "health":        {"support": [1, 5, 11], "denial": [6, 8, 12], "status": "verified",
                      "label": "Health & vitality"},  # 1-5-11 cure: E-Zine Jan2015-Oct2020 ~l.154147/699160/752258, R3 ~l.8715-8719/10818-10822; 6-8-12 disease/danger/defect: R3 ~l.17665
    "study":         {"rule": "higher_education", "label": "Study & learning"},
    "travel":        {"rule": "foreign_travel",   "label": "Travel & change"},
}
DOING = {   # good_for / avoid text per area (generic, not KP canon)
    "career":        ("interviews, meetings, pitching, starting or submitting work",
                      "big career decisions, arguments with seniors"),
    "money":         ("negotiating, collecting dues, planned purchases, investing time in income work",
                      "impulsive spending, lending, risky bets"),
    "relationships": ("important conversations, meeting people, family plans",
                      "heated discussions, new commitments made in a hurry"),
    "health":        ("exercise, routine check-ups, starting healthy habits",
                      "overexertion, ignoring tiredness or routine"),
    "study":         ("learning new topics, exams, research, applications",
                      "starting heavy new subjects, last-minute cramming"),
    "travel":        ("trips, relocation errands, visa or booking work",
                      "unnecessary or rushed journeys"),
}
# Popular planet correspondences. NOT from the KP Readers - shown only as "traditional" lucky things.
PLANET_THINGS = {
    "Sun":     {"colour": "orange / red", "number": 1, "direction": "East"},
    "Moon":    {"colour": "white / silver", "number": 2, "direction": "North-West"},
    "Mars":    {"colour": "red / maroon", "number": 9, "direction": "South"},
    "Mercury": {"colour": "green", "number": 5, "direction": "North"},
    "Jupiter": {"colour": "yellow / gold", "number": 3, "direction": "North-East"},
    "Venus":   {"colour": "white / pink", "number": 6, "direction": "South-East"},
    "Saturn":  {"colour": "dark blue / black", "number": 8, "direction": "West"},
    "Rahu":    {"colour": "smoky grey / dark", "number": 4, "direction": "South-West"},
    "Ketu":    {"colour": "mixed / earthy brown", "number": 7, "direction": "South-West"},
}


# ------------------------------------------------------------------ helpers
def load_profile(path: str | Path) -> dict:
    return json.loads(Path(path).read_text())


def resolve_areas(rules: dict) -> dict:
    out = {}
    for k, a in AREAS.items():
        if "rule" in a:
            r = rules[a["rule"]]
            sup, den, status = r["support"], r["denial"], r.get("status", "?")
        else:
            sup, den, status = a["support"], a["denial"], a.get("status", "?")
        out[k] = {"support": set(sup), "denial": set(den), "status": status, "label": a["label"]}
    return out


def net(hs: set[int], grp: dict) -> float:
    """-1..+1 : share of support houses signified minus share of denial houses signified."""
    return len(hs & grp["support"]) / len(grp["support"]) - len(hs & grp["denial"]) / len(grp["denial"])


def sgn(x: float) -> int:
    return (x > 0) - (x < 0)


def clamp(x: float, lo: float = -1.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, x))


def chain_at(mds: list[kp.Period], t: datetime, depth: int) -> tuple[str, ...] | None:
    u = t.astimezone(timezone.utc)
    cur = next((m for m in mds if m.start <= u < m.end), None)
    if cur is None:
        return None
    while len(cur.lords) < depth:
        subs = kp.sub_periods(cur)
        cur = next((s for s in subs if s.start <= u < s.end), subs[-1])
    return cur.lords


def _sunrise_utc(day_local_midnight: datetime, lat: float, lon: float) -> datetime:
    u = day_local_midnight.astimezone(timezone.utc)
    jd = swe.julday(u.year, u.month, u.day, u.hour + u.minute / 60)
    rsmi = swe.CALC_RISE | getattr(swe, "BIT_HINDU_RISING", 0)
    res, tret = swe.rise_trans(jd, swe.SUN, rsmi, (lon, lat, 0.0), 0.0, 0.0, swe.FLG_MOSEPH)
    if res < 0:
        return day_local_midnight.astimezone(timezone.utc) + timedelta(hours=6)
    y, m, d, h = swe.revjul(tret[0])
    return datetime(y, m, d, tzinfo=timezone.utc) + timedelta(hours=h)


def _lords(jd: float, body: int) -> tuple[str, str, str]:
    sl, star, sub, _ = kp.lords_at(kr._lon(jd, body))
    return sl, star, sub


# ------------------------------------------------------------------ core
class Context:
    """Everything that is constant for one person (natal side)."""

    def __init__(self, profile: dict, rules: dict | None = None):
        b, c = profile["birth"], profile.get("current", profile["birth"])
        self.birth_dt = datetime.fromisoformat(b["datetime"]).replace(tzinfo=ZoneInfo(b["tz"]))
        self.chart = kp.compute_chart(self.birth_dt, b["lat"], b["lon"])
        self.tz = ZoneInfo(c["tz"])
        self.lat, self.lon = c["lat"], c["lon"]
        self.rules = rules or kr.load_rules()
        self.areas = resolve_areas(self.rules)
        self.H = {p: set(kp.planet_houses_signified(self.chart, p)) for p in self.chart.planets}
        self.mds = kp.mahadashas(self.chart)
        self._ref: dict = {}            # (centre date) -> list of per-day raw means (reference sample)

    def lord_net(self, lord: str, grp: dict) -> float:
        """Dasha-lord net with sub-lord permission: a lord whose own sub-lord denies cannot give a positive."""
        v = net(self.H[lord], grp)
        sub = self.chart.planets[lord].sub_lord
        if net(self.H[sub], grp) < 0:
            v = min(v, 0.0)
        return v

    def tone(self, star: str, sub: str, grp: dict) -> float:
        return (STAR_W * net(self.H[star], grp) + SUB_W * net(self.H[sub], grp)) / (STAR_W + SUB_W)


def combine(D: float, M: float, T: float, R: float) -> float:
    comps = {"dasha": D, "moon": M, "transit": T, "ruling": R}
    for k in ("moon", "transit", "ruling"):
        if D * comps[k] < 0:                    # dasha = promise, transit = timing: contradiction is halved
            comps[k] *= 0.5
    tot = sum(COMP_W[k] * comps[k] for k in comps) / sum(COMP_W.values())
    return max(0.0, min(100.0, 50 + 50 * clamp(tot * SCORE_GAIN)))


def slot_scores(ctx: Context, t_mid: datetime, day_lord: str) -> dict:
    jd = kr._jd(t_mid)
    chain = chain_at(ctx.mds, t_mid, CHAIN_DEPTH)
    m_sign, m_star, m_sub = _lords(jd, swe.MOON)
    bodies = {n: _lords(jd, b) for n, (b, _) in TRANSIT_BODIES.items()}
    kl = kr._lon(jd, swe.MEAN_NODE) + 180
    _, ks, kb, _ = kp.lords_at(kl)
    bodies["Ketu"] = (kp.SIGN_LORDS[int((kl % 360) // 30)], ks, kb)
    weights = {n: w for n, (_, w) in TRANSIT_BODIES.items()}
    weights["Ketu"] = TRANSIT_BODIES["Rahu"][1]

    pairs = [(chain[i], chain[i + 1]) for i in range(len(chain) - 1)]
    moon_pin = (m_star, m_sub) in pairs or (m_sub, m_star) in pairs
    sun_pairs = pairs[:2]
    sun_pin = (bodies["Sun"][1], bodies["Sun"][2]) in sun_pairs or (bodies["Sun"][2], bodies["Sun"][1]) in sun_pairs
    rp = {"moon_sub": m_sub, "moon_star": m_star, "moon_sign": m_sign, "day": day_lord}

    res = {}
    for a, grp in ctx.areas.items():
        wsum = sum(CHAIN_W[:len(chain)])
        D = sum(w * ctx.lord_net(l, grp) for w, l in zip(CHAIN_W, chain)) / wsum
        M = ctx.tone(m_star, m_sub, grp)
        if moon_pin and D:
            M = clamp(M + PIN_BOOST * sgn(D))
        T = sum(weights[n] * ctx.tone(v[1], v[2], grp) for n, v in bodies.items()) / sum(weights.values())
        if sun_pin and D:
            T = clamp(T + PIN_BOOST * sgn(D))
        R = sum(RP_W[k] * net(ctx.H[v], grp) for k, v in rp.items()) / sum(RP_W.values())
        res[a] = {"score": combine(D, M, T, R), "D": D, "M": M, "T": T, "R": R}
    return {"t": t_mid, "chain": chain, "moon": (m_star, m_sub), "bodies": bodies,
            "moon_pin": moon_pin, "sun_pin": sun_pin, "rp": rp, "areas": res}


def _blocks(slots: list[dict], pred, min_slots: int = 2) -> list[dict]:
    """Merge consecutive slots satisfying pred into windows (>= min_slots slots)."""
    out, cur = [], None
    half = timedelta(minutes=SLOT_MIN / 2)
    for s in slots:
        if pred(s):
            if cur is None:
                cur = {"start": s["t"] - half, "end": s["t"] + half, "n": 1}
            else:
                cur["end"], cur["n"] = s["t"] + half, cur["n"] + 1
        elif cur:
            out.append(cur)
            cur = None
    if cur:
        out.append(cur)
    return [b for b in out if b["n"] >= min_slots]


def pick_windows(usable: list[dict], key, mode: str) -> list[dict]:
    """Top-quartile ('best') or bottom-quartile ('worst') windows, tie-safe: scores are quantised by the
    Moon star/sub changes, so a plain >= quartile cut can select most of the day. If a cut covers more than
    half of the usable slots it is tightened to strict > / <, and dropped if it still covers too much."""
    vals = sorted(key(s) for s in usable)
    if vals[-1] - vals[0] < MIN_SPREAD:
        return []
    q = vals[int(len(vals) * (0.75 if mode == "best" else 0.25))]
    cmp = (lambda v, t: v >= t) if mode == "best" else (lambda v, t: v <= t)
    strict = (lambda v, t: v > t) if mode == "best" else (lambda v, t: v < t)
    for f in (cmp, strict):
        sel = [s for s in usable if f(key(s), q)]
        if 0 < len(sel) <= len(usable) / 2:
            return _blocks(usable, lambda s: f(key(s), q))
    return []


def label(score: float) -> str:
    """score = percentile of today vs the person's own surrounding days."""
    return ("Very favourable" if score >= 80 else "Favourable" if score >= 60 else
            "Average" if score >= 40 else "Challenging" if score >= 20 else "Difficult")


def _day_slots(ctx: Context, d0: datetime) -> tuple[list[dict], list[dict], datetime, str, str]:
    sunrise = _sunrise_utc(d0, ctx.lat, ctx.lon)
    prev_lord = kp.WEEKDAY_LORD[(d0 - timedelta(days=1)).weekday()]
    today_lord = kp.WEEKDAY_LORD[d0.weekday()]
    slots = []
    n = int(24 * 60 / SLOT_MIN)
    for i in range(n):
        t_mid = d0 + timedelta(minutes=SLOT_MIN * i + SLOT_MIN / 2)
        dl = prev_lord if t_mid.astimezone(timezone.utc) < sunrise else today_lord
        s = slot_scores(ctx, t_mid, dl)
        s["overall"] = sum(v["score"] for v in s["areas"].values()) / len(s["areas"])
        slots.append(s)
    usable = [s for s in slots if USABLE_HOURS[0] <= s["t"].astimezone(ctx.tz).hour < USABLE_HOURS[1]]
    return slots, usable, sunrise, prev_lord, today_lord


def _raw_means(ctx: Context, usable: list[dict]) -> dict:
    out = {a: sum(s["areas"][a]["score"] for s in usable) / len(usable) for a in ctx.areas}
    out["overall"] = sum(s["overall"] for s in usable) / len(usable)
    return out


def reference(ctx: Context, d0: datetime) -> list[dict]:
    """Raw daily means for a sample of days around d0 (+-REF_DAYS, every REF_STEP days). Cached."""
    key = next((k for k in ctx._ref if abs((d0 - k).days) <= REF_DAYS // 2), None)
    if key is None:
        key = d0
        ctx._ref[key] = [_raw_means(ctx, _day_slots(ctx, d0 + timedelta(days=i))[1])
                         for i in range(-REF_DAYS, REF_DAYS + 1, REF_STEP)]
    return ctx._ref[key]


def percentile(x: float, ref: list[float]) -> float:
    return 100.0 * (sum(1 for r in ref if r < x) + 0.5 * sum(1 for r in ref if r == x)) / len(ref)


def backdrop_label(D: float) -> str:
    sc = 50 + 50 * clamp(D * SCORE_GAIN)
    return "supportive" if sc >= 60 else "mixed" if sc >= 42 else "heavy"


def day_report(ctx: Context, day: datetime, diary_scores: dict | None = None) -> dict:
    """day: any tz-aware datetime on the wanted local date."""
    d0 = day.astimezone(ctx.tz).replace(hour=0, minute=0, second=0, microsecond=0)
    slots, usable, sunrise, prev_lord, today_lord = _day_slots(ctx, d0)
    raw = _raw_means(ctx, usable)
    ref = reference(ctx, d0)
    areas = {}
    for a, grp in ctx.areas.items():
        sc = [s["areas"][a]["score"] for s in usable]
        avg = percentile(raw[a], [r[a] for r in ref])
        comp = {k: sum(s["areas"][a][k] for s in usable) / len(usable) for k in ("D", "M", "T", "R")}
        srt = sorted(sc)
        hi, lo = srt[int(len(srt) * 0.75)], srt[int(len(srt) * 0.25)]
        best = pick_windows(usable, lambda s, a=a: s["areas"][a]["score"], "best")
        worst = pick_windows(usable, lambda s, a=a: s["areas"][a]["score"], "worst")
        areas[a] = {"label": grp["label"], "score": round(avg), "verdict": label(avg), "components": comp,
                    "raw": round(raw[a], 1), "backdrop": backdrop_label(comp["D"]),
                    "source_status": grp["status"], "best": best, "avoid": worst}

    ov = [s["overall"] for s in usable]
    avg_ov = percentile(raw["overall"], [r["overall"] for r in ref])
    srt = sorted(ov)
    hi, lo = srt[int(len(srt) * 0.75)], srt[int(len(srt) * 0.25)]
    lucky = pick_windows(usable, lambda s: s["overall"], "best")
    unlucky = pick_windows(usable, lambda s: s["overall"], "worst")

    # ruling-planet candidates today -> lucky / stressing planet (traditional things attached)
    cands = {today_lord, prev_lord} | {s["moon"][0] for s in usable} | {s["moon"][1] for s in usable}
    cands.discard(prev_lord if prev_lord not in {s["rp"]["day"] for s in usable} else None)
    def planet_net(p: str) -> float:
        return sum(net(ctx.H[p], g) for g in ctx.areas.values()) / len(ctx.areas)
    ranked = sorted(cands, key=planet_net, reverse=True)
    lucky_p, stress_p = ranked[0], ranked[-1]

    pins = []
    for w in kr.moon_windows(d0, 5):
        chain = chain_at(ctx.mds, w["start"], CHAIN_DEPTH)
        pairs = [(chain[i], chain[i + 1]) for i in range(len(chain) - 1)]
        if (w["star"], w["sub"]) in pairs or (w["sub"], w["star"]) in pairs:
            pins.append({"start": w["start"], "end": w["end"], "star": w["star"], "sub": w["sub"], "chain": chain})

    personal = None
    if diary_scores:
        personal = [w for w in kr.lucky_windows(diary_scores, d0) if w["tag"]]

    noon = slots[len(slots) // 2]
    ranked_areas = sorted(areas, key=lambda k: areas[k]["score"], reverse=True)
    return {
        "date": d0.date().isoformat(), "weekday": d0.strftime("%A"), "sunrise_utc": sunrise,
        "day_lord": today_lord, "dasha_chain": noon["chain"],
        "overall": {"score": round(avg_ov), "verdict": label(avg_ov), "raw": round(raw["overall"], 1),
                    "backdrop": backdrop_label(sum(a["components"]["D"] for a in areas.values()) / len(areas))},
        "areas": areas, "best_areas": ranked_areas[:2], "weak_areas": ranked_areas[-2:],
        "lucky_windows": lucky, "avoid_windows": unlucky, "moon_pin_windows": pins,
        "sun_pin_today": any(s["sun_pin"] for s in usable),
        "transit_snapshot": {n: {"sign_lord": v[0], "star": v[1], "sub": v[2]} for n, v in noon["bodies"].items()},
        "lucky_planet": {"planet": lucky_p, **PLANET_THINGS[lucky_p]},
        "stress_planet": {"planet": stress_p, "avoid_colour": PLANET_THINGS[stress_p]["colour"]},
        "personal_diary_windows": personal,
    }


# ------------------------------------------------------------------ text rendering
def _fmt(w: dict, tz: ZoneInfo) -> str:
    return f"{w['start'].astimezone(tz):%H:%M}-{w['end'].astimezone(tz):%H:%M}"


def _bar(score: int, width: int = 20) -> str:
    k = round(score / 100 * width)
    return "#" * k + "." * (width - k)


def _prediction(a: str, info: dict, chain: tuple[str, ...]) -> str:
    s, c = info["score"], info["components"]
    drivers = sorted((("running dasha chain", c["D"]), ("Moon's star/sub lords", c["M"]),
                      ("other transits", c["T"]), ("ruling planets", c["R"])), key=lambda x: -abs(x[1]))
    main, sec = drivers[0], drivers[1]
    def word(v: float) -> str:
        return "helps" if v > 0.05 else "pulls against" if v < -0.05 else "stays neutral on"
    tone = ("A better-than-usual day for this" if s >= 60 else "An ordinary day for this" if s >= 40 else
            "A heavier-than-usual day for this")
    txt = (f"{tone} (backdrop of the running periods: {info['backdrop']}). "
           f"Main driver: {main[0]} ({word(main[1])} it); next: {sec[0]} ({word(sec[1])} it).")
    if info["best"]:
        txt += (" Best window: {BEST}." if s >= 40 else " Least heavy window: {BEST}.")
    return txt


def render_text(rep: dict, ctx: Context) -> str:
    tz = ctx.tz
    L = []
    L.append(f"KP DAILY OUTLOOK - {rep['weekday']} {rep['date']}   (place: {ctx.lat:.2f}N {ctx.lon:.2f}E)")
    L.append("Dasha chain: " + " > ".join(rep["dasha_chain"]) + f"    Day lord: {rep['day_lord']}")
    L.append("=" * 78)
    L.append(f"TODAY'S SCORE: {rep['overall']['score']}/100  {rep['overall']['verdict']}"
             f"   (today ranked against your own days of the surrounding ~4 months; 50 = typical)")
    L.append(f"Period backdrop (running dasha chain): {rep['overall']['backdrop']}")
    L.append("")
    for a, info in sorted(rep["areas"].items(), key=lambda kv: -kv[1]["score"]):
        flag = "  (house groups unverified)" if info["source_status"] == "unverified" else ""
        L.append(f"{info['label']:<18}{info['score']:>3}  [{_bar(info['score'])}] {info['verdict']:<15} backdrop: {info['backdrop']}{flag}")
    L.append("")
    L.append("LUCKY WINDOWS (best hours today):")
    L += [f"   + {_fmt(w, tz)}" for w in rep["lucky_windows"]] or ["   (no clear peak today)"]
    L.append("UNLUCKY / CAUTION WINDOWS:")
    L += [f"   - {_fmt(w, tz)}" for w in rep["avoid_windows"]] or ["   (no clear dip today)"]
    if rep["moon_pin_windows"]:
        L.append("TIMING-SENSITIVE (Moon star/sub pairs your running periods, Reader 5; the running periods' backdrop is amplified here):")
        L += [f"   * {_fmt(w, tz)}  Moon star {w['star']} / sub {w['sub']}" for w in rep["moon_pin_windows"]]
    if rep["sun_pin_today"]:
        L.append("   * Sun is in a star/sub pairing of your running Dasha-Bhukti lords today (Reader 5 pinning).")
    L.append("")
    lp, sp = rep["lucky_planet"], rep["stress_planet"]
    good = [DOING[a][0] for a in rep["best_areas"] if rep["areas"][a]["score"] >= 60]
    bad = [DOING[a][1] for a in rep["weak_areas"] if rep["areas"][a]["score"] <= 40]
    L.append("LUCKY THINGS (traditional planet correspondences, not from the KP Readers):")
    L.append(f"   Lucky planet today: {lp['planet']}  colour {lp['colour']}, number {lp['number']}, direction {lp['direction']}")
    L.append(f"   Go easy on: {sp['planet']} energy (avoid {sp['avoid_colour']} for important things)")
    L.append("GOOD FOR: " + ("; ".join(good) if good else "routine work; no area stands out"))
    L.append("BE CAREFUL WITH: " + ("; ".join(bad) if bad else "nothing special"))
    L.append("")
    L.append("PREDICTIONS:")
    for a, info in sorted(rep["areas"].items(), key=lambda kv: -kv[1]["score"]):
        p = _prediction(a, info, rep["dasha_chain"])
        best = ", ".join(_fmt(w, tz) for w in info["best"][:2])
        L.append(f" - {info['label']}: " + p.replace("{BEST}", best))
    L.append("")
    L.append("TRANSITS (star lord / sub lord):")
    L.append("   " + "   ".join(f"{n} {v['star'][:3]}/{v['sub'][:3]}" for n, v in rep["transit_snapshot"].items()))
    if rep.get("personal_diary_windows"):
        L.append("PERSONAL DIARY WINDOWS (learned from your lucky-time diary, Reader 5 ~l.11770):")
        L += [f"   {w['tag']:<8}{_fmt(w, tz)} Moon sub {w['sub']}" for w in rep["personal_diary_windows"]]
    L.append("")
    L.append("Indications per KP rules, not guarantees. Scores use transparent heuristic weights (CONFIG in kp_daily.py).")
    return "\n".join(L)


def render_summary(reps: list[dict]) -> str:
    L = ["Date        Day  Score  Verdict           Best area           Weakest area        Dasha chain"]
    for r in reps:
        b, w = r["best_areas"][0], r["weak_areas"][-1]
        L.append(f"{r['date']}  {r['weekday'][:3]}  {r['overall']['score']:>4}   {r['overall']['verdict']:<16}  "
                 f"{r['areas'][b]['label']:<18}  {r['areas'][w]['label']:<18}  {'>'.join(x[:2] for x in r['dasha_chain'])}")
    return "\n".join(L)


def _jsonable(o):
    if isinstance(o, datetime):
        return o.isoformat()
    if isinstance(o, (set, tuple)):
        return list(o)
    raise TypeError(type(o))


def load_diary(path: str | Path, tz: ZoneInfo) -> dict:
    rows = []
    for r in json.loads(Path(path).read_text()):
        rows.append((datetime.fromisoformat(r[0]).replace(tzinfo=tz), datetime.fromisoformat(r[1]).replace(tzinfo=tz), float(r[2])))
    return kr.learn_sub_scores(rows)


# ------------------------------------------------------------------ CLI
if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="KP daily outlook")
    ap.add_argument("--profile", default=str(Path(__file__).with_name("profile.json")))
    ap.add_argument("--date", help="YYYY-MM-DD (default today)")
    ap.add_argument("--days", type=int, default=1)
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--summary", action="store_true", help="one line per day instead of the full report")
    ap.add_argument("--diary", help="JSON [[start_iso,end_iso,outcome],...] for the personal lucky-time layer")
    a = ap.parse_args()
    ctx = Context(load_profile(a.profile))
    d = (datetime.fromisoformat(a.date) if a.date else datetime.now(ctx.tz)).replace(tzinfo=ctx.tz)
    diary = load_diary(a.diary, ctx.tz) if a.diary else None
    reps = [day_report(ctx, d + timedelta(days=i), diary) for i in range(a.days)]
    if a.json:
        print(json.dumps(reps if a.days > 1 else reps[0], default=_jsonable, indent=2))
    elif a.summary:
        print(render_summary(reps))
    else:
        for r in reps:
            print(render_text(r, ctx))
            print()
