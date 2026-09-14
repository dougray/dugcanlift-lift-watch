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

**Check the paired iPhone first.** The watch has no independent link to this
Mac — watch deployment rides the phone's connection. When the phone drops, the
watch becomes unreachable with it, and every error you get back describes the
watch instead:

```sh
xcrun devicectl list devices
```

You want the **iPhone** reading `connected`. If it reads `unavailable`, nothing
you do to the watch will help. `xctrace list devices` is the clearer view — it
lists both devices under `Devices Offline` when the link is down, which
`devicectl` does not make obvious.

A *reachable* watch also reads `connected`. `available (paired)` means paired to
the phone but not currently reachable from here, and is exactly the state in
which installs fail.

These two messages both mean the link, not the code and not the watch:

- `devicectl`: *The device rejected the connection request.*
  (`RemotePairingError 1007`)
- `xcodebuild`: *may need to be unlocked to recover from previously reported
  preparation errors.*

Neither names the phone, which is what makes this worth writing down: the whole
diagnosis is one device to the left of where the errors point.

In order:

1. **Get the iPhone back to `connected`.** Plug it into this Mac, unlock it,
   accept "Trust This Computer" if asked. Re-check with the command above.
2. If it stays `unavailable` after a replug, open **Xcode → Window → Devices and
   Simulators**. That is where a device gets re-paired and, unlike `devicectl`,
   it shows the real error. If the phone does not appear there either, the
   CoreDevice daemon is stale and a restart of the Mac clears it.
3. Only then look at the watch: unlocked, on the wrist, on this Mac's Wi-Fi.
4. **Developer Mode** must be on (Settings → Privacy & Security → Developer
   Mode, restart when asked) — but it is rarely the cause, and a watch that has
   it off will say so plainly rather than rejecting the connection.

`system_profiler SPUSBDataType` is not a useful check here: it can return
completely empty output, so "no iPhone on USB" from it proves nothing.

Launching remotely can be refused even when the install succeeded:

```
Navigation away from clock is not allowed due to one or more active system states
```

That is watchOS declining to foreground an app while the watch is on a charger
or wrist-down. The app is installed; open it from the watch's app list.

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
