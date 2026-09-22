# Device testing

## Apple Watch

LIFT for Apple Watch moved to `lift-ios` in 2026-09 as LIFT for iPhone's
companion app. Its device testing, including the troubleshooting notes that
were here about a watch install riding the paired iPhone's connection, is in
`lift-ios`'s `Watch/README.md`, and it is built and installed with that repo's
`make device-build` and `make watch-device`.

## Wear OS

There is no phone transport to test — the Wearable Data Layer needs Play
Services on both ends and LIFT Android carries none, so the watch is
standalone and exports by QR code instead. The offline, duplicate-message and
out-of-order delivery scenarios a phone transport would need have no Wear
equivalent.

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
