# Privacy Policy — WHOOPxVisible

**Last updated: 2 November 2026**

WHOOPxVisible is a personal Android application that reads wearable data from the WHOOP API and
optionally from Android Health Connect, and turns it into pacing scores. This policy explains what
the app does with that data.

## What the app collects

- **WHOOP account data**, with your explicit OAuth consent: your name, profile ID, body measurements
  (height, weight, maximum heart rate), and the recovery, cycle, sleep and workout records returned by
  the WHOOP API.
- **Heart-rate readings** from your WHOOP strap's Bluetooth Heart Rate Broadcast, and/or from Health
  Connect if you grant that permission.
- **Information you type into the app**: morning check-ins (sleep quality, symptom severity, energy),
  symptom, medication and activity logs, monthly check-ins, and your pacing thresholds.

## Where the data goes

**All of it stays on your device.** The app stores everything in a local database on the phone. It
does not operate a server, and it does not upload your data anywhere.

## Who it is shared with

**No one.** The app contains no analytics, no advertising, no crash reporting and no third-party
tracking SDKs. Your data is never sold, rented or disclosed.

The only network requests the app makes are to the WHOOP API, in order to fetch your own data.
Those requests are subject to [WHOOP's privacy policy](https://www.whoop.com/privacy-policy/).

## Health Connect

If you grant Health Connect permissions, the app reads heart-rate records only. It never writes to
Health Connect, and data read through Health Connect is stored on your device in the same local
database as everything else. You can revoke access at any time in the Health Connect settings.

## Data retention and deletion

Data remains on your device until you delete it. Signing out of WHOOP in the app's Settings removes
your stored access and refresh tokens. Uninstalling the app removes the local database entirely.
Note that the app uses Android's standard backup exclusions, so its database is not copied to
Google's backup services.

## Not a medical device

WHOOPxVisible is a personal wellbeing and pacing aid. It is not a medical device, it does not
diagnose any condition, and it does not predict or prevent crashes or symptom flares. Do not use it
in place of advice from a clinician.

## Children

The app is not directed at children and is not intended for use by anyone under 16.

## Changes

If this policy changes, the updated version will be published at this URL with a new "last updated"
date.

## Contact

Questions about this policy: open an issue at
https://github.com/Near32/WHOOPxVisible/issues
