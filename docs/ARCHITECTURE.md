# Watch architecture

This repository is LIFT for Wear OS. LIFT for Apple Watch lived here too until
it moved into `lift-ios` as LIFT for iPhone's companion app (lift-ios #28,
merged 2026-10-08). Its architecture (revision-reconciled workouts, the guided
session, the WatchConnectivity transport and its wire contract) is described
in `lift-ios`'s `Watch/README.md`, and this repository's history before the
removal still holds the version that was written here.

## The phone link

Wear OS uses **LIFT Link**, a direct Bluetooth LE channel to LIFT Android
(`docs/LINK-PROTOCOL.md`, `android/wear/…/PhoneLinkPeripheral.kt`). The
Wearable Data Layer is not an option — LIFT Android carries no Play Services by
policy and the Data Layer needs it on both ends — so the channel is our own:
fixed 8-byte frames versioned from the first byte, encrypted characteristics
that require a bond, and a confirmation code both ends derive rather than send.

The watch is still standalone-first. It logs food with no phone present and
exports the log as QR codes the LIFT PWA scans (below). The link adds a way
*in* for the day's workout; it does not replace the way *out* for the food log,
and a watch that never pairs is unchanged.

LIFT Link's own protocol layer — framing, payload codec, pairing code and
state machine — is plain Kotlin in `android/liftkit`'s
`com.dugcanlift.liftkit.link` with no Android imports, tested on the JVM, and
compiled byte-identically into LIFT Android from its own repository. The two
copies are held together by a shared wire fixture rather than by discipline;
their eventual home is `dugcanlift-kit-android`.

## The guided session

The rules for training a pushed plan on the wrist — `GuidedSession`,
`RestTimer`, `SetEntrySeed`, `SessionSnapshot`, `SessionOutbox`, the
prescription formatting — are plain Kotlin in `android/liftkit`, tested on the
JVM like the rest of it; `android/wear` lays them out and owns the radio.
`android/README.md` ("The guided session") describes them.

## Standalone food logging and export

Everything below lives in `android/liftkit`, plain Kotlin with no Android
dependency, unit tested without an emulator (`./gradlew :liftkit:test`). The
app in `android/wear` is screens and storage around it.

- `WatchFoodLibrary` reads the PWA's own `foods.json` (USDA SR Legacy, public
  domain), so a watch that has never met a phone has foods to log, under the
  same names the PWA uses.
- `StandaloneFoodLog` retains every logged entry, capped at 200 entries and by
  nothing else: no age window, so a watch out of contact for months still hands
  over everything it recorded. An entry this build cannot read is carried
  forward, not dropped.
- `StandaloneExport` encodes the log as self-contained JSON — food names and
  per-100 g macros, never identifiers, because the PWA's food data shares no
  identifiers with the phone apps' — then raw-DEFLATEs and base64url-encodes it.
  Codes carry the `1z` / `1u` envelope `SHARE-FORMAT` uses, and are chunked by
  **measured byte size** against an 800-byte budget, not by entry count.
- The export screen (`android/wear`) draws each code with ZXing (`QrBitmap`),
  sized to the inscribed square of a round display so no finder pattern sits
  under the bezel; `qr-nightly.yml` decodes a real screenshot of it every
  night.
- The user clears the log by hand after scanning, behind a confirmation. There
  is no channel back from the PWA, so the watch cannot know a scan succeeded,
  and an automatic clear would lose the log whenever one failed.
