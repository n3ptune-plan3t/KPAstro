package dev.plawan.kp.domain

import dev.plawan.kp.engine.*
import java.time.Instant
import java.time.LocalDate

/**
 * Use-case boundary for KP calculations.
 *
 * This class contains orchestration only. All astronomical and astrological
 * calculations remain in engine/ and retain their existing reference behaviour.
 */
class KpAnalysis {
    fun day(context: Context, date: LocalDate): Pair<DayReport, HoraReport> {
        val at = date.atTime(12, 0).atZone(context.zone)
        return dayReport(context, at) to horaReport(context, at)
    }

    fun life(context: Context, now: Instant = Instant.now()): Pair<LifeRange, List<EventReport>> {
        val range = lifeRange(context.chart, now)
        return range to lifeReport(context.chart, context.rules, now = now, range = range)
    }

    fun birthTimeTolerance(context: Context): Map<String, Tolerance> =
        birthTimeTolerance(context.chart.dtUtc, context.chart.lat, context.chart.lon, 60)

    fun eventTiming(context: Context, key: String, range: LifeRange, near: Instant = Instant.now()): EventTiming =
        exactTimes(
            context.chart,
            context.rules.getValue(key),
            range.start,
            range.end,
            context.lat,
            context.lon,
            3,
            UI_EXACT_CFG,
            near = near,
        )
}
