# Hilt Migration: UI Test Updates Required

After migrating to Hilt dependency injection, all UI tests that use `MainActivity` need to be updated with Hilt support.

## Required Changes for Each Test File

For each test file that uses `createAndroidComposeRule<MainActivity>()`, add the following:

### 1. Add Hilt imports
```kotlin
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
```

### 2. Add @HiltAndroidTest annotation to the test class
```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class YourTestClass {
```

### 3. Add HiltAndroidRule as the first rule (order = 0)
```kotlin
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
```

### 4. Update existing rules to have explicit order
```kotlin
    @get:Rule(order = 1)  // or 2, 3, etc.
    val permissionRule: GrantPermissionRule = ...
    
    @get:Rule(order = 2)  // increment accordingly
    val composeTestRule = createAndroidComposeRule<MainActivity>()
```

### 5. Add @Before method to inject dependencies
```kotlin
    @Before
    fun init() {
        hiltRule.inject()
    }
```

## CRITICAL: HiltTestRunner Setup

**Before running any tests, you MUST set up the HiltTestRunner:**

### 1. Create HiltTestRunner.kt

Create `app/src/androidTest/java/com/example/expensetracker/HiltTestRunner.kt`:

```kotlin
package com.example.expensetracker

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * A custom runner to set up the instrumented application class for tests.
 * This is required for Hilt to work in instrumented tests.
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
```

### 2. Update build.gradle.kts

In `app/build.gradle.kts`, change the test instrumentation runner:

```kotlin
defaultConfig {
    // ... other config
    testInstrumentationRunner = "com.example.expensetracker.HiltTestRunner"
}
```

**Without this setup, ALL tests using MainActivity will fail immediately during initialization.**

## Files That Need Updates

The following UI test files need these changes:

1. ✅ AccountDeletionSafeguardTest.kt (DONE)
2. ✅ AccountNavigationTest.kt (DONE)
3. ⏭️ CategorySearchTest.kt (SKIPPED - uses ComponentActivity, not MainActivity)
4. ✅ CurrencyUiTest.kt (DONE)
5. ✅ CurrencyValidationTest.kt (DONE)
6. ⏭️ DebtControlTest.kt (SKIPPED - uses ComponentActivity, not MainActivity)
7. ✅ InlineAccountCreationTest.kt (DONE)
8. ⏭️ KeywordSearchTest.kt (SKIPPED - uses ComponentActivity, not MainActivity)
9. ✅ PinAuthenticationTest.kt (DONE)
10. ✅ VoiceFlowContentTest.kt / PlusMenuWiringTest (DONE)
11. ✅ VoiceRecognitionIntegrationTest.kt (DONE)

**Note:** Tests using `ComponentActivity` instead of `MainActivity` don't need Hilt support because they render composables in isolation without dependency injection.

## Example: Complete Test Class Header

```kotlin
package com.example.expensetracker.ui

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.expensetracker.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class YourTestClass {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    // ... your tests
}
```

## Why This Is Necessary

- `MainActivity` now uses `@AndroidEntryPoint` and expects Hilt to provide `ExpenseViewModel`
- Without `@HiltAndroidTest` and `HiltAndroidRule`, Hilt won't initialize properly in tests
- The `order` parameter ensures rules execute in the correct sequence (Hilt first)
- `hiltRule.inject()` must be called before the activity starts

## Testing

After making these changes, run:
```bash
./gradlew connectedAndroidTest
```

Or for a specific test:
```bash
./gradlew connectedAndroidTest --tests "com.example.expensetracker.ui.AccountDeletionSafeguardTest"
```
