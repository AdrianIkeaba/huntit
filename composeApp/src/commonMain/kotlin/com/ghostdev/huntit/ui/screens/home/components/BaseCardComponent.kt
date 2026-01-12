package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.unit.Dp

@Composable
fun BaseCardComponent(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val cardShape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .drawBehind {
                // Draw shadow first
                val dx = 2.dp.toPx()
                val dy = 2.dp.toPx()
                withTransform({
                    translate(left = dx, top = dy)
                }) {
                    drawRoundRect(
                        color = Color.Transparent,
                        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                    )
                }
            }
            .background(Color.Transparent, cardShape)
            .drawBehind {
                val strokeW = 0.5.dp.toPx()
                val radius = cornerRadius.toPx()
                val tx = 1.dp.toPx()
                val ty = 0.6.dp.toPx()
                // First stroke
                drawRoundRect(
                    color = Color(0xFF1A1A1A),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = strokeW)
                )
                // Second stroke with dash effect
                withTransform({ translate(tx, ty) }) {
                    drawRoundRect(
                        color = Color(0xFF1A1A1A),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(
                            width = strokeW * 0.75f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 3f), 0f)
                        )
                    )
                }
            }
    ) {
        content()
    }
}
