package com.battleon

object EffectPhaseCardResolver {

    fun applyEffect(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {
        val ownerIsPlayer = owner == ChoiceOwner.PLAYER

        val currentCard = if (ownerIsPlayer) {
            game.lastPlayerCard
        } else {
            game.lastOpponentCard
        } ?: return game

        return when (currentCard.id) {

            CardId.ARAIGNEESOLDAT -> {
                game.copy(
                    pendingChoice = CardEffectManager.buildAraigneeSoldatPendingChoice(
                        owner = owner
                    ),
                    infoMessage = null
                )
            }

            CardId.NOURRICEARAIGNEE -> {
                game.copy(
                    pendingChoice = CardEffectManager.buildNourriceAraigneePendingChoice(
                        game = game,
                        owner = owner
                    ),
                    infoMessage = null
                )
            }

            CardId.AGILE -> {
                game.copy(
                    pendingChoice = CardEffectManager.buildAgilePendingChoice(
                        owner = owner
                    ),
                    infoMessage = null
                )
            }

            CardId.REVENDEUR -> {
                val gameWithGold = if (ownerIsPlayer) {
                    game.copy(playerGold = game.playerGold + 1)
                } else {
                    game.copy(opponentGold = game.opponentGold + 1)
                }

                CardEffectManager.startScry(
                    game = gameWithGold,
                    sourceCardId = CardId.REVENDEUR,
                    resolver = owner,
                    target = owner,
                    amount = 1,
                    canDiscardViewedCards = true
                )
            }

            CardId.MAUVAISGENIE -> {
                game.copy(
                    pendingChoice = CardEffectManager.buildBadGeniePendingChoice(
                        game = game,
                        owner = owner
                    ),
                    infoMessage = null
                )
            }

            CardId.ENVOUTEUSE -> {
                game.copy(
                    pendingChoice = CardEffectManager.buildEnvouteusePendingChoice(
                        game = game,
                        owner = owner
                    ),
                    infoMessage = null
                )
            }

            CardId.MAGE -> {
                game.copy(
                    pendingChoice = CardEffectManager.buildMagePendingChoice(
                        owner = owner
                    ),
                    infoMessage = null
                )
            }

            CardId.TACTICIEN -> {
                game.copy(
                    pendingChoice = CardEffectManager.buildTacticienPendingChoice(
                        owner = owner
                    ),
                    infoMessage = null
                )
            }

            CardId.CHAMANE -> {
                CardEffectManager.startChamaneSubterfuge(
                    game = game,
                    owner = owner
                )
            }

            CardId.DEVINDELUMIERE -> {
                CardEffectManager.startScry(
                    game = game,
                    sourceCardId = CardId.DEVINDELUMIERE,
                    resolver = owner,
                    target = owner,
                    amount = 2,
                    canDiscardViewedCards = true
                )
            }

            CardId.DEVINDESTENEBRES -> {
                CardEffectManager.startScry(
                    game = game,
                    sourceCardId = CardId.DEVINDESTENEBRES,
                    resolver = owner,
                    target = if (ownerIsPlayer) ChoiceOwner.OPPONENT else ChoiceOwner.PLAYER,
                    amount = 2,
                    canDiscardViewedCards = true
                )
            }

            CardId.SQUELETTE_FAIBLESSE -> {
                if (ownerIsPlayer) {
                    game.copy(
                        playerNextCardPowerBonus = game.playerNextCardPowerBonus - 1
                    )
                } else {
                    game.copy(
                        opponentNextCardPowerBonus = game.opponentNextCardPowerBonus - 1
                    )
                }
            }

            CardId.MAITREDESCARNAGES -> {
                game.copy(
                    pendingChoice = CardEffectManager.buildMasterOfCarnagesPendingChoice(
                        game = game,
                        owner = owner
                    ),
                    infoMessage = null
                )
            }

            CardId.CURSED,
            CardId.SQUELETTE_HANTE -> {
                CardEffectManager.discardTopCardWithReshuffleIfNeeded(
                    game = game,
                    targetIsPlayer = ownerIsPlayer
                )
            }

            else -> {
                val effectResult = applyCardEffect(
                    effectOwnerIsPlayer = ownerIsPlayer,
                    cardId = currentCard.id,
                    playerHp = game.playerHp,
                    opponentHp = game.opponentHp,
                    playerGold = game.playerGold,
                    opponentGold = game.opponentGold,
                    playerDeck = game.playerDeck,
                    opponentDeck = game.opponentDeck,
                    playerDiscard = game.playerDiscard,
                    opponentDiscard = game.opponentDiscard
                )

                game.copy(
                    playerHp = effectResult.playerHp,
                    opponentHp = effectResult.opponentHp,
                    playerGold = effectResult.playerGold,
                    opponentGold = effectResult.opponentGold,
                    playerDeck = effectResult.playerDeck,
                    opponentDeck = effectResult.opponentDeck,
                    playerDiscard = effectResult.playerDiscard,
                    opponentDiscard = effectResult.opponentDiscard,
                    infoMessage = null
                )
            }
        }
    }
}