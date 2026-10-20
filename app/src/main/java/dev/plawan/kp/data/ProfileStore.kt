package dev.plawan.kp.data

import android.content.Context
import dev.plawan.kp.engine.Place
import dev.plawan.kp.engine.Profile
import java.time.LocalDateTime
import java.time.ZoneId

/** Offline profile persistence (SharedPreferences). */
class ProfileStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("kp_profile", Context.MODE_PRIVATE)

    fun load(): Profile? {
        val b = sp.getString("birth", null) ?: return null
        return runCatching {
            Profile(
                sp.getString("name", "") ?: "", LocalDateTime.parse(b),
                Place(ZoneId.of(sp.getString("bz", "Asia/Kolkata")!!), sp.getString("blat_s", "0")!!.toDouble(), sp.getString("blon_s", "0")!!.toDouble()),
                Place(ZoneId.of(sp.getString("cz", "Asia/Kolkata")!!), sp.getString("clat_s", "0")!!.toDouble(), sp.getString("clon_s", "0")!!.toDouble()),
            )
        }.getOrNull()
    }

    fun save(p: Profile) {
        // coordinates stored as strings to keep full double precision
        sp.edit().putString("name", p.name).putString("birth", p.birthLocal.toString())
            .putString("bz", p.birth.zone.id).putString("blat_s", p.birth.lat.toString()).putString("blon_s", p.birth.lon.toString())
            .putString("cz", p.current.zone.id).putString("clat_s", p.current.lat.toString()).putString("clon_s", p.current.lon.toString())
            .apply()
    }
}
