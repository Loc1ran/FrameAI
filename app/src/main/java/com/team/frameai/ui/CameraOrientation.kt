package com.team.frameai.ui

import android.view.OrientationEventListener
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

internal fun cameraQuarterTurn(orientation: Int, previous: Int): Int {
    if (orientation !in 0..359) return previous
    val distance = abs((orientation - previous + 540) % 360 - 180)
    // Keep the last direction near a diagonal so labels do not flicker back and forth.
    return if (distance < 60) previous else ((orientation + 45) / 90 * 90) % 360
}

@Composable
internal fun rememberCameraOrientation(): Int {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var degrees by remember { mutableIntStateOf(0) }
    DisposableEffect(context, owner) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                degrees = cameraQuarterTurn(orientation, degrees)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && listener.canDetectOrientation()) listener.enable()
            if (event == Lifecycle.Event.ON_PAUSE) listener.disable()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable(); owner.lifecycle.removeObserver(observer) }
    }
    return degrees
}

/** Rotates only this content, measuring text in its reading direction instead of clipping it. */
@Composable
internal fun CameraLabel(
    degrees: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = { Box(contentAlignment = Alignment.Center) { content() } }, modifier = modifier) { measurables, constraints ->
        val sideways = degrees == 90 || degrees == 270
        val childConstraints = if (sideways) Constraints(
            minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth, maxHeight = constraints.maxWidth,
        ) else constraints
        val child = measurables.single().measure(childConstraints)
        val width = if (sideways) child.height else child.width
        val height = if (sideways) child.width else child.height
        layout(width, height) {
            child.placeWithLayer((width - child.width) / 2, (height - child.height) / 2) {
                rotationZ = -degrees.toFloat()
            }
        }
    }
}
