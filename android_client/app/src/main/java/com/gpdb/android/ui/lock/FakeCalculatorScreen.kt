package com.gpdb.android.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

@Composable
fun FakeCalculatorScreen(
    unlockPin: String = "",
    onUnlock: () -> Unit
) {
    var displayText by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var resetOnNextDigit by remember { mutableStateOf(false) }

    // Secret entry tracking
    var enteredKeySequence by remember { mutableStateOf("") }
    var titleTapCount by remember { mutableStateOf(0) }

    val format = remember { DecimalFormat("#,###.########") }
    val calcErrorText = com.gpdb.android.util.I18n.string("lock.fakeCalculatorError")

    fun checkSecretUnlock() {
        val targetPin = if (unlockPin.isNotBlank()) unlockPin else "1234"
        if (enteredKeySequence.endsWith(targetPin)) {
            onUnlock()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF17171C))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Invisible secret tap target on top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            titleTapCount++
                            if (titleTapCount >= 4) {
                                onUnlock()
                            }
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = com.gpdb.android.util.I18n.string("lock.fakeCalculator"),
                    color = Color.Gray.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (pendingOp != null && operand1 != null) {
                    Text(
                        text = "${format.format(operand1)} $pendingOp",
                        color = Color.Gray.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            }

            // Main Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                val textSize = when {
                    displayText.length > 12 -> 36.sp
                    displayText.length > 8 -> 48.sp
                    else -> 64.sp
                }
                Text(
                    text = displayText,
                    color = Color.White,
                    fontSize = textSize,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }

            // Keypad Grid
            val buttons = listOf(
                listOf("C", "±", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            )

            buttons.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { label ->
                        val isZero = label == "0" && row.size == 3
                        val weight = if (isZero) 2.2f else 1f

                        val isOperation = label in listOf("÷", "×", "-", "+", "=")
                        val isSpecial = label in listOf("C", "±", "%")

                        val bgColor = when {
                            isOperation -> Color(0xFF4B5EFC)
                            isSpecial -> Color(0xFF4E505F)
                            else -> Color(0xFF2E2F3E)
                        }

                        val textColor = Color.White

                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(72.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(bgColor)
                                .pointerInput(label) {
                                    detectTapGestures(
                                        onTap = {
                                            when (label) {
                                                in "0".."9" -> {
                                                    enteredKeySequence += label
                                                    if (resetOnNextDigit || displayText == "0") {
                                                        displayText = label
                                                        resetOnNextDigit = false
                                                    } else {
                                                        if (displayText.length < 15) {
                                                            displayText += label
                                                        }
                                                    }
                                                }

                                                "." -> {
                                                    if (!displayText.contains(".")) {
                                                        displayText += "."
                                                    }
                                                }

                                                "C" -> {
                                                    displayText = "0"
                                                    operand1 = null
                                                    pendingOp = null
                                                    resetOnNextDigit = false
                                                }

                                                "±" -> {
                                                    if (displayText != "0") {
                                                        displayText = if (displayText.startsWith("-")) {
                                                            displayText.substring(1)
                                                        } else {
                                                            "-$displayText"
                                                        }
                                                    }
                                                }

                                                "%" -> {
                                                    val v = displayText.toDoubleOrNull() ?: 0.0
                                                    displayText = (v / 100.0).toString()
                                                }

                                                "÷", "×", "-", "+" -> {
                                                    operand1 = displayText.toDoubleOrNull()
                                                    pendingOp = label
                                                    resetOnNextDigit = true
                                                }

                                                "=" -> {
                                                    checkSecretUnlock()
                                                    val op1 = operand1
                                                    val op = pendingOp
                                                    val op2 = displayText.toDoubleOrNull()
                                                    if (op1 != null && op != null && op2 != null) {
                                                        val res = when (op) {
                                                            "+" -> op1 + op2
                                                            "-" -> op1 - op2
                                                            "×" -> op1 * op2
                                                            "÷" -> if (op2 != 0.0) op1 / op2 else Double.NaN
                                                            else -> op2
                                                        }
                                                        displayText = if (res.isNaN()) calcErrorText else format.format(res)
                                                        operand1 = null
                                                        pendingOp = null
                                                        resetOnNextDigit = true
                                                    }
                                                }
                                            }
                                        },
                                        onLongPress = {
                                            // 长按 C 键 1.5 秒直接密道脱离计算器
                                            if (label == "C") {
                                                onUnlock()
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = if (label.length > 1) 22.sp else 28.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
