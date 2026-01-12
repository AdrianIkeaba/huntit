package com.ghostdev.huntit.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * Possible states of the bottom sheet.
 */
enum class SheetValue {
    Collapsed, // Minimized (peeking)
    Expanded    // Fully expanded
}

/**
 * AnchoredBottomSheet that remains visible at the bottom of the screen and can be dragged up to show more content.
 *
 * @param modifier The modifier to apply to the bottom sheet container
 * @param sheetContent The content to display within the bottom sheet
 * @param sheetShape The shape of the bottom sheet surface
 * @param sheetBackgroundColor The background color of the bottom sheet
 * @param initialValue The initial state of the bottom sheet
 * @param peekHeight The height of the bottom sheet when collapsed
 * @param expandedHeight The height of the bottom sheet when expanded, or null to use content size
 * @param onStateChange Optional callback for when sheet state changes
 */
@Composable
fun AnchoredBottomSheet(
    modifier: Modifier = Modifier,
    sheetContent: @Composable () -> Unit,
    sheetShape: Shape,
    sheetBackgroundColor: Color,
    initialValue: SheetValue = SheetValue.Collapsed,
    peekHeight: Dp = 100.dp,
    expandedHeight: Dp? = null,
    onStateChange: ((SheetValue) -> Unit)? = null
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Keep track of sheet state
    var sheetState by remember { mutableStateOf(initialValue) }

    // Keep track of sheet height
    var sheetHeight by remember { mutableStateOf(0) }

    // Calculate visible height based on state
    var visibleHeight by remember { mutableStateOf(peekHeight) }

    // Calculate background scrim alpha based on expansion progress
    val maxScrimAlpha = 0.2f
    val scrimAlpha = remember(visibleHeight, sheetHeight, expandedHeight, sheetState) {
        with(density) {
            val visibleHeightPx = visibleHeight.toPx()
            val peekHeightPx = peekHeight.toPx()

            if (expandedHeight != null) {
                // Calculate progress from collapsed to expanded (0f to 1f)
                val expandedHeightPx = expandedHeight.toPx()
                val progressRange = expandedHeightPx - peekHeightPx
                val currentProgress = visibleHeightPx - peekHeightPx

                if (progressRange > 0) {
                    val progress = (currentProgress / progressRange).coerceIn(0f, 1f)
                    maxScrimAlpha * progress
                } else {
                    0f
                }
            } else if (sheetHeight > 0) {
                // Fallback if expandedHeight is not specified
                val progressRange = sheetHeight.toFloat() - peekHeightPx
                val currentProgress = visibleHeightPx - peekHeightPx

                if (progressRange > 0) {
                    val progress = (currentProgress / progressRange).coerceIn(0f, 1f)
                    maxScrimAlpha * progress
                } else {
                    0f
                }
            } else {
                // Default when we don't have enough information yet
                if (sheetState == SheetValue.Expanded) maxScrimAlpha else 0f
            }
        }
    }

    // Choose animation based on direction for the most natural feel
    fun getAnimationSpec(isExpanding: Boolean): AnimationSpec<Float> {
        return if (isExpanding) {
            // When expanding, use spring for a slight bounce at the end
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.5f
            )
        } else {
            // When collapsing, use tween with easing for a smooth finish
            tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        }
    }

    // Whenever state changes, update visibleHeight and notify caller
    fun updateSheetState(newState: SheetValue) {
        if (sheetState != newState) {
            val targetHeight = when (newState) {
                SheetValue.Collapsed -> peekHeight
                SheetValue.Expanded -> expandedHeight ?: with(density) { sheetHeight.toDp() }
            }

            // Animate the height change
            scope.launch {
                // Check if we're expanding or collapsing
                val isExpanding = targetHeight > visibleHeight

                // Animate from current height to target height
                animate(
                    initialValue = visibleHeight.value,
                    targetValue = targetHeight.value,
                    animationSpec = getAnimationSpec(isExpanding)
                ) { value, _ ->
                    visibleHeight = value.dp
                }

                // Update state after animation completes
                sheetState = newState
                onStateChange?.invoke(newState)
            }
        }
    }

    // Detect if user drags down a small amount to close
    fun shouldCollapseOnDrag(dragAmount: Float): Boolean {
        return sheetState == SheetValue.Expanded && dragAmount > 20f
    }

    // Detect if user drags up enough to expand
    fun shouldExpandOnDrag(dragAmount: Float, currentHeight: Dp): Boolean {
        // If already near expanded or strong upward drag, expand
        if (sheetState == SheetValue.Collapsed) {
            // Detect either a strong upward flick or dragging beyond 25% of the way up
            val nearExpanded = expandedHeight?.let { expanded ->
                val quarterWay = peekHeight + ((expanded - peekHeight) * 0.25f)
                currentHeight > quarterWay
            } ?: false

            return dragAmount < -15f || nearExpanded
        }
        return false
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Scrim that only appears when the sheet is expanded
        if (sheetState == SheetValue.Expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        enabled = true,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // Clicking outside the sheet collapses it
                        updateSheetState(SheetValue.Collapsed)
                    },
            )
        }

        // Surface containing the sheet content
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(visibleHeight)
                    .drawBehind {
                        val shadowRadius = if (sheetState == SheetValue.Collapsed) 4.dp else 2.dp
                        val offsetY = if (sheetState == SheetValue.Collapsed) (-2).dp else (-1).dp

                        drawRoundRect(
                            color = Color.Black,
                            topLeft = Offset(0f, offsetY.toPx()),
                            size = Size(
                                width = size.width,
                                height = size.height + shadowRadius.toPx()
                            ),
                            cornerRadius = CornerRadius(
                                24.dp.toPx(),
                                24.dp.toPx()
                            )
                        )
                    }
                    .onSizeChanged { size ->
                        // Keep track of the full content height
                        if (sheetHeight < size.height) {
                            sheetHeight = size.height
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                // When drag ends, snap to nearest state with smooth animation
                                scope.launch {
                                    val expanded = sheetState == SheetValue.Expanded

                                    // Use position threshold to determine target state
                                    val threshold = if (expanded) {
                                        // Only need to drag down 20% of the way to collapse
                                        (sheetHeight / 5) * 4
                                    } else {
                                        // Make expansion very sensitive - just 15% upward drag triggers expansion
                                        (sheetHeight / 20) * 3 // 15% threshold instead of 40%
                                    }

                                    val currentHeightPx = with(density) { visibleHeight.toPx() }
                                    val newState = if (currentHeightPx.toInt() > threshold) {
                                        SheetValue.Expanded
                                    } else {
                                        SheetValue.Collapsed
                                    }

                                    updateSheetState(newState)
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                // Update height while dragging - immediate feedback during drag
                                scope.launch {
                                    // If dragging down from expanded state, make it easier to close
                                    if (shouldCollapseOnDrag(dragAmount)) {
                                        updateSheetState(SheetValue.Collapsed)
                                        return@launch
                                    }

                                    // Check if user is dragging upward enough to expand
                                    if (shouldExpandOnDrag(dragAmount, visibleHeight)) {
                                        // Expand the sheet with the full animation
                                        updateSheetState(SheetValue.Expanded)
                                        return@launch
                                    }

                                    // Smooth drag response - adjust dampening based on direction
                                    // Make upward drags more responsive (less dampening) for easier expansion
                                    val dampingFactor = if (dragAmount < 0) 0.7f else 0.8f
                                    val dampedAmount = dragAmount * dampingFactor

                                    val newHeight = (visibleHeight - dampedAmount.toDp())
                                        .coerceIn(
                                            peekHeight,
                                            expandedHeight ?: with(density) { sheetHeight.toDp() })
                                    visibleHeight = newHeight
                                }
                            }
                        )
                    }
                    .zIndex(10f),
                shape = sheetShape,
                color = sheetBackgroundColor
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    sheetContent()
                }
            }
        }
    }
}