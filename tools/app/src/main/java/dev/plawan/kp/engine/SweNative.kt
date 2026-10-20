package dev.plawan.kp.engine

import java.time.Instant

/** JNI surface of the bundled Swiss Ephemeris (Moshier, KP sidereal). Implemented in cpp/swe_jni.c. */
object SweNative {
    @JvmStatic external fun init()
    @JvmStatic external fun calcUt(jd: Double, body: Int, flags: Int): DoubleArray
    @JvmStatic external fun housesEx(jd: Double, lat: Double, lon: Double, flags: Int): DoubleArray
    @JvmStatic external fun ayanamsaUt(jd: Double): Double
    @JvmStatic external fun riseSun(jd: Double, lon: Double, lat: Double, rsmi: Int): DoubleArray
}

/** Thread-safe facade (Swiss Ephemeris keeps global state, so every call is serialised). */
object Swe {
    const val SUN = 0; const val MOON = 1; const val MERCURY = 2; const val VENUS = 3
    const val MARS = 4; const val JUPITER = 5; const val SATURN = 6; const val MEAN_NODE = 10
    private const val FLG_MOSEPH = 4; private const val FLG_SPEED = 256; private const val FLG_SIDEREAL = 65536
    private const val CALC_RISE = 1; private const val CALC_MTRANSIT = 4; private const val HINDU_RISING = 896   // disc centre, no refraction
    private val lock = Any()
    @Volatile private var ready = false

    private fun ensure() {
        if (ready) return
        synchronized(lock) {
            if (!ready) { System.loadLibrary("kpswe"); SweNative.init(); ready = true }
        }
    }

    fun jd(t: Instant): Double = t.epochSecond / 86400.0 + t.nano / 86400e9 + 2440587.5
    fun instant(jd: Double): Instant = Instant.ofEpochMilli(Math.round((jd - 2440587.5) * 86400000.0))

    /** Sidereal (KP) longitude, speed. */
    fun body(jd: Double, body: Int): DoubleArray { ensure(); return synchronized(lock) { SweNative.calcUt(jd, body, FLG_MOSEPH or FLG_SIDEREAL or FLG_SPEED) } }
    fun lon(jd: Double, body: Int): Double = body(jd, body)[0]
    /** 12 Placidus cusps followed by ascmc[0..9]. */
    fun houses(jd: Double, lat: Double, lon: Double): DoubleArray { ensure(); return synchronized(lock) { SweNative.housesEx(jd, lat, lon, FLG_SIDEREAL or FLG_MOSEPH) } }
    fun ayanamsa(jd: Double): Double { ensure(); return synchronized(lock) { SweNative.ayanamsaUt(jd) } }
    /** Returns (returnCode, sunriseJd) for the first sunrise after `jd`. */
    fun sunrise(jd: Double, lat: Double, lon: Double): Pair<Int, Double> {
        ensure(); val r = synchronized(lock) { SweNative.riseSun(jd, lon, lat, CALC_RISE or HINDU_RISING) }
        return r[0].toInt() to r[1]
    }
    /** Returns (returnCode, jd) of the first upper meridian transit of the Sun after `jd` (local noon). */
    fun transit(jd: Double, lat: Double, lon: Double): Pair<Int, Double> {
        ensure(); val r = synchronized(lock) { SweNative.riseSun(jd, lon, lat, CALC_MTRANSIT) }
        return r[0].toInt() to r[1]
    }
}
