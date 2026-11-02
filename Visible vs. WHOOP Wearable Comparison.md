# **Visible vs. WHOOP Wearable Comparison**

[https\://gemini.google.com/app/ce654ef6c3932200](https://gemini.google.com/app/ce654ef6c3932200)

*User prompt: Is there independent research on the visible band? Comparison to whoop ?*

Response: The research base surrounding **Visible** (the Visible Armband) and **WHOOP** reflects very different design intent: WHOOP is backed by an extensive body of independent literature focused on sports performance, heart rate accuracy, and sleep staging, while Visible’s scientific body is newer and explicitly targets pacing and energy management for complex chronic illnesses (such as Long COVID, ME/CFS, and dysautonomia/POTS).

### **1\. Independent Research Status**

#### **Visible Armband**

> * **Primary Scope:** Research on Visible is in its early stages and focuses on feasibility, patient-reported outcomes, and heart-rate–guided pacing rather than benchmark sensor validation against ECG/PSG.  
> * **Key Findings:** Preliminary observational and feasibility studies—conducted in collaboration with researchers from Mount Sinai’s Cohen Center and the University of Liverpool—demonstrated high user adherence and positive patient-reported improvements in managing energy budgets and symptom flares (Sawyer et al., 2024).  
> * **Sensor Context:** Visible utilizes an upper-arm Polar PPG sensor (the Polar Verity Sense OEM), which benefits from Polar’s well-established independent validation literature establishing upper-arm optical sensors as significantly more accurate during movement than wrist-worn PPG devices.

#### **WHOOP (3.0 / 4.0)**

> * **Primary Scope:** Broadly evaluated across dozens of independent clinical and sports science trials for sleep architecture, continuous heart rate (HR), and heart rate variability (HRV).  
> * **Key Findings:** Systematic reviews show WHOOP has high agreement with gold-standard polysomnography (PSG) for total sleep duration, light sleep, and deep sleep, though it shows higher variance in REM stage accuracy (Schyvens et al., 2024).  
> * **Sensor Context:** Independent evaluations (e.g., *The Quantified Scientist*) highlight that while WHOOP is reasonably accurate at rest, wrist placement is prone to motion artifacts and cadence tracking errors during daily movement, whereas bicep placement significantly increases sensor fidelity.

### **2\. Direct Feature & Purpose Comparison**

| Feature / Dimension | Visible (Visible Plus) | WHOOP 4.0 |
| :---- | :---- | :---- |
| **Primary Target** | Energy management, illness pacing (ME/CFS, Long COVID, POTS) | Athletic recovery, cardiovascular strain, sleep optimization |
| **Form Factor / Location** | Upper Armband (Polar PPG engine) | Wrist strap, bicep band, or WHOOP Body wear |
| **Core Metric** | Real-time HR alerts, Pace Score, Energy Budget | Strain Score, Recovery Score, HRV, Sleep Needs |
| **Pacing & Thresholds** | Custom real-time HR threshold alerts to prevent overexertion/PEM | Focuses on pushing exertion to drive adaptation/fitness |
| **Pricing Model** | Free app (morning HRV) / Subscription for Armband | Monthly or annual subscription (hardware included) |

### **3\. Key Differences in Practice**

> 1. **Algorithmic Orientation (Strain vs. Pacing):**  
   * **WHOOP** rewards cardiovascular strain—higher heart rates generate higher Strain scores, encouraging athletic progression.  
   * **Visible** treats heart rate spikes as an excursion against an "Energy Budget." It alerts users in real time when crossing an anaerobic or exertion threshold to prevent Post-Exertional Malaise (PEM).  
> 2. **Placement & Sensor Stability:**  
   * Visible’s default location on the upper arm provides a clearer PPG signal due to better perfusion and fewer motion artifacts from wrist flexing.  
   * WHOOP defaults to the wrist, where motion artifacts can trigger false HR spikes (a known issue for users tracking dysautonomia or light walking), though switching to a bicep band resolves much of this discrepancy.  
> 3. **Data Granularity:**  
   * WHOOP offers detailed multi-stage sleep analysis, continuous recovery metrics, and broad integration with fitness platforms.  
   * Visible focuses on subjective symptom tagging alongside continuous HR to correlate physiological stress directly with illness flare-ups.

   **References**

   Sawyer, A., Preston, R., Leeming, H., Martin-Fuller, L., Proal, A., & Putrino, D. (2024). *Wearable Technology in the Management of Complex Chronic Illness: Preliminary Survey Results on Self-Reported Outcomes*. Springer Nature. [https\://doi.org/10.21203/rs.3.rs-5389182/v1](https://doi.org/10.21203/rs.3.rs-5389182/v1) Cited by: 2

   Schyvens, A.-M., Van Oost, N. C., Aerts, J.-M., Masci, F., Peters, B., Neven, A., Dirix, H., Wets, G., Ross, V., & Verbraecken, J. (2024). Accuracy of Fitbit Charge 4, Garmin Vivosmart 4, and WHOOP Versus Polysomnography: Systematic Review. *JMIR mHealth and uHealth*, *12*, e52192. [https\://doi.org/10.21206/52192](https://www.google.com/search?q=https://doi.org/10.21206/52192) Cited by: 100

   ---

   *User prompt: I am in autistic burnout, I already have a whoop, is there benefit of getting a visible wristband? Could I extract data from whoop and compute something like visible is computing? What about Fitbit sense data (I have it too) for visible metric computation? What about combining both source?*

### **Response: 1\. Is there a benefit to getting Visible if you already have WHOOP/Fitbit?**

The main advantage of Visible is not the hardware—it uses a re-badged Polar Verity Sense upper-arm band—but its **software paradigm, notification behavior, and pacing philosophy**.

| Dimension | WHOOP & Fitbit | Visible |
| :---- | :---- | :---- |
| **Philosophical Goal** | Optimize strain, push cardiovascular limits, maximize productivity/fitness. | **Pacing / Energy Conservation** to prevent Post-Exertional Malaise (PEM) and crashes. |
| **Real-Time Alerts** | HR zone alerts intended for workout training. | **Real-time over-exertion alerts** triggered when HR crosses a low threshold (e.g., \>100–110 bpm during routine tasks). |
| **Scoring Logic** | Higher strain/activity increases your "score" positively. | Higher heart rate consumes an "Energy Budget" (PacePoints). Once points run out, you are over budget. |
| **Subjective Data Integration** | Focuses on physiological sleep/strain metrics. | Integrates daily symptom tracking, cognitive load, and sensory fatigue alongside biometrics. |

**Is it worth buying Visible?** If you experience executive dysfunction or reduced interoception from burnout, the **real-time gentle vibration alerts** when your heart rate spikes during simple tasks (like showering or cooking) are hard to replicate natively on WHOOP without custom setups. However, if your primary goal is tracking trends and setting an energy budget without paying another subscription, **you do not need Visible**—you can compute equivalent metrics from your existing hardware.

### **2\. Can you compute Visible-like metrics using WHOOP or Fitbit?**

Yes. Visible’s two core algorithmic components—**Morning Stability Score** and **PacePoints (Energy Budget)**—can be modeled directly from your raw wearable streams.

#### **A. Replicating the Morning Stability Score**

Visible's morning score (1–5 scale) assesses baseline autonomic nervous system stability using resting biometrics and subjective inputs.

> * **Input Data:**  
  * **rMSSD (HRV)**: WHOOP exports raw rMSSD in milliseconds (measured during SWS/deep sleep). Fitbit provides daily HRV via Web API / Health Connect.  
  * **Resting Heart Rate (RHR)**: Baseline sleeping RHR.  
  * **Subjective Inputs**: Morning sensory/autistic fatigue rating (1–5 scale).  
> * **Mathematical Approach:**  
  1. Compute a 30-day rolling baseline for rMSSDmean​, rMSSDstd​, RHRmean​, and RHRstd​.  
  2. Compute normalized Z-scores:  
     *Z*HRV​\=rMSSDstd​rMSSDtoday​−rMSSDmean​​  
     *Z*RHR​\=RHRstd​RHRtoday​−RHRmean​​  
  3. Calculate an autonomic deviation score:  
     Dev=*w*1​⋅(−*Z*HRV​)+*w*2​⋅(*Z*RHR​)  
     *(Note: Drops in HRV and spikes in RHR indicate sympathetic dominance / autonomic stress).*  
  4. Map Dev alongside your morning subjective rating to scale a daily capacity score from 1 to 5\.

#### **B. Replicating PacePoints (Energy Budget & Exertion Index)**

Visible converts heart-rate duration into points accumulated across three heart-rate zones:

> 1. **Resting Zone** (\<HRactivity​): 0 points/min  
> 2. **Activity Zone** (HRactivity​ to HRexertion​): Low point accumulation rate  
> 3. **Exertion Zone** (\>HRexertion​): Exponentially higher accumulation rate  
> * **Setting Thresholds for Autistic Burnout / Pacing:**  
  * HRactivity​\=RHR+15 bpm  
  * HRexertion​\=RHR+0.5×(HRmax​−RHR) (or hardcoded to ≈100–110 bpm)  
> * **Discrete Accumulation Formula:**  
>   PacePoints=*t*\=1∑*T*awake​​*f*(HR*t*​)  
>   where *f*(HR*t*​)=⎩⎨⎧​0*c*1​*c*2​⋅*eα*(HR*t*​−HRexertion​)​if HR*t*​\<HRactivity​if HRactivity​≤HR*t*​\<HRexertion​if HR*t*​≥HRexertion​​  
>   *(Setting c1​\=1, c2​\=3, and α=0.05 creates an exponential penalty for sustained cardiovascular spikes).*

### **3\. Combining WHOOP and Fitbit Data**

Combining both streams yields higher accuracy by utilizing the distinct strengths of each device:

`[ WHOOP 4.0 ] ──> SWS/Deep Sleep Metrics (rMSSD, RHR, Sleep Architecture) ──┐`  
                                                                           `├──> [ Processing Pipeline ] ──> Pacing & Energy Dashboard`  
`[ Fitbit Sense ] ──> High-Frequency Continuous HR + Daily EDA / Skin Temp ───┘`

> 1. **Morning Baseline (Use WHOOP):** WHOOP calculates sleep-stage-weighted rMSSD during slow-wave sleep. This eliminates circadian variance compared to waking spot-checks or non-stage-aware sensors.  
> 2. **Intraday Continuous HR & Pacing (Use Fitbit Sense):** Fitbit’s continuous PPG sampling can be extracted via Android Health Connect, Apple Health, or the Fitbit Web API. Use this intraday stream for real-time accumulation of PacePoints.  
> 3. **Autonomic Context (Use Fitbit EDA / Skin Temp):** Fitbit Sense includes electrodermal activity (EDA) and skin temperature variation. Sudden EDA spikes without corresponding physical movement strongly correlate with **sensory overload or executive distress** rather than physical exertion—providing a valuable marker for autistic burnout.

### **Recommended Implementation Steps**

If you want to build or automate this workflow without buying Visible:

> 1. **Data Ingestion:**  
   * Export WHOOP sleep/HRV data via the **WHOOP API** (/v1/recovery and /v1/cycles).  
   * Export Fitbit continuous minute-by-minute HR data via **Health Connect** or the **Fitbit Web API** (/1/user/-/activities/heart/date/...).  
> 2. **Automation / Dashboarding:**  
   * Write a Python script to ingest both streams and compute the daily rolling Z-scores and PacePoints budget.  
   * Visualize the data in **Grafana**, an **Obsidian plugin**, or an **iOS/Android Shortcut** that pushes a morning capacity score and daily PacePoints target to your phone/smartwatch.  
> 3. **Real-time Alert Alternative:**  
   * Set a custom HR alert zone on your Fitbit or WHOOP for when intraday HR exceeds your chosen threshold (RHR+25 bpm) during non-exercise periods to simulate Visible's real-time over-exertion warnings.

   **Sources**

> 1. [Visible Reviews \| Read Customer Service Reviews of makevisible.com](https://uk.trustpilot.com/review/makevisible.com?page=2&utm_source=gemini)  
> 2. [Blog \- Visible](https://www.makevisible.com/blog?utm_source=gemini)  
> 3. [Visible: The pacing app for people with ME/CFS and Long Covid](https://meassociation.org.uk/2024/04/visible-the-pacing-app-for-people-with-me-cfs-and-long-covid/?utm_source=gemini)  
> 4. [Visible Plus: Pacing \- Mark Lovett Wells](https://www.mwells.org/health/long-covid/visible/pacing/?utm_source=gemini)  
   ---

### **1\. Is there a benefit to getting Visible if you already have WHOOP/Fitbit?**

The main advantage of Visible is not the hardware—it uses a re-badged Polar Verity Sense upper-arm band—but its **software paradigm, notification behavior, and pacing philosophy**.

| Dimension | WHOOP & Fitbit | Visible |
| :---- | :---- | :---- |
| **Philosophical Goal** | Optimize strain, push cardiovascular limits, maximize productivity/fitness. | **Pacing / Energy Conservation** to prevent Post-Exertional Malaise (PEM) and crashes. |
| **Real-Time Alerts** | HR zone alerts intended for workout training. | **Real-time over-exertion alerts** triggered when HR crosses a low threshold (e.g., \>100–110 bpm during routine tasks). |
| **Scoring Logic** | Higher strain/activity increases your "score" positively. | Higher heart rate consumes an "Energy Budget" (PacePoints). Once points run out, you are over budget. |
| **Subjective Data Integration** | Focuses on physiological sleep/strain metrics. | Integrates daily symptom tracking, cognitive load, and sensory fatigue alongside biometrics. |

**Is it worth buying Visible?**  
If you experience executive dysfunction or reduced interoception from burnout, the **real-time gentle vibration alerts** when your heart rate spikes during simple tasks (like showering or cooking) are hard to replicate natively on WHOOP without custom setups. However, if your primary goal is tracking trends and setting an energy budget without paying another subscription, **you do not need Visible**—you can compute equivalent metrics from your existing hardware.

### **2\. Can you compute Visible-like metrics using WHOOP or Fitbit?**

Yes. Visible’s two core algorithmic components—**Morning Stability Score** and **PacePoints (Energy Budget)**—can be modeled directly from your raw wearable streams.

#### **A. Replicating the Morning Stability Score**

Visible's morning score (1–5 scale) assesses baseline autonomic nervous system stability using resting biometrics and subjective inputs.

* **Input Data:**  
* 

  1. **rMSSD (HRV)**: WHOOP exports raw rMSSD in milliseconds (measured during SWS/deep sleep). Fitbit provides daily HRV via Web API / Health Connect.  
  2.   
  3. **Resting Heart Rate (RHR)**: Baseline sleeping RHR.  
  4.   
  5. **Subjective Inputs**: Morning sensory/autistic fatigue rating (1–5 scale).  
  6.   
* **Mathematical Approach:**  
* 

  1. Compute a 30-day rolling baseline for $rMSS{D}_{mean}$, $rMSS{D}_{std}$, $RH{R}_{mean}$, and $RH{R}_{std}$.  
  2.   
  3. Compute normalized Z-scores:  
  4. 

  5.   
  6. ${Z}_{HRV}=\frac{rMSS{D}_{today}-rMSS{D}_{mean}}{rMSS{D}_{std}}$  
  7.   
  8. ${Z}_{RHR}=\frac{RH{R}_{today}-RH{R}_{mean}}{RH{R}_{std}}$  
  9. Calculate an autonomic deviation score:  
  10. 

  11.   
  12. $Dev={w}_{1}\cdot (-{Z}_{HRV})+{w}_{2}\cdot ({Z}_{RHR})$  
  13. *(Note: Drops in HRV and spikes in RHR indicate sympathetic dominance / autonomic stress).*  
  14.   
  15. Map $Dev$ alongside your morning subjective rating to scale a daily capacity score from 1 to 5\.  
  16. 

#### **B. Replicating PacePoints (Energy Budget & Exertion Index)**

Visible converts heart-rate duration into points accumulated across three heart-rate zones:

1. **Resting Zone** ($<H{R}_{activity}$): 0 points/min  
2.   
3. **Activity Zone** ($H{R}_{activity}$ to $H{R}_{exertion}$): Low point accumulation rate  
4.   
5. **Exertion Zone** ($>H{R}_{exertion}$): Exponentially higher accumulation rate  
6.   
* **Setting Thresholds for Autistic Burnout / Pacing:**  
* 

  * $H{R}_{activity}=RHR+15bpm$  
  *   
  * $H{R}_{exertion}=RHR+0.5\times (H{R}_{max}-RHR)$ (or hardcoded to $\approx 100--110bpm$)  
  *   
* **Discrete Accumulation Formula:**  
* 

*   
* $PacePoints=\sum\limits_{t=1}^{{T}_{awake}}f(H{R}_{t})$  
* \$\$\\text{where } f(\\text{HR}\_t) \= \\begin{cases} 0 & \\text{if } \\text{HR}\_t \< \\text{HR}\_{\\text{activity}} \\\\ c\_1 & \\text{if } \\text{HR}\_{\\text{activity}} \\le \\text{HR}\_t \< \\text{HR}\_{\\text{exertion}} \\\\ c\_2 \\cdot e^{\\alpha (\\text{HR}\_t \- \\text{HR}\_{\\text{exertion}})} & \\text{if } \\text{HR}\_t \\ge \\text{HR}\_{\\text{exertion}} \\end{cases}\$\$  
* *(Setting ${c}_{1}=1$, ${c}_{2}=3$, and $\alpha =0.05$ creates an exponential penalty for sustained cardiovascular spikes).*  
* 

### **3\. Combining WHOOP and Fitbit Data**

Combining both streams yields higher accuracy by utilizing the distinct strengths of each device:

```

[ WHOOP 4.0 ] ──> SWS/Deep Sleep Metrics (rMSSD, RHR, Sleep Architecture) ──┐
                                                                           ├──> [ Processing Pipeline ] ──> Pacing & Energy Dashboard
[ Fitbit Sense ] ──> High-Frequency Continuous HR + Daily EDA / Skin Temp ───┘

```

1. **Morning Baseline (Use WHOOP):**  
2. WHOOP calculates sleep-stage-weighted rMSSD during slow-wave sleep. This eliminates circadian variance compared to waking spot-checks or non-stage-aware sensors.  
3.   
4. **Intraday Continuous HR & Pacing (Use Fitbit Sense):**  
5. Fitbit’s continuous PPG sampling can be extracted via Android Health Connect, Apple Health, or the Fitbit Web API. Use this intraday stream for real-time accumulation of PacePoints.  
6.   
7. **Autonomic Context (Use Fitbit EDA / Skin Temp):**  
8. Fitbit Sense includes electrodermal activity (EDA) and skin temperature variation. Sudden EDA spikes without corresponding physical movement strongly correlate with **sensory overload or executive distress** rather than physical exertion—providing a valuable marker for autistic burnout.  
9. 

### **Recommended Implementation Steps**

If you want to build or automate this workflow without buying Visible:

1. **Data Ingestion:**  
2. 

   * Export WHOOP sleep/HRV data via the **WHOOP API** (/v1/recovery and /v1/cycles).  
   *   
   * Export Fitbit continuous minute-by-minute HR data via **Health Connect** or the **Fitbit Web API** (/1/user/-/activities/heart/date/...).  
   *   
3. **Automation / Dashboarding:**  
4. 

   * Write a Python script to ingest both streams and compute the daily rolling Z-scores and PacePoints budget.  
   *   
   * Visualize the data in **Grafana**, an **Obsidian plugin**, or an **iOS/Android Shortcut** that pushes a morning capacity score and daily PacePoints target to your phone/smartwatch.  
   *   
5. **Real-time Alert Alternative:**  
6. 

   * Set a custom HR alert zone on your Fitbit or WHOOP for when intraday HR exceeds your chosen threshold ($RHR+25bpm$) during non-exercise periods to simulate Visible's real-time over-exertion warnings.

   