# Device testing

## Apple Watch

1. Open `apple/LiftWatch.xcodeproj` in Xcode (regenerate first if you edited
   `apple/project.yml`: `cd apple && xcodegen generate`).
2. Select a signing team for the `LiftWatch` target (Signing & Capabilities).
3. Select your paired Apple Watch as the run destination and Run.
   - The app is `WKWatchOnly`, so it installs and launches without a
     companion iPhone app being present.
4. Verify a workout can be created and edited while the phone is unavailable
   (airplane mode on the watch, or leave the phone in another room).
5. Restore connectivity and verify the newest complete revision is imported
   once and acknowledged (see `apple/LiftKit`'s `WorkoutStoreTests` for the
   rules this should follow).

### From the command line

Once a signing team is set in the project:

```sh
cd apple
xcodebuild -project LiftWatch.xcodeproj -scheme LiftWatch \
  -destination 'platform=watchOS,name=<Your Watch Name>' build
```

List the exact destination string Xcode sees for your paired watch:

```sh
xcodebuild -project LiftWatch.xcodeproj -scheme LiftWatch -showdestinations
```

A sandboxed/CI shell without access to Apple's local device-discovery service
(`devicectl`/`xctrace` showing your paired iPhone as "unavailable" despite a
USB connection) cannot deploy to physical hardware — run the above from a
normal Terminal, or use Xcode's Run button directly.

## Wear OS

There is no phone transport to test — the Wearable Data Layer needs Play
Services on both ends and LIFT Android carries none, so the watch is
standalone and exports by QR code instead. The offline, duplicate-message and
out-of-order delivery scenarios above have no Wear equivalent.

What does need a device or emulator:

```bash
# an emulator, if you don't have a watch paired
~/Library/Android/sdk/emulator/emulator -avd Wear_Round &
cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :wear:installDebug
```

- **Log a food with the phone disconnected**, or with no phone ever paired.
  Search runs against the bundled library, so it must work with the watch in
  airplane mode.
- **Export and scan.** Open Export, and read the codes with `Scan from watch`
  in the web app. A code that a camera cannot acquire is the failure this
  screen exists to avoid: the QR is sized to the inscribed square of a round
  display, because a full-bleed code has its corner finder patterns clipped by
  the bezel and cannot be read at all.
- **Clear after scanning.** "Done — clear these" removes only the entries that
  were on screen when the export opened, and asks first. Anything logged while
  the export was open must survive.
- **Rotary input** on the amount screen, in both grams and ounces.
