package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class SoloMissionStartRequest(
    val missionId: String,
    val difficulty: String,
    val selectedRuneIds: List<String> = emptyList(),
    val selectedCardIds: List<String> = emptyList()
)