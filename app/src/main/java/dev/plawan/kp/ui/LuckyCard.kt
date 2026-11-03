package dev.plawan.kp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.plawan.kp.engine.*

private fun swatch(name: String): Color = when (name.lowercase()) {
    "orange" -> Color(0xFFF57C00)
    "dark red" -> Color(0xFF8B1A1A)
    "red" -> Color(0xFFD32F2F)
    "deep red" -> Color(0xFF9B1B30)
    "white" -> Color(0xFFF5F5F5)
    "silvery" -> Color(0xFFC0C0C0)
    "cream" -> Color(0xFFFFF1CC)
    "ivory" -> Color(0xFFFFFFF0)
    "green" -> Color(0xFF2E7D32)
    "purple" -> Color(0xFF7B1FA2)
    "violet" -> Color(0xFF8E24AA)
    "blue" -> Color(0xFF1976D2)
    "indigo" -> Color(0xFF3F51B5)
    "black" -> Color(0xFF212121)
    "dark brown" -> Color(0xFF5D4037)
    else -> Color(0xFF9E9E9E) // mixed colours
}

private fun trad(src: String) = if (src.isEmpty()) " · traditional, not in the Readers" else ""

@Composable
fun LuckyCard(r: DayReport, h: HoraReport?) {
    val k = r.luckyKit
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var uses by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf(false) }
    KpCard {
        SectionTitle("Lucky colours & numbers")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Lucky planet ${k.primary.planet}", KpColors.Good, filled = true)
            k.secondary?.let { Pill("also ${it.planet}", KpColors.Gold) }
            if (k.dayLordIsLucky) Pill("its own day", KpColors.Indigo)
        }

        Text("Colours", style = MaterialTheme.typography.labelLarge, color = muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            k.colours.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(swatch(c), 16.dp); Spacer(Modifier.width(6.dp))
                    Text(c, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }

        Text("Numbers", style = MaterialTheme.typography.labelLarge, color = muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            k.numbers.forEach { Pill(it.toString(), KpColors.Gold, filled = true) }
        }
        if (k.numberSeries.isNotEmpty())
            Text("${k.primary.planet} number series: " + k.numberSeries.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = muted)
        if (k.primary.numbersSrc.isEmpty())
            Text("${k.primary.planet}'s number is a traditional correspondence, not in the Readers.", style = MaterialTheme.typography.bodySmall, color = muted)
        k.dateNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = KpColors.Gold) }

        Text("Direction: ${k.primary.direction}" + trad(k.primary.directionSrc), style = MaterialTheme.typography.bodyMedium)
        if (k.avoidColours.isNotEmpty() || k.avoidNumbers.isNotEmpty()) {
            val parts = buildList {
                if (k.avoidColours.isNotEmpty()) add("colours " + k.avoidColours.joinToString(" / ").lowercase() + " (${r.stressPlanet.planet} energy)")
                if (k.avoidNumbers.isNotEmpty()) add("number " + k.avoidNumbers.joinToString(", ") + " (${k.primary.planet}, Reader 2)")
            }
            Text("Go easy on: " + parts.joinToString("; "), style = MaterialTheme.typography.bodyMedium, color = KpColors.Bad)
        }
        h?.let { hr ->
            val mine = hr.horas.filter { it.lord == k.primary.planet }.take(3)
            if (mine.isNotEmpty())
                Text("${k.primary.planet} hora today: " + mine.joinToString("   ") { "${fmtTime(it.start, r.zone)}–${fmtTime(it.end, r.zone)}" },
                    style = MaterialTheme.typography.bodyMedium)
        }
        if (k.primary.note.isNotEmpty()) Text(k.primary.note, style = MaterialTheme.typography.bodySmall, color = muted)

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text(if (uses) "Hide how to use them" else "How to use them today",
            Modifier.clickable { uses = !uses }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (uses) {
            k.uses.forEach { u ->
                Text(u.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                u.tips.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                if (u.windowLabel.isNotEmpty()) {
                    Text(if (u.windows.isEmpty()) "${u.windowLabel}: no clear peak, keep a steady pace"
                         else "${u.windowLabel}: " + u.windows.joinToString("   ") { fmtWindow(it, r.zone) },
                        style = MaterialTheme.typography.bodyMedium, color = KpColors.Good)
                    if (u.avoid.isNotEmpty())
                        Text("Caution: " + u.avoid.joinToString("   ") { fmtWindow(it, r.zone) }, style = MaterialTheme.typography.bodyMedium, color = KpColors.Bad)
                }
                Text(u.basis, style = MaterialTheme.typography.labelSmall, color = muted)
            }
            Text(k.footer, style = MaterialTheme.typography.bodySmall, color = muted)
        }

        Text(if (sources) "Hide sources" else "Sources",
            Modifier.clickable { sources = !sources }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (sources) {
            listOfNotNull(k.primary, k.secondary).forEach { l ->
                Text(l.planet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("Colours: " + (l.coloursSrc.ifEmpty { "traditional" }), style = MaterialTheme.typography.bodySmall, color = muted)
                Text("Numbers: " + (l.numbersSrc.ifEmpty { "traditional" }), style = MaterialTheme.typography.bodySmall, color = muted)
                Text("Direction: " + (l.directionSrc.ifEmpty { "traditional" }), style = MaterialTheme.typography.bodySmall, color = muted)
                if (l.day.isNotEmpty()) Text("Day: ${l.day}" + (if (l.daySrc.isEmpty()) " (traditional)" else " (${l.daySrc})"), style = MaterialTheme.typography.bodySmall, color = muted)
                if (l.stars.isNotEmpty()) Text("Moon-star days: " + l.stars.joinToString(", ") + " (${l.starsSrc})", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}
