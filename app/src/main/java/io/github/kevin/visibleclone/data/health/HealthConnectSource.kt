package io.github.kevin.visibleclone.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.kevin.visibleclone.data.local.HrSampleEntity
import java.time.Instant

/**
 * Fallback intraday heart-rate source: whatever the Fitbit app has written to Health Connect.
 * Fitbit's continuous HR lands in Health Connect at roughly minute granularity, so this is not
 * suitable for real-time alerts but is fine for retrospective PacePoints.
 */
class HealthConnectSource(private val context: Context) {

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )
    }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.UPDATE_REQUIRED
        else -> Availability.UNAVAILABLE
    }

    enum class Availability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

    suspend fun grantedPermissions(): Set<String> =
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    suspend fun heartRate(from: Instant, to: Instant): List<HrSampleEntity> {
        val client = HealthConnectClient.getOrCreate(context)
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to),
            )
        )
        return response.records.flatMap { record ->
            record.samples.map { s ->
                HrSampleEntity(
                    timestampMs = s.time.toEpochMilli(),
                    source = "health",
                    bpm = s.beatsPerMinute.toInt(),
                )
            }
        }
    }
}
