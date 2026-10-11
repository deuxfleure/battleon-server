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

        // =====================================================
        // TIQUE — soin différé après le combat
        //
        // L'activation depuis la défausse a déjà :
        // - détruit Tique
        // - consommé tous les jetons Sang
        // - ajouté leur quantité à la Force de la carte active
        //
        // Le soin dépend maintenant de la différence de Force finale.
        // Le flag est immédiatement consommé pour éviter un second soin
        // si POST_COMBAT est résolu plusieurs fois.
        // =====================================================

        if (updatedGame.playerTiqueHealPending) {
            val healAmount = maxOf(
                0,
                playerPower - opponentPower
            )

            updatedGame = updatedGame.copy(
                playerTiqueHealPending = false
            )

            if (healAmount > 0) {
                updatedGame = TokenManager.heal(
                    game = updatedGame,
                    target = ChoiceOwner.PLAYER,
                    amount = healAmount
                )
            }
        }

        if (updatedGame.opponentTiqueHealPending) {
            val healAmount = maxOf(
                0,
                opponentPower - playerPower
            )

            updatedGame = updatedGame.copy(
                opponentTiqueHealPending = false
            )

            if (healAmount > 0) {
                updatedGame = TokenManager.heal(
                    game = updatedGame,
                    target = ChoiceOwner.OPPONENT,
                    amount = healAmount
                )
            }
        }

        //=============================================
        //======== ------ COTER Joueur ---- ===========
        //=============================================
        if (!game.playerEffectBlockedBySentinelle) {

            // VENIMEUX — déclenchement unique après une victoire
            if (!updatedGame.playerPostCombatVenomousHandled) {
                updatedGame = updatedGame.copy(
                    playerPostCombatVenomousHandled = true
                )

                if (playerWonCombat) {
                    val venomous = CardEffectManager.getEffectiveVenomous(
                        game = updatedGame,
                        card = playerCard,
                        isPlayer = true,
                        currentBonus = game.playerCurrentCardVenomousBonus,
                        effectsBlocked = game.playerCardEffectsBlockedByAmbush
                    )

                    if (venomous > 0) {
                        updatedGame = CardEffectManager.addPoisonToken(
                            game = updatedGame,
                            target = ChoiceOwner.OPPONENT,
                            source = ChoiceOwner.PLAYER,
                            amount = venomous,
                        )
                    }
                }
            }

            if (playerWonCombat && playerCard.id == CardId.NECROMANCIEN) {
                updatedGame = updatedGame.copy(
                    opponentDiscard = updatedGame.opponentDiscard + CardEffectManager.createRandomSkeletonCard()
                )
            }

            if (playerWonCombat && playerCard.id == CardId.ECUMEDESMERS) {
                updatedGame = CardEffectManager.addBurnToken(
                    game = updatedGame,
                    target = ChoiceOwner.OPPONENT,
                )
            }
            // DISCIPLE DE LA FLAMME
            if (playerCard.id == CardId.DISCIPLEDELAFLAMME) {

                // S'il gagne, il inflige Brûlure.
                if (playerWonCombat) {
                    updatedGame = CardEffectManager.addBurnToken(
                        game = updatedGame,
                        target = ChoiceOwner.OPPONENT
                    )
                }

                // Dans tous les cas, la prochaine carte inflige +1 dégât.
                updatedGame = updatedGame.copy(
                    playerNextCardDamageBonus =
                        updatedGame.playerNextCardDamageBonus + 1
                )
            }

            if (playerLostCombat && playerCard.id == CardId.TIQUE) {
                updatedGame = CardEffectManager.addBloodToken(
                    game = updatedGame,
                    target = ChoiceOwner.PLAYER
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
                    source = ChoiceOwner.PLAYER,
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

            // DÉVOREUR D'ESPOIRS
            if (playerCard.id == CardId.DEVOREURDESPOIRS) {
                if (playerLostCombat) {
                    // S'il perd : l'adversaire perd 1 Or.
                    updatedGame = updatedGame.copy(
                        opponentGold = maxOf(0, updatedGame.opponentGold - 1)
                    )
                }

                if (playerWonCombat) {
                    // S'il gagne : la prochaine carte adverse perd 1 Force.
                    updatedGame = updatedGame.copy(
                        opponentNextCardPowerBonus =
                            updatedGame.opponentNextCardPowerBonus - 1
                    )
                }
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
                                playerCard.id == CardId.AGILE ||
                                playerCard.id == CardId.GARDEVAUDOU
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

            if (playerLostCombat && playerCard.id == CardId.COSTAUD) {
                updatedGame = updatedGame.copy(
                    opponentNextCardPowerBonus = updatedGame.opponentNextCardPowerBonus - 2
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

            if (
                playerLostCombat &&
                playerCard.id == CardId.REPRESAILLES &&
                !updatedGame.playerPostCombatSacrificeHandled
            ) {
                updatedGame = CardEffectManager.addPoisonToken(
                    game = updatedGame,
                    target = ChoiceOwner.OPPONENT,
                    source = ChoiceOwner.PLAYER,
                    amount = 1
                )

                return CardEffectManager.startScry(
                    game = updatedGame,
                    sourceId = CardId.REPRESAILLES.name,
                    resolver = ChoiceOwner.PLAYER,
                    target = ChoiceOwner.OPPONENT,
                    amount = 1,
                    completionContext = ScryCompletionContext.POST_COMBAT,
                    canDiscardViewedCards = true
                )
            }

            if (playerCard.id == CardId.PORTEURDEGIDEDECHU) {
                if (playerLostCombat) {
                    updatedGame = TokenManager.heal(
                        game = updatedGame,
                        target = ChoiceOwner.PLAYER,
                        amount = 1
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

            // VENIMEUX — déclenchement unique après une victoire
            if (!updatedGame.opponentPostCombatVenomousHandled) {
                updatedGame = updatedGame.copy(
                    opponentPostCombatVenomousHandled = true
                )

                if (opponentWonCombat) {
                    val venomous = CardEffectManager.getEffectiveVenomous(
                        game = updatedGame,
                        card = opponentCard,
                        isPlayer = false,
                        currentBonus = game.opponentCurrentCardVenomousBonus,
                        effectsBlocked = game.opponentCardEffectsBlockedByAmbush
                    )

                    if (venomous > 0) {
                        updatedGame = CardEffectManager.addPoisonToken(
                            game = updatedGame,
                            target = ChoiceOwner.PLAYER,
                            source = ChoiceOwner.OPPONENT,
                            amount = venomous
                        )
                    }
                }
            }

            if (opponentWonCombat && opponentCard.id == CardId.NECROMANCIEN) {
                updatedGame = updatedGame.copy(
                    playerDiscard = updatedGame.playerDiscard + CardEffectManager.createRandomSkeletonCard()
                )
            }

            if (opponentWonCombat && opponentCard.id == CardId.ECUMEDESMERS) {
                updatedGame = CardEffectManager.addBurnToken(
                    game = updatedGame,
                    target = ChoiceOwner.PLAYER
                )
            }

            // DISCIPLE DE LA FLAMME
            if (opponentCard.id == CardId.DISCIPLEDELAFLAMME) {

                // S'il gagne, il inflige Brûlure.
                if (opponentWonCombat) {
                    updatedGame = CardEffectManager.addBurnToken(
                        game = updatedGame,
                        target = ChoiceOwner.PLAYER
                    )
                }

                // Dans tous les cas, la prochaine carte inflige +1 dégât.
                updatedGame = updatedGame.copy(
                    opponentNextCardDamageBonus =
                        updatedGame.opponentNextCardDamageBonus + 1
                )
            }

            if (opponentLostCombat && opponentCard.id == CardId.TIQUE) {
                updatedGame = CardEffectManager.addBloodToken(
                    game = updatedGame,
                    target = ChoiceOwner.OPPONENT
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

            // DÉVOREUR D'ESPOIRS
            if (opponentCard.id == CardId.DEVOREURDESPOIRS) {
                if (opponentLostCombat) {
                    // S'il perd : le joueur perd 1 Or.
                    updatedGame = updatedGame.copy(
                        playerGold = maxOf(0, updatedGame.playerGold - 1)
                    )
                }

                if (opponentWonCombat) {
                    // S'il gagne : la prochaine carte du joueur perd 1 Force.
                    updatedGame = updatedGame.copy(
                        playerNextCardPowerBonus =
                            updatedGame.playerNextCardPowerBonus - 1
                    )
                }
            }

            // OEUF D'ARAIGNÉE — poison si perte
            if (opponentLostCombat && opponentCard.id == CardId.OEUFDARAIGNEE) {
                updatedGame = CardEffectManager.addPoisonToken(
                    game = updatedGame,
                    target = ChoiceOwner.PLAYER,
                    source = ChoiceOwner.OPPONENT,
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
                                opponentCard.id == CardId.AGILE ||
                                opponentCard.id == CardId.GARDEVAUDOU
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

            if (opponentLostCombat && opponentCard.id == CardId.COSTAUD) {
                updatedGame = updatedGame.copy(
                    playerNextCardPowerBonus = updatedGame.playerNextCardPowerBonus - 2
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

            if (
                opponentLostCombat &&
                opponentCard.id == CardId.REPRESAILLES &&
                !updatedGame.opponentPostCombatSacrificeHandled
            ) {
                updatedGame = CardEffectManager.addPoisonToken(
                    game = updatedGame,
                    target = ChoiceOwner.PLAYER,
                    source = ChoiceOwner.OPPONENT,
                    amount = 1
                )

                return CardEffectManager.startScry(
                    game = updatedGame,
                    sourceId = CardId.REPRESAILLES.name,
                    resolver = ChoiceOwner.OPPONENT,
                    target = ChoiceOwner.PLAYER,
                    amount = 1,
                    completionContext = ScryCompletionContext.POST_COMBAT,
                    canDiscardViewedCards = true
                )
            }


            if (opponentCard.id == CardId.PORTEURDEGIDEDECHU) {
                if (opponentLostCombat) {
                    updatedGame = TokenManager.heal(
                        game = updatedGame,
                        target = ChoiceOwner.OPPONENT,
                        amount = 1
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