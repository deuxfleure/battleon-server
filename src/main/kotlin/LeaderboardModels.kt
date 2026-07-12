package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class LeaderboardEntryResponse(
    val userId: Int,
    val rank: Int,
    val displayName: String,
    val titleId: String?,
    val elo: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val latestSoloMissionId: String?,
    val latestSoloDifficulty: String?
)

@Serializable
data class LeaderboardResponse(
    val currentPlayer: LeaderboardEntryResponse,
    val topPlayers: List<LeaderboardEntryResponse>
)