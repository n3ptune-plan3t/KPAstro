package dev.plawan.kp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plawan.kp.KpViewModel
import dev.plawan.kp.data.SampleProfile
import dev.plawan.kp.engine.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun SettingsScreen(vm: KpViewModel, modifier: Modifier = Modifier, onSaved: () -> Unit) {
    val saved by vm.profile.collectAsStateWithLifecycle()
    val p = saved ?: SampleProfile.value
    var name by remember(saved) { mutableStateOf(p.name) }
    var date by remember(saved) { mutableStateOf(p.birthLocal.toLocalDate().toString()) }
    var time by remember(saved) { mutableStateOf(p.birthLocal.toLocalTime().toString()) }
    var bz by remember(saved) { mutableStateOf(p.birth.zone.id) }
    var blat by remember(saved) { mutableStateOf(p.birth.lat.toString()) }
    var blon by remember(saved) { mutableStateOf(p.birth.lon.toString()) }
    var same by remember(saved) { mutableStateOf(p.current == p.birth) }
    var cz by remember(saved) { mutableStateOf(p.current.zone.id) }
    var clat by remember(saved) { mutableStateOf(p.current.lat.toString()) }
    var clon by remember(saved) { mutableStateOf(p.current.lon.toString()) }
    var err by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (saved == null) "Welcome" else "Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("Everything stays on this device – the app has no internet permission.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        KpCard {
            SectionTitle("Birth details")
            Field("Name", name) { name = it }
            Field("Date (YYYY-MM-DD)", date) { date = it }
            Field("Local time (HH:MM, 24h)", time) { time = it }
            Field("Time zone ID", bz) { bz = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field("Latitude (+N)", blat, num = true) { blat = it } }
                Box(Modifier.weight(1f)) { Field("Longitude (+E)", blon, num = true) { blon = it } }
            }
            Text("A wrong birth time or place changes cusp sub-lords – check the “Birth time” tab.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        KpCard {
            SectionTitle("Where you are now")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Same as birth place", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(same, { same = it })
            }
            if (!same) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Field("Time zone ID", cz) { cz = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { Field("Latitude", clat, num = true) { clat = it } }
                    Box(Modifier.weight(1f)) { Field("Longitude", clon, num = true) { clon = it } }
                }
            }
            Text("Current place drives sunrise (day lord) and ruling planets; longitudes and dashas do not depend on it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        err?.let { Text(it, color = KpColors.Bad, style = MaterialTheme.typography.bodySmall) }
        Button({
            runCatching {
                val birth = Place(ZoneId.of(bz.trim()), blat.trim().toDouble(), blon.trim().toDouble())
                val cur = if (same) birth else Place(ZoneId.of(cz.trim()), clat.trim().toDouble(), clon.trim().toDouble())
                require(birth.lat in -66.0..66.0 && birth.lon in -180.0..180.0) { "Latitude must be within ±66° (Placidus limit)" }
                vm.saveProfile(Profile(name.trim(), LocalDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim())), birth, cur))
            }.onSuccess { err = null; onSaved() }.onFailure { err = "Check the fields: ${it.message}" }
        }, Modifier.fillMaxWidth().height(52.dp)) { Text("Save and compute chart") }
        Text("KP Astro 0.1 · Swiss Ephemeris (Moshier, KP ayanamsa) via JNI · AGPL-3.0\n" + DISCLAIMER,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun Field(label: String, value: String, num: Boolean = false, onChange: (String) -> Unit) = OutlinedTextField(
    value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
    shape = RoundedCornerShape(14.dp),
    keyboardOptions = if (num) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
)
