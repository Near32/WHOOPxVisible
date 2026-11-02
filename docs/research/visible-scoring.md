# Visible's scoring — what is public, what we approximate

Sources:
- https://www.makevisible.com/blog/a-more-accurate-stability-score
- https://www.makevisible.com/blog/a-new-way-to-get-your-stability-score
- https://www.makevisible.com/ (product page)
- The comparison doc in this repo (`Visible vs. WHOOP Wearable Comparison.md`)

## What Visible actually says

### Stability Score (renamed from "Morning Stability Score")

- Scale **1–5**.
- Inputs: **overnight** resting heart rate, overnight HRV, self-reported sleep quality,
  self-reported symptoms.
- Awake/moving periods during the night are **excluded**; it uses average readings across several
  hours of sleep rather than a single morning spot-check.
- Compared against the user's personal normal range, built from **up to 90 days** of prior data.
- New users get a score from whatever data exists; accuracy improves over time.
- Evening symptoms and crash reports also feed the score.
- **The formula, weights and cut points are not published.**

### PacePoints

- A daily **energy budget** expressed in points (a product screenshot shows "4.6 out of 14
  PacePoints used today").
- Activities (showering, walking, cooking) consume points; the budget is consumed through the day.
- Crossing into the **exertion zone** triggers a **pacing alert** warning of overexertion.
- **The point formula, zone thresholds and budget derivation are not published.**

### Other features
- Daily check-ins: sleep quality, symptom severity, physical/mental/emotional exertion, periods,
  medication, infections.
- Activity, symptom and medication tracking.
- Continuous heart rate and overnight HRV via the band.
- Monthly check-in (retrospective form).
- Clinician dashboard / export.
- In-app research studies and clinical-trial matching. (Out of scope for us.)

## The model we implement

### Stability Score

For each biometric input, over a trailing window of `baselineDays` (default 30, max 90):

```
z = (today - mean) / stddev
```

Autonomic deviation (high = more stressed):

```
Dev = w_hrv * (-z_hrv) + w_rhr * (z_rhr) + w_sleep * (-sleepQualityNorm) + w_symptom * symptomSeverityNorm
```

Defaults: `w_hrv = 1.0`, `w_rhr = 1.0`, `w_sleep = 0.5`, `w_symptom = 0.5`.

Then bucket `Dev` into 1–5 using cut points, clipped:

| Score | Dev at most |
|---|---|
| 5 | −1.0 |
| 4 | −0.25 |
| 3 | 0.75 |
| 2 | 1.75 |
| 1 | +∞ |

All of the above is in `PacingConfig` and editable in-app. These numbers are **ours**, not
Visible's; they exist to be tuned against a week of lived experience.

### PacePoints

Per minute of HR:

```
HR_activity  = RHR + activityOffset          (default 15)
HR_exertion  = RHR + exertionFraction * (HRmax - RHR)   (default 0.5)

f(hr) = 0                                     if hr <  HR_activity
      = c1                                    if HR_activity <= hr < HR_exertion
      = c2 * exp(alpha * (hr - HR_exertion))  if hr >= HR_exertion

pacePoints = sum over minutes f(hr)
```

Defaults `c1 = 1`, `c2 = 3`, `alpha = 0.05`. `RHR` = today's resting HR from WHOOP recovery;
`HRmax` = WHOOP body measurement `max_heart_rate`, falling back to `220 - age` from the profile or
a manual override.

### Daily budget from Stability

Interpolated between two anchors, editable:

| Stability | Budget (points) |
|---|---|
| 1 | 4 |
| 5 | 14 |

Linear in between: `budget = 4 + (stability - 1) * (14 - 4) / 4`.

## Known divergences from Visible

- We use WHOOP's overnight rMSSD (deep-sleep weighted) rather than Visible's band; not identical.
- Our sleep-quality and symptom terms come from a 1–5 check-in, not Visible's validated scale.
- Budget anchors and cut points are guesses.
- This is a pacing aid, not a medical device. It does not diagnose, treat or predict crashes.
