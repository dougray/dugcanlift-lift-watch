# Play Console: Data safety and permission declarations (draft)

A filled-in draft of the answers Play Console asks for, written from the code as
of LIFT for Wear OS 1.1 (versionCode 3, `android/wear/build.gradle.kts`).
Nothing here has been submitted. Every claim cites the file it comes from; if
the code changes, check the claim against it again.

Play's definitions matter for the answers below:

- **Collected** means data sent off the device by the app, to the developer or
  anyone else. Processing that stays on the device is not collection.
- **Shared** means data transferred to a third party. Play exempts transfers the
  user starts and would expect.

## Summary

| Question | Draft answer |
| --- | --- |
| Does the app collect or share any required user data types? | **No.** The app makes no network requests at all. See "Why nothing is collected". |
| Is all user data encrypted in transit? | Not applicable: nothing is transmitted over a network. The one link off the watch, LIFT Link to the user's own phone, uses encrypted Bluetooth characteristics that require a bond (`docs/LINK-PROTOCOL.md`). If the form insists on an answer, **Yes**. |
| Do you provide a way for users to request that their data be deleted? | Nothing is held off the device, so there is nothing to request. Uninstalling removes it all; the food log also clears from Export, behind a confirmation (`ExportScreen.kt`). If the form requires a mechanism, answer that data is not collected by the developer. |
| Account creation | None. No login, no accounts, no server operated by the developer. |
| Independent security review | No. |
| Children | Not directed at children. |

## Why nothing is collected

- **No `INTERNET` permission.** `android/wear/src/main/AndroidManifest.xml`
  declares none, and `PhoneLinkManifestTest` fails if it ever appears.
  `PermissionsPinTest` pins the release APK's *merged* manifest (the app's own
  plus every library's) to the exact list below, so a dependency cannot add it
  quietly either.
- **No HTTP client and no network code.** `android/wear/build.gradle.kts` and
  `android/liftkit/build.gradle.kts` depend on Compose for Wear OS, AndroidX
  Activity, Wear Input, Health Services (`androidx.health:health-services-client`,
  an AndroidX library that binds to the watch's own Health Services system
  package), ZXing core (QR drawing), kotlinx-coroutines and
  kotlinx-serialization. No analytics, advertising or crash-reporting SDK, and
  no Google Play Services (`PhoneLinkManifestTest` asserts no Play Services
  coordinate is named in the manifest or the build file). A search of
  `android/wear/src/main` and `android/liftkit/src/main` for `http`,
  `java.net`, sockets or an HTTP library finds only XML namespace URIs.
- **`android:allowBackup="false"`** (`AndroidManifest.xml`) keeps Android's own
  Auto Backup from copying app data to Google Drive.
- **The food library is bundled**: `android/liftkit/src/main/resources/foods.json`
  (USDA SR Legacy, 7,793 foods). Search is a substring match on the watch
  (`WatchFoodLibrary.search`); no query leaves it.

## Data types, one by one

### Health and fitness — not collected

- **Fitness info:** the food log (`StandaloneFoodLog`, stored by
  `PrefsLogStorage.kt` in app-private SharedPreferences), the plan the phone
  pushed, the session in progress and finished sessions not yet acknowledged
  (`SessionStore.kt`, SharedPreferences).
- **Health info: heart rate.** Read from the watch's own sensor through Health
  Services during a session (`HeartRateRecorder.kt`). Readings are kept **in
  memory only**, six minutes of them (`SetHeartRates.kt`); what is stored is the
  highest bpm around each set and the session's average and highest, on the
  session (`FinishedSessionBuilder.kt`).
- **Leaves the watch only by user action**, neither of which is collection:
  - **QR export**: the user opens Export and the food log is drawn on the
    watch's own screen as QR codes (`StandaloneExport.kt`, `ExportScreen.kt`)
    for the LIFT web app to scan with a camera. Food only; no heart rate, no
    workouts.
  - **LIFT Link**: a direct, encrypted Bluetooth LE connection to the LIFT for
    Android phone the user paired with a six-digit code
    (`PhoneLinkPeripheral.kt`, `docs/LINK-PROTOCOL.md`, `link/PairingCode.kt`).
    Finished sessions and logged sets travel to the phone with their heart-rate
    figures (`link/LinkPayloads.kt`). This is the user's own device, not the
    developer or a third party.
- **Answer:** not collected, not shared.

### Location — not collected

The app declares no location permission. The watch is a Bluetooth peripheral
that advertises and accepts a connection and never scans, so `BLUETOOTH_SCAN`
is deliberately absent (`AndroidManifest.xml` comment) and no location question
arises.

### Everything else

Personal info, financial info, messages, contacts, calendar, photos and
videos, audio, files and docs, web browsing, app activity, device or other
IDs, app info and performance: **not collected**. The app has no account, no
identifier of its own sent anywhere, and no crash reporting.

## Permissions, and what each is for

The release APK declares exactly these (`PermissionsPinTest.expected`):

| Permission | What it is for | Where |
| --- | --- | --- |
| `BLUETOOTH_ADVERTISE` | LIFT Link: the watch advertises so the paired phone can find it. Runtime (Nearby devices). | `AndroidManifest.xml`; requested in `PhoneLinkPeripheral.kt` |
| `BLUETOOTH_CONNECT` | LIFT Link: accept the phone's connection and serve the link's characteristics. Runtime (Nearby devices). | `AndroidManifest.xml`; `PhoneLinkPeripheral.kt` |
| `BLUETOOTH` (`maxSdkVersion=30`) | The same, on Android 11 (API 30, the app's `minSdk`), where the two above do not exist. Install-time. | `AndroidManifest.xml` |
| `BLUETOOTH_ADMIN` (`maxSdkVersion=30`) | Advertising on API 30. Install-time. | `AndroidManifest.xml` |
| `BODY_SENSORS` (`maxSdkVersion=35`) | Heart rate during a session, up to Wear OS 5.1. Runtime. | `AndroidManifest.xml`; `HeartRateRecorder.heartRatePermission` |
| `android.permission.health.READ_HEART_RATE` | Heart rate during a session from Wear OS 6 (API 36), which replaced `BODY_SENSORS` for apps targeting 36 or higher (this one targets 37). Runtime. | `AndroidManifest.xml`; `HeartRateRecorder.heartRatePermission` |
| `VIBRATE` | The two-pulse buzz at the end of a rest interval. Install-time. | `AndroidManifest.xml`; `Haptics.kt` (`buzzRestOver`) |
| `com.dugcanlift.liftwear.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Not a request to the user: AndroidX Core declares this signature-level permission and holds it itself, so a receiver registered not-exported on older Android is protected from other apps. | merged manifest; `PermissionsPinTest` |

Refusing a runtime permission costs only its feature: without Bluetooth the
watch still logs food and exports by QR; without heart rate a session runs with
no bpm line and every set is still logged (`AndroidManifest.xml` comments,
`HeartRateRecorder.kt`). Both hardware features are `required="false"`.

## Health permission declaration (draft text)

Play Console asks why the app needs `BODY_SENSORS` and
`android.permission.health.READ_HEART_RATE`. A draft answer, for Doug to
review:

> LIFT for Wear OS shows the user's heart rate on the watch while they run a
> strength-training session they started, and records, for each set, the
> highest heart rate measured around it, plus the session's average and
> highest. It reads heart rate only while a session is open, through the
> watch's own Health Services, and only to show it and attach those figures to
> the session. Individual readings are held in memory and never stored. The
> figures stay on the watch, and go only to the LIFT for Android app on the
> user's own phone, over a direct Bluetooth connection the user paired, when a
> session is finished. The app has no internet permission, so heart rate data
> is never sent to the developer or any third party, and it is never used for
> advertising. Declining the permission leaves every other feature working.

- **Category for the health apps declaration:** fitness (workout tracking) and
  nutrition (food logging).
- **Privacy policy URL:** `https://www.dugcanlift.com/app/privacy/`, whose
  "The watch apps" > "Wear OS" section describes these permissions
  (`PermissionsPinTest` names that page as the one the list must match).
- **Uncertain:** whether Play Console treats `health.READ_HEART_RATE` on Wear OS
  through Health Services as a Health Connect permission and shows its Health
  Connect permissions form. The `android.permission.health.*` namespace is
  Health Connect's, but this app does not use the Health Connect client and
  reads nothing from Health Connect. If the form appears, the text above
  answers it for `READ_HEART_RATE`, and no other Health Connect permission is
  declared. The manifest notes that a Wear app needs no privacy-policy
  rationale activity for it; that requirement is for phone apps
  (`AndroidManifest.xml` comment).

## Other declarations Play Console will ask for

- **Ads:** no ads.
- **App access:** no login needed; every feature is reachable without an
  account. LIFT Link needs a phone with LIFT for Android, which reviewers may
  not have; the food log, QR export and Start workout all work without one.
- **Content rating questionnaire** and **target audience**: not directed at
  children.
- **Foreground service, background location, exact alarms:** none declared.

## Still missing before a submission

- The app created in Play Console under the existing developer account, and
  the Wear OS form factor opted in.
- **An Android App Bundle.** `release.yml` publishes `lift-wear.apk`; Play
  needs an `.aab`.
- Play App Signing enrollment, and the same upload-key decision as LIFT for
  Android, since both are signed with the LIFT product-family key
  (`android/README.md`, "Cutting a Wear OS release").
- Closed testing requirements for a new app on a personal developer account.
