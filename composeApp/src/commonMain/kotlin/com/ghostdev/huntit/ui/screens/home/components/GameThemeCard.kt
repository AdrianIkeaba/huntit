package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.paint
// No check icon needed
import com.ghostdev.huntit.data.model.GameTheme as DataGameTheme
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.patrickHandScFont
import com.ghostdev.huntit.ui.theme.testSohneFont

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 2.dp

@Composable
fun GameThemeCard(
    modifier: Modifier = Modifier,
    themes: List<GameTheme>,
    selectedTheme: DataGameTheme = DataGameTheme.OUTDOORS_NATURE,
    onThemeChanged: (DataGameTheme) -> Unit = {}
) {
    // Mapping UI themes to data model themes
    val themeToDataMap = mapOf(
        0 to DataGameTheme.OUTDOORS_NATURE,
        1 to DataGameTheme.INDOORS_HOUSE,
        2 to DataGameTheme.FASHION_STYLE,
        3 to DataGameTheme.SCHOOL_STUDY,
        4 to DataGameTheme.POP_CULTURE
    )

    val selectedThemeId = themes.indexOfFirst {
        themeToDataMap[it.id] == selectedTheme
    }.takeIf { it >= 0 } ?: 0

    BaseCardComponent(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title row with 3D effect
            Row(
                modifier = Modifier.wrapContentSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon with circular background
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MainYellow, CircleShape)
                        .border(1.dp, GameBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier.size(14.dp),
                        painter = painterResource(Res.drawable.paint),
                        contentDescription = null
                    )
                }

                Text(
                    text = "Game Theme",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = testSohneFont(),
                        color = GameBlack,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Selected theme display with 3D effect
            val currentTheme = themes[selectedThemeId]
            GamifiedThemeBox(
                theme = currentTheme,
                isSelected = true
            )

            // Theme selection horizontal row
            Text(
                text = "Choose a theme:",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = patrickHandFont(),
                    color = GameBlack.copy(alpha = 0.7f)
                ),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(themes.size) { index ->
                    val theme = themes[index]
                    ThemeSelectionItem(
                        theme = theme,
                        isSelected = index == selectedThemeId,
                        onClick = {
                            themeToDataMap[theme.id]?.let { dataTheme ->
                                onThemeChanged(dataTheme)
                            }
                        }
                    )
                }
            }
        }
    }
}

data class GameTheme(
    val id: Int,
    val image: DrawableResource,
    val title: String,
    val description: String
)

@Composable
private fun GamifiedThemeBox(
    theme: GameTheme,
    isSelected: Boolean
) {
    // Main Card with shadow effect
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        // Shadow layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 4.dp, x = 4.dp)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(16.dp))
                .background(GameBlack.copy(alpha = 0.2f))
        )

        // Content layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .border(1.5.dp, GameBlack, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(MainYellow)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Theme Icon in a circular container
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(GameWhite, CircleShape)
                        .border(1.5.dp, GameBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier
                            .size(32.dp),
                        painter = painterResource(theme.image),
                        contentDescription = theme.title
                    )
                }

                // Theme details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = theme.title,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontFamily = testSohneFont(),
                            fontWeight = FontWeight.Bold,
                            color = GameBlack
                        )
                    )

                    Text(
                        text = theme.description,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = patrickHandFont(),
                            color = GameBlack.copy(alpha = 0.8f)
                        )
                    )
                }

                // Selected indicator
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(GameBlack, CircleShape)
                            .border(1.dp, GameWhite, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontFamily = testSohneFont(),
                                fontWeight = FontWeight.Bold,
                                color = GameWhite
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSelectionItem(
    theme: GameTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animated scale effect for interactive feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ThemeScale"
    )

    // Offset for 3D button effect
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) GameShadowHeight else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "ThemeOffset"
    )

    Box(
        modifier = Modifier
            .wrapContentSize()
            .scale(scale)
    ) {
        // Shadow (static at bottom)
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GameBlack)
        )

        // Main button (moves when pressed)
        Box(
            modifier = Modifier
                .size(70.dp)
                .offset(y = offsetY)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) MainYellow else GameWhite)
                .border(1.5.dp, GameBlack, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Theme icon
                Image(
                    modifier = Modifier
                        .size(36.dp),
                    painter = painterResource(theme.image),
                    contentDescription = theme.title
                )

                // Short title that fits in the box
                val shortTitle = when (theme.id) {
                    0 -> "Outdoors"
                    1 -> "Indoors"
                    2 -> "Fashion"
                    3 -> "School"
                    4 -> "Pop Culture"
                    else -> theme.title.split(" ").firstOrNull() ?: ""
                }

                Text(
                    text = shortTitle,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontFamily = patrickHandFont(),
                        color = GameBlack,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    }
}