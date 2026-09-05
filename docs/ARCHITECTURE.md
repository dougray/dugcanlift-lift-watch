# Watch architecture

Both watch applications may create, retain, edit, and later synchronize a workout without a live phone connection.

The domain contract must preserve stable workout, exercise, and set IDs. A workout snapshot is revisioned; a receiver inserts an unknown ID, accepts a newer revision, ignores an older revision, treats an identical revision as idempotent, and acknowledges the accepted revision.

Platform transports are implementation details:

- watchOS uses WatchConnectivity to the iPhone application.
- Wear OS uses the Wearable Data Layer to the Android application.
