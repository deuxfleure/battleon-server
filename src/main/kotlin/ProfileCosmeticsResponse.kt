package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class ProfileCosmeticResponse(
    val id: String,
    val type: String,
    val displayName: String
)

@Serializable
data class ProfileCosmeticsResponse(
    val cosmetics: List<ProfileCosmeticResponse>
)