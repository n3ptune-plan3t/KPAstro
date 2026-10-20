package dev.plawan.kp.engine

import java.time.Duration
import java.time.Instant
import kotlin.math.min

// Port of kp_rules.py: promise (cusp sub-lord), dasha-window scan, Reader-5 transit pinning, lucky-time diary,
// birth-time sensitivity. Event rules come from rules.json (never hard-coded).

data class Rule(
    val key: String, val cusp: Int, val primary: Int, val support: List<Int>, val denial: List<Int>,
    val status: String, val source: String, val dependsOn: List<String> = emptyList(),
)

fun parseRules(json: String): LinkedHashMap<String, Rule> {
    @Suppress("UNCHECKED_CAST") val root = MiniJson.parse(json) as Map<String, Any?>
    val out = LinkedHashMap<String, Rule>()
    for ((k, v) in root) {
        if (k.startsWith("_")) continue
        @Suppress("UNCHECKED_CAST") val m = v as Map<String, Any?>
        fun ints(n: String) = (m[n] as List<*>).map { (it as Double).toInt() }
        fun strs(n: String) = (m[n] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        out[k] = Rule(k, (m["cusp"] as Double).toInt(), (m["primary"] as Double).toInt(), ints("support"), ints("denial"),
            m["status"] as? String ?: "?", m["source"] as? String ?: "", strs("depends_on"))
    }
    return out
}

private fun housesOf(ch: Chart, planet: String): Set<Int> = planetHousesSignified(ch, planet).toSet()

// ------------------------------------------------------------------ 1. promise
data class Promise(val cuspSubLord: String, val houses: List<Int>, val support: List<Int>, val denial: List<Int>, val verdict: String)

fun promise(ch: Chart, rule: Rule): Promise {
    val csl = ch.cusps[rule.cusp - 1].subLord
    val hs = housesOf(ch, csl)
    val sup = hs.intersect(rule.support.toSet()).sorted(); val den = hs.intersect(rule.denial.toSet()).sorted()
    val verdict = when {
        sup.size == rule.support.size && den.isEmpty() -> "strong"
        sup.size >= 2 && sup.size > den.size -> "promised"
        sup.isNotEmpty() -> "mixed"
        else -> "denied"
    }
    return Promise(csl, hs.sorted(), sup, den, verdict)
}

// ------------------------------------------------------------------ 2. timing
data class LordCheck(val planet: String, val support: List<Int>, val denial: List<Int>, val subLord: String, val permission: Boolean)

fun lordCheck(ch: Chart, planet: String, rule: Rule): LordCheck {
    val hs = housesOf(ch, planet)
    val sup = hs.intersect(rule.support.toSet()).sorted(); val den = hs.intersect(rule.denial.toSet()).sorted()
    val sub = ch.planets.getValue(planet).subLord; val shs = housesOf(ch, sub)
    val permission = shs.intersect(rule.denial.toSet()).size <= shs.intersect(rule.support.toSet()).size
    return LordCheck(planet, sup, den, sub, permission)
}

fun periodCheck(ch: Chart, rule: Rule, lords: List<String>): Pair<Boolean, List<LordCheck>> {
    val det = lords.map { lordCheck(ch, it, rule) }
    val union = det.flatMap { it.support }.toSet()
    val allHouses = lords.flatMap { housesOf(ch, it) }.toSet()
    val ok = det.all { it.support.isNotEmpty() && it.permission } &&
        rule.primary in (union + allHouses) && union.size >= min(2, rule.support.size)
    return ok to det
}

data class DashaWindow(
    val start: Instant, var end: Instant, val lords: MutableList<List<String>>,
    var parentActive: Boolean = true, val parents: MutableList<String> = mutableListOf(),
)

fun scan(ch: Chart, rule: Rule, start: Instant, end: Instant, depth: Int = 2): List<DashaWindow> {
    val hits = ArrayList<Period>()
    val cache = HashMap<String, LordCheck>()
    fun lc(l: String) = cache.getOrPut(l) { lordCheck(ch, l, rule) }
    fun walk(p: Period) {
        if (!p.end.isAfter(start) || !p.start.isBefore(end)) return
        if (!p.lords.all { lc(it).support.isNotEmpty() && lc(it).permission }) return
        if (p.lords.size == depth) { if (periodCheck(ch, rule, p.lords).first) hits.add(p); return }
        subPeriods(p).forEach { walk(it) }
    }
    mahadashas(ch).forEach { walk(it) }
    val merged = ArrayList<DashaWindow>()
    for (p in hits.sortedBy { it.start }) {
        val s = if (p.start.isAfter(start)) p.start else start
        val e = if (p.end.isBefore(end)) p.end else end
        val last = merged.lastOrNull()
        // adjacent = touching within a second (float rounding between sibling parents)
        if (last != null && Math.abs(Duration.between(last.end, s).toMillis()) < 1000) { last.end = e; last.lords.add(p.lords) }
        else merged.add(DashaWindow(s, e, mutableListOf(p.lords)))
    }
    return merged
}

// A parent event gates its children softly: the child's windows are always kept, but
// flagged when they do not nest inside a parent window (see rules.json depends_on).
data class ParentGate(val key: String, val verdict: String, val promised: Boolean, val windows: Int)

data class EventReport(val rule: Rule, val promise: Promise, val windows: List<DashaWindow>, val parents: List<ParentGate> = emptyList())

private fun overlap(a: DashaWindow, b: DashaWindow) = a.start.isBefore(b.end) && b.start.isBefore(a.end)

fun lifeReport(ch: Chart, rules: Map<String, Rule>, years: Int = 20, now: Instant = Instant.now(), depth: Int = 2): List<EventReport> {
    val end = now.plusDays(365.25 * years)
    val base = rules.values.associate { r ->
        val pr = promise(ch, r)
        val wins = if (pr.verdict == "strong" || pr.verdict == "promised") scan(ch, r, now, end, depth) else emptyList()
        r.key to Pair(pr, wins)
    }
    return rules.values.map { r ->
        val (pr, wins) = base.getValue(r.key)
        val parents = r.dependsOn.mapNotNull { pk ->
            val gate = base[pk] ?: return@mapNotNull null
            ParentGate(pk, gate.first.verdict, gate.first.verdict == "strong" || gate.first.verdict == "promised", gate.second.size)
        }
        if (r.dependsOn.isNotEmpty()) for (w in wins) {
            val hits = r.dependsOn.filter { pk -> base[pk]?.second?.any { ow -> overlap(ow, w) } == true }
            w.parents.addAll(hits)
            w.parentActive = hits.isNotEmpty()
        }
        EventReport(r, pr, wins, parents)
    }
}

// ------------------------------------------------------------------ 3. Moon windows
data class MoonWindow(val start: Instant, var end: Instant, val star: String, val sub: String)

fun moonWindows(dayStart: Instant, stepMin: Int = 5): List<MoonWindow> {
    val out = ArrayList<MoonWindow>(); var t = dayStart; val end = dayStart.plusSeconds(86400)
    val step = stepMin * 60L
    while (t.isBefore(end)) {
        val l = lordsAt(Swe.lon(Swe.jd(t), Swe.MOON))
        val last = out.lastOrNull()
        if (last != null && last.star == l.star && last.sub == l.sub) last.end = t.plusSeconds(step)
        else out.add(MoonWindow(t, t.plusSeconds(step), l.star, l.sub))
        t = t.plusSeconds(step)
    }
    return out
}

// ------------------------------------------------------------------ 4. transit pinning (Reader 5 ~l.2626)
data class PinWindow(val start: Instant, var end: Instant, val star: String, val sub: String, val chain: List<String> = emptyList())

fun transitPin(body: Int, a: String, b: String, start: Instant, end: Instant, stepMin: Int = 60): List<PinWindow> {
    val out = ArrayList<PinWindow>(); var t = start; val step = stepMin * 60L
    while (t.isBefore(end)) {
        val l = lordsAt(Swe.lon(Swe.jd(t), body))
        if ((l.star == a && l.sub == b) || (l.star == b && l.sub == a)) {
            val last = out.lastOrNull()
            if (last != null && last.end == t && last.star == l.star && last.sub == l.sub) last.end = t.plusSeconds(step)
            else out.add(PinWindow(t, t.plusSeconds(step), l.star, l.sub))
        }
        t = t.plusSeconds(step)
    }
    return out
}

fun pinPeriod(ch: Chart, rule: Rule, start: Instant, end: Instant, body: Int = Swe.SUN): List<PinWindow> {
    val res = ArrayList<PinWindow>()
    for (w in scan(ch, rule, start, end, 2)) for (lords in w.lords) {
        val (md, ad) = lords[0] to lords[1]
        val parent = mahadashas(ch).first { it.lords == listOf(md) }
        for (p in subPeriods(parent)) if (p.lords == listOf(md, ad) && p.end.isAfter(start) && p.start.isBefore(end)) {
            val s = if (p.start.isAfter(start)) p.start else start; val e = if (p.end.isBefore(end)) p.end else end
            transitPin(body, md, ad, s, e).forEach { res.add(it.copy(chain = listOf(md, ad))) }
        }
    }
    return res
}

// ------------------------------------------------------------------ 5. personal lucky-time diary (Reader 5 ~l.11770-11850)
data class DiaryEntry(val start: Instant, val end: Instant, val outcome: Double)
data class SubScore(val minutes: Int, val avg: Double)
data class LuckyWindow(val start: Instant, val end: Instant, val star: String, val sub: String, val tag: String?, val avg: Double?)

fun moonSubAt(t: Instant): Pair<String, String> { val l = lordsAt(Swe.lon(Swe.jd(t), Swe.MOON)); return l.star to l.sub }

fun learnSubScores(diary: List<DiaryEntry>, stepMin: Int = 10): Map<String, SubScore> {
    val minutes = HashMap<String, Int>(); val sum = HashMap<String, Double>()
    for (e in diary) {
        var t = e.start
        while (t.isBefore(e.end)) {
            val sub = moonSubAt(t).second
            minutes[sub] = (minutes[sub] ?: 0) + stepMin; sum[sub] = (sum[sub] ?: 0.0) + e.outcome * stepMin
            t = t.plusSeconds(stepMin * 60L)
        }
    }
    return minutes.mapValues { (k, v) -> SubScore(v, sum.getValue(k) / v) }
}

fun luckyWindows(scores: Map<String, SubScore>, dayStart: Instant, minMinutes: Int = 120, minAvg: Double = 0.3): List<LuckyWindow> =
    moonWindows(dayStart, 10).map { w ->
        val sc = scores[w.sub]
        val tag = if (sc != null && sc.minutes >= minMinutes) when { sc.avg >= minAvg -> "lucky"; sc.avg <= -minAvg -> "unlucky"; else -> null } else null
        LuckyWindow(w.start, w.end, w.star, w.sub, tag, sc?.avg)
    }

// ------------------------------------------------------------------ 6. birth-time sensitivity
data class Tolerance(val sub: String, val before: Int?, val after: Int?)

fun birthTimeTolerance(dt: Instant, lat: Double, lon: Double, maxMin: Int = 120): LinkedHashMap<String, Tolerance> {
    fun subs(t: Instant): List<String> { val c = computeChart(t, lat, lon); return c.cusps.map { it.subLord } + c.planets.values.map { it.subLord } }
    val base0 = computeChart(dt, lat, lon)
    val names = (1..12).map { "H$it" } + base0.planets.keys
    val base = subs(dt)
    val before = arrayOfNulls<Int>(names.size); val after = arrayOfNulls<Int>(names.size)
    for (m in 1..maxMin) for (sign in listOf(-1, 1)) {
        val cur = subs(dt.plusSeconds(sign * m * 60L)); val arr = if (sign < 0) before else after
        for (i in names.indices) if (arr[i] == null && cur[i] != base[i]) arr[i] = m
    }
    return LinkedHashMap<String, Tolerance>().also { o -> names.indices.forEach { o[names[it]] = Tolerance(base[it], before[it], after[it]) } }
}
