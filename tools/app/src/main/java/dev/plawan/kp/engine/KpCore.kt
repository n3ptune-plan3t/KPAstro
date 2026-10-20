package dev.plawan.kp.engine

import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.min

// Port of kp_core.py: ephemeris -> houses -> star/sub/sub-sub lords -> significators -> Vimshottari -> ruling planets.

val SIGNS = listOf("Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces")
val SIGN_LORDS = listOf("Mars", "Venus", "Mercury", "Moon", "Sun", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Saturn", "Jupiter")
val VIM_ORDER = listOf("Ketu", "Venus", "Sun", "Moon", "Mars", "Rahu", "Jupiter", "Saturn", "Mercury")
val VIM_YEARS = mapOf("Ketu" to 7, "Venus" to 20, "Sun" to 6, "Moon" to 10, "Mars" to 7, "Rahu" to 18, "Jupiter" to 16, "Saturn" to 19, "Mercury" to 17)
const val NAK_SPAN = 360.0 / 27
const val YEAR_DAYS = 365.25
val WEEKDAY_LORD = listOf("Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Sun")   // Mon..Sun
val BODIES = linkedMapOf("Sun" to Swe.SUN, "Moon" to Swe.MOON, "Mars" to Swe.MARS, "Mercury" to Swe.MERCURY,
    "Jupiter" to Swe.JUPITER, "Venus" to Swe.VENUS, "Saturn" to Swe.SATURN)
const val NODE_CONJ_ORB = 10.0 / 3
const val NODE_USE_ASPECTS = false
val ASPECT_ANGLES = listOf(60, 90, 120, 180)
const val RAHU_MEAN = true

fun Instant.plusDays(d: Double): Instant = plusNanos((d * 86400e9).toLong())

// ---------------------------------------------------------------- lord tables
data class SubRow(val start: Double, val end: Double, val star: String, val sub: String)
data class Lords(val sign: String, val star: String, val sub: String, val subSub: String)

val SUB_TABLE: List<SubRow> = buildList {
    for (n in 0 until 27) {
        val star = VIM_ORDER[n % 9]; val i = VIM_ORDER.indexOf(star); var pos = n * NAK_SPAN
        for (k in 0 until 9) {
            val sub = VIM_ORDER[(i + k) % 9]; val span = NAK_SPAN * VIM_YEARS.getValue(sub) / 120
            add(SubRow(pos, pos + span, star, sub)); pos += span
        }
    }
}

fun lordsAt(lon0: Double): Lords {
    val lon = ((lon0 % 360.0) + 360.0) % 360.0
    val signLord = SIGN_LORDS[(lon / 30).toInt()]
    val row = SUB_TABLE.firstOrNull { lon >= it.start && lon < it.end } ?: SUB_TABLE.last()
    val i = VIM_ORDER.indexOf(row.sub); val span = row.end - row.start; var pos = row.start
    for (k in 0 until 9) {
        val ss = VIM_ORDER[(i + k) % 9]; val w = span * VIM_YEARS.getValue(ss) / 120
        if ((lon >= pos && lon < pos + w) || k == 8) return Lords(signLord, row.star, row.sub, ss)
        pos += w
    }
    error("unreachable")
}

// ---------------------------------------------------------------- chart
data class Point(
    val name: String, val lon: Double, val speed: Double, val sign: String, val signLord: String,
    val starLord: String, val subLord: String, val subSubLord: String, var house: Int = 0,
) {
    val retrograde get() = speed < 0
    val degInSign get() = lon % 30.0
}

fun makePoint(name: String, lon0: Double, speed: Double = 0.0): Point {
    val lon = ((lon0 % 360.0) + 360.0) % 360.0
    val l = lordsAt(lon)
    return Point(name, lon, speed, SIGNS[(lon / 30).toInt()], l.sign, l.star, l.sub, l.subSub)
}

fun houseOf(lon: Double, cusps: List<Double>): Int {
    for (h in 0 until 12) {
        val a = cusps[h]; val b = cusps[(h + 1) % 12]
        if (a <= b) { if (lon >= a && lon < b) return h + 1 } else if (lon >= a || lon < b) return h + 1
    }
    return 1
}

data class Sig(val a: List<String>, val b: List<String>, val c: List<String>, val d: List<String>)

class Chart(
    val dtUtc: Instant, val lat: Double, val lon: Double, val jd: Double, val ayanamsa: Double,
    val planets: LinkedHashMap<String, Point>, val cusps: List<Point>, val asc: Point,
) {
    lateinit var significators: Map<Int, Sig>
}

fun computeChart(dt: ZonedDateTime, lat: Double, lon: Double): Chart = computeChart(dt.toInstant(), lat, lon)

fun computeChart(u: Instant, lat: Double, lon: Double): Chart {
    val jd = Swe.jd(u)
    val planets = LinkedHashMap<String, Point>()
    for ((name, body) in BODIES) { val xx = Swe.body(jd, body); planets[name] = makePoint(name, xx[0], xx[3]) }
    val xx = Swe.body(jd, if (RAHU_MEAN) Swe.MEAN_NODE else 11)
    planets["Rahu"] = makePoint("Rahu", xx[0], xx[3])
    planets["Ketu"] = makePoint("Ketu", xx[0] + 180, xx[3])
    val h = Swe.houses(jd, lat, lon)
    val cuspLons = h.take(12)
    val cusps = cuspLons.mapIndexed { i, c -> makePoint("H${i + 1}", c) }
    val asc = makePoint("Asc", h[12])
    planets.values.forEach { it.house = houseOf(it.lon, cuspLons) }
    return Chart(u, lat, lon, jd, Swe.ayanamsa(jd), planets, cusps, asc).also { it.significators = computeSignificators(it) }
}

// ---------------------------------------------------------------- significators
fun computeSignificators(ch: Chart): Map<Int, Sig> = (1..12).associateWith { h ->
    val occ = ch.planets.values.filter { it.house == h }.map { it.name }
    val lord = ch.cusps[h - 1].signLord
    Sig(
        a = ch.planets.values.filter { it.starLord in occ }.map { it.name }, b = occ,
        c = ch.planets.values.filter { it.starLord == lord }.map { it.name }, d = listOf(lord),
    )
}

fun angle(a: Double, b: Double): Double { val d = abs(a - b) % 360; return min(d, 360 - d) }

fun nodeAgents(ch: Chart, node: String): List<String> {
    val n = ch.planets.getValue(node); val agents = ArrayList<String>()
    for (p in ch.planets.values) {
        if (p.name == "Rahu" || p.name == "Ketu" || p.name == node) continue
        if (angle(p.lon, n.lon) <= NODE_CONJ_ORB) agents.add(p.name)
    }
    if (NODE_USE_ASPECTS) for (p in ch.planets.values) {
        if (p.name == "Rahu" || p.name == "Ketu" || p.name in agents) continue
        if (ASPECT_ANGLES.any { abs(angle(p.lon, n.lon) - it) <= NODE_CONJ_ORB }) agents.add(p.name)
    }
    for (lord in listOf(n.starLord, n.signLord)) if (lord !in agents && lord != "Rahu" && lord != "Ketu") agents.add(lord)
    return agents
}

fun planetHousesSignified(ch: Chart, name: String, withAgents: Boolean = true): List<Int> {
    fun own(pl: String): List<Int> {
        val res = ArrayList<Int>()
        for (sel in listOf<(Sig) -> List<String>>({ it.a }, { it.b }, { it.c }, { it.d }))
            for (h in 1..12) if (pl in sel(ch.significators.getValue(h)) && h !in res) res.add(h)
        return res
    }
    val res = own(name).toMutableList()
    if (withAgents && (name == "Rahu" || name == "Ketu"))
        for (ag in nodeAgents(ch, name)) for (h in own(ag)) if (h !in res) res.add(h)
    return res
}

// ---------------------------------------------------------------- dasha
data class Period(val lords: List<String>, val start: Instant, val end: Instant) {
    fun contains(t: Instant) = !t.isBefore(start) && t.isBefore(end)
    val lengthDays get() = (end.toEpochMilli() - start.toEpochMilli()) / 86400000.0
}

private fun split(lord: String, start: Instant, lengthDays: Double, parent: List<String>): List<Period> {
    val i = VIM_ORDER.indexOf(lord); val out = ArrayList<Period>(); var t = start
    for (k in 0 until 9) {
        val sub = VIM_ORDER[(i + k) % 9]; val d = lengthDays * VIM_YEARS.getValue(sub) / 120
        out.add(Period(parent + sub, t, t.plusDays(d))); t = t.plusDays(d)
    }
    return out
}

fun mahadashas(ch: Chart): List<Period> {
    val moon = ch.planets.getValue("Moon").lon
    val idx = (moon / NAK_SPAN).toInt(); val elapsed = (moon % NAK_SPAN) / NAK_SPAN
    val lord = VIM_ORDER[idx % 9]
    var t = ch.dtUtc.plusDays(-VIM_YEARS.getValue(lord) * YEAR_DAYS * elapsed)
    val out = ArrayList<Period>(); val i = VIM_ORDER.indexOf(lord)
    for (k in 0 until 9) {
        val l = VIM_ORDER[(i + k) % 9]; val d = VIM_YEARS.getValue(l) * YEAR_DAYS
        out.add(Period(listOf(l), t, t.plusDays(d))); t = t.plusDays(d)
    }
    return out
}

fun subPeriods(p: Period): List<Period> = split(p.lords.last(), p.start, p.lengthDays, p.lords)

fun periodsAt(ch: Chart, at: Instant, depth: Int = 3): Period? {
    var cur = mahadashas(ch).firstOrNull { it.contains(at) } ?: return null
    while (cur.lords.size < depth) cur = subPeriods(cur).firstOrNull { it.contains(at) } ?: break
    return cur
}

// ---------------------------------------------------------------- ruling planets
/** KP vara: the day lord changes at local sunrise. `at` must carry the location's local zone. */
fun sunriseDayLord(at: ZonedDateTime, lat: Double, lon: Double): String {
    val jd = Swe.jd(at.toInstant())
    var t = jd - 1.5; var last: Double? = null
    for (n in 0 until 4) {
        val (res, tret) = Swe.sunrise(t, lat, lon)
        if (res < 0) return WEEKDAY_LORD[at.minusHours(6).dayOfWeek.value - 1]   // circumpolar fallback
        if (tret > jd) break
        last = tret; t = tret + 0.01
    }
    return finishDayLord(last, jd, at, lat, lon)
}

private fun finishDayLord(last0: Double?, jd: Double, at: ZonedDateTime, lat: Double, lon: Double): String {
    val last = last0 ?: Swe.sunrise(jd - 2.5, lat, lon).second
    val riseLocal = Swe.instant(last).atZone(at.zone)
    return WEEKDAY_LORD[riseLocal.dayOfWeek.value - 1]
}

data class RulingPlanets(val chain: LinkedHashMap<String, String>, val ordered: List<String>, val derived: List<String>)

fun rulingPlanets(at: ZonedDateTime, lat: Double, lon: Double): RulingPlanets {
    val ch = computeChart(at, lat, lon)
    val m = ch.planets.getValue("Moon"); val a = ch.asc
    val chain = linkedMapOf("asc_sub" to a.subLord, "asc_star" to a.starLord, "asc_sign" to a.signLord,
        "moon_sub" to m.subLord, "moon_star" to m.starLord, "moon_sign" to m.signLord, "day" to sunriseDayLord(at, lat, lon))
    val ordered = ArrayList<String>(); chain.values.forEach { if (it !in ordered) ordered.add(it) }
    val derived = listOf("Rahu", "Ketu").filter { n -> n !in ordered && nodeAgents(ch, n).any { it in ordered } }
    return RulingPlanets(chain, ordered, derived)
}
