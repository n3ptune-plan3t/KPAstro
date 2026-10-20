"""kp_core.py - Base logic for KP (Krishnamurti Paddhati) astrology.

Requires: pip install pyswisseph
Layers: ephemeris -> houses -> star/sub/sub-sub lords -> significators
        -> Vimshottari dasha -> ruling planets
"""
from __future__ import annotations
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
import swisseph as swe

# ---------------------------------------------------------------- constants
SIGNS = ["Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra",
         "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"]
SIGN_LORDS = ["Mars", "Venus", "Mercury", "Moon", "Sun", "Mercury", "Venus",
              "Mars", "Jupiter", "Saturn", "Saturn", "Jupiter"]
VIM_ORDER = ["Ketu", "Venus", "Sun", "Moon", "Mars", "Rahu", "Jupiter",
             "Saturn", "Mercury"]
VIM_YEARS = {"Ketu": 7, "Venus": 20, "Sun": 6, "Moon": 10, "Mars": 7,
             "Rahu": 18, "Jupiter": 16, "Saturn": 19, "Mercury": 17}
NAK_SPAN = 360.0 / 27            # 13deg20'
YEAR_DAYS = 365.25               # solar/Julian year (Phala Dipika; JHora, KP Astro default). 360 = Savana, minority use.
WEEKDAY_LORD = ["Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Sun"]  # Mon..Sun
BODIES = {"Sun": swe.SUN, "Moon": swe.MOON, "Mars": swe.MARS,
          "Mercury": swe.MERCURY, "Jupiter": swe.JUPITER, "Venus": swe.VENUS,
          "Saturn": swe.SATURN}
NODE_CONJ_ORB = 10 / 3           # 3deg20' - KP orb for conjunction/aspect (one nakshatra quarter)
NODE_USE_ASPECTS = False         # some KP teachers add aspecting planets as node agents; off by default
ASPECT_ANGLES = (60, 90, 120, 180)   # Western major aspects (used only if NODE_USE_ASPECTS)
RAHU_MEAN = True                 # KP ephemeris traditionally uses mean node; set False for true node


# ---------------------------------------------------------------- lord tables
def _build_sub_table() -> list[tuple[float, float, str, str]]:
    """243 rows of (start, end, star_lord, sub_lord) across 0-360 sidereal."""
    rows = []
    for n in range(27):
        star = VIM_ORDER[n % 9]
        i = VIM_ORDER.index(star)
        pos = n * NAK_SPAN
        for k in range(9):
            sub = VIM_ORDER[(i + k) % 9]
            span = NAK_SPAN * VIM_YEARS[sub] / 120
            rows.append((pos, pos + span, star, sub))
            pos += span
    return rows


SUB_TABLE = _build_sub_table()


def lords_at(lon: float) -> tuple[str, str, str, str]:
    """Return (sign_lord, star_lord, sub_lord, sub_sub_lord) for a sidereal longitude."""
    lon %= 360.0
    sign_lord = SIGN_LORDS[int(lon // 30)]
    for start, end, star, sub in SUB_TABLE:
        if start <= lon < end:
            # sub-sub: split the sub span in Vimshottari proportion, starting at sub lord
            i = VIM_ORDER.index(sub)
            span = end - start
            pos = start
            for k in range(9):
                ss = VIM_ORDER[(i + k) % 9]
                w = span * VIM_YEARS[ss] / 120
                if pos <= lon < pos + w or k == 8:
                    return sign_lord, star, sub, ss
                pos += w
    raise ValueError(f"longitude out of range: {lon}")


# ---------------------------------------------------------------- chart
@dataclass
class Point:
    name: str
    lon: float
    speed: float = 0.0
    sign: str = ""
    sign_lord: str = ""
    star_lord: str = ""
    sub_lord: str = ""
    sub_sub_lord: str = ""
    house: int = 0

    @property
    def retrograde(self) -> bool:
        return self.speed < 0


def _point(name: str, lon: float, speed: float = 0.0) -> Point:
    lon %= 360.0
    sl, star, sub, ss = lords_at(lon)
    return Point(name, lon, speed, SIGNS[int(lon // 30)], sl, star, sub, ss)


def house_of(lon: float, cusps: list[float]) -> int:
    for h in range(12):
        a, b = cusps[h], cusps[(h + 1) % 12]
        if a <= b:
            if a <= lon < b:
                return h + 1
        elif lon >= a or lon < b:      # wraps past 360
            return h + 1
    return 1


@dataclass
class Chart:
    dt_utc: datetime
    lat: float
    lon: float
    jd: float
    ayanamsa: float
    planets: dict[str, Point]
    cusps: list[Point]               # index 0 = house 1
    asc: Point
    significators: dict[int, dict[str, list[str]]] = field(default_factory=dict)


def compute_chart(dt: datetime, lat: float, lon: float) -> Chart:
    """dt must be timezone-aware; it is converted to UTC."""
    if dt.tzinfo is None:
        raise ValueError("datetime must be timezone-aware")
    u = dt.astimezone(timezone.utc)
    jd = swe.julday(u.year, u.month, u.day,
                    u.hour + u.minute / 60 + u.second / 3600)
    swe.set_sid_mode(swe.SIDM_KRISHNAMURTI)
    flags = swe.FLG_MOSEPH | swe.FLG_SIDEREAL | swe.FLG_SPEED  # Moshier: no data files needed

    planets: dict[str, Point] = {}
    for name, body in BODIES.items():
        xx, _ = swe.calc_ut(jd, body, flags)
        planets[name] = _point(name, xx[0], xx[3])
    node = swe.MEAN_NODE if RAHU_MEAN else swe.TRUE_NODE
    xx, _ = swe.calc_ut(jd, node, flags)
    planets["Rahu"] = _point("Rahu", xx[0], xx[3])
    planets["Ketu"] = _point("Ketu", xx[0] + 180, xx[3])

    cusps_raw, ascmc = swe.houses_ex(jd, lat, lon, b"P", swe.FLG_SIDEREAL)
    cusp_lons = list(cusps_raw[-12:])              # tolerate 12- or 13-length returns
    cusps = [_point(f"H{i+1}", c) for i, c in enumerate(cusp_lons)]
    asc = _point("Asc", ascmc[0])
    for p in planets.values():
        p.house = house_of(p.lon, cusp_lons)

    ch = Chart(u, lat, lon, jd, swe.get_ayanamsa_ut(jd), planets, cusps, asc)
    ch.significators = compute_significators(ch)
    return ch


# ---------------------------------------------------------------- significators
def compute_significators(ch: Chart) -> dict[int, dict[str, list[str]]]:
    """KP 4-level significators per house:
    A: planets in star of occupants   B: occupants
    C: planets in star of house lord  D: house lord (cusp sign lord)
    Rahu/Ketu agency (conjunction/aspect) is not modelled here."""
    out = {}
    for h in range(1, 13):
        occupants = [p.name for p in ch.planets.values() if p.house == h]
        lord = ch.cusps[h - 1].sign_lord
        A = [p.name for p in ch.planets.values() if p.star_lord in occupants]
        C = [p.name for p in ch.planets.values() if p.star_lord == lord]
        out[h] = {"A": A, "B": occupants, "C": C, "D": [lord]}
    return out


def _angle(a: float, b: float) -> float:
    d = abs(a - b) % 360
    return min(d, 360 - d)


def node_agents(ch: Chart, node: str) -> list[str]:
    """Planets Rahu/Ketu act as agent for, strongest first (KP rule: results come in the
    order conjunction -> (aspect) -> star lord -> sign lord)."""
    n = ch.planets[node]
    agents: list[str] = []
    for p in ch.planets.values():
        if p.name in ("Rahu", "Ketu") or p.name == node:
            continue
        if _angle(p.lon, n.lon) <= NODE_CONJ_ORB:
            agents.append(p.name)
    if NODE_USE_ASPECTS:
        for p in ch.planets.values():
            if p.name in ("Rahu", "Ketu") or p.name in agents:
                continue
            if any(abs(_angle(p.lon, n.lon) - a) <= NODE_CONJ_ORB for a in ASPECT_ANGLES):
                agents.append(p.name)
    for lord in (n.star_lord, n.sign_lord):      # nodes' own star lord is usually a real planet or other node
        if lord not in agents and lord not in ("Rahu", "Ketu"):
            agents.append(lord)
    return agents


def planet_houses_signified(ch: Chart, name: str, with_agents: bool = True) -> list[int]:
    """Houses a planet signifies (strongest levels first). For Rahu/Ketu, houses of their
    agents are appended after their own."""
    def own(pl: str) -> list[int]:
        res = []
        for lvl in "ABCD":
            for h, d in ch.significators.items():
                if pl in d[lvl] and h not in res:
                    res.append(h)
        return res

    res = own(name)
    if with_agents and name in ("Rahu", "Ketu"):
        for ag in node_agents(ch, name):
            for h in own(ag):
                if h not in res:
                    res.append(h)
    return res


# ---------------------------------------------------------------- dasha
@dataclass
class Period:
    lords: tuple[str, ...]           # (MD,) or (MD, AD) or (MD, AD, PD)
    start: datetime
    end: datetime


def _split(lord: str, start: datetime, length_days: float, parent: tuple[str, ...]) -> list[Period]:
    i = VIM_ORDER.index(lord)
    out, t = [], start
    for k in range(9):
        sub = VIM_ORDER[(i + k) % 9]
        d = length_days * VIM_YEARS[sub] / 120
        out.append(Period(parent + (sub,), t, t + timedelta(days=d)))
        t += timedelta(days=d)
    return out


def mahadashas(ch: Chart) -> list[Period]:
    moon = ch.planets["Moon"].lon
    idx = int(moon // NAK_SPAN)
    elapsed = (moon % NAK_SPAN) / NAK_SPAN
    lord = VIM_ORDER[idx % 9]
    t = ch.dt_utc - timedelta(days=VIM_YEARS[lord] * YEAR_DAYS * elapsed)
    out, i = [], VIM_ORDER.index(lord)
    for k in range(9):
        l = VIM_ORDER[(i + k) % 9]
        d = VIM_YEARS[l] * YEAR_DAYS
        out.append(Period((l,), t, t + timedelta(days=d)))
        t += timedelta(days=d)
    return out


def sub_periods(p: Period) -> list[Period]:
    """Next level down (MD->AD->PD->SD)."""
    return _split(p.lords[-1], p.start, (p.end - p.start).total_seconds() / 86400, p.lords)


def periods_at(ch: Chart, at: datetime, depth: int = 3) -> Period | None:
    """Deepest running period at `at` down to `depth` levels."""
    at = at.astimezone(timezone.utc)
    cur = next((m for m in mahadashas(ch) if m.start <= at < m.end), None)
    while cur and len(cur.lords) < depth:
        cur = next((s for s in sub_periods(cur) if s.start <= at < s.end), cur)
        if len(cur.lords) >= depth:
            break
    return cur


# ---------------------------------------------------------------- ruling planets
def sunrise_day_lord(at: datetime, lat: float, lon: float) -> str:
    """KP/Hindu vara: the day lord changes at local sunrise, not midnight.
    `at` must be tz-aware in the location's local zone (weekday of the sunrise is taken locally)."""
    u = at.astimezone(timezone.utc)
    jd = swe.julday(u.year, u.month, u.day, u.hour + u.minute / 60 + u.second / 3600)
    rsmi = swe.CALC_RISE | getattr(swe, "BIT_HINDU_RISING", 0)   # disc centre, no refraction (panchang convention)
    t, last = jd - 1.5, None
    for _ in range(4):
        res, tret = swe.rise_trans(t, swe.SUN, rsmi, (lon, lat, 0.0), 0.0, 0.0, swe.FLG_MOSEPH)
        if res < 0:                      # circumpolar: fall back to a 06:00 local cutoff
            local = at - timedelta(hours=6)
            return WEEKDAY_LORD[local.weekday()]
        if tret[0] > jd:
            break
        last, t = tret[0], tret[0] + 0.01
    if last is None:                     # sunrise not yet reached within window -> previous day's lord
        res, tret = swe.rise_trans(jd - 2.5, swe.SUN, rsmi, (lon, lat, 0.0), 0.0, 0.0, swe.FLG_MOSEPH)
        last = tret[0]
    y, m, d, h = swe.revjul(last)
    rise_utc = datetime(y, m, d, tzinfo=timezone.utc) + timedelta(hours=h)
    return WEEKDAY_LORD[rise_utc.astimezone(at.tzinfo).weekday()]


def ruling_planets(at: datetime, lat: float, lon: float) -> dict:
    """KP ruling planets at a moment, in strength order as taught by Krishnamurti's school:
    Lagna sub > Lagna star > Lagna sign > Moon sub > Moon star > Moon sign > Day lord.
    Rahu/Ketu are added to `derived` when one of their agents (conjunct planet or sign lord)
    is already a ruling planet - to be used sparingly."""
    ch = compute_chart(at, lat, lon)
    m, a = ch.planets["Moon"], ch.asc
    chain = {"asc_sub": a.sub_lord, "asc_star": a.star_lord, "asc_sign": a.sign_lord,
             "moon_sub": m.sub_lord, "moon_star": m.star_lord, "moon_sign": m.sign_lord,
             "day": sunrise_day_lord(at, lat, lon)}
    ordered: list[str] = []
    for v in chain.values():
        if v not in ordered:
            ordered.append(v)
    derived = [n for n in ("Rahu", "Ketu")
               if n not in ordered and any(ag in ordered for ag in node_agents(ch, n))]
    return {"chain": chain, "ordered": ordered, "derived": derived}


# ---------------------------------------------------------------- demo
if __name__ == "__main__":
    from zoneinfo import ZoneInfo
    dt = datetime(1990, 5, 15, 10, 30, tzinfo=ZoneInfo("Asia/Kolkata"))
    ch = compute_chart(dt, 28.6139, 77.2090)
    print(f"KP ayanamsa: {ch.ayanamsa:.4f}")
    print(f"Asc {ch.asc.lon:.4f} {ch.asc.sign} {ch.asc.star_lord}/{ch.asc.sub_lord}")
    for p in ch.planets.values():
        print(f"{p.name:8}{p.lon:9.4f} {p.sign:12}H{p.house:<3}"
              f"{p.star_lord}/{p.sub_lord}/{p.sub_sub_lord}{' R' if p.retrograde else ''}")
    print("Cusps:", [f"{c.sign[:3]} {c.star_lord}/{c.sub_lord}" for c in ch.cusps])
    print("H7 significators:", ch.significators[7])
    for n in ("Rahu", "Ketu"):
        print(n, "agents:", node_agents(ch, n), "houses:", planet_houses_signified(ch, n))
    print("Now:", periods_at(ch, datetime.now(timezone.utc)))
    print("Ruling:", ruling_planets(datetime.now(ZoneInfo("Asia/Kolkata")), 28.6139, 77.2090))
