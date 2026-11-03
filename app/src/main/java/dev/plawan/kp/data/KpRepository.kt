package dev.plawan.kp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dev.plawan.kp.engine.Context as KpContext
import dev.plawan.kp.engine.Place
import dev.plawan.kp.engine.Profile
import dev.plawan.kp.engine.Rule
import dev.plawan.kp.engine.parseRules
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Application data boundary. Bundled and validated cached rules remain available offline.
 * Remote updates are explicitly user-triggered and never send birth/profile data.
 */
class KpRepository(context: Context) {
    private val appContext = context.applicationContext
    private val profileStore = ProfileStore(appContext)
    private val cache = appContext.getSharedPreferences("kp_remote_rules", Context.MODE_PRIVATE)

    fun loadProfile(): Profile? = profileStore.load()

    fun saveProfile(profile: Profile) = profileStore.save(profile)

    fun loadRules(): Map<String, Rule> {
        val bundled = readBundledRules()
        val cached = cache.getString(CACHE_KEY, null)
        if (!cached.isNullOrBlank()) {
            runCatching { validateRules(cached, bundled.keys) }.getOrNull()?.let { return it }
        }
        return bundled
    }

    /** Download, validate and cache the catalogue. On failure the caller keeps current rules. */
    fun refreshRules(): Map<String, Rule> {
        val bundled = readBundledRules()
        val connection = (URL(RULES_URL).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KPAstro-Android")
            instanceFollowRedirects = false
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Rule server returned HTTP \${connection.responseCode}"
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            require(body.toByteArray(Charsets.UTF_8).size <= MAX_RULE_BYTES) { "Rule catalogue is too large" }
            val validated = validateRules(body, bundled.keys)
            cache.edit().putString(CACHE_KEY, body).apply()
            return validated
        } finally {
            connection.disconnect()
        }
    }

    /** Last-known device position only; this does not make a network request. */
    fun lastKnownLocation(): Place? {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val now = System.currentTimeMillis()
        val best: Location? = manager.getProviders(true).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { now - it.time <= MAX_LOCATION_AGE_MS }.maxByOrNull { it.time }
        val location = best ?: return null
        if (location.latitude !in -66.0..66.0 || location.longitude !in -180.0..180.0) return null
        return Place(java.time.ZoneId.systemDefault(), location.latitude, location.longitude)
    }

    fun createContext(profile: Profile, rules: Map<String, Rule>): KpContext =
        KpContext(profile, rules)

    private fun readBundledRules(): Map<String, Rule> =
        appContext.assets.open("rules.json").bufferedReader().use { reader ->
            parseRules(reader.readText())
        }

    private fun validateRules(json: String, expectedKeys: Set<String>): Map<String, Rule> {
        val parsed = parseRules(json)
        require(parsed.keys == expectedKeys) { "Remote catalogue has a different event set" }
        require(parsed.isNotEmpty()) { "Remote catalogue is empty" }
        parsed.forEach { (key, rule) ->
            require(rule.key == key && rule.cusp in 1..12 && rule.primary in 1..12) { "Invalid rule: $key" }
            require((rule.support + rule.altSupport + rule.anySupport + rule.denial).all { it in 1..12 }) {
                "Invalid house number in rule: $key"
            }
            require(rule.status in setOf("verified", "partial", "unverified")) { "Invalid status in rule: $key" }
            require(rule.source.isNotBlank()) { "Missing source citation in rule: $key" }
        }
        return parsed
    }

    companion object {
        private const val CACHE_KEY = "validated_rules_json"
        private const val MAX_RULE_BYTES = 512 * 1024
        private const val MAX_LOCATION_AGE_MS = 24L * 60L * 60L * 1000L
        private const val RULES_URL =
            "https://raw.githubusercontent.com/n3ptune-plan3t/KPAstro/deepseek/app/src/main/assets/rules.json"
    }
}
