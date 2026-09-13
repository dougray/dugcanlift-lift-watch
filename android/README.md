# Wear OS application

Two Gradle modules, root project `LiftWear`:

- `:liftkit` — plain Kotlin, no Android/Wear dependency. Standalone food
  logging domain: `WatchFoodLibrary`, `StandaloneFoodLog`, `StandaloneExport`
  (the QR export encoder), and the models they share. No simulator or
  emulator is needed to build or test this module.
- `:wear` — the Wear OS app (Compose for Wear OS): home, food search,
  rotary amount entry, meal selection, and the QR export screen
  (`QrBitmap`, `ExportScreen`). Depends on `:liftkit`.

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

## No phone sync

LIFT Android carries no Google Play Services by policy, and the Wearable Data
Layer needs Play Services on both the watch and the phone — so there is no
phone transport here. The watch is standalone-first: it logs food with no
phone present and exports the log as a QR code the LIFT PWA scans, the same
path watchOS uses (`android/liftkit/StandaloneExport.kt`,
`docs/ARCHITECTURE.md`).
