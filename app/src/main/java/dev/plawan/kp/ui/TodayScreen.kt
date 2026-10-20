package dev.plawan.kp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plawan.kp.KpViewModel
import dev.plawan.kp.engine.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@Composable
fun TodayScreen(vm: KpViewModel, modifier: Modifier = Modifier) {
    val rep by vm.report.collectAsStateWithLifecycle()
    val loading by vm.loadingDay.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val hr by vm.hora.collectAsStateWithLifecycle()
    val r = rep
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DateBar(date, onPrev = { vm.shiftDay(-1) }, onNext = { vm.shiftDay(1) }, onToday = { vm.today() }) }
        if (err != null) item { KpCard { Text("Something went wrong: $err", color = KpColors.Bad) } }
        if (r == null || (loading && r.date != date)) {
            item { Box(Modifier.fillMaxWidth().height(300.dp)) { Loading() } }
            return@LazyColumn
        }
        item { Hero(r) }
        item { Timeline(r) }
        item { SectionTitle("Life areas", Modifier.padding(top = 4.dp)) }
        items(r.areas.sortedByDescending { it.score }, key = { it.key }) { AreaRow(it, r) }
        item { Guidance(r) }
        hr?.let { h -> item { HoraCard(h, r.zone) } }
        item { Transits(r) }
        item { Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
    }
}

@Composable
private fun DateBar(date: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) =
    Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onPrev) { Text("‹", style = MaterialTheme.typography.titleLarge) }
        Column(Modifier.weight(1f).clickable { onToday() }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (date == LocalDate.now()) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Text(DATE.format(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onNext) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }

@Composable
private fun Hero(r: DayReport) = KpCard {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ScoreRing(r.overallScore, size = 132)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.overallVerdict, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = scoreColor(r.overallScore))
            Text("Ranked against your own days over the surrounding ~4 months; 50 is typical.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Pill("Periods ${r.overallBackdrop}", when (r.overallBackdrop) { "supportive" -> KpColors.Good; "heavy" -> KpColors.Bad; else -> KpColors.Gold })
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        r.dashaChain.forEachIndexed { i, l ->
            if (i > 0) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Pill(l, KpColors.Indigo, filled = i == r.dashaChain.lastIndex)
        }
        Spacer(Modifier.width(6.dp)); Pill("Day ${r.dayLord}", KpColors.Gold)
    }
}

@Composable
private fun Timeline(r: DayReport) {
    var detail by remember { mutableStateOf(false) }
    val personal = r.personal
    KpCard {
        SectionTitle("Hours at a glance")
        val dayStart = r.date.atStartOfDay(r.zone).toInstant()
        val now = Instant.now().takeIf { r.date == LocalDate.now(r.zone) }
        DayStrip(r.lucky, r.avoid, r.moonPins.map { TWindow(it.start, it.end) }, dayStart, now)
        Legend()
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        WindowRow("Lucky", r.lucky, r.zone, KpColors.Good, "no clear peak")
        WindowRow("Caution", r.avoid, r.zone, KpColors.Bad, "no clear dip")
        if (r.moonPins.isNotEmpty() || r.sunPinToday || !personal.isNullOrEmpty()) {
            Text(if (detail) "Hide timing details" else "Timing details",
                Modifier.clickable { detail = !detail }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (detail) {
                r.moonPins.forEach { Text("${fmtTime(it.start, r.zone)}–${fmtTime(it.end, r.zone)}  ·  Moon ${it.star}/${it.sub} pins your running periods",
                    style = MaterialTheme.typography.bodySmall, color = KpColors.Gold) }
                if (r.sunPinToday) Text("The Sun sits in a star/sub pairing of your running Dasha–Bhukti lords today.",
                    style = MaterialTheme.typography.bodySmall, color = KpColors.Gold)
                personal?.forEach { Text("${it.tag}  ${fmtTime(it.start, r.zone)}–${fmtTime(it.end, r.zone)}  ·  Moon sub ${it.sub} (your diary)",
                    style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun Legend() = Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    listOf("lucky" to KpColors.Good, "caution" to KpColors.Bad, "timing pin" to KpColors.Gold).forEach { (t, c) ->
        Row(verticalAlignment = Alignment.CenterVertically) { Dot(c, 7.dp); Spacer(Modifier.width(5.dp)); Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun WindowRow(title: String, ws: List<TWindow>, z: ZoneId, c: Color, empty: String) =
    Row(verticalAlignment = Alignment.Top) {
        Row(Modifier.width(80.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(c, 7.dp); Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = c, fontWeight = FontWeight.SemiBold)
        }
        Text(if (ws.isEmpty()) "— $empty" else ws.joinToString("   ") { fmtWindow(it, z) }, style = MaterialTheme.typography.bodyMedium)
    }

@Composable
private fun AreaRow(a: AreaReport, r: DayReport) {
    var open by remember { mutableStateOf(false) }
    KpCard(Modifier.clickable { open = !open }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Dot(scoreColor(a.score), 9.dp); Spacer(Modifier.width(10.dp))
            Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${a.score}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scoreColor(a.score))
            Spacer(Modifier.width(8.dp))
            Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ScoreBar(a.score)
        if (open) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(a.verdict, scoreColor(a.score))
                Pill("backdrop ${a.backdrop}", MaterialTheme.colorScheme.secondary)
                if (a.sourceStatus == "unverified") Pill("unverified", KpColors.Gold)
            }
            Text(a.tone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Main driver: ${a.mainDriver.name} (${a.mainDriver.effect} it) · next: ${a.nextDriver.name} (${a.nextDriver.effect} it)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (a.best.isNotEmpty()) Text((if (a.score >= 40) "Best window " else "Least heavy ") + a.best.take(2).joinToString(", ") { fmtWindow(it, r.zone) },
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Guidance(r: DayReport) = KpCard {
    SectionTitle("Good for · Be careful with")
    Text("Good for: " + r.goodFor.joinToString("; ").ifEmpty { "routine work; no area stands out" }, style = MaterialTheme.typography.bodyMedium)
    Text("Be careful with: " + r.beCareful.joinToString("; ").ifEmpty { "nothing special" }, style = MaterialTheme.typography.bodyMedium)
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(KpColors.Good); Spacer(Modifier.width(8.dp))
        Text("Lucky planet ${r.luckyPlanet.planet} · ${r.luckyPlanet.thing.colour} · number ${r.luckyPlanet.thing.number} · ${r.luckyPlanet.thing.direction}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(KpColors.Bad); Spacer(Modifier.width(8.dp))
        Text("Go easy on ${r.stressPlanet.planet} energy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Traditional correspondences – not from the KP Readers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Transits(r: DayReport) = KpCard {
    SectionTitle("Transits · star / sub lord")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        r.transits.forEach { (n, t) -> Pill("$n ${t.star.take(3)}/${t.sub.take(3)}", MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun HoraCard(h: HoraReport, z: java.time.ZoneId) = KpCard {
    SectionTitle("Planetary hours (hora)")
    Text("Reader 3 ~l.30440-30580: sunrise to noon is split in six, each hora in seven sub-horas. Afternoon and night continue the same cycle (assumption).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val now = Instant.now()
    val cur = h.horas.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
    cur?.let { Pill("Now: ${it.lord} hora until ${fmtTime(it.end, z)}", KpColors.Gold) }
    Text("Your running pair ${h.dashaPair.first}–${h.dashaPair.second} is served by (hora + sub, or day lord + hora):", style = MaterialTheme.typography.labelMedium)
    if (h.dashaPins.isEmpty()) Text("(no such hora today)", style = MaterialTheme.typography.bodySmall)
    h.dashaPins.forEach { Text("${fmtTime(it.start, z)}–${fmtTime(it.end, z)}  ·  ${it.hora}" + (it.sub?.let { s -> " hora, $s sub" } ?: " hora on ${h.dayLord} day"), style = MaterialTheme.typography.bodySmall) }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
    h.muhurta.forEach { (name, ws) ->
        Text(name, style = MaterialTheme.typography.labelMedium, color = if (name.startsWith("Avoid")) KpColors.Bad else KpColors.Good)
        Text(if (ws.isEmpty()) "(none today)" else ws.take(6).joinToString("   ") { "${fmtTime(it.start, z)}–${fmtTime(it.end, z)}" }, style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        h.horas.forEach { Pill("${it.lord.take(3)} ${fmtTime(it.start, z)}", if (it.night) MaterialTheme.colorScheme.secondary else KpColors.Gold) }
    }
}
