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
