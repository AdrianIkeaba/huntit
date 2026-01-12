package com.ghostdev.huntit.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ghostdev.huntit.ui.theme.BackgroundColor
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class DoodleType { Circle, Squircle, Donut, Star, Triangle, Capsule, Cross }

private data class Doodle(
    val type: DoodleType,
    val relX: Float,
    val relY: Float,
    val sizeDp: Float,
    val color: Color,
    val shadowColor: Color,
    val amplitudeDp: Float,
    val phase: Float,
    val speedMultiplier: Int,
    val initialRotation: Float
)

@Composable
@Preview
fun AnimatedBackground(
    modifier: Modifier = Modifier,
    seed: Long? = null,
    content: @Composable () -> Unit = {}
) {
    val density = LocalDensity.current

    val palette = remember {
        listOf(
            Color(0xFFFF6B6B), // Coral Red
            Color(0xFFFF9F43), // Orange
            Color(0xFFFECA57), // Yellow
            Color(0xFF1DD1A1), // Lime
            Color(0xFF54A0FF), // Sky Blue
            Color(0xFF9C88FF), // Purple
            Color(0xFFFF9FF3), // Pink
        )
    }

    val doodles = remember(seed) {
        val baseSeed = seed ?: Random.nextLong()
        val rng = Random(baseSeed)

        fun <T> List<T>.randomRng(): T = this[rng.nextInt(this.size)]
        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        fun Color.darken(factor: Float = 0.75f): Color {
            return Color(
                red = (this.red * factor).coerceIn(0f, 1f),
                green = (this.green * factor).coerceIn(0f, 1f),
                blue = (this.blue * factor).coerceIn(0f, 1f),
                alpha = this.alpha
            )
        }

        val count = rng.nextInt(10, 15)
        val list = mutableListOf<Doodle>()

        val cols = 3
        val rows = 5
        val cellIndices = (0 until cols * rows).toMutableList().apply { shuffle(rng) }
        val chosenIndices = cellIndices.take(count)
        val margin = 0.02f

        chosenIndices.forEach { idx ->
            val cx = idx % cols
            val cy = idx / cols

            val relX = lerp(cx / cols.toFloat() + margin, (cx + 1) / cols.toFloat() - margin, rng.nextFloat())
            val relY = lerp(cy / rows.toFloat() + margin, (cy + 1) / rows.toFloat() - margin, rng.nextFloat())

            val type = DoodleType.entries.randomRng()
            val sizeDp = rng.nextInt(28, 55).toFloat()
            val color = palette.randomRng()

            // Speed logic: Pick random integer between -2 and 2, excluding 0
            var speed = rng.nextInt(-2, 3)
            if (speed == 0) speed = 1

            list += Doodle(
                type = type,
                relX = relX,
                relY = relY,
                sizeDp = sizeDp,
                color = color,
                shadowColor = color.darken(0.7f),
                amplitudeDp = rng.nextInt(12, 22).toFloat(),
                phase = rng.nextFloat() * (2f * PI).toFloat(),
                speedMultiplier = speed,
                initialRotation = rng.nextFloat() * 360f
            )
        }
        list
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")

    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "floating_time"
    )

    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing), // 20 seconds for one base rotation
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            doodles.forEach { d ->
                val widthPx = size.width
                val heightPx = size.height

                // Base position
                val baseX = d.relX * widthPx
                val baseY = d.relY * heightPx

                // Smooth floating (Sine wave)
                val ampPx = d.amplitudeDp * density.density
                val yOffset = sin(animTime + d.phase) * ampPx

                // Seamless rotation calculation
                // (Base Rotation) + (0..360 * Multiplier)
                val currentRotation = d.initialRotation + (rotationAnim * d.speedMultiplier)

                val center = Offset(baseX, baseY + yOffset)
                val sizePx = d.sizeDp * density.density
                val depthPx = 7.dp.toPx()

                draw3DDoodle(
                    center = center,
                    sizePx = sizePx,
                    depthPx = depthPx,
                    rotation = currentRotation,
                    doodle = d
                )
            }
        }
        content()
    }
}

private fun DrawScope.draw3DDoodle(
    center: Offset,
    sizePx: Float,
    depthPx: Float,
    rotation: Float,
    doodle: Doodle
) {
    rotate(rotation, pivot = center) {
        with(doodle) {
            // Shadow/Depth Layer
            drawShape(
                type = type,
                center = center.copy(y = center.y + depthPx),
                sizePx = sizePx,
                color = shadowColor
            )

            // Main Face Layer
            drawShape(
                type = type,
                center = center,
                sizePx = sizePx,
                color = color
            )

            // Highlight
            drawHighlight(type, center, sizePx)
        }
    }
}

private fun DrawScope.drawShape(
    type: DoodleType,
    center: Offset,
    sizePx: Float,
    color: Color
) {
    val half = sizePx / 2f

    when (type) {
        DoodleType.Circle -> {
            drawCircle(color = color, radius = half, center = center)
        }
        DoodleType.Squircle -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - half, center.y - half),
                size = Size(sizePx, sizePx),
                cornerRadius = CornerRadius(sizePx * 0.4f, sizePx * 0.4f)
            )
        }
        DoodleType.Capsule -> {
            val capsuleWidth = sizePx * 1.3f
            val capsuleHeight = sizePx * 0.6f
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - capsuleWidth / 2, center.y - capsuleHeight / 2),
                size = Size(capsuleWidth, capsuleHeight),
                cornerRadius = CornerRadius(capsuleHeight / 2, capsuleHeight / 2)
            )
        }
        DoodleType.Cross -> {
            val barThickness = sizePx * 0.35f
            val barLength = sizePx * 1.1f
            val corner = CornerRadius(barThickness / 2, barThickness / 2)
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - barLength / 2, center.y - barThickness / 2),
                size = Size(barLength, barThickness),
                cornerRadius = corner
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - barThickness / 2, center.y - barLength / 2),
                size = Size(barThickness, barLength),
                cornerRadius = corner
            )
        }
        DoodleType.Donut -> {
            drawCircle(
                color = color,
                radius = half,
                center = center,
                style = Stroke(width = sizePx * 0.35f)
            )
        }
        DoodleType.Star -> {
            val path = createStarPath(center, sizePx, 5, 0.45f)
            drawPath(path = path, color = color)
        }
        DoodleType.Triangle -> {
            val path = Path().apply {
                moveTo(center.x, center.y - half)
                lineTo(center.x + half, center.y + half * 0.8f)
                lineTo(center.x - half, center.y + half * 0.8f)
                close()
            }
            drawPath(path = path, color = color)
        }
    }
}

private fun DrawScope.drawHighlight(type: DoodleType, center: Offset, sizePx: Float) {
    val half = sizePx / 2f
    val highlightColor = Color.White.copy(alpha = 0.4f)
    val highlightOffset = Offset(center.x - half * 0.35f, center.y - half * 0.4f)

    when(type) {
        DoodleType.Donut -> {
            drawCircle(
                color = highlightColor,
                radius = sizePx * 0.07f,
                center = Offset(center.x - half*0.6f, center.y - half * 0.6f)
            )
        }
        DoodleType.Capsule -> {
            drawRoundRect(
                color = highlightColor,
                topLeft = Offset(center.x - sizePx * 0.4f, center.y - sizePx * 0.2f),
                size = Size(sizePx * 0.3f, sizePx * 0.1f),
                cornerRadius = CornerRadius(sizePx*0.05f)
            )
        }
        DoodleType.Cross -> {
            drawCircle(
                color = highlightColor,
                radius = sizePx * 0.06f,
                center = Offset(center.x - sizePx*0.05f, center.y - sizePx*0.05f)
            )
        }
        else -> {
            drawCircle(
                color = highlightColor,
                radius = sizePx * 0.12f,
                center = highlightOffset
            )
        }
    }
}

private fun createStarPath(center: Offset, size: Float, points: Int, innerRatio: Float): Path {
    val outerRadius = size / 2f
    val innerRadius = outerRadius * innerRatio
    val path = Path()
    val angleStep = PI / points
    var angle = -PI / 2

    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + r * cos(angle).toFloat()
        val y = center.y + r * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        angle += angleStep
    }
    path.close()
    return path
}