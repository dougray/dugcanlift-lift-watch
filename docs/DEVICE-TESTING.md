# Device testing

## Apple Watch

LIFT for Apple Watch moved to `lift-ios` (lift-ios #28, merged 2026-10-08) as LIFT for iPhone's
companion app. Its device testing, including the troubleshooting notes that
were here about a watch install riding the paired iPhone's connection, is in
`lift-ios`'s `Watch/README.md`, and it is built and installed with that repo's
`make device-build` and `make watch-device`.

## Wear OS

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

### The phone link (LIFT Link)

**This needs two real radios and has not been run on any.** A Wear OS AVD does
not emulate a Bluetooth controller, and two emulators cannot bond, so none of
the following can be checked without a watch and a phone in the same room. The
protocol itself is covered on the JVM (`:liftkit`'s `link` tests, including a
loopback harness that runs both ends through an in-memory pipe at a 23-byte
MTU) — the radio is what is left.

On a real watch and a real phone, in order:

1. **Pair.** Watch → Phone → "Pair with phone". On the phone, Home → Watch →
   "Pair with my watch". The watch should appear by name. Expect the system
   Bluetooth pairing dialog on **both** devices — the characteristics are
   encrypted, so the first write is what triggers bonding, and the phone
   retries that write once the bond completes.
2. **Check the code.** Both screens must show the same six digits. Confirm on
   the phone, accept on the watch, in that order and then again in the other
   order on a second attempt — the protocol allows either, and only a device
   test proves the UI does too.
3. **Refuse on the watch.** Nothing must be remembered on either end.
4. **Pair the wrong watch on purpose**, if two are available: the codes must
   differ, which is the whole reason the code exists.
5. **Push a plan** from the phone's Watch card. Check `adb logcat` on the watch
   for the plan arriving, and that a prescription of reps with no weight arrives
   with **no weight** rather than a zero.
6. **Walk away and come back.** The link should drop and re-establish with no
   taps at all once the watch app is foregrounded again.
7. **Close LIFT on the watch.** The watch must stop advertising: the phone's
   card should say it is waiting, and the watch must behave exactly as it did
   before this feature — food logging and QR export, offline.
8. **Turn Bluetooth off** on each end in turn; both apps should say so plainly
   rather than spinning.
9. **Battery.** Leave the watch advertising, app open, for an hour and compare
   against an hour with the app open and the link idle. Advertising is the cost
   this design chose to pay only while the app is open; measure it rather than
   assuming it.

### Heart rate per set

An emulator produces no heart rate, so none of this has been checked anywhere but on the JVM
(`HeartRatePerSetTest` against the hand-worked fixture, `SetHeartRatesTest`, `LinkHeartRateTest`).
On the Pixel Watch 2 and the moto g, worn:

1. **A real session.** Three exercises, one of them a superset, wrist down between sets. The bpm
   line shows on Wear OS 6; each set in Log set's list reads "max … bpm" within a few bpm of the
   highest reading around it, and Summary reads "Heart rate: average … · max … bpm".
2. **Versions.** This watch (version 4) with a LIFT Android at version 3: the session arrives
   whole, with no heart rate. Both at version 4: the figures are on the phone's set rows and the
   session's line is on its card.
3. **Refuse the permission.** The session logs exactly as before, with no figures and no heart-rate
   line, and nothing reads zero.
