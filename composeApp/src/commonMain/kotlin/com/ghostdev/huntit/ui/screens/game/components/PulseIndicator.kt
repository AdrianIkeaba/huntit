package com.ghostdev.huntit.ui.screens.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.theme.MainGreen
import com.ghostdev.huntit.ui.theme.jetbrainsMonoFont
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun PulseIndicator(
    time: String = "24:34",
    color: Color = MainGreen,
    modifier: Modifier = Modifier
) {
    val periodMs = 3600L
    val offsetsMs = longArrayOf(0L, 1200L, 2400L)

    val startNs = remember { Clock.System.now().nanosecondsOfSecond - periodMs * 1_000_000L}
    var frameTimeNs by remember { mutableLongStateOf(startNs) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now -> frameTimeNs = now }
        }
    }

    fun phase(offsetMs: Long): Float {
        val elapsedMs = (frameTimeNs - startNs) / 1_000_000L + offsetMs
        return ((elapsedMs % periodMs).toFloat() / periodMs.toFloat())
    }

    Box(
        modifier = modifier
            .wrapContentSize()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        @Composable
        fun Ring(p: Float) = Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 1f + 0.5f * p
                    scaleY = 1f + 0.5f * p
                    alpha = 1f - p
                }
                .border(1.5.dp, color, CircleShape)
        )

        Ring(phase(offsetsMs[0]))
        Ring(phase(offsetsMs[1]))
        Ring(phase(offsetsMs[2]))

        Box(
            Modifier
                .wrapContentSize()
                .background(Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = time,
                style = TextStyle(
                    fontFamily = jetbrainsMonoFont(),
                    color = color,
                    fontSize = 54.sp
                )
            )
        }
    }
}