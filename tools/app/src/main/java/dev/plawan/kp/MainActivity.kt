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

private val TABS = listOf(KpNav.Today to "Today", KpNav.Life to "Life", KpNav.Chart to "Chart", KpNav.Setup to "Setup")

@Composable
fun App(vm: KpViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = if (profile == null) 3 else tab
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (profile != null) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEachIndexed { i, (icon, label) ->
                    val selected = current == i
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = i },
                        icon = { KpNavIcon(icon, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (current) {
            0 -> TodayScreen(vm, m)
            1 -> LifeScreen(vm, m)
            2 -> ChartScreen(vm, m)
            else -> SettingsScreen(vm, m, onSaved = { tab = 0 })
        }
    }
}
