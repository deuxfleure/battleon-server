package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val id: Int,
    val username: String,
    val displayName: String,
    val avatar: String?,
    val title: String?,
    val chosenFaction: String,
    val tutorialEnabled: Boolean,
    val shopPopupEnabled: Boolean,
    val soundEnabled: Boolean,
    val musicEnabled: Boolean,
    val gems: Int,
    val dust: Int,
    val wins: Int,
    val losses: Int,
    val email: String?,
    val hasBattlePass: Boolean,
    val language: String,
)

@Serializable
data class UpdateProfileRequest(
    val avatar: String? = null,
    val title: String? = null,
    val language: String?,
    val chosenFaction: String? = null,
    val tutorialEnabled: Boolean? = null,
    val shopPopupEnabled: Boolean? = null,
    val soundEnabled: Boolean? = null,
    val musicEnabled: Boolean? = null
)