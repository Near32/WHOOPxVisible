# Visible Clone (for WHOOP)

A personal Android app that recreates [Visible](https://www.makevisible.com/)'s pacing model on top of
a WHOOP strap plus Fitbit data via Health Connect. Two scores:

- **Stability Score** (1–5) — overnight HRV, resting HR, sleep-stage totals and your morning check-in,
  z-scored against a rolling baseline.
- **PacePoints** — a daily energy budget consumed by heart-rate zones, with a live alert when you
  cross your exertion threshold.

Design and research notes are in [`docs/research/`](docs/research/).
Privacy policy: [PRIVACY.md](https://github.com/Near32/visible-clone/blob/main/PRIVACY.md)

## What actually works where

| Capability | Source | Notes |
|---|---|---|
| Overnight HRV, resting HR, sleep stages, strain | WHOOP REST API `/v2` | Needs sign-in |
| Live heart rate + exertion alerts | WHOOP **Broadcast Heart Rate** over BLE | Toggle it in the WHOOP app first |
| Fallback intraday HR | Health Connect (Fitbit) | ~minute granularity, not real-time |
| Check-ins, logs, monthly check-in | Local Room DB | On-device only |

**The WHOOP REST API has no intraday heart-rate stream**, so PacePoints for past days are estimated
from each day's average HR and labelled as such. Only today, with live BLE or Health Connect data, is
measured.

## Build

```bash
cp local.properties.example local.properties   # add WHOOP client id/secret
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Register `visibleclone://oauth` as the redirect URL in the
[WHOOP developer dashboard](https://developer.whoop.com/).

```bash
./gradlew test    # domain tests: Baseline, StabilityScore, PacePoints, PacingAlerter
```

`local.properties` must also contain `sdk.dir` — Gradle needs it.

## Tuning

Visible does not publish its formulas. Every weight and threshold is in
`domain/PacingConfig.kt` and editable in Settings; the Today screen's "Explain score" shows the
components. Tune after a week of real data rather than trusting the defaults.

## Deliberately not built

Backend, webhooks, Play Store packaging, multi-user, cloud sync, ML sleep staging. Each has a clear
trigger in the plan (`docs/` history) but none is needed for a single user on one phone.

This is a pacing aid, not a medical device.
