package dev.plawan.kp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy")

@Composable
fun LifeScreen(vm: KpViewModel, modifier: Modifier = Modifier) {
    val life by vm.life.collectAsStateWithLifecycle()
    val ctx by vm.context.collectAsStateWithLifecycle()
    LaunchedEffect(ctx) { vm.loadLife() }
    val l = life; val c = ctx
    if (l == null || c == null) { Loading("Scanning the next 20 years of dasha periods…"); return }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Life outlook", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Promise = cusp sub-lord vs the event's house groups. Windows = dasha–bhukti pairs that support it, next 20 years.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(l, key = { it.rule.key }) { EventCard(it, c.zone) }
        item { Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
    }
}

private fun verdictColor(v: String) = when (v) { "strong", "promised" -> KpColors.Good; "mixed" -> KpColors.Gold; else -> KpColors.Bad }

@Composable
private fun EventCard(e: EventReport, zone: ZoneId) {
    var open by remember { mutableStateOf(false) }
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
            Text(if (e.promise.verdict in listOf("strong", "promised")) "No matching dasha–bhukti window in the next 20 years."
                 else "Windows are only scanned when the promise is strong or promised.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        e.windows.take(if (open) 50 else 3).forEach { w ->
            Text(MONTH.format(w.start.atZone(zone)) + " → " + MONTH.format(w.end.atZone(zone)) +
                (if (w.parents.isNotEmpty()) "  · after " + w.parents.joinToString("/") { it.replace('_', ' ') }
                 else if (!w.parentActive) "  · outside parent window" else ""),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (open) Text(w.lords.joinToString(", ") { it.joinToString("–") },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (e.windows.size > 3 && !open) Text("+${e.windows.size - 3} more · tap to expand", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (open) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Text("Support ${e.rule.support} · denial ${e.rule.denial}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(e.rule.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
