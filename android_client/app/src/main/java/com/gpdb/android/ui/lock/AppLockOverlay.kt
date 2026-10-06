package com.gpdb.android.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.gpdb.android.util.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppLockOverlay(
    correctPin: String,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shakeError by remember { mutableStateOf(false) }

    val canUseBiometric = remember {
        biometricEnabled && BiometricHelper.isBiometricAvailable(context)
    }

    val authTitleText = com.gpdb.android.util.I18n.string("lock.authTitle")
    val authSubtitleText = com.gpdb.android.util.I18n.string("lock.authSubtitle")
    val enterPinText = com.gpdb.android.util.I18n.string("lock.enterPin")
    val cancelText = com.gpdb.android.util.I18n.string("common.cancel")
    val pinErrorText = com.gpdb.android.util.I18n.string("lock.pinError")

    // Auto trigger biometric on start if available
    LaunchedEffect(Unit) {
        if (canUseBiometric && activity != null) {
            delay(300)
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = authTitleText,
                subtitle = authSubtitleText,
                negativeButtonText = if (correctPin.isNotBlank()) enterPinText else cancelText,
                onSuccess = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlocked()
                },
                onError = { /* Let user use keypad */ }
            )
        }
    }

    fun submitPin(pin: String) {
        if (correctPin.isBlank() || pin == correctPin) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUnlocked()
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            errorMessage = pinErrorText
            shakeError = true
            scope.launch {
                delay(600)
                enteredPin = ""
                shakeError = false
            }
        }
    }

    val maxPinLength = if (correctPin.length in 4..6) correctPin.length else 4

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = com.gpdb.android.util.I18n.string("lock.privacyLockTitle"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = errorMessage ?: com.gpdb.android.util.I18n.string("lock.enterPasswordPrompt"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(maxPinLength) { index ->
                        val filled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            // Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            when (key) {
                                "BIO" -> {
                                    if (canUseBiometric && activity != null) {
                                        IconButton(
                                            onClick = {
                                                BiometricHelper.showBiometricPrompt(
                                                    activity = activity,
                                                    title = authTitleText,
                                                    subtitle = authSubtitleText,
                                                    negativeButtonText = if (correctPin.isNotBlank()) enterPinText else cancelText,
                                                    onSuccess = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onUnlocked()
                                                    }
                                                )
                                            },
                                            modifier = Modifier.size(72.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = com.gpdb.android.util.I18n.string("lock.biometricUnlock"),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(72.dp))
                                    }
                                }

                                "DEL" -> {
                                    IconButton(
                                        onClick = {
                                            if (enteredPin.isNotEmpty()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                enteredPin = enteredPin.dropLast(1)
                                                errorMessage = null
                                            }
                                        },
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = com.gpdb.android.util.I18n.string("lock.delete"),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                            .clickable {
                                                if (enteredPin.length < maxPinLength) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    val newPin = enteredPin + key
                                                    enteredPin = newPin
                                                    errorMessage = null
                                                    if (newPin.length == maxPinLength) {
                                                        submitPin(newPin)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
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
