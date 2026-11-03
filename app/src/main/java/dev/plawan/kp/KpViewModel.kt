package dev.plawan.kp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.plawan.kp.data.KpRepository
import dev.plawan.kp.domain.KpAnalysis
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
    private val repository = KpRepository(app)
    private val analysis = KpAnalysis()
    private var rules = repository.loadRules()
    private val mutex = Mutex()
    private val timingMutex = Mutex()

    val profile = MutableStateFlow(repository.loadProfile())
    val context = MutableStateFlow<Context?>(null)
    val date = MutableStateFlow(LocalDate.now())
    val report = MutableStateFlow<DayReport?>(null)
    val hora = MutableStateFlow<HoraReport?>(null)
    val loadingDay = MutableStateFlow(false)
    val life = MutableStateFlow<List<EventReport>?>(null)
    val lifeSpan = MutableStateFlow<LifeRange?>(null)
    val tolerance = MutableStateFlow<Map<String, Tolerance>?>(null)
    val timings = MutableStateFlow<Map<String, EventTiming>>(emptyMap())
    val timingBusy = MutableStateFlow<Set<String>>(emptySet())
    val error = MutableStateFlow<String?>(null)
    val ruleUpdateStatus = MutableStateFlow<String?>(null)

    init { profile.value?.let { rebuild(it) } }

    fun saveProfile(p: Profile) {
        repository.saveProfile(p)
        profile.value = p
        rebuild(p)
    }

    /** Explicitly refresh the rule catalogue over HTTPS; validated rules are cached for offline use. */
    fun refreshRules() = viewModelScope.launch {
        ruleUpdateStatus.value = "Checking for rule updates…"
        runCatching {
            withContext(Dispatchers.IO) { repository.refreshRules() }
        }.onSuccess { updated ->
            rules = updated
            ruleUpdateStatus.value = "Rule catalogue updated (${updated.size} rules)."
            profile.value?.let { rebuild(it) }
        }.onFailure {
            ruleUpdateStatus.value = "Update unavailable: ${it.message ?: "network error"}. Using saved rules."
        }
    }

    /** Reads the last known device location after the UI has obtained runtime permission. */
    fun lastKnownLocation(): Place? = runCatching { repository.lastKnownLocation() }.getOrNull()

    private fun rebuild(p: Profile) = viewModelScope.launch {
        error.value = null
        life.value = null
        lifeSpan.value = null
        tolerance.value = null
        report.value = null
        hora.value = null
        timings.value = emptyMap()
        timingBusy.value = emptySet()
        runCatching {
            withContext(Dispatchers.Default) {
                mutex.withLock { repository.createContext(p, rules) }
            }
        }.onSuccess {
            context.value = it
            loadDay()
        }.onFailure {
            error.value = it.message ?: it.toString()
        }
    }

    fun shiftDay(delta: Long) {
        date.value = date.value.plusDays(delta)
        loadDay()
    }

    fun today() {
        date.value = LocalDate.now()
        loadDay()
    }

    fun loadDay() = viewModelScope.launch {
        val ctx = context.value ?: return@launch
        val requestedDate = date.value
        loadingDay.value = true
        runCatching {
            withContext(Dispatchers.Default) {
                mutex.withLock { analysis.day(ctx, requestedDate) }
            }
        }.onSuccess {
            if (requestedDate == date.value) {
                report.value = it.first
                hora.value = it.second
            }
        }.onFailure {
            error.value = it.message ?: it.toString()
        }
        loadingDay.value = false
    }

    fun loadLife() = viewModelScope.launch {
        val ctx = context.value ?: return@launch
        if (life.value != null) return@launch
        runCatching {
            withContext(Dispatchers.Default) {
                mutex.withLock { analysis.life(ctx, Instant.now()) }
            }
        }.onSuccess {
            lifeSpan.value = it.first
            life.value = it.second
        }.onFailure {
            error.value = it.message ?: it.toString()
        }
    }

    fun loadTolerance() = viewModelScope.launch {
        val ctx = context.value ?: return@launch
        if (tolerance.value != null) return@launch
        runCatching {
            withContext(Dispatchers.Default) {
                mutex.withLock { analysis.birthTimeTolerance(ctx) }
            }
        }.onSuccess { tolerance.value = it }
            .onFailure { error.value = it.message ?: it.toString() }
    }

    /** Calculate exact event timing lazily; keep long scans off the day/life mutex. */
    fun loadEventTiming(key: String) = viewModelScope.launch {
        val ctx = context.value ?: return@launch
        val range = lifeSpan.value ?: return@launch
        if (timings.value.containsKey(key) || key in timingBusy.value) return@launch
        timingBusy.value = timingBusy.value + key
        runCatching {
            withContext(Dispatchers.Default) {
                timingMutex.withLock { analysis.eventTiming(ctx, key, range) }
            }
        }.onSuccess {
            if (context.value === ctx) timings.value = timings.value + (key to it)
        }.onFailure {
            error.value = it.message ?: it.toString()
        }
        timingBusy.value = timingBusy.value - key
    }
}
