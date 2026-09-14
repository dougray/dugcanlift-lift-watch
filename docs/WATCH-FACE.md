# The LIFT watch face

watchOS has no third-party watch faces. What LIFT ships is two complications
that sit in Apple's **Infograph Modular** face, plus a shareable copy of that
face once it is configured.

## Configuring it

1. Install the watch app. The complications appear once it has launched once.
2. On the watch, long-press the face, tap Edit, and pick Infograph Modular.
3. Set the slots:
   - top-left circular — LIFT, Steps
   - middle rectangular — LIFT, Macros
   - bottom-left circular — Weather
   - bottom-right circular — Heart Rate
4. Open LIFT, tap Goals, and set the five targets. Until you do, the
   complications measure against LIFT iOS's defaults (1748 kcal, 160 g
   protein, 167 g carbs, 49 g fat, 10,000 steps).
5. Grant the Health prompt on first launch, or the steps slot shows a dash.

## Sharing it

Long-press the face, tap Share, and send it as a `.watchface`.

The LIFT slots only fill for someone who already has the watch app installed.
Face Sharing points recipients at an App Store listing that does not exist —
LIFT is sideload-only for now — so a shared face reaches anyone else with two
empty slots. This is a limitation of distribution, not of the face.

## What is live and what is not

The time, date, heart rate and weather are Apple's and update continuously.

The macros are current the moment you log food on the watch, because the app
reloads that complication on every write. Nothing else moves them: food
logged on the phone does not reach the watch, since LIFT iOS and this app are
not `WCSession` peers (see the design doc's closing section).

Steps are read from HealthKit on a metered refresh and lag by minutes — they
will visibly disagree with the Activity ring beside them. On Always-On
Display, complications redraw about once a minute and render dimmed.

## Why the steps slot shows a dash

HealthKit never reveals whether a *read* was authorized: a denied read
returns an empty result, identical to a day with no steps. So the app records
that it asked, and until it has, the complication shows a dash rather than a
zero it cannot justify. A zero after that is a real zero.
