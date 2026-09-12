package com.battleon

object TokenManager {

    object TokenIds {
        const val POISON = "POISON"
        const val BURN = "BURN"
        const val DISEASE = "DISEASE"
    }

    fun getTokenAmount(
        game: GameState,
        target: ChoiceOwner,
        tokenId: String
    ): Int {
        val tokens = when (target) {
            ChoiceOwner.PLAYER -> game.playerTokens
            ChoiceOwner.OPPONENT -> game.opponentTokens
        }

        return tokens
            .firstOrNull { it.tokenId == tokenId }
            ?.amount
            ?: 0
    }

    fun hasToken(
        game: GameState,
        target: ChoiceOwner,
        tokenId: String
    ): Boolean {
        return getTokenAmount(
            game = game,
            target = target,
            tokenId = tokenId
        ) > 0
    }

    fun addToken(
        game: GameState,
        target: ChoiceOwner,
        tokenId: String,
        amount: Int = 1,
        maxAmount: Int? = null
    ): GameState {
        if (amount <= 0) {
            return game
        }

        val currentTokens = when (target) {
            ChoiceOwner.PLAYER -> game.playerTokens
            ChoiceOwner.OPPONENT -> game.opponentTokens
        }

        val currentAmount = currentTokens
            .firstOrNull { it.tokenId == tokenId }
            ?.amount
            ?: 0

        val rawNewAmount = currentAmount + amount

        val updatedAmount = if (maxAmount != null) {
            minOf(rawNewAmount, maxAmount)
        } else {
            rawNewAmount
        }

        // Exemple : Brûlure/Maladie déjà présents avec maxAmount = 1.
        // Recevoir à nouveau le même jeton ne change rien.
        if (updatedAmount == currentAmount) {
            return game
        }

        val updatedTokens =
            currentTokens.filterNot { it.tokenId == tokenId } +
                    TokenStack(
                        tokenId = tokenId,
                        amount = updatedAmount
                    )

        var updatedGame = when (target) {
            ChoiceOwner.PLAYER -> game.copy(
                playerTokens = updatedTokens
            )

            ChoiceOwner.OPPONENT -> game.copy(
                opponentTokens = updatedTokens
            )
        }

        val actuallyAdded =
            updatedAmount - currentAmount

        if (actuallyAdded > 0) {
            updatedGame = GameLogManager.tokenGained(
                game = updatedGame,
                owner = target,
                tokenId = tokenId,
                amount = actuallyAdded
            )
        }

        return resolveImmediateTokenEffects(
            game = updatedGame,
            target = target,
            tokenId = tokenId
        )
    }

    fun removeToken(
        game: GameState,
        target: ChoiceOwner,
        tokenId: String,
        amount: Int
    ): GameState {
        if (amount <= 0) {
            return game
        }

        val currentTokens = when (target) {
            ChoiceOwner.PLAYER -> game.playerTokens
            ChoiceOwner.OPPONENT -> game.opponentTokens
        }

        val currentAmount = currentTokens
            .firstOrNull { it.tokenId == tokenId }
            ?.amount
            ?: return game

        val remainingAmount =
            maxOf(0, currentAmount - amount)

        val updatedTokens =
            currentTokens.filterNot { it.tokenId == tokenId } +
                    if (remainingAmount > 0) {
                        listOf(
                            TokenStack(
                                tokenId = tokenId,
                                amount = remainingAmount
                            )
                        )
                    } else {
                        emptyList()
                    }

        val removedAmount =
            currentAmount - remainingAmount

        var updatedGame = when (target) {
            ChoiceOwner.PLAYER -> game.copy(
                playerTokens = updatedTokens
            )

            ChoiceOwner.OPPONENT -> game.copy(
                opponentTokens = updatedTokens
            )
        }

        if (removedAmount > 0) {
            updatedGame = GameLogManager.tokenRemoved(
                game = updatedGame,
                owner = target,
                tokenId = tokenId,
                amount = removedAmount
            )
        }

        return updatedGame
    }

    fun removeAllToken(
        game: GameState,
        target: ChoiceOwner,
        tokenId: String
    ): GameState {
        val currentAmount = getTokenAmount(
            game = game,
            target = target,
            tokenId = tokenId
        )

        if (currentAmount <= 0) {
            return game
        }

        return removeToken(
            game = game,
            target = target,
            tokenId = tokenId,
            amount = currentAmount
        )
    }

    fun heal(
        game: GameState,
        target: ChoiceOwner,
        amount: Int
    ): GameState {
        if (amount <= 0) {
            return game
        }

        if (
            hasToken(
                game = game,
                target = target,
                tokenId = TokenIds.DISEASE
            )
        ) {
            return game
        }

        return when (target) {
            ChoiceOwner.PLAYER -> game.copy(
                playerHp = game.playerHp + amount
            )

            ChoiceOwner.OPPONENT -> game.copy(
                opponentHp = game.opponentHp + amount
            )
        }
    }

    fun onDiscardReshuffledIntoDeck(
        game: GameState,
        target: ChoiceOwner
    ): GameState {
        return removeAllToken(
            game = game,
            target = target,
            tokenId = TokenIds.DISEASE
        )
    }

    private fun resolveImmediateTokenEffects(
        game: GameState,
        target: ChoiceOwner,
        tokenId: String
    ): GameState {
        return when (tokenId) {
            TokenIds.POISON -> resolvePoison(
                game = game,
                target = target
            )

            else -> game
        }
    }

    private fun resolvePoison(
        game: GameState,
        target: ChoiceOwner
    ): GameState {
        var updatedGame = game

        while (
            getTokenAmount(
                game = updatedGame,
                target = target,
                tokenId = TokenIds.POISON
            ) >= 3
        ) {
            updatedGame = removeToken(
                game = updatedGame,
                target = target,
                tokenId = TokenIds.POISON,
                amount = 3
            )

            updatedGame = when (target) {
                ChoiceOwner.PLAYER -> updatedGame.copy(
                    playerHp = maxOf(
                        0,
                        updatedGame.playerHp - 1
                    )
                )

                ChoiceOwner.OPPONENT -> updatedGame.copy(
                    opponentHp = maxOf(
                        0,
                        updatedGame.opponentHp - 1
                    )
                )
            }

            updatedGame = GameLogManager.tokenTriggered(
                game = updatedGame,
                owner = target,
                tokenId = TokenIds.POISON,
                value = 1
            )

        }

        return updatedGame
    }
}