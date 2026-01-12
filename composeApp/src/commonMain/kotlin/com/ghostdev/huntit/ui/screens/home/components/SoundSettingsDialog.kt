package com.ghostdev.huntit.ui.screens.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ghostdev.huntit.ui.theme.MainYellow
import com.ghostdev.huntit.ui.theme.patrickHandFont
import com.ghostdev.huntit.ui.theme.testSohneFont
import com.ghostdev.huntit.utils.LocalAudioPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private val GameBlack = Color(0xFF1A1A1A)
private val GameWhite = Color(0xFFFFFFFF)

@Composable
fun SoundSettingsDialog(
    onDismiss: () -> Unit,
    backgroundMusicEnabled: Boolean = true,
    soundEffectsEnabled: Boolean = true,
    musicVolume: Float = 0.5f,
    soundEffectsVolume: Float = 0.5f,
    onBackgroundMusicToggled: (Boolean) -> Unit,
    onSoundEffectsToggled: (Boolean) -> Unit,
    onMusicVolumeChanged: (Float) -> Unit,
    onSoundEffectsVolumeChanged: (Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val audioPlayer = LocalAudioPlayer.current
    
    var localBackgroundMusicEnabled by remember { mutableStateOf(backgroundMusicEnabled) }
    var localSoundEffectsEnabled by remember { mutableStateOf(soundEffectsEnabled) }
    var localMusicVolume by remember { mutableStateOf(musicVolume) }
    var localSoundEffectsVolume by remember { mutableStateOf(soundEffectsVolume) }
    
    var closeAnimation by remember { mutableStateOf(false) }
    val dialogScale by animateFloatAsState(
        targetValue = if (closeAnimation) 0.8f else 1f,
        label = "DialogScale"
    )
    val dialogAlpha by animateFloatAsState(
        targetValue = if (closeAnimation) 0f else 1f, 
        label = "DialogAlpha"
    )

    Dialog(onDismissRequest = {
        coroutineScope.launch {
            closeAnimation = true
            delay(200) // Animation duration
            onDismiss()
        }
    }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = 4.dp, y = 4.dp)
                    .background(
                        color = GameBlack.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp)
            )


            Column(
                modifier = Modifier
                    .scale(dialogScale)
                    .alpha(dialogAlpha)
                    .fillMaxWidth()
                    .background(
                        color = GameWhite,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 2.dp,
                        color = GameBlack,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                Text(
                    text = "Sound Settings",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontFamily = testSohneFont(),
                        fontWeight = FontWeight.Bold,
                        color = GameBlack
                    )
                )


                SettingsToggleOption(
                    title = "Background Music",
                    isChecked = localBackgroundMusicEnabled,
                    onCheckedChange = { newValue -> 
                        localBackgroundMusicEnabled = newValue
                        
                        if (newValue) {
                            audioPlayer?.setVolume(localMusicVolume)
                            audioPlayer?.resumeBackgroundMusic()
                            
                            try {
                                kotlinx.coroutines.MainScope().launch {
                                    kotlinx.coroutines.delay(100)
                                    audioPlayer?.playBackgroundMusic("files/background_music.mp3")
                                }
                            } catch (e: Exception) {
                                audioPlayer?.playBackgroundMusic("files/background_music.mp3")
                            }
                        } else {
                            audioPlayer?.pauseBackgroundMusic()
                            audioPlayer?.setVolume(0f)
                        }
                        
                        onBackgroundMusicToggled(newValue)
                        
                        if (newValue) {
                            audioPlayer?.playSound("files/button_click.mp3")
                        }
                    }
                )

                if (localBackgroundMusicEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Music Volume",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontFamily = patrickHandFont(),
                                fontWeight = FontWeight.Normal,
                                color = GameBlack
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Custom styled Slider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .align(Alignment.Center)
                                    .background(
                                        color = GameBlack.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            )

                            Slider(
                                value = localMusicVolume,
                                onValueChange = { newVolume ->

                                    localMusicVolume = newVolume
                                    

                                    if (localBackgroundMusicEnabled) {
                                        audioPlayer?.setVolume(newVolume)
                                    }
                                },
                                onValueChangeFinished = {

                                    onMusicVolumeChanged(localMusicVolume)

                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MainYellow,
                                    activeTrackColor = MainYellow,
                                    inactiveTrackColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }


                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${(localMusicVolume * 100).toInt()}%",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = testSohneFont(),
                                    fontWeight = FontWeight.Bold,
                                    color = GameBlack
                                )
                            )
                        }
                    }
                }


                SettingsToggleOption(
                    title = "Sound Effects",
                    isChecked = localSoundEffectsEnabled,
                    onCheckedChange = { newValue -> 
                        localSoundEffectsEnabled = newValue
                        

                        onSoundEffectsToggled(newValue)
                        
                        // If turning off sound effects, set volume to 0 to immediately disable them
                        if (!newValue) {
                            audioPlayer?.setSoundEffectsVolume(0f)
                        } else {
                            // If turning on, restore the volume to the slider value
                            audioPlayer?.setSoundEffectsVolume(localSoundEffectsVolume)
                            // Play sound effect to confirm the change is working
                            audioPlayer?.playSound("files/button_click.mp3")
                        }
                    }
                )
                
                // Only show sound effects volume slider if sound effects are enabled
                if (localSoundEffectsEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Sound Effects Volume",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontFamily = patrickHandFont(),
                                fontWeight = FontWeight.Normal,
                                color = GameBlack
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Custom styled Slider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .align(Alignment.Center)
                                    .background(
                                        color = GameBlack.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            )

                            Slider(
                                value = localSoundEffectsVolume,
                                onValueChange = { newVolume ->

                                    localSoundEffectsVolume = newVolume
                                    
                                    // Apply sound effects volume change in real-time
                                    audioPlayer?.setSoundEffectsVolume(newVolume)
                                },
                                onValueChangeFinished = {
                                    // Save to preferences when user releases the slider
                                    onSoundEffectsVolumeChanged(localSoundEffectsVolume)
                                    
                                    // Play a sound effect to demonstrate the new volume level
                                    // Only if sound effects are enabled and volume is > 0
                                    if (localSoundEffectsEnabled && localSoundEffectsVolume > 0) {
                                        audioPlayer?.playSound("files/button_click.mp3")
                                    }
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MainYellow,
                                    activeTrackColor = MainYellow,
                                    inactiveTrackColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }


                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${(localSoundEffectsVolume * 100).toInt()}%",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = testSohneFont(),
                                    fontWeight = FontWeight.Bold,
                                    color = GameBlack
                                )
                            )
                        }
                    }
                }

                // Close Button
                GamifiedButton(
                    text = "CLOSE",
                    onClick = {
                        // Play button click sound if sound effects are enabled
                        // This is the last chance to play a sound, so we must respect the current setting
                        if (localSoundEffectsEnabled && localSoundEffectsVolume > 0) {
                            audioPlayer?.playSound("files/button_click.mp3")
                        }
                        
                        coroutineScope.launch {
                            closeAnimation = true
                            delay(200) // Animation duration
                            onDismiss()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleOption(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 16.sp,
                fontFamily = patrickHandFont(),
                fontWeight = FontWeight.Normal,
                color = GameBlack
            )
        )

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MainYellow,
                checkedTrackColor = MainYellow.copy(alpha = 0.3f),
                checkedBorderColor = GameBlack,
                uncheckedThumbColor = GameWhite,
                uncheckedTrackColor = GameBlack.copy(alpha = 0.1f),
                uncheckedBorderColor = GameBlack
            )
        )
    }
}

@Composable
private fun GamifiedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val offsetY by animateFloatAsState(
        targetValue = if (isPressed) 4f else 0f,
        label = "ButtonOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        // Shadow Layer (Static at bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(46.dp)
                .background(GameBlack, RoundedCornerShape(12.dp))
        )

        // Button Layer (Moves when pressed)
        Box(
            modifier = Modifier
                .offset(y = offsetY.dp)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(46.dp)
                .background(MainYellow, RoundedCornerShape(12.dp))
                .border(2.dp, GameBlack, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = testSohneFont(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = GameBlack
                )
            )
        }
    }
}