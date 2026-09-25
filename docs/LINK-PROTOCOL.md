# LIFT Link — the Wear OS ↔ LIFT Android channel

Version 2, and it still speaks version 1. A direct Bluetooth LE channel between LIFT Android (the phone) and the LIFT Wear OS
watch, so the phone can push the day's workout to the wrist and the wrist can send back what was
actually lifted.

## Why it exists

The Apple side already has a phone transport: `WCSession`, with the wire contract in
`shared/contracts/workout-sync.schema.json`. The Wear app has none, on purpose — the Wearable Data
Layer needs Google Play Services on **both** ends, LIFT Android carries none by policy, and the
Wear app has no `INTERNET` permission at all. That is why it exports by QR code today.

The alternatives were: adopt Play Services (reversing a deliberate decision on two apps), or write
a direct channel. This is the direct channel. **Nothing here adds Play Services or an internet
permission to either app, and `PhoneLinkManifestTest` fails if anything ever does.**

**With no phone, nothing changes.** The watch logs food, retains it, and exports by QR exactly as
before. The radio is only on while the LIFT app is open, and only when the user has paired or is
pairing.

## Where the code is

The framing, the payload codec, the pairing-code derivation and the state machine are plain Kotlin
with no Android imports and no JSON library, in

```
android/liftkit/src/main/kotlin/com/dugcanlift/liftkit/link/
```

and **byte-identically** in the phone repo at `link/src/main/kotlin/com/dugcanlift/liftkit/link/`.
Two repositories, one implementation, copied. The long-term home is `dugcanlift-kit-android`'s
`:liftcore`, which both apps already depend on; until then `fixtures/link-wire.txt` — the same file
in both repos — is what keeps the copies honest: each repo's `LinkWireFixtureTest` encodes the same
canonical objects and asserts the same bytes, so a copy that drifts fails in the repo where it
drifted rather than in a gym.

Only the radio is platform code: `android/wear/…/PhoneLinkPeripheral.kt` here, and
`app/…/watchlink/WatchLinkTransport.kt` on the phone.

## Roles: the watch advertises, the phone scans

**The watch is the GATT peripheral. The phone is the central.**

- **Choosing needs a list, and a list comes from scanning.** The phone has the screen that can show
  two watches by name with signal strength and let a person pick one. A watch cannot usefully
  present a list of phones.
- **Bonding is the central's to start.** Android's pairing flow is initiated by the connecting
  side, and a system pairing dialog belongs on the device the user is already holding for setup.
- **Reconnecting needs no scan.** Afterwards the phone connects to the remembered address with
  `autoConnect = true`, a pending connection the stack completes as soon as the watch advertises
  again. Nothing scans for the life of the pairing — which is also why the watch asks for
  `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT` and never `BLUETOOTH_SCAN`.
- **The watch's radio stays off when LIFT is closed.** A peripheral is only reachable while it
  advertises, so this is the honest knob for battery: the watch advertises while the Phone screen
  is open, or while the app is in the foreground with a phone already remembered. Holding the link
  through a whole guided session with the screen off will need a `connectedDevice` foreground
  service, and that belongs with the guided session.

### GATT layout

| | UUID | Properties | Permissions |
|---|---|---|---|
| Service | `6f1e2d40-9c3b-4a51-8f7a-2b5d1c0e7a10` | primary | — |
| RX (central → peripheral) | `6f1e2d41-…` | `WRITE` | `WRITE_ENCRYPTED` |
| TX (peripheral → central) | `6f1e2d42-…` | `NOTIFY` | `READ_ENCRYPTED` |
| CCCD | `00002902-…` | — | `READ_ENCRYPTED`, `WRITE_ENCRYPTED` |

One command channel, two characteristics because BLE is directional. Writes are
write-with-response, which is also the flow control: the response is what says the previous frame
landed. Notifications go one at a time, waiting for `onNotificationSent`, because a second
notification before that one is silently dropped by the stack.

The service UUID is in the advertisement (18 of its 31 bytes); the device name is in the scan
response, so the stack does not truncate it.

### Bonding and encryption

Both characteristics are declared **encrypted**. An unbonded central's first write is refused by
the stack with an authentication error, which is what starts pairing; on Android the central
usually has to retry that write once the bond completes, and `WatchLinkTransport` does.

`PhoneLinkPeripheral.onCharacteristicWriteRequest` **also** refuses a device whose
`bondState != BOND_BONDED`, with `GATT_INSUFFICIENT_AUTHENTICATION`. That is deliberate belt and
braces: this channel carries a training log and a bodyweight, and "an unbonded read must not be
possible" is not something to leave resting on one constant being right.

And a third layer, in the protocol itself: `LinkSession` refuses every data message with
`NOT_PAIRED` until the handshake has completed, so even a bonded peer that skipped the handshake
gets nothing.

### The confirmation code

Bonding proves the link is encrypted. It does not prove you bonded the watch you meant to, which
matters in a gym with two of them on a bench. So:

1. The central sends `HELLO` with 8 random bytes.
2. The peripheral answers `HELLO_ACK` with 8 random bytes of its own.
3. Both ends compute `SHA-256(lower ‖ higher)` of the two nonces, ordered so each end gets the same
   answer, and take the first four bytes modulo 1 000 000 — **six digits, shown on both screens.**
4. The phone's user confirms (`PAIR_CONFIRM`); the watch's user accepts (`PAIR_RESULT`). **Both**,
   in either order. Either refusal ends it and nothing is remembered.

The code is never transmitted. A device showing the right number is a device that took part in this
handshake, and the number is fresh every time, so last week's proves nothing.

Afterwards each end remembers the other's address. A remembered peer skips steps 3 and 4 entirely
and goes straight to ready — nobody taps anything to reconnect.

## Framing

Fixed 8-byte header, big-endian, then this frame's slice of the payload.

```
 offset size  field
 0      1     version   protocol version of this frame
 1      1     type      opcode
 2      1     flags     bit 0 FIRST, bit 1 LAST, bits 2-7 reserved, must be zero
 3      1     seq       message sequence, 0..255 wrapping; every frame of one message shares it
 4      2     length    payload bytes in THIS frame
 6      2     crc       CRC-16/CCITT-FALSE over bytes 0..5 and the payload
 8      N     payload
```

**Byte 0 is the version, and version 1's header shape is frozen for ever.** That is the point of
putting it first: any build, however old, can read byte 0 of anything that arrives, recognise a
version it does not know, and refuse politely rather than parse a later layout as if it were this
one. A future version may change the rest of the header freely, because no old reader will get past
byte 0 — and an `ERROR` is always sent at version 1, so a refusal is legible to the peer being
refused.

The CRC is not there because BLE is lossy; the link layer already checks that. It is there because
a truncated or concatenated write is a failure an ATT stack *can* hand up, and a length field alone
would let a short frame read past its end.

A message longer than one frame is split across frames sharing a `seq`, the first flagged FIRST and
the last flagged LAST. The channel carries one message at a time in each direction, which is what
makes a single reassembly buffer correct. Reassembly is bounded at 64 KiB and refuses rather than
truncates. A continuation with no start, a continuation of a different message, a bad CRC, a
reserved flag bit — all refused, none guessed at.

At the 23-byte MTU every BLE device must accept, that is 12 bytes of payload per write. The link
requests MTU 247 and usually gets it (244 usable, 236 of payload), but nothing assumes it: the
budget starts at the floor and is raised only when `onMtuChanged` says so.

## Messages

Opcodes mirror `workout-sync.schema.json`'s envelope events wherever an event means the same
thing, so the Apple and Wear channels stay legible to each other.

| Code | Name | Direction | Schema equivalent |
|---|---|---|---|
| `0x01` | `HELLO` | phone → watch | — |
| `0x02` | `HELLO_ACK` | watch → phone | — |
| `0x03` | `PAIR_CONFIRM` | phone → watch | — |
| `0x04` | `PAIR_RESULT` | watch → phone | — |
| `0x10` | `PLAN_PUSHED` | phone → watch | `PLAN_PUSHED` |
| `0x11` | `PLAN_REQUEST` | watch → phone | `PLAN_REQUEST` |
| `0x20` | `SET_LOGGED` | watch → phone | `SET_LOGGED` |
| `0x21` | `SESSION_FINISHED` | watch → phone | `SESSION_FINISHED` |
| `0x30` | `ACK` | either | `WORKOUT_SYNC_ACK` |
| `0x3F` | `ERROR` | either | — |

An opcode this build has never heard of gets an `ERROR` with `UNKNOWN_TYPE` and **the link stays
up**, the same way the schema has a receiver ignore an event it does not know rather than fail. A
version disagreement, or a message out of order during the handshake, does end the link: there is
nothing else to say.

`ACK` carries what was *stored* — the id, the revision and one of `INSERTED` / `ACCEPTED` /
`IGNORED` / `IDEMPOTENT` — rather than which frame carried it, because reconciliation clears an
outbox entry by id and revision and a frame number means nothing a week later. Those four outcomes
are `ARCHITECTURE.md`'s, unchanged, so both channels acknowledge in the same words.

## Payloads

Binary, with **presence bits**, and no JSON.

That is not a performance choice. `workout-sync.schema.json`, PLAN-FORMAT and this app all say the
same thing about a prescription: **every field is optional, `[null, 5]` means "five reps, you pick
the weight", and blank must never render as zero.** A presence bit makes an absent field
*physically absent* — there is no zero on the wire for a later reader to mistake for a number. A
set that prescribes nothing at all is one byte: `0x00`.

`LinkPayloadsTest` pins this directly, including that adding `reps` to an empty set grows the
payload by exactly the two bytes of the reps and nothing else.

Every decoder is strict. A reserved presence bit, a reps of zero, an RPE outside 1–10, a revision
of zero, or a single trailing byte is refused with an `ERROR`, never tidied up.

### Units on the wire

- **Weights are kilograms**, as the schema's `weightKg` is, carried as **whole grams in a uint32**.
  An integer has one representation on both ends; a float has two roundings and a platform's
  opinion about which. LIFT Android stores **pounds** and converts at its own mapping layer — a
  missing conversion there is silent and 2.2× wrong, which is why `WatchPlanMapperTest` pins it.
- **RPE** is tenths, 10–100, so 8.5 survives.
- **Distance** is centimetres in a uint32; **duration** is seconds.
- **Days** are `yyyy-MM-dd`, local, like every day key in LIFT.
- **Side** is SHARE-FORMAT's flags codes — 1 left, 2 right — and **absent means both**. There is
  deliberately no "both" value to write by accident.

### Version 2: a coach's sides

Version 2 added two fields to `PLAN_PUSHED` and nothing else:

- **`eachSide` on an exercise** — every prescribed set is done on both sides (PLAN-FORMAT.md
  "Sides", `b: 1`). "3 × 8 each side" stays three prescribed rows and is six sets, three a side.
- **a named `side` on a prescribed set** — one set for one limb, the set tuple's sixth position.
  Legal whether or not its exercise is each-side, where it means that set alone is single-limb.

Both are **presence bits in masks version 1 already reserved** (`0x08` on the exercise mask, `0x10`
on the prescribed-set mask), so **a plan that says nothing about sides encodes to exactly the bytes
version 1 wrote** and the `plan` line of `fixtures/link-wire.txt` did not move. `eachSide` is a bit
with no bytes behind it — the bit *is* the value, and `false` writes nothing at all.

Two things make this a version a version-1 peer can still be talked to, rather than a break:

1. **Frames go out at the negotiated version, not at this build's newest** (`LinkCodec.version`,
   which starts at `MIN_SUPPORTED_VERSION` and is raised once the handshake has settled). Otherwise a
   version-2 build would write `02` in byte 0 of its own HELLO and a version-1 peer would refuse the
   handshake itself — the version range in `HELLO` would never get a chance to mean anything. An
   `ERROR` still always goes at version 1, as this document has always said.
2. **`LinkSession.pushPlan` strips the sides when the link settled on version 1.** Losing a label
   costs a lifter a letter beside a number; losing the frame costs them the whole workout.

So: a version-2 phone and a version-1 watch get a working link with no side markers, and two
version-2 builds get the sides. `LinkPlanSidesTest` (shared, in both repos) pins all of it, including
that stripping a sided plan gives byte for byte what a plan without sides gives.

**Not in version 2:** duration and distance on a prescription, and per-set `restSeconds` beyond what
version 1 already carried. A conditioning piece prescribed by time still reaches the watch as a set
with no numbers.

### What v1 carries

- `PLAN_PUSHED` — plan id, revision, name, source (`ROUTINE` / `COACH_PLAN`), optional
  `scheduledFor`, then exercises: name, optional equipment (a lift's identity is name *and*
  equipment), optional note, prescribed sets, and an optional `lastPerformed` so the watch can show
  "last: 185×5 @8".
- `PLAN_REQUEST` — empty. The watch asking the phone to push the current one.
- `SET_LOGGED` — session id, revision, exercise, set index, the set, and when. Optional streaming:
  a phone on the bench stays in step, and nothing is lost if none arrive.
- `SESSION_FINISHED` — the source of truth: session id, revision, day, name, start time, and the
  exercises with their sets.

## Errors

| Code | Name | Ends the link? |
|---|---|---|
| 1 | `UNSUPPORTED_VERSION` | yes |
| 2 | `MALFORMED_FRAME` | no |
| 3 | `NOT_PAIRED` | no |
| 4 | `UNEXPECTED_MESSAGE` | only during the handshake |
| 5 | `PAYLOAD_TOO_LARGE` | no |
| 6 | `UNKNOWN_TYPE` | no |
| 7 | `PAIRING_REJECTED` | yes |

An error code the receiver does not know is still reported, carrying the peer's own number, rather
than being swallowed.

## What is proven, and what is not

Proven by unit tests, on the JVM, with no device:

- the frame header's byte layout, against the published CRC-16/CCITT-FALSE check value;
- refusal of a newer version, a bad CRC, a truncated frame, a reserved flag;
- fragmentation and reassembly at MTU 23 and 247, sequence wrap, abandoned partials;
- every optional field absent, present, and at its boundaries;
- the handshake, both confirm orders, both rejections, the remembered-peer path, version refusal
  in both directions, and the `NOT_PAIRED` guard in front of a training log;
- `LinkLoopbackTest`: two `LinkSession`s and two `LinkCodec`s talking through an in-memory pipe —
  pairing, a whole plan at a 23-byte MTU, sets and a session coming back, and a corrupted frame
  mid-plan that the link survives;
- `LinkWireFixtureTest`: the committed bytes, which is the only test here that is about more than
  this repo agreeing with itself.

**Not proven, because it needs two radios:** MTU negotiation with a real stack, bonding and the
retry after it, `autoConnect` reconnection, advertising on real Wear hardware (`Wear_Round`-class
AVDs do not emulate a Bluetooth radio, and two emulators cannot bond), and battery cost. Those are
device tests — see `DEVICE-TESTING.md`.

## Changing it

Anything that changes a byte on the wire is a version bump of `LinkProtocol.VERSION`, made in
**both** repos together, with `fixtures/link-wire.txt` regenerated deliberately and reviewed — not
regenerated to make a test pass. A peer at a version the other does not support refuses politely;
that is what the version byte is for, and it only works if the version is actually bumped.
