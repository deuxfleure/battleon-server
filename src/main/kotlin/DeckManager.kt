package com.battleon

object DeckManager {

    fun reshuffleDiscardIntoDeckIfNeeded(
        game: GameState,
        target: ChoiceOwner
    ): GameState {

        val deck = when (target) {
            ChoiceOwner.PLAYER -> game.playerDeck
            ChoiceOwner.OPPONENT -> game.opponentDeck
        }

        val discard = when (target) {
            ChoiceOwner.PLAYER -> game.playerDiscard
            ChoiceOwner.OPPONENT -> game.opponentDiscard
        }

        // Aucun recyclage nécessaire
        if (deck.isNotEmpty() || discard.isEmpty()) {
            return game
        }

        val reshuffledDeck = discard.shuffled()

        val reshuffledGame = when (target) {
            ChoiceOwner.PLAYER -> game.copy(
                playerDeck = reshuffledDeck,
                playerDiscard = emptyList(),
                playerGold = game.playerGold + 1
            )

            ChoiceOwner.OPPONENT -> game.copy(
                opponentDeck = reshuffledDeck,
                opponentDiscard = emptyList(),
                opponentGold = game.opponentGold + 1
            )
        }

        return TokenManager.onDiscardReshuffledIntoDeck(
            game = reshuffledGame,
            target = target
        )
    }
}