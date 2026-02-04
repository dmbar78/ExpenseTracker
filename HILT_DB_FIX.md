# Hilt Migration Fix: Shared Database Instance

## Critical Bug Discovered

While fixing test timeouts, a critical bug was found in the Hilt `DatabaseModule`.

### The Problem
The `DatabaseModule` was creating a **new** `RoomDatabase` instance using `Room.databaseBuilder()` instead of reusing the existing singleton from `AppDatabase.getDatabase()`.

**Consequences:**
1. **Test Failures:** Tests used `AppDatabase.getDatabase()` to setup/clear data, while the App (via Hilt) used a separate instance. Room's `InvalidationTracker` does not propagate across different instances, so the App's UI never updated when the Test changed the DB.
2. **Missing Migrations:** The Hilt provider **forgot to add migrations**, which would cause the app to crash on user devices during updates.

### The Fix
Updated `di/DatabaseModule.kt` to delegate to the existing singleton:

```kotlin
@Provides
@Singleton
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
    return AppDatabase.getDatabase(context)
}
```

## How checking this fixed the tests
- **Before:** Test clears DB -> Test DB Instance updates -> App DB Instance (used by ViewModel) **does nothing**. Test waits forever for UI update.
- **After:** Test clears DB -> Shared DB Instance updates -> InvalidationTracker notifies ViewModel -> UI updates -> Test passes.

## Recommendations
- Always ensure Hilt provides the same Singleton instance as manual accessors (or remove manual accessors entirely and rely solely on Hilt, but legacy code often needs the static accessor).
- Ensure Migrations are included in the Hilt provider if constructing manually.

This fix should resolve ALL remaining timing/timeout issues in `AccountDeletionSafeguardTest` and `VoiceRecognitionIntegrationTest` without requiring excessive 10s timeouts (though the timeouts are safe to keep).
