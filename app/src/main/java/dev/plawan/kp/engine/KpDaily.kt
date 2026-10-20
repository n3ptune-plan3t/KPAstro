package dev.plawan.kp.engine

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Port of kp_daily.py. The 0-100 score is a presentation layer with transparent heuristic weights (below);
// the Readers define no numeric score. Output = "indications per KP rules", not guarantees.

object Cfg {
    const val SLOT_MIN = 30
    val USABLE_HOURS = 5 until 23
    const val CHAIN_DEPTH = 4
    val CHAIN_W = listOf(1, 2, 3, 4)
    val COMP_W = mapOf("dasha" to 0.35, "moon" to 0.30, "transit" to 0.20, "ruling" to 0.15)
    const val STAR_W = 1; const val SUB_W = 2
    const val PIN_BOOST = 0.5
    const val REF_DAYS = 60; const val REF_STEP = 3
    const val MIN_SPREAD = 4.0
    const val SCORE_GAIN = 1.8
    val TRANSIT_BODIES = linkedMapOf("Sun" to (Swe.SUN to 1), "Mars" to (Swe.MARS to 1), "Mercury" to (Swe.MERCURY to 1),
        "Venus" to (Swe.VENUS to 1), "Jupiter" to (Swe.JUPITER to 2), "Saturn" to (Swe.SATURN to 2), "Rahu" to (Swe.MEAN_NODE to 2))
    val RP_W = linkedMapOf("moon_sub" to 4, "moon_star" to 3, "moon_sign" to 2, "day" to 1)
}

data class AreaDef(val key: String, val label: String, val rule: String?, val support: List<Int>, val denial: List<Int>, val status: String,
                   val goodFor: String, val avoid: String)

// Sources for the two explicit-group areas (all others come from rules.json):
//  money  support 2,6,10,11 = Reader 3 ~l.12380-12392 (2,10,11 make money; 6 borrowing; 12 expenses deducted from the benefits to leave the profit shown by 11)
//         and ~l.12930-12938 (debt realised in periods of 2,6,10,11). Denial 12 = expenses (same lines). Denial 8 = 'loss and misery or unexpected gains'
//         (Reader 3 ~l.12388) is ambiguous in the book -> status stays "partial".
//  health support 1,5,11 = cure/recovery: KP-E-Zine Jan2015-Oct2020 ~l.154147, 699160, 752258; Feb2007-Dec2010 ~l.61178; Reader 3 ~l.8715-8719, 10818-10822.
//         denial 6,8,12 = disease, danger, defect: Reader 3 ~l.17665 (also ~l.9864); 'if connected with 6,12 no cure' E-Zine ~l.752258.
val AREAS = listOf(
    AreaDef("career", "Career & work", "job", emptyList(), emptyList(), "", "interviews, meetings, pitching, starting or submitting work", "big career decisions, arguments with seniors"),
    AreaDef("money", "Money & gains", null, listOf(2, 6, 10, 11), listOf(8, 12), "partial", "negotiating, collecting dues, planned purchases, income work", "impulsive spending, lending, risky bets"),
    AreaDef("relationships", "Relationships", "marriage", emptyList(), emptyList(), "", "important conversations, meeting people, family plans", "heated discussions, new commitments made in a hurry"),
    AreaDef("health", "Health & vitality", null, listOf(1, 5, 11), listOf(6, 8, 12), "verified", "exercise, routine check-ups, starting healthy habits", "overexertion, ignoring tiredness or routine"),
    AreaDef("study", "Study & learning", "higher_education", emptyList(), emptyList(), "", "learning new topics, exams, research, applications", "starting heavy new subjects, last-minute cramming"),
    AreaDef("travel", "Travel & change", "foreign_travel", emptyList(), emptyList(), "", "trips, relocation errands, visa or booking work", "unnecessary or rushed journeys"),
)

data class PlanetThing(val colour: String, val number: Int, val direction: String)
// Popular correspondences, NOT from the KP Readers.
val PLANET_THINGS = mapOf(
    "Sun" to PlanetThing("orange / red", 1, "East"), "Moon" to PlanetThing("white / silver", 2, "North-West"),
    "Mars" to PlanetThing("red / maroon", 9, "South"), "Mercury" to PlanetThing("green", 5, "North"),
    "Jupiter" to PlanetThing("yellow / gold", 3, "North-East"), "Venus" to PlanetThing("white / pink", 6, "South-East"),
    "Saturn" to PlanetThing("dark blue / black", 8, "West"), "Rahu" to PlanetThing("smoky grey / dark", 4, "South-West"),
    "Ketu" to PlanetThing("mixed / earthy brown", 7, "South-West"),
)

// ------------------------------------------------------------------ profile + context
data class Place(val zone: ZoneId, val lat: Double, val lon: Double)
data class Profile(val name: String, val birthLocal: LocalDateTime, val birth: Place, val current: Place)

class Group(val support: Set<Int>, val denial: Set<Int>, val status: String, val label: String)

fun net(hs: Set<Int>, g: Group): Double =
    hs.intersect(g.support).size.toDouble() / g.support.size - hs.intersect(g.denial).size.toDouble() / g.denial.size

private fun sgn(x: Double) = if (x > 0) 1 else if (x < 0) -1 else 0
private fun clamp(x: Double, lo: Double = -1.0, hi: Double = 1.0) = maxOf(lo, minOf(hi, x))

class Context(val profile: Profile, val rules: Map<String, Rule>) {
    val birthDt: ZonedDateTime = profile.birthLocal.atZone(profile.birth.zone)
    val chart = computeChart(birthDt, profile.birth.lat, profile.birth.lon)
    val zone: ZoneId = profile.current.zone
    val lat = profile.current.lat; val lon = profile.current.lon
    val areas: LinkedHashMap<String, Group> = LinkedHashMap<String, Group>().also { m ->
        for (a in AREAS) {
            val r = a.rule?.let { rules.getValue(it) }
            m[a.key] = if (r != null) Group(r.support.toSet(), r.denial.toSet(), r.status, a.label) else Group(a.support.toSet(), a.denial.toSet(), a.status, a.label)
        }
    }
    val H: Map<String, Set<Int>> = chart.planets.keys.associateWith { planetHousesSignified(chart, it).toSet() }
    val mds = mahadashas(chart)
    internal val ref = LinkedHashMap<Instant, List<Map<String, Double>>>()

    fun lordNet(lord: String, g: Group): Double {
        var v = net(H.getValue(lord), g)
        if (net(H.getValue(chart.planets.getValue(lord).subLord), g) < 0) v = minOf(v, 0.0)
        return v
    }
    fun tone(star: String, sub: String, g: Group) =
        (Cfg.STAR_W * net(H.getValue(star), g) + Cfg.SUB_W * net(H.getValue(sub), g)) / (Cfg.STAR_W + Cfg.SUB_W)
}

fun combine(D: Double, M: Double, T: Double, R: Double): Double {
    val c = linkedMapOf("dasha" to D, "moon" to M, "transit" to T, "ruling" to R)
    for (k in listOf("moon", "transit", "ruling")) if (D * c.getValue(k) < 0) c[k] = c.getValue(k) * 0.5
    val tot = c.entries.sumOf { Cfg.COMP_W.getValue(it.key) * it.value } / Cfg.COMP_W.values.sum()
    return maxOf(0.0, minOf(100.0, 50 + 50 * clamp(tot * Cfg.SCORE_GAIN)))
}

fun chainAt(mds: List<Period>, t: Instant, depth: Int): List<String>? {
    var cur = mds.firstOrNull { it.contains(t) } ?: return null
    while (cur.lords.size < depth) { val subs = subPeriods(cur); cur = subs.firstOrNull { it.contains(t) } ?: subs.last() }
    return cur.lords
}

// ------------------------------------------------------------------ slots
data class AreaSlot(val score: Double, val D: Double, val M: Double, val T: Double, val R: Double)
data class Triple3(val signLord: String, val star: String, val sub: String)
class Slot(
    val t: Instant, val chain: List<String>, val moon: Pair<String, String>, val bodies: Map<String, Triple3>,
    val moonPin: Boolean, val sunPin: Boolean, val rp: Map<String, String>, val areas: Map<String, AreaSlot>,
) { val overall get() = areas.values.sumOf { it.score } / areas.size }

private fun lords3(jd: Double, body: Int): Triple3 { val l = lordsAt(Swe.lon(jd, body)); return Triple3(l.sign, l.star, l.sub) }

fun sunriseUtc(d0: ZonedDateTime, lat: Double, lon: Double): Instant {
    val u = d0.toInstant(); val jd = Swe.jd(u.truncatedTo(ChronoUnit.MINUTES))
    val (res, tret) = Swe.sunrise(jd, lat, lon)
    return if (res < 0) u.plusSeconds(6 * 3600) else Swe.instant(tret)
}

fun slotScores(ctx: Context, tMid: Instant, dayLord: String): Slot {
    val jd = Swe.jd(tMid)
    val chain = chainAt(ctx.mds, tMid, Cfg.CHAIN_DEPTH)!!
    val moon = lords3(jd, Swe.MOON)
    val bodies = LinkedHashMap<String, Triple3>()
    for ((n, bw) in Cfg.TRANSIT_BODIES) bodies[n] = lords3(jd, bw.first)
    val kl = Swe.lon(jd, Swe.MEAN_NODE) + 180
    val kLords = lordsAt(kl)
    bodies["Ketu"] = Triple3(SIGN_LORDS[((((kl % 360) + 360) % 360) / 30).toInt()], kLords.star, kLords.sub)
    val weights = Cfg.TRANSIT_BODIES.mapValues { it.value.second }.toMutableMap().also { it["Ketu"] = it.getValue("Rahu") }

    val pairs = (0 until chain.size - 1).map { chain[it] to chain[it + 1] }
    val moonPin = (moon.star to moon.sub) in pairs || (moon.sub to moon.star) in pairs
    val sunPairs = pairs.take(2); val sun = bodies.getValue("Sun")
    val sunPin = (sun.star to sun.sub) in sunPairs || (sun.sub to sun.star) in sunPairs
    val rp = linkedMapOf("moon_sub" to moon.sub, "moon_star" to moon.star, "moon_sign" to moon.signLord, "day" to dayLord)

    val res = LinkedHashMap<String, AreaSlot>()
    for ((a, g) in ctx.areas) {
        val wsum = Cfg.CHAIN_W.take(chain.size).sum().toDouble()
        val D = Cfg.CHAIN_W.zip(chain).sumOf { (w, l) -> w * ctx.lordNet(l, g) } / wsum
        var M = ctx.tone(moon.star, moon.sub, g)
        if (moonPin && D != 0.0) M = clamp(M + Cfg.PIN_BOOST * sgn(D))
        var T = bodies.entries.sumOf { (n, v) -> weights.getValue(n) * ctx.tone(v.star, v.sub, g) } / weights.values.sum()
        if (sunPin && D != 0.0) T = clamp(T + Cfg.PIN_BOOST * sgn(D))
        val R = rp.entries.sumOf { (k, v) -> Cfg.RP_W.getValue(k) * net(ctx.H.getValue(v), g) } / Cfg.RP_W.values.sum()
        res[a] = AreaSlot(combine(D, M, T, R), D, M, T, R)
    }
    return Slot(tMid, chain, moon.star to moon.sub, bodies, moonPin, sunPin, rp, res)
}

data class TWindow(val start: Instant, val end: Instant, val n: Int = 0)

fun blocks(slots: List<Slot>, minSlots: Int = 2, pred: (Slot) -> Boolean): List<TWindow> {
    val out = ArrayList<TWindow>(); var cur: TWindow? = null; val half = Cfg.SLOT_MIN * 30L
    for (s in slots) {
        if (pred(s)) cur = if (cur == null) TWindow(s.t.minusSeconds(half), s.t.plusSeconds(half), 1) else TWindow(cur.start, s.t.plusSeconds(half), cur.n + 1)
        else if (cur != null) { out.add(cur); cur = null }
    }
    cur?.let { out.add(it) }
    return out.filter { it.n >= minSlots }
}

/** Top-quartile ('best') or bottom-quartile ('worst') windows, tie-safe (see kp_daily.pick_windows). */
fun pickWindows(usable: List<Slot>, best: Boolean, key: (Slot) -> Double): List<TWindow> {
    val vals = usable.map(key).sorted()
    if (vals.last() - vals.first() < Cfg.MIN_SPREAD) return emptyList()
    val q = vals[(vals.size * (if (best) 0.75 else 0.25)).toInt()]
    val loose: (Double) -> Boolean = if (best) { v -> v >= q } else { v -> v <= q }
    val strict: (Double) -> Boolean = if (best) { v -> v > q } else { v -> v < q }
    for (f in listOf(loose, strict)) {
        val n = usable.count { f(key(it)) }
        if (n > 0 && n <= usable.size / 2.0) return blocks(usable) { f(key(it)) }
    }
    return emptyList()
}

fun verdictLabel(s: Double) = when { s >= 80 -> "Very favourable"; s >= 60 -> "Favourable"; s >= 40 -> "Average"; s >= 20 -> "Challenging"; else -> "Difficult" }
fun backdropLabel(D: Double): String { val sc = 50 + 50 * clamp(D * Cfg.SCORE_GAIN); return if (sc >= 60) "supportive" else if (sc >= 42) "mixed" else "heavy" }
fun percentile(x: Double, ref: List<Double>) = 100.0 * (ref.count { it < x } + 0.5 * ref.count { it == x }) / ref.size

class DaySlots(val slots: List<Slot>, val usable: List<Slot>, val sunrise: Instant, val prevLord: String, val todayLord: String)

fun daySlots(ctx: Context, d0: ZonedDateTime): DaySlots {
    val sunrise = sunriseUtc(d0, ctx.lat, ctx.lon)
    val prev = WEEKDAY_LORD[d0.minusDays(1).dayOfWeek.value - 1]; val today = WEEKDAY_LORD[d0.dayOfWeek.value - 1]
    val n = 24 * 60 / Cfg.SLOT_MIN; val start = d0.toInstant()
    val slots = (0 until n).map { i ->
        val tMid = start.plusSeconds(Cfg.SLOT_MIN * 60L * i + Cfg.SLOT_MIN * 30L)
        slotScores(ctx, tMid, if (tMid.isBefore(sunrise)) prev else today)
    }
    val usable = slots.filter { it.t.atZone(ctx.zone).hour in Cfg.USABLE_HOURS }
    return DaySlots(slots, usable, sunrise, prev, today)
}

private fun rawMeans(ctx: Context, usable: List<Slot>): Map<String, Double> {
    val out = LinkedHashMap<String, Double>()
    for (a in ctx.areas.keys) out[a] = usable.sumOf { it.areas.getValue(a).score } / usable.size
    out["overall"] = usable.sumOf { it.overall } / usable.size
    return out
}

private fun reference(ctx: Context, d0: ZonedDateTime): List<Map<String, Double>> {
    val t0 = d0.toInstant()
    val key = ctx.ref.keys.firstOrNull { Math.abs(Math.floorDiv(Duration.between(it, t0).seconds, 86400L)) <= Cfg.REF_DAYS / 2 }
    if (key != null) return ctx.ref.getValue(key)
    val l = (-Cfg.REF_DAYS..Cfg.REF_DAYS step Cfg.REF_STEP).map { rawMeans(ctx, daySlots(ctx, d0.plusDays(it.toLong())).usable) }
    ctx.ref[t0] = l; return l
}

// ------------------------------------------------------------------ report
data class Driver(val name: String, val effect: String)
data class AreaReport(
    val key: String, val label: String, val score: Int, val verdict: String, val D: Double, val M: Double, val T: Double, val R: Double,
    val raw: Double, val backdrop: String, val sourceStatus: String, val best: List<TWindow>, val avoid: List<TWindow>,
    val tone: String, val mainDriver: Driver, val nextDriver: Driver,
)
data class MoonPin(val start: Instant, val end: Instant, val star: String, val sub: String, val chain: List<String>)
data class LuckyPlanet(val planet: String, val thing: PlanetThing)
data class DayReport(
    val date: LocalDate, val weekday: String, val zone: ZoneId, val sunrise: Instant, val dayLord: String, val dashaChain: List<String>,
    val overallScore: Int, val overallVerdict: String, val overallRaw: Double, val overallBackdrop: String,
    val areas: List<AreaReport>, val bestAreas: List<String>, val weakAreas: List<String>,
    val lucky: List<TWindow>, val avoid: List<TWindow>, val moonPins: List<MoonPin>, val sunPinToday: Boolean,
    val transits: Map<String, Triple3>, val luckyPlanet: LuckyPlanet, val stressPlanet: LuckyPlanet,
    val goodFor: List<String>, val beCareful: List<String>, val personal: List<LuckyWindow>?,
)

private fun effectWord(v: Double) = if (v > 0.05) "helps" else if (v < -0.05) "pulls against" else "stays neutral on"

fun dayReport(ctx: Context, day: ZonedDateTime, diary: Map<String, SubScore>? = null): DayReport {
    val d0 = day.withZoneSameInstant(ctx.zone).truncatedTo(ChronoUnit.DAYS)
    val ds = daySlots(ctx, d0); val usable = ds.usable
    val raw = rawMeans(ctx, usable); val ref = reference(ctx, d0)

    val areas = AREAS.map { def ->
        val a = def.key; val g = ctx.areas.getValue(a)
        val avg = percentile(raw.getValue(a), ref.map { it.getValue(a) })
        fun comp(f: (AreaSlot) -> Double) = usable.sumOf { f(it.areas.getValue(a)) } / usable.size
        val D = comp { it.D }; val M = comp { it.M }; val T = comp { it.T }; val R = comp { it.R }
        val key = { s: Slot -> s.areas.getValue(a).score }
        val best = pickWindows(usable, true, key); val worst = pickWindows(usable, false, key)
        val drivers = listOf("running dasha chain" to D, "Moon's star/sub lords" to M, "other transits" to T, "ruling planets" to R).sortedByDescending { Math.abs(it.second) }
        val tone = if (avg >= 60) "A better-than-usual day for this" else if (avg >= 40) "An ordinary day for this" else "A heavier-than-usual day for this"
        AreaReport(a, g.label, Math.rint(avg).toInt(), verdictLabel(avg), D, M, T, R, Math.round(raw.getValue(a) * 10) / 10.0, backdropLabel(D), g.status,
            best, worst, tone, Driver(drivers[0].first, effectWord(drivers[0].second)), Driver(drivers[1].first, effectWord(drivers[1].second)))
    }
    val avgOv = percentile(raw.getValue("overall"), ref.map { it.getValue("overall") })
    val lucky = pickWindows(usable, true) { it.overall }; val unlucky = pickWindows(usable, false) { it.overall }

    val dayLordsSeen = usable.map { it.rp.getValue("day") }.toSet()
    val cands = LinkedHashSet<String>().apply { add(ds.todayLord); add(ds.prevLord); usable.forEach { add(it.moon.first) }; usable.forEach { add(it.moon.second) } }
    if (ds.prevLord !in dayLordsSeen) cands.remove(ds.prevLord)
    fun planetNet(p: String) = ctx.areas.values.sumOf { net(ctx.H.getValue(p), it) } / ctx.areas.size
    val ranked = cands.sortedByDescending { planetNet(it) }
    val luckyP = ranked.first(); val stressP = ranked.last()

    val pins = moonWindows(d0.toInstant(), 5).mapNotNull { w ->
        val chain = chainAt(ctx.mds, w.start, Cfg.CHAIN_DEPTH)!!
        val pairs = (0 until chain.size - 1).map { chain[it] to chain[it + 1] }
        if ((w.star to w.sub) in pairs || (w.sub to w.star) in pairs) MoonPin(w.start, w.end, w.star, w.sub, chain) else null
    }
    val personal = diary?.let { luckyWindows(it, d0.toInstant()).filter { w -> w.tag != null } }

    val noon = ds.slots[ds.slots.size / 2]
    val rankedAreas = areas.sortedByDescending { it.score }.map { it.key }
    val best2 = rankedAreas.take(2); val weak2 = rankedAreas.takeLast(2)
    fun def(k: String) = AREAS.first { it.key == k }
    fun sc(k: String) = areas.first { it.key == k }.score
    return DayReport(
        d0.toLocalDate(), d0.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH), ctx.zone, ds.sunrise, ds.todayLord, noon.chain,
        Math.rint(avgOv).toInt(), verdictLabel(avgOv), Math.round(raw.getValue("overall") * 10) / 10.0, backdropLabel(areas.sumOf { it.D } / areas.size),
        areas, best2, weak2, lucky, unlucky, pins, usable.any { it.sunPin }, noon.bodies,
        LuckyPlanet(luckyP, PLANET_THINGS.getValue(luckyP)), LuckyPlanet(stressP, PLANET_THINGS.getValue(stressP)),
        best2.filter { sc(it) >= 60 }.map { def(it).goodFor }, weak2.filter { sc(it) <= 40 }.map { def(it).avoid }, personal,
    )
}
