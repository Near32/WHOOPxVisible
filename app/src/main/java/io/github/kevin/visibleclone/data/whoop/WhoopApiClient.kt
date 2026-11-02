package io.github.kevin.visibleclone.data.whoop

import io.github.kevin.visibleclone.data.local.BodyMeasurement
import io.github.kevin.visibleclone.data.local.CycleEntity
import io.github.kevin.visibleclone.data.local.RecoveryEntity
import io.github.kevin.visibleclone.data.local.SettingsStore
import io.github.kevin.visibleclone.data.local.SleepEntity
import io.github.kevin.visibleclone.data.local.WhoopTokens
import io.github.kevin.visibleclone.data.local.WorkoutEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Thin WHOOP REST client. Parses into domain entities directly; there is no separate DTO layer
 * because every field we keep is a 1:1 mapping.
 */
class WhoopApiClient(
    private val settings: SettingsStore,
    private val auth: WhoopAuthClient = WhoopAuthClient(),
    private val transport: HttpTransport = HttpTransport(),
) {
    private val base = "https://api.prod.whoop.com/developer"

    /** WHOOP documents RFC 3339 with millisecond precision; Instant.toString() emits microseconds. */
    private val instantFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true }

    /** Serializes refresh: a successful refresh invalidates the previous token pair. */
    private val refreshMutex = Mutex()

    private suspend fun bearer(): String {
        val now = System.currentTimeMillis()
        val current = settings.currentTokens() ?: throw IOException("Not signed in to WHOOP")
        if (current.isValid(now)) return current.accessToken

        return refreshMutex.withLock {
            // Another coroutine may have refreshed while we waited.
            val latest = settings.currentTokens() ?: throw IOException("Not signed in to WHOOP")
            if (latest.isValid(System.currentTimeMillis())) return@withLock latest.accessToken
            val rt = latest.refreshToken ?: throw IOException("Session expired — sign in again")
            val response = auth.refresh(rt)
            settings.saveTokens(
                WhoopTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken ?: rt,
                    expiresAtMs = response.expiresAtMs,
                    scope = response.scope,
                )
            )
            response.accessToken
        }
    }

    private suspend fun getJson(pathAndQuery: String): JsonObject =
        json.parseToJsonElement(transport.get("$base$pathAndQuery", bearer())).jsonObject

    // ---------- profile ----------

    suspend fun profileName(): String {
        val o = getJson("/v2/user/profile/basic")
        val first = o["first_name"]?.jsonPrimitive?.content.orEmpty()
        val last = o["last_name"]?.jsonPrimitive?.content.orEmpty()
        return listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "WHOOP user" }
    }

    suspend fun bodyMeasurement(): BodyMeasurement {
        val o = getJson("/v2/user/measurement/body")
        return BodyMeasurement(
            heightMeter = o["height_meter"]?.jsonPrimitive?.doubleOrNull,
            weightKilogram = o["weight_kilogram"]?.jsonPrimitive?.doubleOrNull,
            maxHeartRate = o["max_heart_rate"]?.jsonPrimitive?.doubleOrNull?.toInt(),
        )
    }

    // ---------- paged collections ----------

    /** Follows next_token until exhausted. [start] / [end] are ISO-8601 instants. */
    suspend fun <T> collectPages(
        path: String,
        start: Instant? = null,
        end: Instant? = null,
        maxPages: Int = 40,
        map: (JsonObject) -> T?,
    ): List<T> {
        val out = mutableListOf<T>()
        var next: String? = null
        var page = 0
        while (page++ < maxPages) {
            val query = buildString {
                append(path).append('?')
                append("limit=25")
                start?.let { append("&start=").append(URLEncoder.encode(instantFormat.format(it), "UTF-8")) }
                end?.let { append("&end=").append(URLEncoder.encode(instantFormat.format(it), "UTF-8")) }
                next?.let { append("&nextToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            android.util.Log.d("WhoopApi", "GET $base$query")
            val o = getJson(query)
            (o["records"] as? JsonArray)?.forEach { rec -> map(rec.jsonObject)?.let { out += it } }
            // A JSON null arrives as the *string* "null" when read via .content — treat it as absent.
            next = o["next_token"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() && it != "null" }
            if (next.isNullOrBlank()) break
        }
        return out
    }

    suspend fun cycles(start: Instant?, end: Instant?): List<CycleEntity> = collectPages("/v2/cycle", start, end) { o ->
        val score = o.obj("score")
        CycleEntity(
            id = o.str("id") ?: return@collectPages null,
            start = o.str("start") ?: "",
            end = o.str("end"),
            strain = score?.num("strain"),
            avgHeartRate = score?.int("average_heart_rate"),
            maxHeartRate = score?.int("max_heart_rate"),
            kilojoule = score?.num("kilojoule"),
        )
    }

    suspend fun recoveries(start: Instant?, end: Instant?): List<RecoveryEntity> =
        collectPages("/v2/recovery", start, end) { o ->
            val score = o.obj("score")
            RecoveryEntity(
                cycleId = o.str("cycle_id") ?: "",
                sleepId = o.str("sleep_id"),
                recoveryScore = score?.num("recovery_score"),
                restingHeartRate = score?.int("resting_heart_rate"),
                hrvRmssd = score?.num("hrv_rmssd_milli"),
                spo2 = score?.num("spo2_percentage"),
                skinTempCelsius = score?.num("skin_temp_celsius"),
                createdAt = o.str("created_at") ?: "",
            ).takeIf { it.cycleId.isNotBlank() }
        }

    suspend fun sleeps(start: Instant?, end: Instant?): List<SleepEntity> =
        collectPages("/v2/activity/sleep", start, end) { o ->
            val score = o.obj("score")
            val stages = score?.obj("stage_summary")
            SleepEntity(
                id = o.str("id") ?: "",
                cycleId = o.str("cycle_id"),
                start = o.str("start") ?: "",
                end = o.str("end") ?: "",
                nap = o["nap"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                inBedMs = stages?.long("total_in_bed_time_milli"),
                awakeMs = stages?.long("total_awake_time_milli"),
                lightMs = stages?.long("total_light_sleep_time_milli"),
                deepMs = stages?.long("total_slow_wave_sleep_time_milli"),
                remMs = stages?.long("total_rem_sleep_time_milli"),
                disturbanceCount = stages?.int("disturbance_count"),
                respiratoryRate = score?.num("respiratory_rate"),
                performancePct = score?.num("sleep_performance_percentage"),
                efficiencyPct = score?.num("sleep_efficiency_percentage"),
            ).takeIf { it.id.isNotBlank() }
        }

    suspend fun workouts(start: Instant?, end: Instant?): List<WorkoutEntity> =
        collectPages("/v2/activity/workout", start, end) { o ->
            val score = o.obj("score")
            val zones = score?.obj("zone_durations")
            WorkoutEntity(
                id = o.str("id") ?: "",
                start = o.str("start") ?: "",
                end = o.str("end") ?: "",
                sportName = o.str("sport_name"),
                strain = score?.num("strain"),
                avgHeartRate = score?.int("average_heart_rate"),
                maxHeartRate = score?.int("max_heart_rate"),
                zone0Ms = zones?.long("zone_zero_milli"),
                zone1Ms = zones?.long("zone_one_milli"),
                zone2Ms = zones?.long("zone_two_milli"),
                zone3Ms = zones?.long("zone_three_milli"),
                zone4Ms = zones?.long("zone_four_milli"),
                zone5Ms = zones?.long("zone_five_milli"),
            ).takeIf { it.id.isNotBlank() }
        }
}

// ---------- JSON helpers ----------

/** Safe nested object lookup: scored records may carry `"score": null`. */
private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject

private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull?.takeIf { it != "null" }
private fun JsonObject.num(k: String): Double? = this[k]?.jsonPrimitive?.doubleOrNull

// WHOOP returns some nominally-integer fields as floats (e.g. resting_heart_rate: 54.0).
// intOrNull/longOrNull reject those, so go through Double and truncate.
private fun JsonObject.int(k: String): Int? = this[k]?.jsonPrimitive?.doubleOrNull?.toInt()
private fun JsonObject.long(k: String): Long? = this[k]?.jsonPrimitive?.doubleOrNull?.toLong()
