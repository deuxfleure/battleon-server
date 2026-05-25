package com.battleon

import kotlinx.serialization.Serializable

@Serializable
enum class DelayedEffectTiming {
    END_TURN
}

@Serializable
enum class DelayedEffectType {
    DESTROY_REVEALED_CARD
}

@Serializable
enum class DelayedEffectTarget {
    PLAYER,
    OPPONENT
}

@Serializable
data class DelayedEffect(
    val timing: DelayedEffectTiming,
    val type: DelayedEffectType,
    val target: DelayedEffectTarget
)