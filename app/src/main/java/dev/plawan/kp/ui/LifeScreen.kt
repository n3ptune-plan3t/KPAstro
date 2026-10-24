package dev.plawan.kp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plawan.kp.KpViewModel
import dev.plawan.kp.engine.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy")
private val DAYTIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@Composable
fun LifeScreen(vm: KpViewModel, modifier: Modifier = Modifier) {
    val life by vm.life.collectAsStateWithLifecycle()
    val ctx by vm.context.collectAsStateWithLifecycle()
    LaunchedEffect(ctx) { vm.loadLife() }
    val span by vm.lifeSpan.collectAsStateWithLifecycle()
    val timings by vm.timings.collectAsStateWithLifecycle()
    val timingBusy by vm.timingBusy.collectAsStateWithLifecycle()
    val l = life; val c = ctx; val sp = span
    if (l == null || c == null || sp == null) { Loading("Scanning dasha periods from birth to the next mahadasha…"); return }
    val now = remember { Instant.now() }
    val ranked = remember(l, now) { rankedEvents(l, c.chart) }
    val top = ranked.take(3).sortedBy { bestWindow(it, c.chart)?.start ?: Instant.MAX }
    val topKeys = top.map { it.rule.key }.toSet()
    var showAll by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Life outlook", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Promise = cusp sub-lord vs the event's house groups. Windows = dasha–bhukti pairs that support it, scanned from birth (${MONTH.format(sp.start.atZone(c.zone))}) to the end of the next mahadasha, ${sp.next} (${MONTH.format(sp.end.atZone(c.zone))}). Now running: ${sp.current}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Top 3 time ranges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("One best window per event, ranked by the A > B > C > D strength of the running lords (Reader 3 ~l.19169-19175); parent events must be promised and active.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (top.isEmpty()) {
            item { Text("No event is promised with an active dasha window in this span.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(top, key = { "top-${it.rule.key}" }) {
            EventCard(it, c.zone, now, timings[it.rule.key], busy = it.rule.key in timingBusy,
                only = bestWindow(it, c.chart), why = true) { vm.loadEventTiming(it.rule.key) }
        }
        item {
            Text(if (showAll) "Hide other events" else "Show all ${l.size} events",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showAll = !showAll }.padding(vertical = 4.dp))
        }
        if (showAll) {
            items(l.filter { it.rule.key !in topKeys }, key = { it.rule.key }) {
                EventCard(it, c.zone, now, timings[it.rule.key], busy = it.rule.key in timingBusy) { vm.loadEventTiming(it.rule.key) }
            }
        }
        item { Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
    }
}

// Reader 3 ~l.19169-19175: A (planets in an occupant's star) > B (occupant) > C (planets in the
// owner's star) > D (house owner). The ranking below uses that order, then promise verdict and timing.
private fun sigRank(ch: Chart, lord: String, house: Int): Int {
    val s = ch.significators[house] ?: return 0
    return when {
        lord in s.a -> 4
        lord in s.b -> 3
        lord in s.c -> 2
        lord in s.d -> 1
        else -> 0
    }
}

private fun bestWindow(e: EventReport, ch: Chart): DashaWindow? {
    val live = e.windows.filter { it.parentActive || e.rule.dependsOn.isEmpty() }
    val pool = if (live.isNotEmpty()) live else e.windows
    val adult = ch.dtUtc.plusSeconds((18L * 365L * 86400L))
    return pool.firstOrNull { !it.end.isBefore(adult) } ?: pool.lastOrNull()
}

private fun rankedEvents(all: List<EventReport>, ch: Chart): List<EventReport> {
    fun eligible(e: EventReport) =
        e.promise.verdict in setOf("strong", "promised") && e.windows.isNotEmpty() &&
            e.parents.all { it.promised } &&
            (e.rule.dependsOn.isEmpty() || e.windows.any { it.parentActive })
    return all.filter(::eligible).sortedWith(
        compareByDescending<EventReport> { e ->
            val w = bestWindow(e, ch)
            if (w == null) 0 else w.lords.maxOfOrNull { chain -> chain.sumOf { sigRank(ch, it, e.rule.primary) } } ?: 0
        }.thenBy { bestWindow(it, ch)?.start ?: Instant.MAX }
    )
}

private fun verdictColor(v: String) = when (v) { "strong", "promised" -> KpColors.Good; "mixed" -> KpColors.Gold; else -> KpColors.Bad }

@Composable
private fun EventCard(e: EventReport, zone: ZoneId, now: Instant, timing: EventTiming?, busy: Boolean, only: DashaWindow? = null, why: Boolean = false, onLoad: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open, e.rule.key) { if (open) onLoad() }
    KpCard(Modifier.clickable { open = !open }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Dot(verdictColor(e.promise.verdict), 9.dp); Spacer(Modifier.width(10.dp))
            Text(e.rule.key.replace('_', ' ').replaceFirstChar { it.uppercase() }, Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Pill(e.promise.verdict, verdictColor(e.promise.verdict), filled = true)
        }
        Text("Cusp ${e.rule.cusp} · sub-lord ${e.promise.cuspSubLord}" +
            if (e.rule.status != "verified") " · ${e.rule.status}" else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (why && e.rule.plain.isNotBlank()) {
            Text(e.rule.plain, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (e.parents.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                e.parents.forEach { p -> Pill("after ${p.key.replace('_', ' ')}", if (p.promised) KpColors.Gold else KpColors.Bad) }
                if (e.parents.any { !it.promised }) Text("parent event not promised", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        if (e.windows.isEmpty()) {
            Text(if (e.promise.verdict in listOf("strong", "promised")) "No matching dasha–bhukti window between birth and the end of the next mahadasha."
                 else "Windows are only scanned when the promise is strong or promised.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val shown = if (only != null) listOf(only) else e.windows.take(if (open) 50 else 3)
        shown.forEach { w ->
            Text(MONTH.format(w.start.atZone(zone)) + " → " + MONTH.format(w.end.atZone(zone)) +
                (if (w.end.isBefore(now)) "  · past" else if (w.start.isBefore(now)) "  · running now" else "") +
                (if (w.parents.isNotEmpty()) "  · after " + w.parents.joinToString("/") { it.replace('_', ' ') }
                 else if (!w.parentActive) "  · outside parent window" else ""),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (open) Text(w.lords.joinToString(", ") { it.joinToString("–") },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (only == null && e.windows.size > 3 && !open) Text("+${e.windows.size - 3} more · tap to expand", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (open) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ExactTiming(e, zone, now, timing, busy)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            if (e.rule.plain.isNotBlank()) {
                Text("In simple words", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(e.rule.plain, style = MaterialTheme.typography.bodyMedium)
            }
            Text("Support houses ${e.rule.support}" +
                (if (e.rule.anySupport.isNotEmpty()) " · any of ${e.rule.anySupport}" else "") +
                (if (e.rule.altSupport.isNotEmpty()) " · variant ${e.rule.altSupport}" else "") +
                " · denial houses ${e.rule.denial}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Source (KP Readers)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(e.rule.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun zoneLabel(p: SensitivePoint) =
    if (p.sign == "*") "star ${p.star} / sub ${p.sub}" else "sign ${p.sign} / star ${p.star} / sub ${p.sub}"

@Composable
private fun ExactTiming(e: EventReport, zone: ZoneId, now: Instant, timing: EventTiming?, busy: Boolean) {
    Text("Exact times · Jupiter, Sun, Moon, Lagna", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    Text("The running dasha–bhukti–antara lords fix the zone (sign, star, sub). Jupiter or another slow lord crossing it gives the month, the Sun the day, the Moon the hour and the Lagna the minute.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    when {
        e.promise.verdict !in listOf("strong", "promised") ->
            Text("Exact timing needs a promised event; this one is ${e.promise.verdict}.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        timing == null && busy ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Scanning the transits over the promised zones…",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        timing == null ->
            Text("Computing the exact times…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        timing.exact.isEmpty() ->
            Text("No crossing of the promised zones by Jupiter, the Sun, the Moon or the Lagna inside the scanned dasha periods.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            val best = bestExact(timing)
            val shown = timing.exact.take(40)
            shown.forEach { w ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pill(w.body)
                    Spacer(Modifier.width(6.dp))
                    Text(DAYTIME.format(w.start.atZone(zone)) + " – " + DAYTIME.format(w.end.atZone(zone)),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (w == best) { Spacer(Modifier.width(6.dp)); Pill("best", KpColors.Good, filled = true) }
                }
                Text(w.chain.joinToString("–") + " · " + zoneLabel(w.point) + " · " + w.precision + " · strength " + w.strength +
                    (if (w.end.isBefore(now)) " · past" else if (w.start.isBefore(now)) " · running now" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (timing.exact.size > shown.size) Text("+${timing.exact.size - shown.size} more",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Source (KP Readers): Reader 5 ~l.9970-10040 (sensitive zones), ~l.2535-2538 (Sun fixes the day), ~l.11170-11180 (Moon the hour, Lagna the minute).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
