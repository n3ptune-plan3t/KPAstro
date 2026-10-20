"""Generate golden.json from the Python reference engine (needs pyswisseph).
Usage: python gen_golden.py > golden.json  -> consumed by tools/host-check (the Kotlin port must match)."""
import json
from datetime import datetime, timezone, timedelta
from zoneinfo import ZoneInfo
import kp_core as kp, kp_rules as kr, kp_daily as kd, kp_hora as kh, kp_timing as kt

IST = ZoneInfo("Asia/Kolkata")
iso = lambda d: d.astimezone(timezone.utc).isoformat()


def chart_dump(ch):
    return {
        "ayanamsa": ch.ayanamsa, "asc": [ch.asc.lon, ch.asc.star_lord, ch.asc.sub_lord, ch.asc.sub_sub_lord],
        "planets": {n: [p.lon, p.house, p.sign_lord, p.star_lord, p.sub_lord, p.sub_sub_lord, p.retrograde]
                    for n, p in ch.planets.items()},
        "cusps": [[c.lon, c.sign_lord, c.star_lord, c.sub_lord, c.sub_sub_lord] for c in ch.cusps],
        "sig": {str(h): d for h, d in ch.significators.items()},
        "houses": {n: kp.planet_houses_signified(ch, n) for n in ch.planets},
        "agents": {n: kp.node_agents(ch, n) for n in ("Rahu", "Ketu")},
        "mds": [[m.lords[0], iso(m.start), iso(m.end)] for m in kp.mahadashas(ch)],
    }


out = {"charts": {}, "ruling": [], "life": {}, "days": {}, "pins": [], "sensitive": {}}
for chain in [("Venus", "Moon", "Mercury"), ("Sun", "Venus", "Mercury"), ("Mercury", "Rahu", "Jupiter"),
              ("Saturn", "Mars", "Mercury"), ("Venus", "Mars", "Venus")]:
    out["sensitive"]["-".join(chain)] = [list(p) for p in kt.sensitive_points(chain)]

specs = {
    "anchor1990": (datetime(1990, 5, 15, 10, 30, tzinfo=IST), 28.6139, 77.2090),
    "user2000": (datetime(2000, 11, 21, 18, 12, tzinfo=IST), 26.4446, 91.4373),
}
for k, (dt, la, lo) in specs.items():
    out["charts"][k] = {"dt": iso(dt), "lat": la, "lon": lo, **chart_dump(kp.compute_chart(dt, la, lo))}

ch = kp.compute_chart(specs["user2000"][0], 26.4446, 91.4373)
for t in [datetime(2026, 10, 16, 4, 30, tzinfo=IST), datetime(2026, 10, 16, 6, 5, tzinfo=IST),
          datetime(2026, 10, 16, 13, 0, tzinfo=IST), datetime(2026, 10, 18, 5, 59, tzinfo=IST),
          datetime(2026, 10, 18, 23, 59, tzinfo=IST)]:
    out["ruling"].append({"at": iso(t), **kp.ruling_planets(t, 26.4446, 91.4373)})

now = datetime(2026, 10, 15, 9, 0, tzinfo=IST)
rules = kr.load_rules()
for name, r in kr.life_report(ch, rules, years=15, now=now).items():
    out["life"][name] = {"promise": r["promise"], "parents": r["parents"],
                         "windows": [[iso(w["start"]), iso(w["end"]), [list(x) for x in w["lords"]],
                                      w["parent_active"], list(w["parents"])] for w in r["windows"]]}
out["periods_at"] = list(kp.periods_at(ch, now, 4).lords)

ctx = kd.Context(kd.load_profile("profile.json"))
w = lambda ws: [[iso(x["start"]), iso(x["end"])] for x in ws]
for day in ("2026-10-16", "2026-10-17"):
    rep = kd.day_report(ctx, datetime.fromisoformat(day).replace(tzinfo=IST))
    out["days"][day] = {
        "overall": rep["overall"], "day_lord": rep["day_lord"], "dasha_chain": list(rep["dasha_chain"]),
        "areas": {a: {"score": i["score"], "raw": i["raw"], "backdrop": i["backdrop"],
                      "D": i["components"]["D"], "M": i["components"]["M"], "T": i["components"]["T"],
                      "R": i["components"]["R"], "best": w(i["best"]), "avoid": w(i["avoid"])}
                  for a, i in rep["areas"].items()},
        "lucky": w(rep["lucky_windows"]), "avoid": w(rep["avoid_windows"]),
        "moon_pins": [[iso(x["start"]), iso(x["end"]), x["star"], x["sub"]] for x in rep["moon_pin_windows"]],
        "sun_pin_today": rep["sun_pin_today"], "transits": rep["transit_snapshot"],
    }
for x in kr.pin_period(ch, rules["job"], now, now + timedelta(days=730)):
    out["pins"].append([iso(x["start"]), iso(x["end"]), x["star"], x["sub"], list(x["chain"])])
out["hora"] = {}
for day in ("2026-10-16", "2026-10-21"):
    d0 = datetime.fromisoformat(day).replace(tzinfo=IST); hr = kh.hora_report(ctx, d0)
    pw = lambda ws: [[iso(x["start"]), iso(x["end"]), x["hora"], x["sub"], x["kind"]] for x in ws]
    out["hora"][day] = {"day_lord": hr["day_lord"], "pair": list(hr["dasha_pair"]),
        "horas": [[iso(h["start"]), iso(h["end"]), h["lord"], h["night"]] for h in hr["horas"]],
        "dasha_pins": pw(hr["dasha_pins"]), "muhurta": {k: pw(v) for k, v in hr["muhurta"].items()}}
out["birth_tol"] = kr.birth_time_tolerance(specs["user2000"][0], 26.4446, 91.4373, 30)
print(json.dumps(out, default=lambda o: sorted(o) if isinstance(o, set) else str(o)))
