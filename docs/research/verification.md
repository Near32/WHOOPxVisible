# Verification record

Run on 2026-11-02, Linux, JDK 21, Android SDK 35/36.

## Automated

```
./gradlew :app:testDebugUnitTest
  BaselineTest        4 tests, 0 failures
  StabilityScoreTest  7 tests, 0 failures
  PacePointsTest      8 tests, 0 failures
  PacingAlerterTest   5 tests, 0 failures
./gradlew :app:assembleDebug   -> BUILD SUCCESSFUL
```

## Verified on device (emulator, API 36, real WHOOP account)

- Sign-in: OAuth + PKCE completed against the live WHOOP identity provider; consent lists all six
  scopes plus the privacy-policy link.
- Sync: 91 cycles, 90 recoveries, 99 sleeps, 428 workouts; incremental and full-backfill paths both
  exercised. 310 workouts carried `zone_durations`.
- Stability: 5/5 from real overnight HRV 63 ms / RHR 47 bpm, 90-night baseline.
- PacePoints: 12.1 of 14 from two logged activities (chess 180 min, dog-walking 42 min), scored
  from WHOOP's measured zone durations.
- Zone calibration: 312 workouts -> boundaries at 105/135/149/164/180 bpm, consistent with the
  offline fit and with 40/60/70/80/90% of heart-rate reserve.

Bugs found and fixed by running it: `next_token: null` parsed as the string "null"; `"score": null`
throwing on `.jsonObject`; `intOrNull` rejecting `54.0` (null resting HR); incremental sync never
refetching stale rows; UTC-vs-local cycle date comparison; Room schema version not bumped; and
per-minute rates ~20x too large against the budget.

## Not yet verified (requires the user's hardware)

1. **WHOOP sign-in.** Needs a real client id/secret in `local.properties` and a device/browser to
   complete the OAuth redirect. The custom-scheme redirect (`visibleclone://oauth`) is registered
   with a `BROWSABLE` intent-filter in `MainActivity`.
2. **BLE heart-rate reception.** Needs the strap with Broadcast Heart Rate on, in range. The service
   scans for the standard Heart Rate service (0x180D), subscribes to 0x2A37 and batches readings to
   Room every 5 s.
3. **Health Connect read.** Needs the Fitbit app writing HR into Health Connect and the permission
   granted from Settings.
4. **Alert delivery.** Needs real elevation above the exertion threshold; unit-tested logic lives in
   `PacingAlerterTest`.
5. **UI rendering on a device.** An emulator image was downloading when this was written; the app
   compiles and the APK installs, but no screen was exercised on-device yet.
