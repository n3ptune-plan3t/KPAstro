"""kp_hora.py - planetary hours (hora), Reader 3 ~l.30440-30580. Mirrors engine/KpHora.kt.
Sunrise->noon /6 (l.30447, 30480-30495); order Sun, Venus, Mercury, Moon, Saturn, Jupiter, Mars from the day lord;
7 equal sub-horas starting with the hora lord (l.30519-30525); pair rule l.30558-30566.
ASSUMPTION: afternoon/night continue the same 12+12 cycle (the book only worked the forenoon)."""
from __future__ import annotations
from datetime import datetime, timedelta, timezone
import swisseph as swe
import kp_core as kp, kp_daily as kd

HORA_ORDER = ["Sun", "Venus", "Mercury", "Moon", "Saturn", "Jupiter", "Mars"]
MUHURTA_PAIRS = {"Marriage (Venus + Jupiter)": ("Venus", "Jupiter"),
                 "Construction (Mercury + Mars)": ("Mercury", "Mars"),
                 "Avoid signing / negotiating (Saturn + Mercury)": ("Saturn", "Mercury")}


def lord_after(lord: str, steps: int) -> str:
    return HORA_ORDER[(HORA_ORDER.index(lord) + steps) % 7]


def _noon_utc(rise: datetime, lat: float, lon: float) -> datetime:
    rise = rise.replace(second=0, microsecond=0)
    jd = swe.julday(rise.year, rise.month, rise.day, rise.hour + rise.minute / 60)
    res, tret = swe.rise_trans(jd, swe.SUN, swe.CALC_MTRANSIT, (lon, lat, 0.0), 0.0, 0.0, swe.FLG_MOSEPH)
    if res < 0:
        return rise + timedelta(hours=6)
    y, m, d, h = swe.revjul(tret[0])
    return datetime(y, m, d, tzinfo=timezone.utc) + timedelta(hours=h)


def hora_day(d0: datetime, lat: float, lon: float) -> list[dict]:
    """24 horas starting at the sunrise of local calendar day d0 (tz-aware local midnight)."""
    rise = kd._sunrise_utc(d0, lat, lon)
    next_rise = kd._sunrise_utc(d0 + timedelta(days=1), lat, lon)
    noon = _noon_utc(rise, lat, lon)
    day_len = (noon - rise) * 2
    sset = rise + day_len
    day_h, night_h = day_len / 12, (next_rise - sset) / 12
    day_lord = kp.WEEKDAY_LORD[d0.weekday()]
    out = []
    for i in range(24):
        night = i >= 12
        s = rise + day_h * i if not night else sset + night_h * (i - 12)
        e = next_rise if i == 23 else (rise + day_h * (i + 1) if not night else sset + night_h * (i - 11))
        out.append({"start": s, "end": e, "lord": lord_after(day_lord, i), "night": night})
    return out


def hora_subs(h: dict) -> list[dict]:
    n = (h["end"] - h["start"]) / 7
    return [{"start": h["start"] + n * j, "end": h["end"] if j == 6 else h["start"] + n * (j + 1), "lord": lord_after(h["lord"], j)} for j in range(7)]


def pair_windows(horas: list[dict], day_lord: str, a: str, b: str) -> list[dict]:
    want, out = {a, b}, []
    for h in horas:
        if h["lord"] != day_lord and {day_lord, h["lord"]} == want:
            out.append({"start": h["start"], "end": h["end"], "hora": h["lord"], "sub": None, "kind": "day+hora"})
        elif h["lord"] == day_lord and a == b == h["lord"]:
            out.append({"start": h["start"], "end": h["end"], "hora": h["lord"], "sub": None, "kind": "day+hora"})
        for s in hora_subs(h):
            if s["lord"] != h["lord"] and {h["lord"], s["lord"]} == want:
                out.append({"start": s["start"], "end": s["end"], "hora": h["lord"], "sub": s["lord"], "kind": "hora+sub"})
    return sorted(out, key=lambda x: x["start"])


def hora_report(ctx, d0: datetime) -> dict:
    horas = hora_day(d0, ctx.lat, ctx.lon); day_lord = kp.WEEKDAY_LORD[d0.weekday()]
    chain = kd.chain_at(ctx.mds, (d0 + timedelta(hours=12)).astimezone(timezone.utc), 2)
    pair = tuple(chain)
    return {"day_lord": day_lord, "horas": horas, "dasha_pair": pair,
            "dasha_pins": pair_windows(horas, day_lord, *pair),
            "muhurta": {k: pair_windows(horas, day_lord, *v) for k, v in MUHURTA_PAIRS.items()}}


if __name__ == "__main__":
    from zoneinfo import ZoneInfo
    d0 = datetime(2026, 10, 21, tzinfo=ZoneInfo("Asia/Kolkata"))   # a Wednesday
    for h in hora_day(d0, 26.4446, 91.4373)[:4]:
        print(h["lord"], h["start"].astimezone(d0.tzinfo).strftime("%H:%M"), h["end"].astimezone(d0.tzinfo).strftime("%H:%M"))
