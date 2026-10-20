package dev.plawan.kp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.plawan.kp.data.ProfileStore
import dev.plawan.kp.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

class KpViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore(app)
    private val rules = parseRules(app.assets.open("rules.json").bufferedReader().use { it.readText() })
    private val mutex = Mutex()

    val profile = MutableStateFlow(store.load())
    val context = MutableStateFlow<Context?>(null)
    val date = MutableStateFlow(LocalDate.now())
    val report = MutableStateFlow<DayReport?>(null)
    val hora = MutableStateFlow<HoraReport?>(null)
    val loadingDay = MutableStateFlow(false)
    val life = MutableStateFlow<List<EventReport>?>(null)
    val tolerance = MutableStateFlow<Map<String, Tolerance>?>(null)
    val error = MutableStateFlow<String?>(null)

    init { profile.value?.let { rebuild(it) } }

    fun saveProfile(p: Profile) { store.save(p); profile.value = p; rebuild(p) }

    private fun rebuild(p: Profile) = viewModelScope.launch {
        error.value = null; life.value = null; tolerance.value = null; report.value = null
        runCatching { withContext(Dispatchers.Default) { mutex.withLock { Context(p, rules) } } }
            .onSuccess { context.value = it; loadDay() }.onFailure { error.value = it.message ?: it.toString() }
    }

    fun shiftDay(delta: Long) { date.value = date.value.plusDays(delta); loadDay() }
    fun today() { date.value = LocalDate.now(); loadDay() }

    fun loadDay() = viewModelScope.launch {
        val ctx = context.value ?: return@launch
        val d = date.value
        loadingDay.value = true
        runCatching { withContext(Dispatchers.Default) { mutex.withLock { val at = d.atTime(12, 0).atZone(ctx.zone); dayReport(ctx, at) to horaReport(ctx, at) } } }
            .onSuccess { if (d == date.value) { report.value = it.first; hora.value = it.second } }.onFailure { error.value = it.message ?: it.toString() }
        loadingDay.value = false
    }

    fun loadLife() = viewModelScope.launch {
        val ctx = context.value ?: return@launch
        if (life.value != null) return@launch
        life.value = withContext(Dispatchers.Default) { mutex.withLock { lifeReport(ctx.chart, ctx.rules, 20, Instant.now()) } }
    }

    fun loadTolerance() = viewModelScope.launch {
        val ctx = context.value ?: return@launch
        if (tolerance.value != null) return@launch
        tolerance.value = withContext(Dispatchers.Default) { mutex.withLock { birthTimeTolerance(ctx.chart.dtUtc, ctx.chart.lat, ctx.chart.lon, 60) } }
    }
}
