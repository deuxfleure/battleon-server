package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class TrainingShopCardRequest(
    val cardId: String
)