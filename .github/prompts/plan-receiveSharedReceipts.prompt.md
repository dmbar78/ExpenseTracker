## Plan: Receive Shared Receipts

Add ExpenseTracker as an Android `ACTION_SEND` target for one JPEG/JPG, PNG, or PDF; stage and OCR the shared file fully on-device; wait for unlock; return to Home and automatically open the existing plus menu; then prefill the selected Expense, Income, or Transfer form with the parsed amount/date. For Expense and Income only, a shared image is also initialized as the transaction photo and is copied permanently only when the transaction is saved.

**Steps**

### Phase 1: Share contract and import state
1. Add the bundled ML Kit Latin text-recognition dependency to `gradle/libs.versions.toml` and `app/build.gradle.kts`. Use Android `PdfRenderer` for PDFs, so no separate PDF dependency is needed. Keep OCR offline and immediately available.
2. Add an `ACTION_SEND`/`DEFAULT` intent filter to `MainActivity` for `image/jpeg`, the nonstandard-but-seen `image/jpg`, `image/png`, and `application/pdf`; retain the launcher filter and make `MainActivity` `singleTop` so warm shares arrive via `onNewIntent`. Do not add storage permissions: the sender's temporary URI read grant is sufficient and the app stages the stream immediately.
3. Introduce a small shared-import model/state machine under `com.example.expensetracker.sharedimport`: `Idle`, `Processing`, `Ready(parsedAmount?, parsedDateMillis, stagedImageUri?)`, and `Error(message)`. Store only primitive staged-path/MIME/parsed values in `SavedStateHandle` so rotation/unlock cannot lose the pending import, and assign each accepted intent an event identity so initial intents and `onNewIntent` are consumed exactly once.
4. Implement a Hilt-backed `SharedFileImportViewModel` and processor that validates `ACTION_SEND`, exactly one `EXTRA_STREAM`, and an allowed MIME/content signature; streams the granted URI into a uniquely named cache file without loading the whole file into memory; and exposes pending state plus explicit consume/cancel cleanup operations. A genuinely unreadable/unsupported stream becomes `Error`; a readable file with no confident OCR fields still becomes `Ready` with a null amount and today's date.

### Phase 2: Offline extraction and deterministic parsing
5. Add an injectable `ReceiptTextExtractor` abstraction with an ML Kit implementation. For JPEG/PNG, create the ML Kit `InputImage` from the staged file so EXIF orientation is honored. For PDF, open `PdfRenderer`, render pages sequentially to bounded-size bitmaps, OCR each page in order, recycle each bitmap immediately, close renderer/recognizer resources, and honor coroutine cancellation. This keeps memory bounded for multipage documents.
6. Add a pure `ReceiptTextParser` that returns independent optional amount/date results and is unit-testable without Android or ML Kit. Reuse `VoiceCommandParser.parseMoneyAmount` for separator normalization, but add receipt-specific candidate scoring: prioritize labels such as `grand total`, `total`, `amount paid`, and `balance due`; de-prioritize subtotal/tax/change; accept only a unique, sufficiently strong best amount. Parse ISO and textual dates first, then numeric dates according to `Locale.getDefault()` ordering; prioritize lines labeled date/transaction date and reject invalid calendar values. Fall back to null amount and a start-of-today timestamp when confidence is insufficient.
7. Bind the extractor/processor through the existing Hilt setup so processor tests can inject a fake extractor. Keep OCR and file I/O on background dispatchers and report failures as localized user-facing messages rather than exceptions escaping to Compose.

### Phase 3: Unlock-safe Home/menu orchestration
8. Have `MainActivity.onCreate` submit an initial share intent once and `onNewIntent` submit warm shares. Hoist the plus-menu expanded state in `MainContent`, observe shared-import state, and gate presentation on `isAppLocked == false`; processing may stage/OCR while locked, but navigation/menu effects must never execute behind the PIN overlay.
9. Once unlocked and import processing is ready, navigate/pop back to the single Home destination, then expand the existing global plus menu. Preserve the existing normal plus-button behavior. If the auto-opened menu is dismissed, treat that as cancel and clear/delete the staged import; selecting Expense, Income, or Transfer consumes it and builds the corresponding existing create route.
10. For each selection, append the parsed amount only when non-null and always append the parsed/fallback date using URI-safe route construction. Expense and Income additionally receive the staged image as a FileProvider `content://` initial-photo argument for JPEG/PNG; Transfer never receives a photo. PDFs only provide parsed fields. Delete staged PDFs and transfer-only staged images after routing; let cache own abandoned temporary files and clean explicit cancellation immediately.
11. Surface processing errors through the existing snackbar/error pattern, return/stay on Home, and leave the ordinary plus menu usable. Add stable test tags only for any newly introduced loading/error UI; do not change existing `GLOBAL_CREATE_*` tags.

### Phase 4: Form prefill and photo lifecycle
12. Extend the `editExpense` navigation contract with a nullable, URI-encoded `initialPhotoUri` argument and pass it into `EditExpenseScreen`. Initialize new Expense/Income photo state from that argument, including the existing `LaunchedEffect(expense)` reset branch, while keeping edit/copy behavior authoritative and leaving the amount focus effects untouched.
13. Reuse existing `amount`/`expenseDateMillis` and `amount`/`transferDateMillis` arguments for all form prefills. Do not add OCR text or complex objects to routes. On Expense/Income save, the existing `saveImageToInternalStorage` path sees the FileProvider `content://` URI and copies it into `filesDir/expense_images`; canceling the form leaves only an OS-cleanable cache file and creates no database/photo record.
14. Add localized strings for unsupported/unreadable import and processing failure states, plus any concise processing status needed while Home waits for OCR. Do not expose raw OCR text or upload receipt content.

### Phase 5: Tests and regression coverage
15. Add pure unit tests for `ReceiptTextParser`: US/EU amounts, total-vs-subtotal/tax/change ranking, duplicate/ambiguous candidates, ISO/textual dates, numeric dates under representative day-first/month-first locales, invalid dates, no amount, no date, and today's fallback. Extend `VoiceCommandParserTest` only if a reusable money-normalization defect is found rather than duplicating parser coverage.
16. Add processor/ViewModel coroutine tests with a fake `ReceiptTextExtractor` and temporary streams for allowed MIME validation, stream staging, one-intent deduplication, error-to-ready fallback distinctions, cancellation cleanup, and state restoration primitives.
17. Add Hilt Compose instrumentation coverage modeled on `PlusMenuExtendedTest`: cold `ACTION_SEND` and warm `onNewIntent`; locked flow does not navigate until successful unlock; Home plus menu auto-opens; each transaction choice receives exact editable amount/date semantics; image Expense/Income shows the Photo section populated; Transfer and PDF do not attach a photo; dismiss clears the pending import; malformed/unreadable share shows an error without breaking the normal plus menu. Inject a fake extractor for deterministic flow tests and keep one focused real-device OCR smoke test fixture for image/PDF rendering integration.
18. Run `./gradlew testDevDebugUnitTest`, then `./gradlew connectedUitestDebugAndroidTest` on an API 34+ emulator/device. Manually verify from Android Photos/Files/PDF viewers that ExpenseTracker appears in the share sheet for `.jpg`, `.jpeg`, `.png`, and `.pdf`; verify cold/warm locked and unlocked launches, rotation while processing/unlocking, locale-sensitive dates, form cancellation, and successful photo persistence after save.

**Relevant files**
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/gradle/libs.versions.toml` — declare bundled ML Kit text recognition.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/build.gradle.kts` — consume OCR dependency and any coroutine Task bridge chosen by implementation.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/AndroidManifest.xml` — share-target MIME filters and `singleTop` activity delivery.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/MainActivity.kt` — initial/new intent intake, lock gate, Home routing, and automatic existing plus-menu expansion.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/NavGraph.kt` — reuse amount/date arguments and add nullable initial photo URI for Expense/Income.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/ui/screens/EditExpenseScreen.kt` — initialize new-form photo state without disturbing edit/copy or focus behavior.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/util/VoiceCommandParser.kt` — reuse public money normalization only.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/sharedimport/SharedFileImport.kt` — new pending import models/state.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/sharedimport/SharedFileImportViewModel.kt` — new lifecycle, deduplication, and cleanup owner.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/sharedimport/SharedFileImportProcessor.kt` — new URI validation/staging and extraction orchestration.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/sharedimport/ReceiptTextExtractor.kt` — new injectable ML Kit/image/PDF extraction boundary.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/sharedimport/ReceiptTextParser.kt` — new pure amount/date candidate parser.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/java/com/example/expensetracker/di/SharedImportModule.kt` — new Hilt bindings for the extractor/processor boundary if constructor injection alone is insufficient.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/main/res/values/strings.xml` — localized import status/errors.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/test/java/com/example/expensetracker/sharedimport/ReceiptTextParserTest.kt` — deterministic parser coverage.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/test/java/com/example/expensetracker/sharedimport/SharedFileImportViewModelTest.kt` — state, deduplication, fallback, and cleanup coverage.
- `c:/Users/TAABADM1/AndroidStudioProjects/ExpenseTracker/app/src/androidTest/java/com/example/expensetracker/ui/SharedFileImportFlowTest.kt` — lock/navigation/menu/form/photo integration coverage, following `PlusMenuExtendedTest` conventions.

**Verification**
1. Unit suite passes with deterministic locale/time inputs and no network/model dependency.
2. Instrumentation suite passes on the isolated `uitestDebug` flavor with a fake OCR extractor, including exact `SemanticsProperties.EditableText` assertions for amount fields to protect the known focus regression.
3. Focused real OCR fixtures prove JPEG orientation, PNG input, and multipage PDF rendering/text concatenation on API 34+.
4. Manual share-sheet tests prove resolver visibility and URI-grant behavior from external apps across cold launch, warm launch, lock/unlock, rotation, save, cancel, and malformed input.

**Decisions**
- Support exactly one shared file via `ACTION_SEND`; `ACTION_SEND_MULTIPLE` is excluded.
- OCR is bundled, offline ML Kit Latin text recognition; no Gemini/network upload and no API key requirement.
- Ambiguous/missing amount remains blank; ambiguous/missing date defaults to today. No confirmation dialog and no rejection solely for weak OCR.
- Ambiguous numeric dates follow the device locale; ISO/textual dates remain unambiguous.
- Shared images auto-attach only to Expense/Income; Transfer and PDF never populate Photo.
- Existing account/category defaults and validation are unchanged; OCR does not infer transaction type, account, category, currency, or transfer endpoints.
- No database migration, broad storage permission, multi-file batching, OCR-text display/storage, or backup/export changes are included.