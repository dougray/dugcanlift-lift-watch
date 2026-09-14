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

```sh
cd apple
make watch          # build and install on the paired Apple Watch
make watch-build    # build only
make devices        # what devicectl can see
make watch-destinations   # what xcodebuild can see
make doctor         # tooling, simulator and watch in one line each
```

The watch has **two different ids and they are not interchangeable**:
`devicectl` uses a CoreDevice UUID (`4D719BE4-…`), while xcodebuild's
`-destination` uses the hardware UDID (`00008310-…`). `make watch-destinations`
prints the second; `make watch WATCH=<id>` wants the first. Passing the wrong
one to `devicectl` fails the same way an unreachable watch does, which makes it
easy to misread.

`make watch` builds against `generic/platform=watchOS` rather than the specific
watch on purpose. A device-targeted `-destination` has to reach the watch
*before* it will compile anything, so a watch that is merely unreachable costs
you the build too; the generic slice compiles regardless and only the install
step needs the hardware.

### When the watch will not take an install

The build succeeding and the install failing are separate problems. These two
messages both mean the watch, not the code:

- `devicectl`: *The device rejected the connection request.*
- `xcodebuild`: *may need to be unlocked to recover from previously reported
  preparation errors.*

In order of likelihood:

1. **Developer Mode is off on the watch.** Settings → Privacy & Security →
   Developer Mode, then restart it when asked. A watch that has only ever been
   paired to a phone will not have this on, and nothing on the Mac can turn it
   on. This is the usual answer.
2. **The watch is locked, off the wrist, or not on this Mac's Wi-Fi.** Watch
   installs go over the network; there is no cable.
3. **It failed preparation earlier and will not retry by itself.** Xcode →
   Window → Devices and Simulators, select the watch, let it finish. That step
   has no command-line equivalent, and it is where the real error appears.

Note that `devicectl list devices` reporting `available (paired)` rather than
`connected` is normal for a watch and is *not* itself the problem — a watch
that installs fine can still read `available (paired)`.

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
