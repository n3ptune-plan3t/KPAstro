package dev.plawan.kp.data

import dev.plawan.kp.engine.Place
import dev.plawan.kp.engine.Profile
import java.time.LocalDateTime
import java.time.ZoneId

/** Pre-fills the setup form (from profile.json in the bundle). Delete this file before publishing the repo if you prefer. */
object SampleProfile {
    val value = Profile(
        "Plawan", LocalDateTime.of(2000, 11, 21, 18, 12),
        Place(ZoneId.of("Asia/Kolkata"), 26.4446, 91.4373), Place(ZoneId.of("Asia/Kolkata"), 26.4446, 91.4373),
    )
}
