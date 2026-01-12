package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import huntit.composeapp.generated.resources.Res
import huntit.composeapp.generated.resources.game_visibility
import huntit.composeapp.generated.resources.people
import huntit.composeapp.generated.resources.private
import huntit.composeapp.generated.resources.public
import org.jetbrains.compose.resources.painterResource

// Consistent Game Colors
private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)
private val GameGrey = Color(0xFFE5E5E5)
private val GameShadowHeight = 4.dp

@Composable
fun GameVisibilityCard(
    isPublic: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Create shadow effect consistent with other cards
    BaseCardComponent(
        modifier = modifier.fillMaxWidth()
    ) {
        // Content Layer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            // Title with icon for better visual appeal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circular icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MainYellow, CircleShape)
                        .border(1.dp, GameBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier.size(14.dp),
                        painter = painterResource(Res.drawable.game_visibility),
                        contentDescription = null
                    )
                }

                Text(
                    text = "Game Visibility",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontFamily = testSohneFont(),
                        fontWeight = FontWeight.Bold,
                        color = GameBlack
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose if others can find your game in the public game listings.",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = patrickHandFont(),
                    color = GameBlack.copy(alpha = 0.7f),
                    lineHeight = 20.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Options now use 3D style consistent with the game's UI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Private option
                VisibilityOption(
                    text = "Private",
                    description = "Only those with the code can join",
                    isSelected = !isPublic,
                    onClick = { onVisibilityChanged(false) },
                    modifier = Modifier.weight(1f)
                )
                
                // Public option
                VisibilityOption(
                    text = "Public",
                    description = "Anyone can find & join the game",
                    isSelected = isPublic,
                    onClick = { onVisibilityChanged(true) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun VisibilityOption(
    text: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MainYellow else GameGrey,
        label = "OptionBg"
    )
    
    val offsetY by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.4f),
        label = "OptionOffset"
    )
    
    // Box with shadow for 3D effect
    Box(modifier = modifier.height(110.dp)) {
        // Shadow Layer
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(106.dp)
                .background(GameBlack.copy(alpha = if (isSelected) 1f else 0.3f), RoundedCornerShape(12.dp))
                .offset(x = 2.dp, y = 2.dp)
        )
        
        // Content Layer
        Box(
            modifier = Modifier
                .offset(y = if (isSelected) offsetY else 0.dp)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(106.dp)
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 2.dp,
                    color = if (isSelected) GameBlack else GameBlack.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Add an enhanced icon to help distinguish the options
                Box(
                    modifier = Modifier.size(32.dp)
                ) {
                    // Shadow
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .offset(x = 1.dp, y = 1.dp)
                                .clip(CircleShape)
                                .background(GameBlack.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                    
                    // Icon circle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.7f), 
                                CircleShape
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) GameBlack else GameBlack.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            modifier = Modifier.size(14.dp),
                            painter = painterResource(if (text == "Private") Res.drawable.private else Res.drawable.public),
                            contentDescription = null
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontFamily = testSohneFont(),
                        fontWeight = FontWeight.Bold,
                        color = GameBlack
                    ),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontFamily = patrickHandFont(),
                        color = GameBlack.copy(alpha = 0.7f)
                    ),
                    textAlign = TextAlign.Center
                )
                
                // Add a checkmark for selected option
                if (isSelected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(GameBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓", 
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}