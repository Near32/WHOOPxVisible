# WHOOP Developer Portal — app configuration (redacted capture)

Source: a printout of the developer dashboard for the `WHOOPxVisible` app, captured 2026-11-02.

**The client ID and client secret are redacted.** They go in `local.properties` (gitignored),
never in this repo. The PDF export is gitignored too — printing the portal page writes your live
secret into a file, so treat any such PDF as a credential and delete it when done.

What this capture establishes:

| Item | Value |
|---|---|
| App status | **Sandbox** — 10 connected members |
| Next tier | Build (100 members), applied for when needed — irrelevant for one user |
| Redirect URL | `visibleclone://oauth` — accepted, so the custom scheme works |
| Privacy policy | Raw GitHub URL accepted |
| Scopes | `read:recovery`, `read:cycles`, `read:sleep`, `read:workout`, `read:profile`, `read:body_measurement` |
| Webhooks | None configured (not used by this app) |
| Rate limits | 10 000/day, 100/minute |

The portal's scope list does not show `offline`, but it must still be requested in the
authorization URL or no refresh token is issued.

---

APPS

TEAM

SUPPORT

DOCS

WHOOPxVisible

Status   SANDBOX  · 0 OF 10 MEMBERS

Your app is on Sandbox, with a limit of 10 connected members. You can apply for Build (100 members) whenever you are ready. From Build
onward, moving up needs half your tier cap connected.

O P E N   R E Q U E S T

L E A R N   M O R E

Credentials

CLIENT ID

<CLIENT_ID — set in local.properties>

CLIENT SECRET

<REDACTED — set in local.properties>

Details

LOGO

CONTACTS

<contact email>

PRIVACY POLICY

https://raw.githubusercontent.com/Near32/WHOOPxVisible/main/PRIVACY.md

REDIRECT URLS

visibleclone://oauth

SCOPES

read:recovery
Allows access to recovery information, including score, heart rate variability, and resting heart rate

read:cycles
Allows access to cycles information, including day strain and average heart rate during a cycle

read:sleep
Allows access to sleep information, such as duration and start and end time

read:workout
Allows access to workout information, including activity type and accumulated strain

read:profile
Allows access to profile information

read:body_measurement
Allows access to body measurements, such as height, weight, and max heart rate

Webhooks

Learn more about webhooks with WHOOP.

WEBHOOK URLS

No webhooks configured

Rate Limits

API DAY RATE LIMIT

10000

API MINUTE RATE LIMIT

100

