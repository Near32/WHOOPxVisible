# WHOOP Developer API — notes for this app

Source: https://developer.whoop.com/docs/developing/overview/ and https://developer.whoop.com/api/
Captured 2026-11-02. Re-check if calls start failing.

## What it is

RESTful HTTP API, OAuth 2.0, per-member access/refresh tokens. Up to 5 apps per developer
account. No official Android SDK.

## Auth

| Purpose | URL |
|---|---|
| Authorization | `https://api.prod.whoop.com/oauth/oauth2/auth` |
| Token exchange / refresh | `https://api.prod.whoop.com/oauth/oauth2/token` |

- Bearer token in `Authorization` header.
- `offline` scope is **required** to receive a refresh token.
- Refreshing **invalidates the previous access and refresh tokens** — always persist the latest
  pair, and serialize concurrent refreshes or the second one fails.
- `expires_in` (seconds). Expired → `401 Unauthorized`.
- `state` is echoed back; must be ≥ 8 characters if generated manually.
- Redirect URL must be registered in the Developer Dashboard. Documented examples are
  `https://whoop.com/example/redirect` and `whoop://example/redirect` — a custom scheme is allowed.
  localhost/loopback is not documented.
- Revoke via `DELETE /v2/user/access` (204).

### Scopes

`read:recovery` `read:cycles` `read:workout` `read:sleep` `read:profile` `read:body_measurement`
plus `offline`. (`whoop-partner/token` is a trusted-partner scope, not for us.)

## Endpoints we use

### User
- `GET /v2/user/profile/basic` → `user_id`, `email`, `first_name`, `last_name`
- `GET /v2/user/measurement/body` → `height_meter`, `weight_kilogram`, `max_heart_rate`
- `DELETE /v2/user/access`

### Cycles (physiological day)
- `GET /v2/cycle?limit&start&end&nextToken`
- `GET /v2/cycle/{cycleId}`
- `GET /v2/cycle/{cycleId}/sleep`
- Fields: `id`, `user_id`, `created_at`, `updated_at`, `start`, `end`, `timezone_offset`,
  `score_state`, `step_count`, `score { strain, kilojoule, average_heart_rate, max_heart_rate }`

### Recovery (one per cycle)
- `GET /v2/recovery?limit&start&end&nextToken`
- `GET /v2/cycle/{cycleId}/recovery`
- Fields: `cycle_id`, `sleep_id`, `user_id`, `created_at`, `updated_at`, `score_state`,
  `score { user_calibrating, recovery_score, resting_heart_rate, hrv_rmssd_milli,
  spo2_percentage, skin_temp_celsius }`

### Sleep
- `GET /v2/activity/sleep?limit&start&end&nextToken`
- `GET /v2/activity/sleep/{sleepId}`
- Fields: `id`, `cycle_id`, `start`, `end`, `timezone_offset`, `nap`, `score_state`,
  `score.stage_summary { total_in_bed_time_milli, total_awake_time_milli, total_no_data_time_milli,
  total_light_sleep_time_milli, total_slow_wave_sleep_time_milli, total_rem_sleep_time_milli,
  sleep_cycle_count, disturbance_count }`, `score.sleep_needed { baseline_milli,
  need_from_sleep_debt_milli, need_from_recent_strain_milli, need_from_recent_nap_milli }`,
  `respiratory_rate`, `sleep_performance_percentage`, `sleep_consistency_percentage`,
  `sleep_efficiency_percentage`

### Workouts
- `GET /v2/activity/workout?limit&start&end&nextToken`
- `GET /v2/activity/workout/{workoutId}`
- Fields: `id`, `start`, `end`, `sport_name`, `sport_id`, `score_state`,
  `score { strain, average_heart_rate, max_heart_rate, kilojoule, percent_recorded,
  distance_meter, altitude_gain_meter, altitude_change_meter, zone_durations { zone_zero_milli
  … zone_five_milli } }`

### Pagination
Collections take `limit` (max **25**, default 10), `start`, `end` (defaults to now), `nextToken`.
Response: `{ records: [...], next_token }`.

### Intraday zone data (important)

Individual workouts **do** carry real time-in-zone data:

```
score.zone_durations = {
  zone_zero_milli … zone_five_milli
}
```

That is measured milliseconds spent in each of WHOOP's six HR zones for that activity — not an
average. WHOOP zone boundaries are fractions of **maximum heart rate** (roughly 50/60/70/80/90%),
not of heart-rate reserve.

So for users who log activities in WHOOP, the app has genuine partial-coverage intraday data:
exact zone time for the periods they tagged. It is not a continuous stream (untagged time is absent),
but it is far better than a daily average for PacePoints.

## What is NOT available

- **No intraday / continuous heart-rate endpoint.** HR only appears as `average_heart_rate` and
  `max_heart_rate` on cycles and workouts, and `resting_heart_rate` on recovery.
- No raw RR intervals, no beat-to-beat, no per-minute HR series.
- No events beyond sleep/recovery/workout.

**Consequence:** PacePoints cannot be computed from the REST API alone. Continuous HR must come from
WHOOP's Heart Rate Broadcast (BLE) or from Health Connect (Fitbit).

## Webhooks

Events: `workout.updated`, `workout.deleted`, `sleep.updated`, `sleep.deleted`,
`recovery.updated`, `recovery.deleted`. New records arrive as `*.updated`.
Payload: `user_id`, `id`, `type`, `trace_id` — a signal only; fetch the object to get data.
Headers `X-WHOOP-Signature`, `X-WHOOP-Signature-Timestamp` (HMAC-SHA256).
Failed deliveries retried 5× over ~1 hour; respond 2XX within a second.
Requires a public HTTPS endpoint → **not used** by this personal app.

## Rate limits

- 100 requests/minute, 10 000 requests/day (raisable via dashboard).
- Headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`; over-limit → `429`.
- Incremental sync from the last successful `end` timestamp is far below this.

## Health Rate Broadcast (BLE)

Not part of the API. Enabled in the WHOOP app: Settings → Device Settings → **Broadcast Heart Rate**
(the strap's side LED pulses blue while broadcasting). The strap then advertises the standard
Bluetooth SIG Heart Rate service, so any phone can pair to it like a chest strap. Reported caveats:
manual toggle per session, must be within BLE range, battery cost, occasional "connected but no data"
requiring re-pair. Works with Peloton, Strava, Zwift, Garmin Edge, etc.
