package io.github.kevin.visibleclone.data.local

import kotlinx.serialization.Serializable

@Serializable
data class WhoopTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
    val scope: String? = null,
) {
    fun isValid(nowMs: Long = System.currentTimeMillis()): Boolean =
        accessToken.isNotBlank() && nowMs < expiresAtMs - 60_000
}

@Serializable
data class BodyMeasurement(
    val heightMeter: Double? = null,
    val weightKilogram: Double? = null,
    val maxHeartRate: Int? = null,
)
