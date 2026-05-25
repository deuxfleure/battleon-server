package com.battleon

import kotlinx.serialization.Serializable

@Serializable
enum class ChoiceOwner {
    PLAYER,
    OPPONENT
}