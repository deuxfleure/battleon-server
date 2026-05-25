package com.battleon

import kotlinx.serialization.Serializable

@Serializable
enum class CardFaction {
    MERCENARY,
    HUMAN,
    BEAST,
    DEMON
}