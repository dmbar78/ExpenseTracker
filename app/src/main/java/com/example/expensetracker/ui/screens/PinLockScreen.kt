package com.example.expensetracker.ui.screens

import android.widget.Toast
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.expensetracker.data.SecurityManager
import com.example.expensetracker.ui.TestTags
import kotlinx.coroutines.delay

enum class PinScreenMode {
    Create, Confirm, Unlock, Change, Remove
}

@Composable
fun PinLockScreen(
    mode: PinScreenMode,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    // Internal state for Create/Confirm flow
    var internalMode by remember(mode) { mutableStateOf(mode) }
    
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
    var lockoutTime by remember { mutableStateOf(0L) }

    // Initial Lockout Check
    LaunchedEffect(Unit) {
         if (SecurityManager.isLockedOut(context)) {
             lockoutTime = SecurityManager.getRemainingLockoutTime(context)
         }
    }
    
    // Countdown Timer
    LaunchedEffect(lockoutTime) {
         if (lockoutTime > 0) {
             while(lockoutTime > 0) {
                 delay(1000)
                 lockoutTime = SecurityManager.getRemainingLockoutTime(context)
             }
         }
    }

    LaunchedEffect(internalMode, lockoutTime) {
        if (lockoutTime > 0) {
            instruction = "Too many attempts. Try again in ${lockoutTime/1000}s"
            isError = true
            pin = "" // Clear PIN on lockout
        } else {
            isError = false // Reset error state when lockout expires
            instruction = when (internalMode) {
                PinScreenMode.Create -> "Create a 6-digit PIN"
                PinScreenMode.Confirm -> "Confirm your PIN"
                PinScreenMode.Unlock -> "Enter PIN"
                PinScreenMode.Remove -> "Enter PIN to Remove"
                PinScreenMode.Change -> "Enter old PIN"
            }
        }
    }
    
    // Auto-trigger biometric on Unlock start (if not locked out)
    LaunchedEffect(internalMode, lockoutTime) {
        if (lockoutTime <= 0 && internalMode == PinScreenMode.Unlock && SecurityManager.isBiometricEnabled(context) && activity != null) {
            val executor = ContextCompat.getMainExecutor(context)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                     super.onAuthenticationSucceeded(result)
                     if (SecurityManager.processBiometricResult(context, result.cryptoObject!!)) {
                         onSuccess()
                     } else {
                         Toast.makeText(context, "Biometric auth failed to unlock key", Toast.LENGTH_SHORT).show()
                     }
                }
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock App")
                .setNegativeButtonText("Use PIN")
                .build()
                
            val crypto = SecurityManager.getBiometricCryptoObject(context)
            if (crypto != null) {
                try {
                   prompt.authenticate(promptInfo, crypto)
                } catch(e: Exception) {
                    // Ignore
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTags.PIN_LOCK_ROOT)
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = instruction,
            style = MaterialTheme.typography.headlineSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(TestTags.PIN_LOCK_INSTRUCTION)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(64.dp))
        
        // Keypad
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("Biometric", "0", "Backspace")
        )
        
        // Handle Logic Inline
        val onKeyClick: (String) -> Unit = { key ->
            if (lockoutTime <= 0) { // Block input during lockout
                when (key) {
                    "Backspace" -> {
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    }
                    "Biometric" -> {
                        // Trigger bio manually
                        if (internalMode == PinScreenMode.Unlock && SecurityManager.isBiometricEnabled(context) && activity != null) {
                             val executor = ContextCompat.getMainExecutor(context)
                            val crypto = SecurityManager.getBiometricCryptoObject(context)
                            if (crypto != null) {
                                val bioPrompt = BiometricPrompt(activity, executor, object: BiometricPrompt.AuthenticationCallback(){
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        if (SecurityManager.processBiometricResult(context, result.cryptoObject!!)) {
                                            onSuccess()
                                        }
                                    }
                                })
                                val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock").setNegativeButtonText("Cancel").build()
                                bioPrompt.authenticate(info, crypto)
                            }
                        }
                    }
                    else -> {
                        if (pin.length < 6) {
                            pin += key
                            if (pin.length == 6) {
                                // Logic
                                when (internalMode) {
                                    PinScreenMode.Create -> {
                                        confirmPin = pin
                                        internalMode = PinScreenMode.Confirm
                                        pin = ""
                                    }
                                    PinScreenMode.Confirm -> {
                                        if (pin == confirmPin) {
                                            SecurityManager.setPin(context, pin)
                                            onSuccess()
                                        } else {
                                            pin = ""
                                            isError = true
                                            instruction = "PINs do not match"
                                        }
                                    }
                                    PinScreenMode.Unlock, PinScreenMode.Remove -> {
                                        if (SecurityManager.verifyPin(context, pin)) {
                                            onSuccess()
                                        } else {
                                            pin = ""
                                            isError = true
                                            // Check Lockout immediately
                                            if (SecurityManager.isLockedOut(context)) {
                                                lockoutTime = SecurityManager.getRemainingLockoutTime(context)
                                            } else {
                                                instruction = "Incorrect PIN"
                                            }
                                        }
                                    }
                                    PinScreenMode.Change -> {
                                         if (SecurityManager.verifyPin(context, pin)) {
                                            // Important: Clear the PIN from state so that when the
                                            // parent re-renders PinLockScreen (or we navigate),
                                            // the field is empty for the next step.
                                            pin = "" 
                                            onSuccess()
                                        } else {
                                            pin = ""
                                            isError = true
                                            if (SecurityManager.isLockedOut(context)) {
                                                lockoutTime = SecurityManager.getRemainingLockoutTime(context)
                                            } else {
                                                instruction = "Incorrect PIN"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    PinKey(
                        key = key,
                        onClick = { onKeyClick(key) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag(TestTags.PIN_LOCK_CANCEL)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun PinKey(key: String, onClick: () -> Unit) {
    val tag = when(key) {
        "Backspace" -> TestTags.PIN_KEY_BACKSPACE
        "Biometric" -> TestTags.PIN_KEY_BIOMETRIC
        else -> TestTags.PIN_KEY_PREFIX + key
    }
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(tag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            "Backspace" -> Icon(Icons.Default.Backspace, contentDescription = "Backspace", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            "Biometric" -> Icon(Icons.Default.Fingerprint, contentDescription = "Biometric", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text(text = key, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
