/* Thin JNI bridge over Swiss Ephemeris (Moshier, no data files) for the KP engine.
 * Swiss Ephemeris is AGPL-3.0 (see swisseph/agpl-3.0.txt); this app is open source. */
#include <jni.h>
#include <string.h>
#include "swisseph/swephexp.h"

#define FN(name) Java_dev_plawan_kp_engine_SweNative_##name

JNIEXPORT void JNICALL FN(init)(JNIEnv *env, jclass c) {
    swe_set_ephe_path(NULL);
    swe_set_sid_mode(SE_SIDM_KRISHNAMURTI, 0, 0);
}

/* returns 6 doubles: lon, lat, dist, lonSpeed, latSpeed, distSpeed (sidereal KP) */
JNIEXPORT jdoubleArray JNICALL FN(calcUt)(JNIEnv *env, jclass c, jdouble jd, jint body, jint flags) {
    double xx[6] = {0}; char serr[256];
    swe_calc_ut(jd, body, flags, xx, serr);
    jdoubleArray out = (*env)->NewDoubleArray(env, 6);
    (*env)->SetDoubleArrayRegion(env, out, 0, 6, xx);
    return out;
}

/* returns 22 doubles: cusps[1..12] then ascmc[0..9] */
JNIEXPORT jdoubleArray JNICALL FN(housesEx)(JNIEnv *env, jclass c, jdouble jd, jdouble lat, jdouble lon, jint flags) {
    double cusps[13] = {0}, ascmc[10] = {0}, res[22];
    swe_houses_ex(jd, flags, lat, lon, 'P', cusps, ascmc);
    memcpy(res, cusps + 1, 12 * sizeof(double));
    memcpy(res + 12, ascmc, 10 * sizeof(double));
    jdoubleArray out = (*env)->NewDoubleArray(env, 22);
    (*env)->SetDoubleArrayRegion(env, out, 0, 22, res);
    return out;
}

JNIEXPORT jdouble JNICALL FN(ayanamsaUt)(JNIEnv *env, jclass c, jdouble jd) {
    return swe_get_ayanamsa_ut(jd);
}

/* returns {retflag, tret}: next Sun rise/set (rsmi) after jd at lon/lat */
JNIEXPORT jdoubleArray JNICALL FN(riseSun)(JNIEnv *env, jclass c, jdouble jd, jdouble lon, jdouble lat, jint rsmi) {
    double geopos[3] = {lon, lat, 0.0}, tret = 0.0, res[2]; char serr[256];
    int rc = swe_rise_trans(jd, SE_SUN, NULL, SEFLG_MOSEPH, rsmi, geopos, 0.0, 0.0, &tret, serr);
    res[0] = (double)rc; res[1] = tret;
    jdoubleArray out = (*env)->NewDoubleArray(env, 2);
    (*env)->SetDoubleArrayRegion(env, out, 0, 2, res);
    return out;
}
