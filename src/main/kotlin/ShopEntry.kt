package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class ShopEntry(
    val card: Card,
    val copiesRemaining: Int
)