package com.example.squintboyadvance.presentation.screens.emulator

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.squintboyadvance.shared.model.ButtonId
import com.example.squintboyadvance.shared.model.ButtonPosition
import com.example.squintboyadvance.shared.model.ControllerLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import androidx.wear.compose.material.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun TouchOverlay(
    layout: ControllerLayout = ControllerLayout(),
    onButtonPress: (ButtonId) -> Unit,
    onButtonRelease: (ButtonId) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val alpha = layout.overlayAlpha
    val useHaptic = layout.hapticFeedback

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val parentWidth = maxWidth
        val parentHeight = maxHeight

        layout.buttons.filter { it.visible }.forEach { button ->
            val btnSize = parentWidth * button.sizePercent
            val offsetX = parentWidth * button.xPercent - btnSize / 2
            val offsetY = parentHeight * button.yPercent - btnSize / 2

            TouchButton(
                button = button,
                size = btnSize,
                offsetX = offsetX,
                offsetY = offsetY,
                alpha = alpha,
                onPress = {
                    if (useHaptic) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onButtonPress(button.buttonId)
                },
                onRelease = {
                    onButtonRelease(button.buttonId)
                }
            )
        }
    }
}

@Composable
private fun TouchButton(
    button: ButtonPosition,
    size: Dp,
    offsetX: Dp,
    offsetY: Dp,
    alpha: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val label = remember(button.buttonId) {
        when (button.buttonId) {
            ButtonId.A -> "A"
            ButtonId.B -> "B"
            ButtonId.START -> "ST"
            ButtonId.SELECT -> "SE"
            ButtonId.DPAD_UP -> "\u25B2"
            ButtonId.DPAD_DOWN -> "\u25BC"
            ButtonId.DPAD_LEFT -> "\u25C0"
            ButtonId.DPAD_RIGHT -> "\u25B6"
            ButtonId.L -> "L"
            ButtonId.R -> "R"
        }
    }

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = alpha * 0.3f))
            .pointerInput(button.buttonId) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = alpha * 0.8f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}
