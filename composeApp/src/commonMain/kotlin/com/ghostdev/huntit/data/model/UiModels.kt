package com.ghostdev.huntit.data.model

/**
 * UI model for displaying participants in the lobby screen
 */
data class ParticipantUiModel(
    val id: String,
    val name: String,
    val avatarId: Int,
    val isHost: Boolean = false,
    val joinOrder: Int
)