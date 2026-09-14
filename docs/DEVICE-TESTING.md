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

## Watch face complications

None of this can be verified in the simulator's watch face editor alone —
complication layout, HealthKit reads and deep links all need hardware.

- [ ] Both complications appear in the Infograph Modular editor's slot picker.
- [ ] **Which family the top-left slot accepts** — `LiftSteps` declares both
      `.accessoryCircular` and `.accessoryCorner` because this is unverified.
      Note which one the face actually uses and delete the other.
- [ ] The macros slot renders three lines without truncation at 41, 45 and
      49 mm. The longest realistic string is a four-digit calorie total
      against a four-digit goal.
- [ ] Log a food on the watch; the macros complication updates without
      reopening the app.
- [ ] Set a goal below today's intake; the offending number turns red and the
      others do not.
- [ ] Check the red state on a tinted face — the numbers must still read
      correctly when the colour is washed out.
- [ ] Before granting the Health prompt, the steps slot shows a dash, never a
      zero.
- [ ] After granting it, the steps slot shows a count within a few minutes.
- [ ] Tap the macros complication from the face: LIFT opens on the food log,
      not on its start screen.
- [ ] **Start a run, drop the wrist, then tap the macros complication.** The
      food log opens over the run; dismissing it returns to a run that is
      still recording.
- [ ] Log food on the watch, then check the export screen still lists every
      entry — the App Group migration must not have lost any.
- [ ] Install over a build that predates the App Group and confirm previously
      logged food is still on the export screen.
