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
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Life outlook", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Promise = cusp sub-lord vs the event's house groups. Windows = dasha–bhukti pairs that support it, scanned from birth (${MONTH.format(sp.start.atZone(c.zone))}) to the end of the next mahadasha, ${sp.next} (${MONTH.format(sp.end.atZone(c.zone))}). Now running: ${sp.current}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(l, key = { it.rule.key }) {
            EventCard(it, c.zone, now, timings[it.rule.key], busy = it.rule.key in timingBusy) { vm.loadEventTiming(it.rule.key) }
        }
        item { Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
    }
}

private fun verdictColor(v: String) = when (v) { "strong", "promised" -> KpColors.Good; "mixed" -> KpColors.Gold; else -> KpColors.Bad }

@Composable
private fun EventCard(e: EventReport, zone: ZoneId, now: Instant, timing: EventTiming?, busy: Boolean, onLoad: () -> Unit) {
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
        e.windows.take(if (open) 50 else 3).forEach { w ->
            Text(MONTH.format(w.start.atZone(zone)) + " → " + MONTH.format(w.end.atZone(zone)) +
                (if (w.end.isBefore(now)) "  · past" else if (w.start.isBefore(now)) "  · running now" else "") +
                (if (w.parents.isNotEmpty()) "  · after " + w.parents.joinToString("/") { it.replace('_', ' ') }
                 else if (!w.parentActive) "  · outside parent window" else ""),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (open) Text(w.lords.joinToString(", ") { it.joinToString("–") },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (e.windows.size > 3 && !open) Text("+${e.windows.size - 3} more · tap to expand", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (open) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ExactTiming(e, zone, now, timing, busy)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            if (e.rule.plain.isNotBlank()) {
                Text("In simple words", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(e.rule.plain, style = MaterialTheme.typography.bodyMedium)
            }
            Text("Support houses ${e.rule.support}" +
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
