package com.battleon

object CardDestructionRules {

    fun canBeDestroyedByOpponent(cardId: CardId): Boolean {
        return when (cardId) {
            CardId.SANGUINAIRE -> false
            else -> true
        }
    }
}