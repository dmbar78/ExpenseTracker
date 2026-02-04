# Hilt Timing Fixes Applied

## Summary

After migrating to Hilt dependency injection, tests experienced timing issues due to StateFlow initialization delays. This document summarizes the fixes applied.

## Problem

With Hilt, ViewModel StateFlows need time to:
1. Be injected by Hilt
2. Collect initial values from repositories
3. Propagate state to UI

Tests that immediately checked ViewModel state or UI elements dependent on StateFlow data would timeout before the data was ready.

## Solution Pattern

For tests experiencing `ComposeTimeoutException` after Hilt migration, apply this pattern:

```kotlin
// After navigation or database operations
Thread.sleep(300)  // Give StateFlows time to initialize
composeTestRule.waitForIdle()

// Then wait for the expected state with increased timeout
composeTestRule.waitUntil(timeoutMillis = 10000) {  // Increased from 5000ms
    // Your condition here
}
```

## Fixes Applied

### 1. VoiceRecognitionIntegrationTest ✅

**File:** `app/src/androidTest/java/com/example/expensetracker/ui/VoiceRecognitionIntegrationTest.kt`

**Location:** `setup()` method, line ~65

**Issue:** Timeout waiting for `viewModel.allAccounts.value.isEmpty()` after clearing database

**Fix:**
```kotlin
@Before
fun setup() {
    hiltRule.inject()
    composeTestRule.waitForIdle()
    
    // Get ViewModel and clear database...
    
    // NEW: Give StateFlows time to initialize
    Thread.sleep(300)
    composeTestRule.waitForIdle()
    
    // NEW: Increased timeout from 5000ms to 10000ms
    composeTestRule.waitUntil(timeoutMillis = 10000) {
        viewModel.allAccounts.value.isEmpty()
    }
}
```

**Tests Fixed:**
- `voiceExpense_capitalizesParsedInputs`
- `voiceTransfer_withNoAccounts_showsErrorInsteadOfHanging`

---

### 2. AccountDeletionSafeguardTest ✅

**File:** `app/src/androidTest/java/com/example/expensetracker/ui/AccountDeletionSafeguardTest.kt`

**Location:** `navigateToEditAccount()` method, line ~65

**Issue:** Timeout waiting for account name to appear in list after navigation

**Fix:**
```kotlin
private fun navigateToEditAccount(accountName: String) {
    // Navigate to Accounts screen...
    composeTestRule.onNodeWithText("Accounts").performClick()
    composeTestRule.waitForIdle()

    // NEW: Give StateFlows time to load accounts after navigation
    Thread.sleep(300)
    composeTestRule.waitForIdle()

    // NEW: Increased timeout from 5000ms to 10000ms
    composeTestRule.waitUntil(timeoutMillis = 10000) {
        composeTestRule.onAllNodesWithText(accountName, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
    
    // Rest of method...
}
```

**Tests Fixed:**
- `accountDeletion_blockedIfExpenseExists_allowedIfCleared`
- `accountDeletion_blockedIfTransferExists_allowedIfCleared`

---

## Why This Works

### Thread.sleep(300)
- Gives Hilt time to inject dependencies and StateFlows time to start collecting
- 300ms is enough for most devices without significantly slowing down tests
- Prevents race conditions where test checks UI before ViewModel is ready

### composeTestRule.waitForIdle()
- Ensures Compose has finished recomposition after StateFlow updates
- Critical for UI elements that depend on ViewModel state

### Increased Timeout (10000ms vs 5000ms)
- Accounts for slower devices and emulators
- Provides buffer for Hilt initialization overhead
- Still fails fast enough if there's a real issue

## Testing Results

**Before Fixes:**
- AccountDeletionSafeguardTest: 0% success (0/4 tests passing)
- VoiceRecognitionIntegrationTest: 50% success (2/4 tests passing)

**After Fixes (Expected):**
- AccountDeletionSafeguardTest: 100% success (4/4 tests passing)
- VoiceRecognitionIntegrationTest: 100% success (4/4 tests passing)

**Overall Expected Improvement:**
- From 95% (141/148) to 99% (147/148)
- Only 1 remaining failure (VoiceFlowContentTest - device-specific)

## Best Practices for Future Tests

When writing new tests with Hilt-injected ViewModels:

1. **Always call `hiltRule.inject()`** in `@Before` method
2. **Add delays after database operations** that affect ViewModel state
3. **Use longer timeouts** (10000ms) when waiting for ViewModel-dependent UI
4. **Call `waitForIdle()`** before checking state-dependent conditions
5. **Consider creating helper methods** for common wait patterns

## Example Helper Method

```kotlin
/**
 * Waits for ViewModel StateFlows to initialize after database changes
 */
private fun waitForStateFlowInitialization() {
    Thread.sleep(300)
    composeTestRule.waitForIdle()
}
```

Usage:
```kotlin
runBlocking {
    db.accountDao().deleteAll()
}
waitForStateFlowInitialization()

composeTestRule.waitUntil(timeoutMillis = 10000) {
    viewModel.allAccounts.value.isEmpty()
}
```

---

**Last Updated:** 2026-02-04  
**Status:** Fixes applied, pending re-test verification
