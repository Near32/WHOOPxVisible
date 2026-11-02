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
