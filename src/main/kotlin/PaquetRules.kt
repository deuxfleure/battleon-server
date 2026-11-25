
package com.battleon

object PaquetRules {

    const val MIN_CARDS = 25
    const val MAX_CARDS = 50

    const val DEFAULT_COPY_LIMIT = 3
    const val MAX_NAME_LENGTH = 20

    const val SLOT_COUNT = 7

    fun isValidSlotNumber(slotNumber: Int): Boolean {
        return slotNumber in 1..SLOT_COUNT
    }

    fun isValidCardCount(totalCards: Int): Boolean {
        return totalCards in MIN_CARDS..MAX_CARDS
    }

    fun getCardCopyLimit(card: Card): Int {
        return card.purchaseLimit ?: DEFAULT_COPY_LIMIT
    }

    fun getMaxUsableCopies(
        card: Card,
        ownedCopies: Int
    ): Int {
        return minOf(
            ownedCopies.coerceAtLeast(0),
            getCardCopyLimit(card)
        )
    }
}
