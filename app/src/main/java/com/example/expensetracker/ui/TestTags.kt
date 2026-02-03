package com.example.expensetracker.ui

/**
 * Stable testTag strings for Compose UI tests.
 * These are treated as a public test API - keep them stable.
 */
object TestTags {
    // EditExpenseScreen
    const val EDIT_EXPENSE_ROOT = "editExpense_root"
    const val EDIT_EXPENSE_DATE_FIELD = "editExpense_dateField"
    const val EDIT_EXPENSE_ACCOUNT_DROPDOWN = "editExpense_accountDropdown"
    const val EDIT_EXPENSE_ACCOUNT_VALUE = "editExpense_accountValue"
    const val EDIT_EXPENSE_AMOUNT_FIELD = "editExpense_amountField"
    const val EDIT_EXPENSE_CATEGORY_DROPDOWN = "editExpense_categoryDropdown"
    const val EDIT_EXPENSE_CATEGORY_VALUE = "editExpense_categoryValue"
    const val EDIT_EXPENSE_CATEGORY_CREATE_NEW = "editExpense_categoryCreateNew"
    const val EDIT_EXPENSE_ACCOUNT_CREATE_NEW = "editExpense_accountCreateNew"
    const val EDIT_EXPENSE_CURRENCY_VALUE = "editExpense_currencyValue"
    const val EDIT_EXPENSE_COMMENT_FIELD = "editExpense_commentField"
    const val EDIT_EXPENSE_SAVE = "editExpense_save"
    const val EDIT_EXPENSE_DELETE = "editExpense_delete"
    const val EDIT_EXPENSE_DELETE_CONFIRM = "editExpense_deleteConfirm"
    const val EDIT_EXPENSE_DELETE_DISMISS = "editExpense_deleteDismiss"
    const val EDIT_EXPENSE_ERROR_ACCOUNT_NOT_FOUND = "editExpense_error_accountNotFound"
    const val EDIT_EXPENSE_ERROR_CATEGORY_NOT_FOUND = "editExpense_error_categoryNotFound"
    const val EDIT_EXPENSE_ERROR_AMOUNT = "editExpense_error_amount"
    const val COPY_BUTTON = "copyButton"

    // AddCategoryScreen
    const val ADD_CATEGORY_ROOT = "addCategory_root"
    const val ADD_CATEGORY_NAME_FIELD = "addCategory_nameField"
    const val ADD_CATEGORY_SAVE = "addCategory_save"

    // AddAccountScreen
    const val ADD_ACCOUNT_ROOT = "addAccount_root"
    const val ADD_ACCOUNT_NAME_FIELD = "addAccount_nameField"
    const val ADD_ACCOUNT_BALANCE_FIELD = "addAccount_balanceField"
    const val ADD_ACCOUNT_CURRENCY_DROPDOWN = "addAccount_currencyDropdown"
    const val ADD_ACCOUNT_CURRENCY_VALUE = "addAccount_currencyValue"
    const val ADD_ACCOUNT_SAVE = "addAccount_save"

    // AddCurrencyScreen
    const val ADD_CURRENCY_ROOT = "addCurrency_root"
    const val ADD_CURRENCY_CODE_DROPDOWN = "addCurrency_codeDropdown"
    const val ADD_CURRENCY_CODE_FIELD = "addCurrency_codeField"
    const val ADD_CURRENCY_NAME_DROPDOWN = "addCurrency_nameDropdown"
    const val ADD_CURRENCY_NAME_FIELD = "addCurrency_nameField"
    const val ADD_CURRENCY_SAVE = "addCurrency_save"

    // EditTransferScreen
    const val EDIT_TRANSFER_ROOT = "editTransfer_root"
    const val EDIT_TRANSFER_DATE_FIELD = "editTransfer_dateField"
    const val EDIT_TRANSFER_SOURCE_DROPDOWN = "editTransfer_sourceDropdown"
    const val EDIT_TRANSFER_SOURCE_VALUE = "editTransfer_sourceValue"
    const val EDIT_TRANSFER_DESTINATION_DROPDOWN = "editTransfer_destinationDropdown"
    const val EDIT_TRANSFER_DESTINATION_VALUE = "editTransfer_destinationValue"
    const val EDIT_TRANSFER_AMOUNT_FIELD = "editTransfer_amountField"
    const val EDIT_TRANSFER_DEST_AMOUNT_FIELD = "editTransfer_destAmountField"
    const val EDIT_TRANSFER_CURRENCY_VALUE = "editTransfer_currencyValue"
    const val EDIT_TRANSFER_COMMENT_FIELD = "editTransfer_commentField"
    const val EDIT_TRANSFER_SAVE = "editTransfer_save"
    const val EDIT_TRANSFER_DELETE = "editTransfer_delete"
    const val EDIT_TRANSFER_DELETE_CONFIRM = "editTransfer_deleteConfirm"
    const val EDIT_TRANSFER_DELETE_DISMISS = "editTransfer_deleteDismiss"
    const val EDIT_TRANSFER_ERROR_SOURCE_NOT_FOUND = "editTransfer_error_sourceNotFound"
    const val EDIT_TRANSFER_ERROR_DEST_NOT_FOUND = "editTransfer_error_destNotFound"
    const val EDIT_TRANSFER_SOURCE_CREATE_NEW = "editTransfer_sourceCreateNew"
    const val EDIT_TRANSFER_DEST_CREATE_NEW = "editTransfer_destCreateNew"

    // Keyword UI
    const val EDIT_EXPENSE_KEYWORD_DROPDOWN = "editExpense_keywordDropdown"
    const val EDIT_EXPENSE_KEYWORD_SEARCH = "editExpense_keywordSearch"
    const val EDIT_EXPENSE_KEYWORD_CREATE_NEW = "editExpense_keywordCreateNew"
    const val EDIT_EXPENSE_KEYWORD_NEW_NAME = "editExpense_keywordNewName"
    const val EDIT_EXPENSE_KEYWORD_CREATE_CONFIRM = "editExpense_keywordCreateConfirm"
    const val EDIT_EXPENSE_KEYWORD_CREATE_DISMISS = "editExpense_keywordCreateDismiss"

    // VoiceRecognitionDialogs
    const val VOICE_DIALOG_RECOGNITION_FAILED = "voiceDialog_recognitionFailed"
    const val VOICE_DIALOG_SAME_ACCOUNT = "voiceDialog_sameAccount"
    const val VOICE_DIALOG_SUCCESS = "voiceDialog_success"

    // Global Create Menu (+ button)
    const val GLOBAL_CREATE_MENU = "globalCreate_menu"
    const val GLOBAL_CREATE_BUTTON = "globalCreate_button"
    const val GLOBAL_CREATE_EXPENSE = "globalCreate_expense"
    const val GLOBAL_CREATE_INCOME = "globalCreate_income"
    const val GLOBAL_CREATE_TRANSFER = "globalCreate_transfer"

    // Dropdown item prefix (append account/category id or normalized name)
    const val ACCOUNT_OPTION_PREFIX = "accountOption_"
    const val CATEGORY_OPTION_PREFIX = "categoryOption_"
    const val CURRENCY_OPTION_PREFIX = "currencyOption_"
    const val KEYWORD_OPTION_PREFIX = "keywordOption_"

    // PinLockScreen
    const val PIN_LOCK_ROOT = "pinLock_root"
    const val PIN_LOCK_INSTRUCTION = "pinLock_instruction"
    const val PIN_KEY_PREFIX = "pinKey_" // pinKey_1, pinKey_0, etc.
    const val PIN_KEY_BACKSPACE = "pinKey_Backspace"
    const val PIN_KEY_BIOMETRIC = "pinKey_Biometric"
    const val PIN_LOCK_CANCEL = "pinLock_cancel"
    
    // Settings Security
    const val SETTINGS_ADD_PIN = "settings_addPin"
    const val SETTINGS_CHANGE_PIN = "settings_changePin"
    const val SETTINGS_REMOVE_PIN = "settings_removePin"
    const val SETTINGS_BIOMETRIC_SWITCH = "settings_biometricSwitch"
}
