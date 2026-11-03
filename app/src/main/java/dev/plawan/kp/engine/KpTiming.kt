package dev.plawan.kp.engine

import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

// Exact event timing (Reader 5 ~l.9970-10040, ~l.11170-11180; Reader 3 ~l.19160-19175).
// When a promised Dasa-Bhukti-Antara (A-B-C) runs, the sensitive points are the rows of
// the 249-sub table whose sign/star/sub lords are exactly that group (Reader 5
// ~l.9970-10040). A transit of the luminaries, the period lords or the Lagna over one
// of those points gives the date, hour and minute. Rahu/Ketu can be star or sub lords in
// the table (but never sign lords). The two-level fallback pins only "star of one" +
// "sub of the other" (Reader 5 ~l.2626).

data class SensitivePoint(val sign: String, val star: String, val sub: String)

data class ExactWindow(
    val start: Instant, val end: Instant, val precision: String, val rank: Int,
    val body: String, val point: SensitivePoint, val chain: List<String>,
    val rule: String, val strength: Int, val source: String,
)

data class MonthCandidate(
    val month: YearMonth,
    val chains: List<List<String>>,
    val triggers: List<ExactWindow>,
)

data class EventTiming(
    val rule: Rule,
    val promise: Promise,
    val broad: List<DashaWindow>,
    val exact: List<ExactWindow>,
    val monthly: List<MonthCandidate> = emptyList(),
)

data class ExactCfg(
    val moonStepMin: Int = 15, val sunStepMin: Int = 240, val ascStepMin: Int = 1,
    val fastStepMin: Int = 120, val slowStepMin: Int = 360,
    val maxPeriods: Int = 60, val maxHits: Int = 600,
)

val EXACT_CFG = ExactCfg()

// On-device preset for the UI: the narrowest 249-row zone is the Sun sub (~40', Reader 5 table),
// which the Moon crosses in ~75 min, so a 45-min step cannot skip a zone and still resolves the
// book's "hour" precision (~l.11170-11180). EXACT_CFG stays as the reference default (parity with
// tools/reference/kp_timing.py DEFAULT_CFG); no edge here is an analytic crossing either way.
// Depth 3 periods are pratyantardasha (1-3 months), so 60 of them stay a second-scale sweep.
val UI_EXACT_CFG = ExactCfg(moonStepMin = 15, sunStepMin = 240, fastStepMin = 120, slowStepMin = 360, maxPeriods = 60, maxHits = 600)

private val SLOW_LORDS = setOf("Jupiter", "Saturn", "Rahu", "Ketu")
private val NODE_BODY = if (RAHU_MEAN) Swe.MEAN_NODE else 11

private const val THREE_LEVEL_SOURCE =
    "Reader 5 ~l.9970-10040: when A-B-C runs, the sign/star/sub positions governed by the three are the sensitive points; their transit by the period lords, the luminaries or the Lagna triggers the event."
private const val TWO_LEVEL_SOURCE =
    "Reader 5 ~l.2626: the result is felt when one luminary transits the star of one lord and the sub of the other."
private const val MOON_SOURCE =
    "Reader 5 ~l.11170-11180 / ~l.2626: the Moon gives the hour; it crosses a sensitive point once in about 27 days."
private const val SUN_SOURCE =
    "Reader 5 ~l.2535-2538: the Sun transits the zone on the day, fixing the date."
private const val ASC_SOURCE =
    "Reader 5 ~l.11170-11180: within the Moon hour the Lagna gives the exact minute."
private const val LORD_SOURCE =
    "Engine convention (not a Reader rule): period-lord windows are sampled coarsely at 6-hour (fast) or 12-hour (slow) intervals. Reader 5 ~l.10026-10038 supports period-lord transits, but does not establish a universal slow-planet=month timing rule."

/**
 * Reader 5's 249 sensitive divisions. The Vimshottari star/sub table has 243 intervals;
 * six intervals cross a sign boundary, so split those intervals to retain the correct
 * sign lord on both sides. This is essential because the three-level trigger is the
 * exact sign + star + sub combination (Reader 5 ~l.9970-10040).
 */
fun sensitiveZoneTable(): List<SensitivePoint> {
    val out = LinkedHashSet<SensitivePoint>()
    for (row in SUB_TABLE) {
        var start = row.start
        while (start < row.end) {
            val signIndex = (start / 30.0).toInt().coerceIn(0, 11)
            val signEnd = minOf(row.end, (signIndex + 1) * 30.0)
            if (signEnd <= start) break
            out.add(SensitivePoint(SIGN_LORDS[signIndex], row.star, row.sub))
            start = signEnd
        }
    }
    return out.toList()
}

fun sensitivePoints(lords: List<String>, depth: Int = 3): List<SensitivePoint> {
    val use = lords.take(if (depth >= 3) 3 else 2)
    if (use.isEmpty()) return emptyList()
    val out = LinkedHashSet<SensitivePoint>()
    when {
        use.size >= 3 -> {
            val need = use.groupingBy { it }.eachCount()
            for (point in sensitiveZoneTable()) {
                if (listOf(point.sign, point.star, point.sub).groupingBy { it }.eachCount() == need)
                    out.add(point)
            }
        }
        use.size == 2 -> {
            out.add(SensitivePoint("*", use[0], use[1]))
            out.add(SensitivePoint("*", use[1], use[0]))
        }
        else -> out.add(SensitivePoint("*", use[0], use[0]))
    }
    return out.toList()
}

private fun matches(pt: SensitivePoint, l: Lords): Boolean =
    (pt.sign == "*" || pt.sign == l.sign) && pt.star == l.star && pt.sub == l.sub

private fun significatorRank(ch: Chart, planet: String, house: Int): Int {
    val sig = ch.significators[house] ?: return 0
    return when (planet) {
        in sig.a -> 4
        in sig.b -> 3
        in sig.c -> 2
        in sig.d -> 1
        else -> 0
    }
}

private fun bodyLords(body: String, jd: Double): Lords = when (body) {
    "Rahu" -> lordsAt(Swe.lon(jd, NODE_BODY))
    "Ketu" -> lordsAt((Swe.lon(jd, NODE_BODY) + 180.0) % 360.0)
    else -> lordsAt(Swe.lon(jd, BODIES.getValue(body)))
}

internal fun scanLords(
    body: String, targets: List<SensitivePoint>, from: Instant, to: Instant, stepMin: Int,
    precision: String, rank: Int, chain: List<String>, strength: Int, rule: String, source: String,
    lordsOf: (Instant) -> Lords,
): List<ExactWindow> {
    if (!to.isAfter(from) || targets.isEmpty()) return emptyList()
    require(stepMin > 0) { "stepMin must be positive" }
    val step = stepMin * 60L
    val out = ArrayList<ExactWindow>()
    var activePoint: SensitivePoint? = null
    var activeStart: Instant? = null

    fun hitAt(t: Instant): SensitivePoint? {
        val lords = lordsOf(t)
        return targets.firstOrNull { matches(it, lords) }
    }
    fun closeAt(end: Instant) {
        val point = activePoint
        val start = activeStart
        if (point != null && start != null && end.isAfter(start)) {
            out.add(ExactWindow(start, end, precision, rank, body, point, chain, rule, strength, source))
        }
        activePoint = null
        activeStart = null
    }
    fun boundaryBetween(left: Instant, right: Instant, leftHit: SensitivePoint?): Instant {
        var lo = left
        var hi = right
        while (Duration.between(lo, hi).toMillis() > 1000L) {
            val mid = lo.plusMillis(Duration.between(lo, hi).toMillis() / 2L)
            if (hitAt(mid) == leftHit) lo = mid else hi = mid
        }
        return hi
    }

    var t = from
    while (t.isBefore(to)) {
        val next = minOf(t.plusSeconds(step), to)
        val leftHit = hitAt(t)
        val rightHit = hitAt(next)

        if (leftHit == rightHit) {
            if (leftHit == null) {
                if (activePoint != null) closeAt(t)
            } else if (activePoint != leftHit) {
                closeAt(t)
                activePoint = leftHit
                activeStart = t
            }
        } else {
            val boundary = boundaryBetween(t, next, leftHit)
            if (activePoint != leftHit) {
                closeAt(t)
                if (leftHit != null) {
                    activePoint = leftHit
                    activeStart = t
                }
            }
            if (leftHit != null) closeAt(boundary)
            if (rightHit != null && boundary.isBefore(to)) {
                activePoint = rightHit
                activeStart = boundary
            }
        }
        t = next
    }
    closeAt(to)
    return out
}

private fun refineAsc(
    base: ExactWindow, targets: List<SensitivePoint>, lat: Double?, lon: Double?, cfg: ExactCfg,
): List<ExactWindow> {
    val la = lat ?: return listOf(base)
    val lo = lon ?: return listOf(base)
    val out = scanLords(
        "Asc", targets, base.start, base.end, cfg.ascStepMin, "minute", 4,
        base.chain, base.strength, base.rule, ASC_SOURCE,
    ) { t -> lordsAt(Swe.houses(Swe.jd(t), la, lo)[12]) }
    return if (out.isEmpty()) listOf(base) else out
}

/**
 * Produce calendar-month candidates from the Reader 5 transit procedure.
 *
 * A candidate is emitted only inside a qualifying Dasa-Bhukti-Anthra and when
 * a period lord or the Sun crosses a sign/star/sub combination belonging to
 * that three-lord group. The app deliberately reports the month, not a false
 * day/hour/minute certainty.
 */
fun exactTimes(
    ch: Chart, rule: Rule, from: Instant, to: Instant, lat: Double? = null, lon: Double? = null,
    depth: Int = 3, cfg: ExactCfg = EXACT_CFG, near: Instant? = null,
    zone: ZoneId = ZoneOffset.UTC,
): EventTiming {
    val pr = promise(ch, rule)
    if (pr.verdict !in setOf("strong", "promised", "mixed")) {
        return EventTiming(rule, pr, emptyList(), emptyList())
    }
    val useDepth = if (depth >= 3) 3 else 2
    val broad = scan(ch, rule, from, to, useDepth)
    val anchor = near ?: from
    val hits = dashaHits(ch, rule, from, to, useDepth)
        .filter { it.end.isAfter(anchor) }
        .sortedBy { it.start }
        .take(cfg.maxPeriods)
    val raw = ArrayList<ExactWindow>()
    for (p in hits) {
        val chain = p.lords.take(useDepth)
        val targets = sensitivePoints(chain, useDepth)
        if (targets.isEmpty()) continue
        val strength = chain.sumOf { significatorRank(ch, it, rule.primary) }
        val s = maxOf(p.start, from)
        val e = minOf(p.end, to)
        val bodies = (chain + "Sun").distinct()
        for (body in bodies) {
            val step = when {
                body == "Moon" -> cfg.moonStepMin
                body == "Sun" -> cfg.sunStepMin
                body in SLOW_LORDS -> cfg.slowStepMin
                else -> cfg.fastStepMin
            }
            val src = if (body == "Sun") SUN_SOURCE else LORD_SOURCE
            raw += scanLords(
                body, targets, s, e, step, "month", if (body == "Sun") 2 else 1,
                chain, strength, rule.key, THREE_LEVEL_SOURCE + " " + src,
            ) { t -> bodyLords(body, Swe.jd(t)) }
        }
    }

    val byMonth = LinkedHashMap<YearMonth, MutableList<ExactWindow>>()
    for (hit in raw) {
        var month = YearMonth.from(hit.start.atZone(zone))
        val last = YearMonth.from(hit.end.minusNanos(1).atZone(zone))
        while (!month.isAfter(last)) {
            byMonth.getOrPut(month) { ArrayList() }.add(hit)
            month = month.plusMonths(1)
        }
    }
    val monthly = byMonth.entries
        .sortedBy { it.key }
        .take(cfg.maxHits)
        .map { (month, triggers) ->
            MonthCandidate(month, triggers.map { it.chain }.distinct(), triggers.distinct())
        }
    return EventTiming(rule, pr, broad, emptyList(), monthly)
}

fun bestExact(t: EventTiming, near: Instant = Instant.now()): ExactWindow? {
    val candidates = t.exact.filter { it.end.isAfter(near) }.ifEmpty { t.exact }
    return candidates.minWithOrNull(
        compareByDescending<ExactWindow> { it.rank }
            .thenBy { Math.abs(Duration.between(near, it.start).toSeconds()) }
            .thenBy { it.start },
    )
}

/** Earliest window that is still active or upcoming; precision rank is only a tie-breaker. */
fun nextExact(t: EventTiming, after: Instant): ExactWindow? =
    t.exact.filter { it.end.isAfter(after) }
        .minWithOrNull(compareBy<ExactWindow> { it.start }.thenByDescending { it.rank })
