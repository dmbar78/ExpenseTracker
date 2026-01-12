package com.example.expensetracker.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.expensetracker.data.*
import java.text.SimpleDateFormat
import java.util.*

// ==================== Filter Icon Button ====================

/**
 * Filter icon button displayed at bottom-left of HomeScreen.
 */
@Composable
fun FilterIconButton(
    hasActiveFilters: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = if (hasActiveFilters) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = "Filter",
            tint = if (hasActiveFilters) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== Main Filter Menu ====================

/**
 * Main filter popup menu with options: Time, Account, Transfer From/To, Category, Reset All.
 */
@Composable
fun FilterMainMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onTimeClick: () -> Unit,
    onAccountClick: () -> Unit,
    onTransferClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onResetAll: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Time") },
            onClick = {
                onDismiss()
                onTimeClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Expense/Income Account") },
            onClick = {
                onDismiss()
                onAccountClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Transfer From/To") },
            onClick = {
                onDismiss()
                onTransferClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Category") },
            onClick = {
                onDismiss()
                onCategoryClick()
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Reset All", color = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismiss()
                onResetAll()
            }
        )
    }
}

// ==================== Time Filter Menu ====================

/**
 * Time filter submenu with options: Day, Week, Month, Year, Period.
 */
@Composable
fun TimeFilterMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDayClick: () -> Unit,
    onWeekClick: () -> Unit,
    onMonthClick: () -> Unit,
    onYearClick: () -> Unit,
    onPeriodClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Day") },
            onClick = {
                onDismiss()
                onDayClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Week") },
            onClick = {
                onDismiss()
                onWeekClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Month") },
            onClick = {
                onDismiss()
                onMonthClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Year") },
            onClick = {
                onDismiss()
                onYearClick()
            }
        )
        DropdownMenuItem(
            text = { Text("Period") },
            onClick = {
                onDismiss()
                onPeriodClick()
            }
        )
    }
}

// ==================== Day Picker Dialog ====================

/**
 * Day picker dialog using Android DatePickerDialog.
 */
@Composable
fun DayPickerDialog(
    currentSelection: Long?,
    onConfirm: (Long) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    
    // Use current selection or today
    currentSelection?.let { calendar.timeInMillis = it }
    
    DisposableEffect(Unit) {
        val dialog = DatePickerDialog(
            context,
            null, // We handle confirmation via custom OK button
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        dialog.setButton(DatePickerDialog.BUTTON_POSITIVE, "OK") { _, _ ->
            // Read the selected date directly from the DatePicker widget
            val picker = dialog.datePicker
            val cal = Calendar.getInstance()
            cal.set(picker.year, picker.month, picker.dayOfMonth, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onConfirm(cal.timeInMillis)
        }
        dialog.setButton(DatePickerDialog.BUTTON_NEGATIVE, "Cancel") { _, _ ->
            onCancel()
        }
        dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Reset") { _, _ ->
            onReset()
        }
        
        dialog.setOnCancelListener { onCancel() }
        dialog.show()
        
        onDispose { dialog.dismiss() }
    }
}

// ==================== Week Picker Dialog ====================

/**
 * Week picker dialog - shows a simple dialog to pick a week.
 */
@Composable
fun WeekPickerDialog(
    currentSelection: Long?,
    onConfirm: (Long) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    val calendar = Calendar.getInstance()
    currentSelection?.let { calendar.timeInMillis = it } ?: run {
        calendar.timeInMillis = getWeekStartMillis(System.currentTimeMillis())
    }
    
    var selectedWeekStart by remember { mutableStateOf(calendar.timeInMillis) }
    
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Week",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Display selected week range
                val weekCal = Calendar.getInstance().apply { timeInMillis = selectedWeekStart }
                val startDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(weekCal.time)
                weekCal.add(Calendar.DAY_OF_MONTH, 6)
                val endDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(weekCal.time)
                
                Text(
                    text = "$startDate - $endDate",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Navigation buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedWeekStart }
                        cal.add(Calendar.WEEK_OF_YEAR, -1)
                        selectedWeekStart = cal.timeInMillis
                    }) {
                        Text("← Prev")
                    }
                    
                    OutlinedButton(onClick = {
                        selectedWeekStart = getWeekStartMillis(System.currentTimeMillis())
                    }) {
                        Text("Today")
                    }
                    
                    OutlinedButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedWeekStart }
                        cal.add(Calendar.WEEK_OF_YEAR, 1)
                        selectedWeekStart = cal.timeInMillis
                    }) {
                        Text("Next →")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onReset) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onConfirm(selectedWeekStart) }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

// ==================== Month Picker Dialog ====================

/**
 * Month picker dialog.
 */
@Composable
fun MonthPickerDialog(
    currentYear: Int?,
    currentMonth: Int?,
    onConfirm: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    val calendar = Calendar.getInstance()
    var selectedYear by remember { mutableStateOf(currentYear ?: calendar.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(currentMonth ?: calendar.get(Calendar.MONTH)) }
    
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Month",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Year selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(onClick = { selectedYear-- }) {
                        Text("◀", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = selectedYear.toString(),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { selectedYear++ }) {
                        Text("▶", style = MaterialTheme.typography.titleLarge)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Month grid (3 columns x 4 rows)
                Column {
                    for (row in 0..3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0..2) {
                                val monthIndex = row * 3 + col
                                val isSelected = monthIndex == selectedMonth
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(4.dp)
                                        .clickable { selectedMonth = monthIndex },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = months[monthIndex].take(3),
                                        modifier = Modifier.padding(12.dp),
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.onPrimary 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onReset) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onConfirm(selectedYear, selectedMonth) }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

// ==================== Year Picker Dialog ====================

/**
 * Year picker dialog.
 */
@Composable
fun YearPickerDialog(
    currentYear: Int?,
    onConfirm: (Int) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    val calendar = Calendar.getInstance()
    var selectedYear by remember { mutableStateOf(currentYear ?: calendar.get(Calendar.YEAR)) }
    
    // Generate years range (current year - 20 to current year + 5)
    val currentActualYear = calendar.get(Calendar.YEAR)
    val years = (currentActualYear - 20..currentActualYear + 5).toList()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = years.indexOf(selectedYear).coerceAtLeast(0)
    )
    
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Year",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Year list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.height(200.dp)
                ) {
                    items(years) { year ->
                        val isSelected = year == selectedYear
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedYear = year },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = year.toString(),
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                color = if (isSelected) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else 
                                    MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onReset) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { onConfirm(selectedYear) }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

// ==================== Period Picker Dialog ====================

/**
 * Period picker dialog for selecting a date range.
 */
@Composable
fun PeriodPickerDialog(
    currentStartMillis: Long?,
    currentEndMillis: Long?,
    onConfirm: (Long, Long) -> Unit,
    onAllTime: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    var startDate by remember { mutableStateOf(currentStartMillis) }
    var endDate by remember { mutableStateOf(currentEndMillis) }
    var allTimeChecked by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Period",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // All Time checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { allTimeChecked = !allTimeChecked },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allTimeChecked,
                        onCheckedChange = { allTimeChecked = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All Time (from earliest transaction to today)")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Date pickers (disabled if All Time is checked)
                if (!allTimeChecked) {
                    // Start date
                    OutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = startDate?.let { "From: ${dateFormat.format(Date(it))}" } 
                                ?: "Select Start Date"
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // End date
                    OutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = endDate?.let { "To: ${dateFormat.format(Date(it))}" } 
                                ?: "Select End Date"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onReset) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (allTimeChecked) {
                                onAllTime()
                            } else if (startDate != null && endDate != null) {
                                // Ensure start <= end
                                val (s, e) = if (startDate!! <= endDate!!) 
                                    startDate!! to endDate!! 
                                else 
                                    endDate!! to startDate!!
                                onConfirm(getDayStartMillis(s), getDayEndMillis(e))
                            } else if (startDate != null) {
                                // Single date = Day filter
                                onConfirm(getDayStartMillis(startDate!!), getDayEndMillis(startDate!!))
                            } else {
                                onCancel()
                            }
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
    
    // Date picker dialogs
    if (showStartPicker) {
        DatePickerDialogHelper(
            initialDate = startDate ?: System.currentTimeMillis(),
            onDateSelected = { 
                startDate = it
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }
    
    if (showEndPicker) {
        DatePickerDialogHelper(
            initialDate = endDate ?: System.currentTimeMillis(),
            onDateSelected = { 
                endDate = it
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

/**
 * Helper composable for Android DatePickerDialog.
 */
@Composable
private fun DatePickerDialogHelper(
    initialDate: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { timeInMillis = initialDate }
    
    DisposableEffect(Unit) {
        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                onDateSelected(cal.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        dialog.setOnCancelListener { onDismiss() }
        dialog.show()
        
        onDispose { dialog.dismiss() }
    }
}

// ==================== Account Filter Dialog ====================

/**
 * Account filter dialog with dropdown selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFilterDialog(
    title: String,
    accounts: List<Account>,
    currentSelection: String?,
    onConfirm: (String?) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedAccount by remember { mutableStateOf(currentSelection) }
    var expanded by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Account dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedAccount ?: "Select Account",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.currency})") },
                                onClick = {
                                    selectedAccount = account.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onReset) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (selectedAccount != null) {
                                onConfirm(selectedAccount)
                            } else {
                                onCancel()
                            }
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

// ==================== Transfer Filter Dialog ====================

/**
 * Transfer filter dialog with source and destination dropdowns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferFilterDialog(
    accounts: List<Account>,
    currentSourceAccount: String?,
    currentDestAccount: String?,
    onConfirm: (String?, String?) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedSource by remember { mutableStateOf(currentSourceAccount) }
    var selectedDest by remember { mutableStateOf(currentDestAccount) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var destExpanded by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Filter Transfers",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Source account dropdown
                Text(
                    text = "From Account",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                ExposedDropdownMenuBox(
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSource ?: "Any",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = sourceExpanded,
                        onDismissRequest = { sourceExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any") },
                            onClick = {
                                selectedSource = null
                                sourceExpanded = false
                            }
                        )
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.currency})") },
                                onClick = {
                                    selectedSource = account.name
                                    sourceExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Destination account dropdown
                Text(
                    text = "To Account",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                ExposedDropdownMenuBox(
                    expanded = destExpanded,
                    onExpandedChange = { destExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedDest ?: "Any",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = destExpanded,
                        onDismissRequest = { destExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any") },
                            onClick = {
                                selectedDest = null
                                destExpanded = false
                            }
                        )
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.currency})") },
                                onClick = {
                                    selectedDest = account.name
                                    destExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onReset) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (selectedSource != null || selectedDest != null) {
                                onConfirm(selectedSource, selectedDest)
                            } else {
                                onCancel()
                            }
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

// ==================== Category Filter Dialog ====================

/**
 * Category filter dialog with dropdown selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterDialog(
    categories: List<Category>,
    currentSelection: String?,
    onConfirm: (String?) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(currentSelection) }
    var expanded by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Filter by Category",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory ?: "Select Category",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onReset) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (selectedCategory != null) {
                                onConfirm(selectedCategory)
                            } else {
                                onCancel()
                            }
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

// ==================== Filter Chips Row ====================

/**
 * Row of filter chips showing active filters.
 */
@Composable
fun FilterChipsRow(
    filterState: FilterState,
    onTimeFilterClick: () -> Unit,
    onAccountFilterClick: () -> Unit,
    onCategoryFilterClick: () -> Unit,
    onTransferFilterClick: () -> Unit,
    onClearTimeFilter: () -> Unit,
    onClearAccountFilter: () -> Unit,
    onClearCategoryFilter: () -> Unit,
    onClearTransferFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!filterState.hasActiveFilters()) return
    
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Time filter chip
        if (filterState.hasTimeFilter()) {
            item {
                FilterChip(
                    label = filterState.timeFilter.toDisplayString(),
                    onClick = onTimeFilterClick,
                    onClear = onClearTimeFilter
                )
            }
        }
        
        // Account filter chip
        if (filterState.hasAccountFilter()) {
            item {
                FilterChip(
                    label = "Account: ${filterState.expenseIncomeAccount}",
                    onClick = onAccountFilterClick,
                    onClear = onClearAccountFilter
                )
            }
        }
        
        // Category filter chip
        if (filterState.hasCategoryFilter()) {
            item {
                FilterChip(
                    label = "Category: ${filterState.category}",
                    onClick = onCategoryFilterClick,
                    onClear = onClearCategoryFilter
                )
            }
        }
        
        // Transfer filter chips
        if (filterState.hasTransferFilter()) {
            item {
                val label = buildString {
                    append("Transfer: ")
                    filterState.transferSourceAccount?.let { append("From $it") }
                    if (filterState.transferSourceAccount != null && filterState.transferDestAccount != null) {
                        append(" → ")
                    }
                    filterState.transferDestAccount?.let { append("To $it") }
                }
                FilterChip(
                    label = label,
                    onClick = onTransferFilterClick,
                    onClear = onClearTransferFilter
                )
            }
        }
    }
}

/**
 * Individual filter chip with label, click action, and clear button.
 */
@Composable
private fun FilterChip(
    label: String,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear filter",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
