package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class PlayerCollectionCard(
    val cardId: String,
    val isOwned: Boolean,
    val selectedSkinId: String? = null,
    val canBeTried: Boolean
)