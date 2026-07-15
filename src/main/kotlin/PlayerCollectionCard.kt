package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class PlayerCollectionCard(
    val cardId: String,
    val isOwned: Boolean,
    val selectedSkinId: String? = null,
    val canBeTried: Boolean,
    val ownedCopies: Int = 0,
    val maxCopies: Int = 1,
    val cost: Int = 0,
    val power: Int = 0,
    val faction: String? = null,
)