# Device testing

1. Open the project under `apple/` in Xcode.
2. Select the paired Apple Watch destination and a signing team.
3. Install the watch app through the paired iPhone/Watch destination.
4. Verify a workout can be created and edited while the phone is unavailable.
5. Restore connectivity and verify that the newest complete revision is imported once and acknowledged.

Repeat equivalent offline, duplicate-message, and out-of-order delivery scenarios for the Wear OS app under `android/`.
