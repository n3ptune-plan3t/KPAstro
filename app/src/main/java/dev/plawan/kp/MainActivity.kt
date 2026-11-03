package dev.plawan.kp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plawan.kp.ui.*

class MainActivity : ComponentActivity() {
    private val vm: KpViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KpTheme { App(vm) } }
    }
}

private data class Destination(val icon: KpNav, val label: String)

private val DESTINATIONS = listOf(
    Destination(KpNav.Today, "Today"),
    Destination(KpNav.Forecast, "Forecast"),
    Destination(KpNav.Chart, "Chart"),
    Destination(KpNav.Profile, "Profile"),
)

@Composable
fun App(vm: KpViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    // A first-time user must create a birth profile before the chart-based
    // destinations are available. Returning users can edit it from Profile.
    val activeIndex = if (profile == null) DESTINATIONS.lastIndex else selectedIndex
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (profile != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    DESTINATIONS.forEachIndexed { index, destination ->
                        val selected = activeIndex == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedIndex = index },
                            icon = {
                                KpNavIcon(
                                    destination.icon,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (activeIndex) {
            0 -> TodayScreen(vm, contentModifier)
            1 -> LifeScreen(vm, contentModifier)
            2 -> ChartScreen(vm, contentModifier)
            else -> SettingsScreen(vm, contentModifier, onSaved = { selectedIndex = 0 })
        }
    }
}
