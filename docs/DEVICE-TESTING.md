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

Not yet implemented. Repeat the equivalent offline, duplicate-message, and
out-of-order delivery scenarios once `android/` has an app to test.
