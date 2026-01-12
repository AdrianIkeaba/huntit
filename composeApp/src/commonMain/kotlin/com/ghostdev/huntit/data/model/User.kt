package com.ghostdev.huntit.data.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarId: Int,
    val totalGamesPlayed: Int = 0
)