package com.battleon

object LinkedCardCatalog {

    private val linkedCards = mapOf(
        CardId.GARDIENSACRE to listOf(CardId.GOLEMDOCRE),
        CardId.CANONNIER to listOf(CardId.BOULETDECANON),
        CardId.JONGLEURDEJANTE to listOf(CardId.BOULETDECANON),
        CardId.LYCANTHROPE to listOf(CardId.LOUPGAROU)
    )

    fun getLinkedCards(cardId: CardId): List<CardId> {
        return linkedCards[cardId].orEmpty()
    }
}