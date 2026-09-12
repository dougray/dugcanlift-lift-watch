# Watch architecture

Both watch applications may create, retain, edit, and later synchronize a
workout without a live phone connection.

The domain contract must preserve stable workout, exercise, and set IDs. A
workout snapshot is revisioned; a receiver inserts an unknown ID, accepts a
newer revision, ignores an older revision, treats an identical revision as
idempotent, and acknowledges the accepted revision.

On Apple, this is implemented in `apple/LiftKit`:

- `WorkoutDraft` — the domain model. Every mutation that changes state goes
  through one `commit` path, so exactly one thing bumps `revision`.
- `SyncEnvelope` — the wire format, matching
  `shared/contracts/workout-sync.schema.json` field-for-field
  (`SyncEnvelopeTests` pins the JSON keys and enum spellings).
- `WorkoutStore.apply(_:)` — the reconciliation rule above, as a pure function
  returning `ReconcileOutcome` (`inserted` / `accepted` / `ignored` /
  `idempotent`); only a stored outcome produces an acknowledgement.
- `SyncOutbox` — queues local edits while the phone is unreachable, collapsing
  to the newest revision per workout, and clears an entry once its revision is
  acknowledged.

Platform transports are implementation details:

- watchOS uses WatchConnectivity to the iPhone application
  (`apple/LiftWatch/PhoneSyncTransport.swift`).
- Wear OS uses the Wearable Data Layer to the Android application (not yet
  implemented — see `android/README.md`).

All of the above except the transport files is plain Swift/Kotlin with no
platform dependency, and is covered by unit tests that don't need a
simulator, a device, or a paired phone.

## Food quick-log

`FOOD_LOGGED` (a `SyncEnvelope` event, watch -> phone) and
`RecentFoodsSnapshot` (a standalone type, phone -> watch via
`WCSession.updateApplicationContext`) are a separate, simpler channel from
the workout-reconciliation flow above: a food log is a one-shot request with
no revision to reconcile, and a recent-foods snapshot is a replace-in-place
cache, not a merged/reconciled record. `RecentFoodsSnapshotStore`
(`UserDefaults`-backed) is this repo's first persistence of any kind — see
its doc comment for why `UserDefaults` was judged sufficient here where
nothing else in this app persists anything.

## Standalone food logging and export

The watch logs food with no paired iPhone, and exports it as a QR code the
LIFT PWA scans.

- `WatchFoodLibrary` bundles the PWA's own `foods.json` (7,793 USDA SR
  Legacy records, public domain). Without it a watch that has never been
  paired has an empty food list, because `RecentFoodsListView` shows only
  what LIFT iOS pushed — its empty state literally says "Log a food on your
  phone to see it here."
- `StandaloneFoodLog` retains every logged entry, capped at 200 entries or
  just under 60 days. It is **separate from `SyncOutbox`** on purpose:
  `transferUserInfo` returns normally even when no iPhone was ever paired,
  and the watch cannot read back from the OS queue, so the outbox retains
  nothing a standalone user could export.
- `StandaloneExport` encodes the log as self-contained JSON — food names and
  per-100 g macros, never identifiers, because the PWA's food data shares no
  identifiers with LIFT iOS's — then raw-DEFLATEs and base64url-encodes it.
  Codes carry the `1z` / `1u` envelope `SHARE-FORMAT` uses, and are chunked
  by **measured byte size** against an 800-byte budget, not by entry count:
  real USDA names run to a median of 51 characters, so the food dictionary
  dominates the payload and no fixed entry count is safe.
- `QRCodeImage` implements QR encoding from scratch (ISO/IEC 18004,
  Reed-Solomon over GF(256), error-correction level M). **Core Image does
  not exist on watchOS** — `CoreImage.framework` is absent from the watchOS
  SDK entirely, though present on iOS — so `CIFilter.qrCodeGenerator()` is
  not available here and nothing in the system will do this for us.
- The user clears the log by hand after scanning, behind a confirmation.
  There is no channel back from the PWA, so the watch cannot know a scan
  succeeded, and an automatic clear would lose the log whenever one failed.

New source files must be registered in `LiftWatch.xcodeproj/project.pbxproj`.
This project lists its sources explicitly rather than using a synced folder,
so a new file is invisible to the build until it is added there.
