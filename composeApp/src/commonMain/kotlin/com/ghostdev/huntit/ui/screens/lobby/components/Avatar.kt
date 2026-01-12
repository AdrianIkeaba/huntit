package com.ghostdev.huntit.ui.screens.lobby.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.theme.patrickHandFont

@Composable
fun Avatars(
    modifier: Modifier = Modifier,
    painter: Painter,
    borderColor: Color = Color.Black,
) {
    val strokeW = 1.dp
    val jitterTx = 1.dp
    val jitterTy = 0.6.dp

    Box(
        modifier = modifier
            .clip(CircleShape)
    ) {
        Image(
            modifier = Modifier.matchParentSize(),
            painter = painter,
            contentDescription = null
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val r = size.minDimension / 2f
                    drawCircle(
                        color = borderColor,
                        radius = r - strokeW.toPx() / 2f,
                        style = Stroke(width = strokeW.toPx())
                    )
                    val jx = jitterTx.toPx()
                    val jy = jitterTy.toPx()
                    withTransform({ translate(jx, jy) }) {
                        drawCircle(
                            color = borderColor,
                            radius = r - strokeW.toPx(),
                            style = Stroke(
                                width = (strokeW.toPx() * 0.75f),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(60f, 6f), 0f)
                            )
                        )
                    }
                }
        )
    }
}

@Composable
fun PlusCountChip(
    modifier: Modifier = Modifier,
    count: Int = 4
) {
    val size = 96.dp
    Box(
        modifier = modifier
            .size(size)
            .background(Color.White, CircleShape)
            .drawBehind {
                drawCircle(color = Color(0xFF1A1A1A), style = Stroke(width = 1.dp.toPx()))
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$count",
            style = TextStyle(
                color = Color.Black,
                fontFamily = patrickHandFont(),
                fontSize = 16.sp
            )
        )
    }
}
