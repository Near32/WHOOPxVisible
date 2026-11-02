# Application behavior spec (derived from the public product)

Sources: https://www.makevisible.com/, the Visible blog index, the comparison doc in this repo.

This is what the app should *feel* like, independent of the exact formulas (those are in
`visible-scoring.md`).

## The daily loop

1. **Wake, open app.** Before any interaction the Today screen already shows last night's
   Stability Score (or "Still learning your baseline — 3 more nights") and the day's PacePoints
   budget.
2. **Morning check-in, ~10 seconds.** Sleep quality (1–5), symptom severity (1–5), and an optional
   free note. This adjusts today's Stability and budget.
3. **Live through the day with the pacing screen or background alerts on.** The band streams HR;
   points accumulate. In the *activity* zone points trickle; in the *exertion* zone they accelerate.
4. **Talk to it: log activity.** "Showering", "cooking", "walking to the shop" — start/stop, and the
   activity shows what it cost. This is the key idea Visible sells: ordinary tasks have a price.
5. **Get interrupted, gently.** A pacing alert when HR crosses the exertion threshold (haptic +
   notification), and a second, calmer one when the daily budget is exhausted.
6. **Evening.** Log symptoms and crashes; these feed tomorrow's score.
7. **Monthly.** A longer retrospective check-in.

## Screens and their content

### Today (redesigned to match Visible's Feb 2026 layout)

Order is PacePoints arc → heart rate → stability → trend, per Visible's updated Today page.

- **PacePoints arc**: semicircular arc filling as points accumulate, with a white **PaceSetter** dot
  showing where an evenly-paced day would be at the current time. Spent total and budget sit inside.
- **Heart rate**: current bpm with zone pill, a zone-banded line, and the live HR toggle.
- Zone breakdown (Rest / Activity / Exertion minutes) with colour dots underneath the arc.
- Stability Score: big number 1–5, one-line interpretation ("steady", "a little strained",
  "take it gently"), and the seven-day band showing the personal normal range.
- PacePoints: a ring, `used / budget`, with the today's zone breakdown underneath.
- Current HR and zone if the live service is running.
- A "Why this score?" sheet listing each component with its z-score.

### Morning check-in
- Three sliders/taps, one Save. No onboarding wall.

### Live
- Large HR, zone colour (calm / activity / exertion), points ticking up, elapsed time.
- A stop button. The foreground notification carries the same numbers.

### History
- Toggles for 7 / 30 days.
- Charts: HRV, RHR, sleep duration, PacePoints used, Stability score, budget adherence.
- Tap a day → its full breakdown and its logs.

### Logs
- Add symptom, medication, or activity. Activity has start/end (defaults to now) and computes its
  own PacePoints from stored HR.
- Lists grouped by day.

### Monthly check-in
- Retrospective: worst/best days, symptom pattern, medication changes, free note.

### Settings
- WHOOP connection (sign in / sign out / profile name).
- Live HR: "Connect to WHOOP broadcast" with scan list, plus Health Connect permission state.
- Thresholds: every value in `PacingConfig`.
- Export CSV, reset data, about/privacy.

## Alert rules

| Trigger | Behaviour |
|---|---|
| HR crosses `HR_exertion` upward and stays 30 s | One notification + haptic; won't repeat for 5 min |
| Daily points reach the budget | One notification; won't repeat until next day |
| BLE broadcast lost, `max_heart_rate` missing | Fall back to Health Connect; if neither, show retrospective mode |

## Empty / degraded states

- Fewer than `minBaselineNights` nights → "Still learning" instead of a score.
- WHOOP not connected → Today shows only manual check-in and logs.
- No intraday HR → PacePoints card says "Live HR unavailable — points will be estimated from the
  day's average" and shows no live alert.
