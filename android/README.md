# Wear OS application

Two Gradle modules, root project `LiftWear`:

- `:liftkit` — plain Kotlin, no Android/Wear dependency. Standalone food
  logging domain: `WatchFoodLibrary`, `StandaloneFoodLog`, `StandaloneExport`
  (the QR export encoder), and the models they share; plus
  `com.dugcanlift.liftkit.link`, the LIFT Link protocol (framing, payload
  codec, pairing code, state machine) shared byte-for-byte with LIFT Android.
  No simulator or emulator is needed to build or test this module.
- `:wear` — the Wear OS app (Compose for Wear OS): home, food search,
  rotary amount entry, meal selection, the QR export screen
  (`QrBitmap`, `ExportScreen`), and the phone link
  (`PhoneLinkPeripheral`, `PhoneLinkScreen`). Depends on `:liftkit`.

## Build and test

Prefix every Gradle invocation with `JAVA_HOME` pointed at Android Studio's
bundled JDK — there is no other JDK on this Mac that Gradle will accept:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :liftkit:test
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :wear:testDebugUnitTest
```

Run Gradle in the foreground; do not background it.

## Emulator

Wear UI that needs a device (as opposed to `:wear`'s unit tests, which run on
the JVM like `:liftkit`'s) runs on the `Wear_Round` AVD, created from the
`system-images;android-34;android-wear;arm64-v8a` image:

```bash
~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager --install \
  "system-images;android-34;android-wear;arm64-v8a" "platforms;android-34"
~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager create avd \
  -n Wear_Round -k "system-images;android-34;android-wear;arm64-v8a" \
  -d "wearos_large_round" --force
```

## Phone sync: LIFT Link, not the Data Layer

LIFT Android carries no Google Play Services by policy, and the Wearable Data
Layer needs Play Services on both the watch and the phone — so there is no Data
Layer transport here and there never will be. **LIFT Link** is a direct
Bluetooth LE channel instead: the watch is the GATT peripheral, the phone is the
central, both characteristics are encrypted so an unbonded read is impossible,
and a six-digit confirmation code computed independently on both ends says you
paired the watch you meant to. The protocol is `docs/LINK-PROTOCOL.md`; the
framing, codec and state machine are pure Kotlin in `:liftkit`'s
`com.dugcanlift.liftkit.link`, byte-identical to the copy in the phone repo and
pinned by a shared wire fixture.

**Still no `INTERNET` permission and still no Play Services** — that constraint
is the whole reason this exists rather than the Data Layer, and
`PhoneLinkManifestTest` fails if either ever comes back.

**The watch is standalone-first, unchanged.** It logs food with no phone present
and exports the log as a QR code the LIFT PWA scans, the same path watchOS uses
(`android/liftkit/StandaloneExport.kt`, `docs/ARCHITECTURE.md`). The radio only
runs while the app is open and a phone is paired or being paired; with LIFT
closed the watch advertises nothing.

## Cutting a Wear OS release

The release build is signed with the LIFT product-family key, the same one
LIFT Android 1.3 carries — different `applicationId`, so the two never
collide on a device. Gradle reads it from `android/keystore.properties`,
which is gitignored and absent from a fresh clone; without it the release
variant still builds, just unsigned.

```
storeFile=/Users/<you>/keystores/dugcanlift-release.jks
storePassword=...
keyAlias=dugcanlift
keyPassword=...
```

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :wear:test :wear:assembleRelease
apksigner verify --print-certs wear/build/outputs/apk/release/wear-release.apk
```

The signer's SHA-256 must match the one LIFT Android reports. Publish the
APK as `assets/downloads/lift-wear.apk` in `dugcanlift-site`.
