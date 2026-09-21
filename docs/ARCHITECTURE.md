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
- Wear OS uses **LIFT Link**, a direct Bluetooth LE channel to LIFT Android
  (`docs/LINK-PROTOCOL.md`, `android/wear/…/PhoneLinkPeripheral.kt`). The Data
  Layer is not an option — LIFT Android carries no Play Services by policy and
  the Data Layer needs it on both ends — so the channel is our own: fixed
  8-byte frames versioned from the first byte, encrypted characteristics that
  require a bond, and a confirmation code both ends derive rather than send.
  The QR export stays exactly as it was
  (`android/liftkit/StandaloneExport.kt`): the link adds a way *in* for the
  day's workout, it does not replace the way *out* for the food log, and a
  watch that never pairs is unchanged.

LIFT Link's own protocol layer — framing, payload codec, pairing code and
state machine — is plain Kotlin in `android/liftkit`'s
`com.dugcanlift.liftkit.link` with no Android imports, tested on the JVM, and
compiled byte-identically into LIFT Android from its own repository. The two
copies are held together by a shared wire fixture rather than by discipline;
their eventual home is `dugcanlift-kit-android`.

All of the above except the transport files is plain Swift/Kotlin with no
platform dependency, and is covered by unit tests that don't need a
simulator, a device, or a paired phone. `android/liftkit` is likewise plain
Kotlin with no platform dependency and no simulator required to test it.

## The guided session

A plan is what the watch is meant to lift today; a `WorkoutDraft` is what was
actually lifted. The two never merge: training against a plan builds an
ordinary draft, so revisions, `SyncOutbox` and `SESSION_FINISHED` behave
exactly as they do for a workout typed in from nothing, and **a session with
no plan is the free-entry flow unchanged** — `WorkoutView` does not even put
the Now page in the tab order.

- `WorkoutPlan` (`apple/LiftKit`) is the `plan` payload of
  `shared/contracts/workout-sync.schema.json`, carried by `PLAN_PUSHED`
  (phone -> watch). `PLAN_REQUEST` (watch -> phone) is a bare envelope asking
  for the current one, sent when the app becomes active and when the phone
  becomes reachable. Identity is the envelope's: `workoutId` is the plan's id
  and `revision` is the plan's revision, so the reconciliation rule above
  covers a re-push with no new machinery.
- **Every prescribed field is optional.** `PrescribedSet.weightKg` is
  `Double?` where `DraftSet.weightKg` — an actual, performed set — is a plain
  `Double`, because PLAN-FORMAT's `[null, 5]` is "five reps, you pick the
  weight" and a blank must never reach a wrist as a zero. `headline(unit:)`
  renders that as "5 reps", and a set prescribing nothing at all as "—".
- `GuidedSession` holds position only: which exercise, which set of it, and
  what to do when one is logged. It advances to the next exercise when this
  one's sets are done, returns the prescription just performed (which is
  where the rest interval comes from), and wraps rather than ending when an
  exercise was skipped and is still owed.
- `LiftingSessionRecorder` runs an `HKWorkoutSession` of
  `.traditionalStrengthTraining` with an `HKLiveWorkoutBuilder`. This is the
  opposite choice from `OutdoorActivityRecorder`, deliberately: that class
  computes distance itself and needs no live statistics, while current,
  average and maximum heart rate are exactly what the builder surfaces — and
  the builder is also what saves the workout with the samples collected
  during it, rather than writing heart rate into a second store. Only one
  `HKWorkoutSession` may be live at a time, which the UI already guarantees:
  an in-progress outdoor recording owns the whole screen.

### The transport gap, measured

`LIFT iOS` and this app are **not** `WCSession` peers today. On a paired
iPhone 17 / Apple Watch Ultra 4 simulator pair (watchOS 27, both apps
installed and running, `simctl list pairs` reporting *active, connected*),
the phone's `WCSession` reports `isPaired == true` but
`isWatchAppInstalled == false` and `isReachable == false`, so every
`transferUserInfo` queues into nothing. `WCSession` connects an iOS app to
*its own* companion watch app; this one is `WKWatchOnly` with its own bundle
id. The complications design already suspected this in prose; this is the
first measurement, and it applies equally to the food snapshot and
`FOOD_LOGGED` that shipped before it — the delivery, not the envelope, is
what is missing.

Until the watch app ships as a companion target, a DEBUG build accepts one
`SyncEnvelope` as JSON from the launch environment
(`LIFT_SYNC_ENVELOPE`, see `WorkoutSessionModel`), delivered through the same
`receive(_:)` the transport calls — so the decode, the revision rule and the
guided start are the real ones and only the delivery is by hand.

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

New source files are picked up by `xcodegen generate` (`make project`), which
globs `apple/LiftWatch`; the `.xcodeproj` is generated and must not be
hand-edited.
