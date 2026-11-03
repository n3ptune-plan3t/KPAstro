package dev.plawan.kp.data

import android.content.Context
import dev.plawan.kp.engine.Context as KpContext
import dev.plawan.kp.engine.Profile
import dev.plawan.kp.engine.Rule
import dev.plawan.kp.engine.parseRules

/**
 * Application data boundary.
 *
 * The UI and ViewModel do not know whether profiles come from preferences or
 * whether the rule catalogue comes from assets. This repository deliberately
 * keeps the current on-device sources as the default implementation.
 */
class KpRepository(context: Context) {
    private val appContext = context.applicationContext
    private val profileStore = ProfileStore(appContext)

    fun loadProfile(): Profile? = profileStore.load()

    fun saveProfile(profile: Profile) = profileStore.save(profile)

    fun loadRules(): Map<String, Rule> =
        appContext.assets.open("rules.json").bufferedReader().use { reader ->
            parseRules(reader.readText())
        }

    fun createContext(profile: Profile, rules: Map<String, Rule>): KpContext =
        KpContext(profile, rules)
}
