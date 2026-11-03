package dev.plawan.kp.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
    val context = LocalContext.current
    val ruleStatus by vm.ruleUpdateStatus.collectAsStateWithLifecycle()

    fun applyDeviceLocation() {
        val place = vm.lastKnownLocation()
        if (place == null) {
            err = "No recent device location. Enable location services or enter coordinates manually."
        } else {
            same = false
            clat = place.lat.toString()
            clon = place.lon.toString()
            cz = place.zone.id
            err = null
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            applyDeviceLocation()
        } else {
            err = "Location permission was not granted. You can still enter coordinates manually."
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (saved == null) "Create your profile" else "Your profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            if (saved == null) "Enter your birth details to generate your KP chart."
            else "Manage the birth data and calculation location used by your chart.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        KpCard {
            SectionTitle("Birth details")
            Field("Name (optional)", name) { name = it }
            Field("Birth date · YYYY-MM-DD", date) { date = it }
            Field("Birth time · 24-hour HH:MM", time) { time = it }
            Field("Birth time zone", bz) { bz = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Field("Latitude · north +", blat, num = true) { blat = it } }
                Box(Modifier.weight(1f)) { Field("Longitude · east +", blon, num = true) { blon = it } }
            }
            Text(
                "Use the coordinates of the birth place, not the place you live now. " +
                    "The birth time zone is the zone that applied on the birth date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        KpCard {
            SectionTitle("Calculation location")
            Text(
                "Used for local sunrise, day lord and ruling planets. It does not change the natal planetary longitudes or dashas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use birth place", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(same, { same = it })
            }
            Button(
                onClick = {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) applyDeviceLocation()
                    else locationPermission.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Use device location") }
            if (!same) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Field("Current time zone", cz) { cz = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { Field("Latitude", clat, num = true) { clat = it } }
                    Box(Modifier.weight(1f)) { Field("Longitude", clon, num = true) { clon = it } }
                }
            }
        }

        KpCard {
            SectionTitle("Online rule updates")
            Text(
                "Check the project's HTTPS rule catalogue. Validated updates are cached; chart calculations continue to work offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { vm.refreshRules() }, modifier = Modifier.fillMaxWidth()) {
                Text("Check for rule updates")
            }
            ruleStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        err?.let { Text(it, color = KpColors.Bad, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                runCatching {
                    val birth = Place(ZoneId.of(bz.trim()), blat.trim().toDouble(), blon.trim().toDouble())
                    val current = if (same) birth else Place(
                        ZoneId.of(cz.trim()), clat.trim().toDouble(), clon.trim().toDouble(),
                    )
                    require(birth.lat in -66.0..66.0 && birth.lon in -180.0..180.0) {
                        "Birth latitude must be within ±66° (Placidus limit)"
                    }
                    require(current.lat in -66.0..66.0 && current.lon in -180.0..180.0) {
                        "Calculation latitude must be within ±66° (Placidus limit)"
                    }
                    vm.saveProfile(
                        Profile(
                            name.trim(),
                            LocalDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim())),
                            birth,
                            current,
                        ),
                    )
                }.onSuccess {
                    err = null
                    onSaved()
                }.onFailure {
                    err = "Check the fields: ${it.message}"
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (saved == null) "Create profile and calculate" else "Save profile")
        }
        Text(
            "KP Astro 0.1 · Swiss Ephemeris (Moshier, KP ayanamsa) via JNI · AGPL-3.0\n$DISCLAIMER",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun Field(label: String, value: String, num: Boolean = false, onChange: (String) -> Unit) = OutlinedTextField(
    value,
    onChange,
    Modifier.fillMaxWidth(),
    label = { Text(label) },
    singleLine = true,
    shape = RoundedCornerShape(14.dp),
    keyboardOptions = if (num) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
)
