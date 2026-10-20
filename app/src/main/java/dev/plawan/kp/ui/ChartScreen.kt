package dev.plawan.kp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plawan.kp.KpViewModel
import dev.plawan.kp.engine.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DMY = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val ABBR = mapOf("Sun" to "Su", "Moon" to "Mo", "Mars" to "Ma", "Mercury" to "Me", "Jupiter" to "Ju", "Venus" to "Ve", "Saturn" to "Sa", "Rahu" to "Ra", "Ketu" to "Ke")
private fun dms(p: Point): String { val d = p.degInSign; return "%d°%02d'".format(d.toInt(), ((d % 1) * 60).toInt()) }

@Composable
fun ChartScreen(vm: KpViewModel, modifier: Modifier = Modifier) {
    val ctx by vm.context.collectAsStateWithLifecycle()
    val c = ctx ?: run { Loading(); return }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.profile.name.ifBlank { "Birth chart" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("KP ayanamsa ${"%.4f".format(c.chart.ayanamsa)}° · Placidus · mean node",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Asc ${dms(c.chart.asc)} ${c.chart.asc.sign} · ${c.chart.asc.starLord}/${c.chart.asc.subLord}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ScrollableTabRow(tab, containerColor = androidx.compose.ui.graphics.Color.Transparent, edgePadding = 8.dp) {
            listOf("Chart", "Planets", "Cusps", "Dasha", "Birth time").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> item { Diamond(c.chart) }
                1 -> item { PlanetTable(c.chart) }
                2 -> item { CuspTable(c.chart) }
                3 -> item { DashaTree(c) }
                else -> item { Sensitivity(vm) }
            }
        }
    }
}

@Composable
private fun Diamond(ch: Chart) = KpCard {
    SectionTitle("Bhava chart (house = cusp interval)")
    val line = MaterialTheme.colorScheme.outline
    val pos = listOf(.5f to .25f, .25f to .125f, .125f to .25f, .25f to .5f, .125f to .75f, .25f to .875f,
        .5f to .75f, .75f to .875f, .875f to .75f, .75f to .5f, .875f to .25f, .75f to .125f)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val s = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val st = Stroke(1.5.dp.toPx())
            drawRect(line, style = st)
            drawLine(line, Offset(0f, 0f), Offset(w, w), 1.5.dp.toPx()); drawLine(line, Offset(w, 0f), Offset(0f, w), 1.5.dp.toPx())
            drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, w / 2); lineTo(w / 2, w); lineTo(0f, w / 2); close() }, line, style = st)
        }
        pos.forEachIndexed { i, (fx, fy) ->
            val occ = ch.planets.values.filter { it.house == i + 1 }.joinToString(" ") { ABBR.getValue(it.name) + if (it.retrograde) "ᴿ" else "" }
            Column(Modifier.offset(x = s * fx - 34.dp, y = s * fy - 22.dp).width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${i + 1}", fontSize = 11.sp, color = KpColors.Gold)
                Text(occ, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun PlanetTable(ch: Chart) = KpCard {
    SectionTitle("Planets · sign / star / sub / sub-sub")
    ch.planets.values.forEachIndexed { i, p ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name + if (p.retrograde) " ᴿ" else "", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${dms(p)} ${p.sign}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp)); Pill("H${p.house}", KpColors.Indigo)
        }
        Text("${p.signLord} · ${p.starLord} · ${p.subLord} · ${p.subSubLord}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CuspTable(ch: Chart) = KpCard {
    SectionTitle("Placidus cusps · sign / star / sub")
    ch.cusps.forEachIndexed { i, p ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(34.dp)) { Text("H${i + 1}", color = KpColors.Gold, fontWeight = FontWeight.SemiBold) }
            Text("${dms(p)} ${p.sign}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${p.starLord}/${p.subLord}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DashaTree(c: Context) {
    val now = remember { Instant.now() }
    val z = c.zone
    var open by remember { mutableStateOf(c.mds.indexOfFirst { it.contains(now) }) }
    KpCard {
        SectionTitle("Vimshottari dasha (365.25-day year)")
        c.mds.forEachIndexed { i, md ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            val running = md.contains(now)
            Column(Modifier.clickable { open = if (open == i) -1 else i }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(md.lords[0], Modifier.weight(1f), fontWeight = FontWeight.Bold, color = if (running) KpColors.Gold else MaterialTheme.colorScheme.onSurface)
                    Text("${DMY.format(md.start.atZone(z))} – ${DMY.format(md.end.atZone(z))}", style = MaterialTheme.typography.bodySmall)
                }
                if (open == i) subPeriods(md).forEach { ad ->
                    val adRun = ad.contains(now)
                    Row(Modifier.padding(start = 16.dp)) {
                        Text("${ad.lords[1]}", Modifier.weight(1f), color = if (adRun) KpColors.Gold else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (adRun) FontWeight.Bold else FontWeight.Normal)
                        Text("${DMY.format(ad.start.atZone(z))} – ${DMY.format(ad.end.atZone(z))}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Sensitivity(vm: KpViewModel) {
    val tol by vm.tolerance.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadTolerance() }
    KpCard {
        SectionTitle("Birth-time sensitivity (±60 min)")
        Text("Minutes the birth time can move before a sub-lord changes. KP is sub-lord driven: anything flipping within a minute or two is not trustworthy until the time is verified.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val t = tol
        if (t == null) { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth()) } else t.forEach { (n, x) ->
            val worst = minOf(x.before ?: 999, x.after ?: 999)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n, Modifier.width(72.dp), fontWeight = FontWeight.SemiBold); Text(x.sub, Modifier.weight(1f))
                Text("−${x.before ?: ">60"} / +${x.after ?: ">60"} min", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp)); Pill(if (worst <= 3) "fragile" else if (worst <= 10) "check" else "ok", if (worst <= 3) KpColors.Bad else if (worst <= 10) KpColors.Gold else KpColors.Good)
            }
        }
    }
}
