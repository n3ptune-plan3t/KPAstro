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
    ok(rules.getValue("first_job").cusp == 6 && rules.getValue("first_job").promiseMode == "star_all",
        "first_job uses the Reader 6 6th-cusp star condition")
    ok(rules.getValue("first_private_job").promiseMode == "star_sub_all",
        "private job checks star and sub channels separately")
    ok(rules.getValue("love_affair").promiseMode == "star_all" &&
        rules.getValue("recovery_from_illness").promiseMode == "star_all",
        "love affair and recovery use their Reader-specified star channel")
    ok(rules.getValue("marriage").subDenialOwners == listOf(1, 6, 10) &&
        rules.getValue("marriage").subDenialSignifies == listOf(12),
        "marriage veto uses the Reader 4 owner-plus-signification condition")

    ok(sensitiveZoneTable().size == 249, "Reader 5 has 249 sign/star/sub zones")
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
    ok(life.all { e -> e.windows.all { w -> w.lords.all { it.size == 3 } } },
        "life forecast windows are evaluated at Dasa-Bhukti-Anthra depth")
    ok(life.all { e -> e.promise.verdict != "strong" || e.promise.support.isNotEmpty() },
        "a strong event promise must have explicit supporting houses")
    val pins = pinPeriod(ch, rules.getValue("job"), now, now.plusSeconds(730L * 86400)); ok(pins.size == l(g["pins"]).size, "pins count ${pins.size}")
    val ctx = Context(Profile("t", LocalDateTime.of(2000, 11, 21, 18, 12), Place(ist, 26.4446, 91.4373), Place(ist, 26.4446, 91.4373)), rules)
    val tierGroup = Group(setOf(2, 6), setOf(8), "verified", "tier test")
    ok(netStrength(mapOf(2 to 4), tierGroup) > netStrength(mapOf(2 to 1), tierGroup),
        "A-tier support outranks D-tier support")
    ok(netStrength(mapOf(8 to 4), tierGroup) < netStrength(emptyMap(), tierGroup),
        "A-tier denial reduces indication")

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
    // Transit-zone scanner must refine a sampled exit boundary to within one second.
    val scanStart = Instant.parse("2026-01-01T00:00:00Z")
    val scanEnd = scanStart.plusSeconds(600)
    val target = SensitivePoint("*", "Sun", "Moon")
    val synthetic = scanLords(
        "Moon", listOf(target), scanStart, scanEnd, 10, "hour", 3,
        listOf("Sun", "Moon"), 0, "scanner_test", "synthetic",
    ) { at ->
        if (at.isBefore(scanStart.plusSeconds(300))) Lords("Aries", "Sun", "Moon", "Mars")
        else Lords("Aries", "Sun", "Mars", "Mars")
    }
    ok(synthetic.size == 1 && synthetic[0].start == scanStart &&
        Math.abs(Duration.between(synthetic[0].end, scanStart.plusSeconds(300)).seconds) <= 1,
        "scanLords refines exit boundary")
    val syntheticEntry = scanLords(
        "Moon", listOf(target), scanStart, scanEnd, 10, "hour", 3,
        listOf("Sun", "Moon"), 0, "scanner_test", "synthetic",
    ) { at ->
        if (at.isBefore(scanStart.plusSeconds(300))) Lords("Aries", "Sun", "Mars", "Mars")
        else Lords("Aries", "Sun", "Moon", "Mars")
    }
    ok(syntheticEntry.size == 1 &&
        Math.abs(Duration.between(syntheticEntry[0].start, scanStart.plusSeconds(300)).seconds) <= 1 &&
        syntheticEntry[0].end == scanEnd, "scanLords refines entry boundary")

    val old = ExactWindow(scanStart.minusSeconds(7200), scanStart.minusSeconds(7100), "minute", 4,
        "Asc", target, listOf("Sun", "Moon"), "test", 10, "test")
    val upcoming = ExactWindow(scanStart.plusSeconds(3600), scanStart.plusSeconds(3700), "hour", 3,
        "Moon", target, listOf("Sun", "Moon"), "test", 8, "test")
    val event = EventTiming(rules.getValue("job"), Promise("Sun", emptyList(), emptyList(), emptyList(), "strong"),
        emptyList(), listOf(old, upcoming))
    ok(bestExact(event, scanStart) == upcoming, "bestExact prefers an upcoming window")
    ok(nextExact(event, scanStart) == upcoming, "nextExact chooses earliest upcoming window")
    println("checks=$checks fails=$fails"); if (fails > 0) System.exit(1)
}
