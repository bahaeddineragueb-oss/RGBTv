package com.rgbtv.app.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** TV focus: scale 1.05 + 2px blue border + 180ms (§16, §44). */
fun Modifier.tvFocus(
    scale: Float = 1.05f,
    radius: Int = 12,
    onFocus: (Boolean) -> Unit = {}
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        animationSpec = tween(180),
        label = "tv"
    )
    this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .onFocusChanged {
            focused = it.isFocused
            onFocus(it.isFocused)
        }
        .focusable()
        .clip(RoundedCornerShape(radius.dp))
        .border(
            if (focused) 2.dp else 0.dp,
            if (focused) Cine.blue else Color.Transparent,
            RoundedCornerShape(radius.dp)
        )
}

/** Clickable without ripple (TV focus border is the feedback). */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Non-interactive scale readout for rows that paint focus themselves. */
@Composable
fun focusScaleOf(focused: Boolean, scale: Float = 1.05f): Float {
    val s by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        animationSpec = tween(180),
        label = "tv2"
    )
    return s
}
