package dev.plawan.kp.engine

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Planetary hours (hora) - Reader 3 "Predictive Stellar Astrology" ~l.30440-30580 (Hora / Muhurtha chapter).
//  * Sunrise to local noon is divided into 6 equal parts; the first belongs to the lord of the day (l.30447-30450, 30480-30495).
//  * Order of hora lords: day lord, then Moon follows Mercury, Saturn follows Moon ... i.e. the Chaldean order
//    Sun, Venus, Mercury, Moon, Saturn, Jupiter, Mars (Wednesday example: Mercury, Moon, Saturn, l.30488-30495).
//  * Each hora is split into 7 equal sub-horas starting with the hora lord and following the same order (l.30519-30525).
//  * Combination rule: Dasha lord / Bhukti lord pair is served by "Mars hora + Jupiter sub, Jupiter hora + Mars sub,
//    Mars day + Jupiter hora, Jupiter day + Mars hora" (l.30558-30566).
// ASSUMPTION (flagged): the book only worked the forenoon; the afternoon and night are taken as the same 12+12 cycle
// (day = 2 x sunrise-to-noon, night = remainder to next sunrise, each split in 12), continuing the 7-planet order.

val HORA_ORDER = listOf("Sun", "Venus", "Mercury", "Moon", "Saturn", "Jupiter", "Mars")

data class Hora(val start: Instant, val end: Instant, val lord: String, val night: Boolean)
data class HoraSub(val start: Instant, val end: Instant, val lord: String)
data class HoraPin(val start: Instant, val end: Instant, val hora: String, val sub: String?, val kind: String)

/** Muhurta pairs named in Reader 3 (~l.30517 avoid, ~l.30568-30572 prefer). Pair = {day or hora lord, hora or sub lord}. */
val MUHURTA_PAIRS = linkedMapOf(
    "Marriage (Venus + Jupiter)" to ("Venus" to "Jupiter"),
    "Construction (Mercury + Mars)" to ("Mercury" to "Mars"),
    "Avoid signing / negotiating (Saturn + Mercury)" to ("Saturn" to "Mercury"),
)

fun horaLordAfter(lord: String, steps: Int) = HORA_ORDER[(HORA_ORDER.indexOf(lord) + steps) % 7]

/** The 24 horas that start at the sunrise of the local calendar day `d0` (local midnight). */
fun horaDay(d0: ZonedDateTime, lat: Double, lon: Double): List<Hora> {
    val rise = sunriseUtc(d0, lat, lon)
    val nextRise = sunriseUtc(d0.plusDays(1), lat, lon)
    val (rc, tjd) = Swe.transit(Swe.jd(rise.truncatedTo(ChronoUnit.MINUTES)), lat, lon)
    val noon = if (rc < 0) rise.plusSeconds(6 * 3600) else Swe.instant(tjd)
    val dayLen = Duration.between(rise, noon).toMillis() * 2
    val set = rise.plusMillis(dayLen)
    val dayH = dayLen / 12; val nightH = Duration.between(set, nextRise).toMillis() / 12
    val dayLord = WEEKDAY_LORD[d0.dayOfWeek.value - 1]
    return (0 until 24).map { i ->
        val night = i >= 12
        val s = if (!night) rise.plusMillis(dayH * i) else set.plusMillis(nightH * (i - 12))
        val e = if (!night) rise.plusMillis(dayH * (i + 1)) else if (i == 23) nextRise else set.plusMillis(nightH * (i - 11))
        Hora(s, e, horaLordAfter(dayLord, i), night)
    }
}

fun horaSubs(h: Hora): List<HoraSub> {
    val len = Duration.between(h.start, h.end).toMillis() / 7
    return (0 until 7).map { j -> HoraSub(h.start.plusMillis(len * j), if (j == 6) h.end else h.start.plusMillis(len * (j + 1)), horaLordAfter(h.lord, j)) }
}

/** Windows where the pair {a,b} is served: hora lord + sub-hora lord, or day lord + hora lord (Reader 3 ~l.30558-30566). */
fun horaPairWindows(horas: List<Hora>, dayLord: String, a: String, b: String): List<HoraPin> {
    val want = setOf(a, b); val out = ArrayList<HoraPin>()
    for (h in horas) {
        if (h.lord != dayLord && setOf(dayLord, h.lord) == want) out.add(HoraPin(h.start, h.end, h.lord, null, "day+hora"))
        else if (h.lord == dayLord && a == b && a == h.lord) out.add(HoraPin(h.start, h.end, h.lord, null, "day+hora"))
        for (s in horaSubs(h)) if (s.lord != h.lord && setOf(h.lord, s.lord) == want) out.add(HoraPin(s.start, s.end, h.lord, s.lord, "hora+sub"))
    }
    return out.sortedBy { it.start }
}

data class HoraReport(val dayLord: String, val horas: List<Hora>, val dashaPair: Pair<String, String>, val dashaPins: List<HoraPin>, val muhurta: Map<String, List<HoraPin>>)

fun horaReport(ctx: Context, day: ZonedDateTime): HoraReport {
    val d0 = day.withZoneSameInstant(ctx.zone).truncatedTo(ChronoUnit.DAYS)
    val horas = horaDay(d0, ctx.lat, ctx.lon); val dayLord = WEEKDAY_LORD[d0.dayOfWeek.value - 1]
    val chain = chainAt(ctx.mds, d0.toInstant().plusSeconds(12 * 3600), 2)!!
    val pair = chain[0] to chain[1]
    return HoraReport(dayLord, horas, pair, horaPairWindows(horas, dayLord, pair.first, pair.second),
        MUHURTA_PAIRS.mapValues { (_, p) -> horaPairWindows(horas, dayLord, p.first, p.second) })
}
