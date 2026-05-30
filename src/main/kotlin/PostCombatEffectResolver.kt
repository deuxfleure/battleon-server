package com.battleon

object PostCombatEffectResolver {

    fun apply(game: GameState): GameState {
        var updatedGame = game

        val playerCard = game.lastPlayerCard
        val opponentCard = game.lastOpponentCard

        if (playerCard == null || opponentCard == null) {
            return updatedGame
        }

        val playerPower = CardEffectManager.getEffectivePower(
            game = game,
            card = playerCard,
            ownerGold = game.playerGold,
            nextCardPowerBonus = game.playerCurrentCardPowerBonus,
            isPlayer = true
        )

        val opponentPower = CardEffectManager.getEffectivePower(
            game = game,
            card = opponentCard,
            ownerGold = game.opponentGold,
            nextCardPowerBonus = game.opponentCurrentCardPowerBonus,
            isPlayer = false
        )

        val playerLostCombat = playerPower < opponentPower
        val opponentLostCombat = opponentPower < playerPower

        val playerWonCombat = playerPower > opponentPower
        val opponentWonCombat = opponentPower > playerPower

        val drawCombat = opponentPower == playerPower

        //=============================================
        //======== ------ COTER Joueur ---- ===========
        //=============================================
        if (!game.playerEffectBlockedBySentinelle) {
            if (playerWonCombat && playerCard.id == CardId.NECROMANCIEN) {
                updatedGame = updatedGame.copy(
                    opponentDiscard = updatedGame.opponentDiscard + CardEffectManager.createRandomSkeletonCard()
                )
            }

            if (
                !updatedGame.playerPostCombatSacrificeHandled &&
                playerPower > opponentPower &&
                playerCard.id == CardId.ARAIGNEEGEANTE
            ) {
                return updatedGame.copy(
                    pendingChoice = CardEffectManager.buildAraigneeGeantePendingChoice(
                        game = updatedGame,
                        owner = ChoiceOwner.PLAYER
                    ),
                    infoMessage = null
                )
            }

            // OEUF D'ARAIGNÉE — poison si perte
            if (playerLostCombat && playerCard.id == CardId.OEUFDARAIGNEE) {
                updatedGame = CardEffectManager.addPoisonToken(
                    game = updatedGame,
                    target = ChoiceOwner.OPPONENT,
                    amount = 1
                )
            }
            // OEUF D'ARAIGNÉE — choix après combat
            if (
                !updatedGame.playerPostCombatSacrificeHandled &&
                playerCard.id == CardId.OEUFDARAIGNEE
            ) {
                return updatedGame.copy(
                    pendingChoice = CardEffectManager.buildOeufAraigneePendingChoice(
                        game = updatedGame,
                        owner = ChoiceOwner.PLAYER
                    ),
                    infoMessage = null
                )
            }

            // GARDIEN DES ENFERS
            if (playerCard.id == CardId.GARDIENDESENFERS) {
                val playerWon = playerPower > opponentPower
                val diff = playerPower - opponentPower

                if (playerWon && diff > 0) {
                    updatedGame = CardEffectManager.applyBretteur(
                        game = updatedGame,
                        owner = ChoiceOwner.PLAYER,
                        amount = diff
                    )
                }
            }

            if (
                !updatedGame.playerPostCombatSacrificeHandled &&
                (
                        playerCard.id == CardId.DEVINDELUMIERE ||
                                playerCard.id == CardId.DEVINDESTENEBRES ||
                                playerCard.id == CardId.AGILE
                        )
            ) {
                return updatedGame.copy(
                    pendingChoice = CardEffectManager.buildPostCombatSacrificePendingChoice(
                        cardId = playerCard.id,
                        owner = ChoiceOwner.PLAYER
                    ),
                    infoMessage = null
                )
            }

            if (playerLostCombat && playerCard.id == CardId.DURACUIRE) {
                updatedGame = updatedGame.copy(
                    playerNextCardPowerBonus = updatedGame.playerNextCardPowerBonus + 2,
                    playerNextCardDamageBonus = updatedGame.playerNextCardDamageBonus + 1
                )
            }
            if (playerLostCombat && playerCard.id == CardId.EPINENOIRE) {
                updatedGame = updatedGame.copy(
                    opponentHp = maxOf(0, updatedGame.opponentHp - 1)
                )
            }

            if (playerLostCombat && playerCard.id == CardId.DANSEUSEMACABRE) {
                updatedGame = updatedGame.copy(
                    playerGold = updatedGame.playerGold + 2
                )
            }

            if (!playerLostCombat && playerCard.id == CardId.SANGPACTE) {
                updatedGame = updatedGame.copy(
                    playerHp = maxOf(0, updatedGame.playerHp - 1),
                    playerGold = updatedGame.playerGold + 2
                )
            }

            if (playerCard.id == CardId.BULLDOZER) {
                val playerWon = playerPower > opponentPower
                val diff = playerPower - opponentPower

                if (
                    playerWon &&
                    diff >= 4 &&
                    CardEffectManager.canDestroyOneCardWithFiveCardRule(
                        game = updatedGame,
                        isPlayer = false
                    )
                ) {
                    updatedGame = updatedGame.copy(
                        lastOpponentCard = null,
                        opponentDisplayedTurnCard = null
                    )
                }
            }

            if (playerCard.id == CardId.PORTEURDEGIDEDECHU) {
                if (playerLostCombat) {
                    updatedGame = updatedGame.copy(
                        playerHp = updatedGame.playerHp + 1
                    )
                }

                if (playerPower > opponentPower) {
                    updatedGame = CardEffectManager.applyBretteur(
                        game = updatedGame,
                        owner = ChoiceOwner.PLAYER,
                        amount = 1
                    )
                }
            }
        }
        //=============================================
        //====== ------ COTER ADVERSAIRE ---- =========
        //=============================================
        if (!game.opponentEffectBlockedBySentinelle) {

            if (opponentWonCombat && opponentCard.id == CardId.NECROMANCIEN) {
                updatedGame = updatedGame.copy(
                    playerDiscard = updatedGame.playerDiscard + CardEffectManager.createRandomSkeletonCard()
                )
            }

            if (
                !updatedGame.opponentPostCombatSacrificeHandled &&
                opponentPower > playerPower &&
                opponentCard.id == CardId.ARAIGNEEGEANTE
            ) {
                return updatedGame.copy(
                    pendingChoice = CardEffectManager.buildAraigneeGeantePendingChoice(
                        game = updatedGame,
                        owner = ChoiceOwner.OPPONENT
                    ),
                    infoMessage = null
                )
            }

            // OEUF D'ARAIGNÉE — poison si perte
            if (opponentLostCombat && opponentCard.id == CardId.OEUFDARAIGNEE) {
                updatedGame = CardEffectManager.addPoisonToken(
                    game = updatedGame,
                    target = ChoiceOwner.PLAYER,
                    amount = 1
                )
            }
            // OEUF D'ARAIGNÉE — choix après combat
            if (
                !updatedGame.opponentPostCombatSacrificeHandled &&
                opponentCard.id == CardId.OEUFDARAIGNEE
            ) {
                return updatedGame.copy(
                    pendingChoice = CardEffectManager.buildOeufAraigneePendingChoice(
                        game = updatedGame,
                        owner = ChoiceOwner.OPPONENT
                    ),
                    infoMessage = null
                )
            }

            // GARDIEN DES ENFERS
            if (opponentCard.id == CardId.GARDIENDESENFERS) {
                val opponentWon = opponentPower > playerPower
                val diff = opponentPower - playerPower

                if (opponentWon && diff > 0) {
                    updatedGame = CardEffectManager.applyBretteur(
                        game = updatedGame,
                        owner = ChoiceOwner.OPPONENT,
                        amount = diff
                    )
                }
            }

            if (
                !updatedGame.opponentPostCombatSacrificeHandled &&
                (
                        opponentCard.id == CardId.DEVINDELUMIERE ||
                                opponentCard.id == CardId.DEVINDESTENEBRES ||
                                opponentCard.id == CardId.AGILE
                        )
            ) {
                return updatedGame.copy(
                    pendingChoice = CardEffectManager.buildPostCombatSacrificePendingChoice(
                        cardId = opponentCard.id,
                        owner = ChoiceOwner.OPPONENT
                    ),
                    infoMessage = null
                )
            }

            if (opponentLostCombat && opponentCard.id == CardId.DURACUIRE) {
                updatedGame = updatedGame.copy(
                    opponentNextCardPowerBonus = updatedGame.opponentNextCardPowerBonus + 2,
                    opponentNextCardDamageBonus = updatedGame.opponentNextCardDamageBonus + 1
                )
            }
            if (opponentLostCombat && opponentCard.id == CardId.EPINENOIRE) {
                updatedGame = updatedGame.copy(
                    playerHp = maxOf(0, updatedGame.playerHp - 1)
                )
            }

            if (opponentLostCombat && opponentCard.id == CardId.DANSEUSEMACABRE) {
                updatedGame = updatedGame.copy(
                    opponentGold = updatedGame.opponentGold + 2
                )
            }

            if (!opponentLostCombat && opponentCard.id == CardId.SANGPACTE) {
                updatedGame = updatedGame.copy(
                    opponentHp = maxOf(0, updatedGame.opponentHp - 1),
                    opponentGold = updatedGame.opponentGold + 2
                )
            }

            if (opponentCard.id == CardId.BULLDOZER) {
                val opponentWon = opponentPower > playerPower
                val diff = opponentPower - playerPower

                if (
                    opponentWon &&
                    diff >= 4 &&
                    CardEffectManager.canDestroyOneCardWithFiveCardRule(
                        game = updatedGame,
                        isPlayer = true
                    )
                ) {
                    updatedGame = updatedGame.copy(
                        lastPlayerCard = null,
                        playerDisplayedTurnCard = null
                    )
                }
            }

            if (opponentCard.id == CardId.PORTEURDEGIDEDECHU) {
                if (opponentLostCombat) {
                    updatedGame = updatedGame.copy(
                        opponentHp = updatedGame.opponentHp + 1
                    )
                }

                if (opponentPower > playerPower) {
                    updatedGame = CardEffectManager.applyBretteur(
                        game = updatedGame,
                        owner = ChoiceOwner.OPPONENT,
                        amount = 1
                    )
                }
            }
        }

        return updatedGame
    }
}