// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uncoalesced.impart.presentation.theme.ImpartPanicDark
import com.uncoalesced.impart.presentation.theme.ImpartTextPrimary

private const val DEFAULT_HOLD_DURATION_MS = 1500

/**
 * Press-and-hold confirmation control. Fires only after the hold threshold elapses;
 * releasing early cancels and resets the fill. Used for every irreversible panic dispatch.
 */
@Composable
fun HoldToFireButton(
    label: String,
    containerColor: Color,
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    holdDurationMs: Int = DEFAULT_HOLD_DURATION_MS
) {
    var isHeld by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val rejectNotifier = rememberObscuredTouchNotifier()

    LaunchedEffect(isHeld) {
        if (!isHeld) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(holdDurationMs, easing = LinearEasing))
        isHeld = false
        onFire()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .rejectObscuredTouches(rejectNotifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHeld = true
                        tryAwaitRelease()
                        isHeld = false
                    }
                )
            }
            .semantics { contentDescription = "$label. Press and hold to confirm." },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(ImpartTextPrimary.copy(alpha = 0.25f))
        )
        Text(
            text = if (isHeld) "KEEP HOLDING TO CONFIRM" else label,
            color = ImpartTextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview
@Composable
private fun HoldToFireButtonPreview() {
    HoldToFireButton(
        label = "BROADCAST PANIC (ALL NODES)",
        containerColor = ImpartPanicDark,
        onFire = {},
        modifier = Modifier.fillMaxWidth().height(64.dp)
    )
}
