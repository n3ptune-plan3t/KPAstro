import dev.plawan.kp.engine.*
import java.time.*

var fails = 0; var checks = 0
fun ok(c: Boolean, msg: String) { checks++; if (!c) { fails++; println("FAIL: $msg") } }
fun near(a: Double, b: Double, tol: Double, msg: String) = ok(Math.abs(a - b) <= tol, "$msg kotlin=$a golden=$b")
@Suppress("UNCHECKED_CAST") fun m(o: Any?) = o as Map<String, Any?>
@Suppress("UNCHECKED_CAST") fun l(o: Any?) = o as List<Any?>
fun d(o: Any?) = o as Double
fun inst(o: Any?) = OffsetDateTime.parse(o as String).toInstant()
fun sec(a: Instant, b: Instant) = Math.abs(Duration.between(a, b).seconds)
fun wins(o: Any?) = l(o).map { l(it) }

fun main(args: Array<String>) {
    val g = m(MiniJson.parse(java.io.File(args[0]).readText()))
    val rules = parseRules(java.io.File(args[1]).readText())
    for ((k, v) in m(g["sensitive"] ?: emptyMap<String, Any?>())) {
        val got = sensitivePoints(k.split("-")).map { listOf(it.sign, it.star, it.sub).joinToString("/") }
        ok(got == l(v).map { l(it).joinToString("/") }, "sensitive $k")
    }
    val ist = ZoneId.of("Asia/Kolkata")
    for ((k, cv) in m(g["charts"])) {
        val c = m(cv); val ch = computeChart(inst(c["dt"]), d(c["lat"]), d(c["lon"]))
        near(ch.ayanamsa, d(c["ayanamsa"]), 1e-6, "$k ayanamsa")
        val asc = l(c["asc"]); near(ch.asc.lon, d(asc[0]), 1e-5, "$k asc"); ok(listOf(ch.asc.starLord, ch.asc.subLord, ch.asc.subSubLord) == asc.drop(1), "$k asc lords")
        for ((n, pv) in m(c["planets"])) { val p = l(pv); val q = ch.planets.getValue(n)
            near(q.lon, d(p[0]), 1e-5, "$k $n lon"); ok(q.house == (d(p[1])).toInt(), "$k $n house")
            ok(listOf(q.signLord, q.starLord, q.subLord, q.subSubLord) == p.subList(2, 6), "$k $n lords"); ok(q.retrograde == p[6], "$k $n retro") }
        l(c["cusps"]).forEachIndexed { i, cv2 -> val x = l(cv2); val q = ch.cusps[i]
            near(q.lon, d(x[0]), 1e-5, "$k H${i + 1}"); ok(listOf(q.signLord, q.starLord, q.subLord, q.subSubLord) == x.drop(1), "$k H${i + 1} lords") }
        for ((n, hv) in m(c["houses"])) ok(planetHousesSignified(ch, n) == l(hv).map { d(it).toInt() }, "$k houses $n")
        for ((n, av) in m(c["agents"])) ok(nodeAgents(ch, n) == l(av), "$k agents $n")
        val mds = mahadashas(ch); l(c["mds"]).forEachIndexed { i, mv -> val x = l(mv)
            ok(mds[i].lords[0] == x[0], "$k md lord"); ok(sec(mds[i].start, inst(x[1])) <= 2 && sec(mds[i].end, inst(x[2])) <= 2, "$k md dates $i") }
    }
    val u = m(m(g["charts"])["user2000"]); val ch = computeChart(inst(u["dt"]), d(u["lat"]), d(u["lon"]))
    for (rv in l(g["ruling"])) { val r = m(rv); val t = inst(r["at"]).atZone(ist); val rp = rulingPlanets(t, d(u["lat"]), d(u["lon"]))
        ok(rp.ordered == l(r["ordered"]) && rp.derived == l(r["derived"]) && rp.chain == m(r["chain"]), "ruling ${r["at"]}: ${rp.chain} vs ${r["chain"]}") }
    val now = ZonedDateTime.of(2026, 10, 15, 9, 0, 0, 0, ist).toInstant()
    ok(periodsAt(ch, now, 4)!!.lords == l(g["periods_at"]), "periods_at")
    val life = lifeReport(ch, rules, 15, now)
    for (e in life) { val gv = m(m(g["life"])[e.rule.key]); val gp = m(gv["promise"])
        ok(e.promise.verdict == gp["verdict"] && e.promise.cuspSubLord == gp["cusp_sub_lord"] && e.promise.support == l(gp["support"]).map { d(it).toInt() }, "promise ${e.rule.key}")
        val gpa = l(gv["parents"]).map { m(it) }
        ok(e.parents.size == gpa.size && e.parents.zip(gpa).all { (a, b) -> a.key == b["key"] && a.verdict == b["verdict"] && a.promised == b["promised"] }, "parents ${e.rule.key}")
        val gw = wins(gv["windows"]); ok(e.windows.size == gw.size, "windows count ${e.rule.key}: ${e.windows.size} vs ${gw.size}")
        e.windows.zip(gw).forEach { (a, b) -> ok(sec(a.start, inst(b[0])) <= 2 && sec(a.end, inst(b[1])) <= 2 && a.lords == l(b[2]).map { x -> l(x) } && a.parentActive == b[3] && a.parents.toList() == l(b[4]), "window ${e.rule.key}") } }
    val pins = pinPeriod(ch, rules.getValue("job"), now, now.plusSeconds(730L * 86400)); ok(pins.size == l(g["pins"]).size, "pins count ${pins.size}")
    val ctx = Context(Profile("t", LocalDateTime.of(2000, 11, 21, 18, 12), Place(ist, 26.4446, 91.4373), Place(ist, 26.4446, 91.4373)), rules)
    for ((day, rv) in m(g["days"])) { val r = m(rv); val rep = dayReport(ctx, LocalDate.parse(day).atStartOfDay(ist))
        val ov = m(r["overall"]); ok(rep.overallScore == d(ov["score"]).toInt(), "$day overall ${rep.overallScore} vs ${ov["score"]}"); near(rep.overallRaw, d(ov["raw"]), 0.051, "$day raw")
        ok(rep.dayLord == r["day_lord"] && rep.dashaChain == l(r["dasha_chain"]), "$day dayLord/chain")
        for ((a, av) in m(r["areas"])) { val x = m(av); val q = rep.areas.first { it.key == a }
            ok(q.score == d(x["score"]).toInt(), "$day $a score ${q.score} vs ${x["score"]}"); near(q.D, d(x["D"]), 1e-6, "$day $a D"); near(q.M, d(x["M"]), 1e-6, "$day $a M")
            near(q.T, d(x["T"]), 1e-6, "$day $a T"); near(q.R, d(x["R"]), 1e-6, "$day $a R"); ok(q.backdrop == x["backdrop"], "$day $a backdrop")
            fun same(w: List<TWindow>, o: Any?, n: String) { val gw = wins(o); ok(w.size == gw.size && w.zip(gw).all { (p, z) -> sec(p.start, inst(z[0])) <= 2 && sec(p.end, inst(z[1])) <= 2 }, "$day $a $n windows") }
            same(q.best, x["best"], "best"); same(q.avoid, x["avoid"], "avoid") }
        val gl = wins(r["lucky"]); ok(rep.lucky.size == gl.size && rep.lucky.zip(gl).all { (p, z) -> sec(p.start, inst(z[0])) <= 2 }, "$day lucky")
        ok(rep.moonPins.size == l(r["moon_pins"]).size && rep.sunPinToday == r["sun_pin_today"], "$day pins")
        for ((n, tv) in m(r["transits"])) { val t = m(tv); val q = rep.transits.getValue(n); ok(q.star == t["star"] && q.sub == t["sub"] && q.signLord == t["sign_lord"], "$day transit $n") } }
    for ((day, hv) in m(g["hora"])) { val h = m(hv); val hr = horaReport(ctx, LocalDate.parse(day).atStartOfDay(ist))
        ok(hr.dayLord == h["day_lord"] && listOf(hr.dashaPair.first, hr.dashaPair.second) == l(h["pair"]), "$day hora lord/pair")
        val gh = l(h["horas"]).map { l(it) }; ok(hr.horas.size == gh.size, "$day hora count")
        hr.horas.zip(gh).forEach { (a, b) -> ok(sec(a.start, inst(b[0])) <= 2 && sec(a.end, inst(b[1])) <= 2 && a.lord == b[2] && a.night == b[3], "$day hora ${a.lord}") }
        fun pins(ws: List<HoraPin>, o: Any?, n: String) { val gp = l(o).map { l(it) }; ok(ws.size == gp.size && ws.zip(gp).all { (p, z) -> sec(p.start, inst(z[0])) <= 2 && sec(p.end, inst(z[1])) <= 2 && p.hora == z[2] && p.sub == z[3] && p.kind == z[4] }, "$day $n pins ${ws.size} vs ${gp.size}") }
        pins(hr.dashaPins, h["dasha_pins"], "dasha"); for ((k, v) in m(h["muhurta"])) pins(hr.muhurta.getValue(k), v, k) }
    val tol = birthTimeTolerance(inst(u["dt"]), d(u["lat"]), d(u["lon"]), 30)
    for ((n, tv) in m(g["birth_tol"])) { val t = m(tv); val q = tol.getValue(n)
        ok(q.sub == t["sub"] && q.before == (t["before"] as Double?)?.toInt() && q.after == (t["after"] as Double?)?.toInt(), "tol $n ${q} vs $t") }
    println("checks=$checks fails=$fails"); if (fails > 0) System.exit(1)
}
