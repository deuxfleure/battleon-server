package com.battleon

sealed class TrainingAiShopDecision {
    data object PassTurn : TrainingAiShopDecision()
    data class BuyTarget(val cardId: String) : TrainingAiShopDecision()
    data object NoTargetAvailable : TrainingAiShopDecision()
}

object TrainingAiShopManager {

    private fun hasSentinelleDestroyTargetInDiscard(game: GameState): Boolean {
        return game.opponentDiscard.any { card ->
            card.id == CardId.CURSED || card.id == CardId.WARRIOR
        }
    }

    private fun stillHasSentinelleDestroyTargetInDeck(game: GameState): Boolean {
        return game.opponentDeck.any { card ->
            card.id == CardId.CURSED || card.id == CardId.WARRIOR
        }
    }

    private fun isTemporarilyForbiddenForTrainingAi(cardId: CardId): Boolean {
        return when (cardId) {
            CardId.AGILE,
            CardId.ARAIGNEEGEANTE -> true

            else -> false
        }
    }

    fun chooseTrainingAiShopDecision(game: GameState): TrainingAiShopDecision {
        if (game.opponentPassedShop) {
            return TrainingAiShopDecision.PassTurn
        }

        val currentTargetId = game.opponentPendingShopPurchaseCardId

        val currentTargetEntry = game.shopEntries.firstOrNull { entry ->
            entry.card.id.name == currentTargetId && entry.copiesRemaining > 0
        }

        val shouldPassThisTurnForSentinelle =
            currentTargetEntry?.card?.id == CardId.SENTINELLE &&
                    !hasSentinelleDestroyTargetInDiscard(game) &&
                    stillHasSentinelleDestroyTargetInDeck(game)

        if (shouldPassThisTurnForSentinelle) {
            return TrainingAiShopDecision.PassTurn
        }

        val shouldKeepCurrentTarget =
            currentTargetEntry != null &&
                    !isTemporarilyForbiddenForTrainingAi(currentTargetEntry.card.id) &&
                    (
                            currentTargetEntry.card.id != CardId.SENTINELLE ||
                                    hasSentinelleDestroyTargetInDiscard(game)
                            )

        val validTargetEntry = if (shouldKeepCurrentTarget) {
            currentTargetEntry
        } else {
            game.shopEntries
                .filter { entry ->
                    entry.copiesRemaining > 0 &&
                            !isTemporarilyForbiddenForTrainingAi(entry.card.id) &&
                            (
                                    entry.card.id != CardId.SENTINELLE ||
                                            hasSentinelleDestroyTargetInDiscard(game)
                                    )
                }
                .randomOrNull()
        }

        return when {
            validTargetEntry == null -> TrainingAiShopDecision.NoTargetAvailable
            game.opponentGold < validTargetEntry.card.cost -> TrainingAiShopDecision.PassTurn
            else -> TrainingAiShopDecision.BuyTarget(validTargetEntry.card.id.name)
        }
    }
}