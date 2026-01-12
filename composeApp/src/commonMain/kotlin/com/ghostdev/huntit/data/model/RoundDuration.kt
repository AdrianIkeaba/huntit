package com.ghostdev.huntit.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Enum for round_duration column in game_rooms table
 * Note: seconds field represents the round duration in seconds
 */
@Serializable
enum class RoundDuration(val durationName: String, val seconds: Int) {
    @SerialName("quick")
    QUICK("quick", 30),

    @SerialName("standard")
    STANDARD("standard", 60),

    @SerialName("marathon")
    MARATHON("marathon", 90)
}

/**
 * Enum for theme column in game_rooms table
 */
@Serializable
enum class GameTheme(val displayName: String) {
    @SerialName("outdoors_nature")
    OUTDOORS_NATURE("Outdoors and Nature"),

    @SerialName("indoors_house")
    INDOORS_HOUSE("Indoors and House"),

    @SerialName("fashion_style")
    FASHION_STYLE("Fashion and Style"),

    @SerialName("school_study")
    SCHOOL_STUDY("School and Study"),

    @SerialName("pop_culture")
    POP_CULTURE("Pop Culture and Fun")
}

/**
 * Enum for status column in game_rooms table
 */
@Serializable
enum class GameStatus {
    @SerialName("lobby")
    LOBBY,

    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("finished")
    FINISHED
}