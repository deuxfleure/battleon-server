package com.battleon

/** Résolution des effets post-combat selon la priorité du combat.
 * Les drapeaux persistants permettent de reprendre après les choix.
 */
object PostCombatEffectResolver {

    private fun getEffectProgress(
        game: GameState,
        owner: ChoiceOwner
    ): PostCombatEffectProgress? {
        return when (owner) {
            ChoiceOwner.PLAYER -> game.playerPostCombatEffectProgress
            ChoiceOwner.OPPONENT -> game.opponentPostCombatEffectProgress
        }
    }

    private fun setEffectProgress(
        game: GameState,
        owner: ChoiceOwner,
        progress: PostCombatEffectProgress?
    ): GameState {
        return when (owner) {
            ChoiceOwner.PLAYER -> game.copy(
                playerPostCombatEffectProgress = progress
            )

            ChoiceOwner.OPPONENT -> game.copy(
                opponentPostCombatEffectProgress = progress
            )
        }
    }

    fun apply(game: GameState): GameState {
        if (game.pendingChoice != null || game.activeScryState != null) return game

        // La Force finale est conservée par COMBAT : ne pas la recalculer après
        // la destruction d'une carte ou la modification de l'Or pendant POST_COMBAT.
        val playerPower = game.playerEffectivePower
        val opponentPower = game.opponentEffectivePower

        val order = game.postCombatResolutionOrder ?: determineResolutionOrder(
            playerHasPrems = game.lastPlayerCard?.let { hasPrems(it) } ?: false,
            opponentHasPrems = game.lastOpponentCard?.let { hasPrems(it) } ?: false,
            playerPower = playerPower,
            opponentPower = opponentPower,
            playerHp = game.playerHp,
            opponentHp = game.opponentHp,
            playerGold = game.playerGold,
            opponentGold = game.opponentGold
        )
        var result = game.copy(postCombatResolutionOrder = order)
        val sides = when (order) {
            ResolutionOrder.PLAYER_FIRST -> listOf(ChoiceOwner.PLAYER, ChoiceOwner.OPPONENT)
            ResolutionOrder.OPPONENT_FIRST -> listOf(ChoiceOwner.OPPONENT, ChoiceOwner.PLAYER)
        }
        for (side in sides) {
            result = if (side == ChoiceOwner.PLAYER) {
                resolvePlayerPostCombat(result, playerPower, opponentPower)
            } else {
                resolveOpponentPostCombat(result, playerPower, opponentPower)
            }
            if (result.pendingChoice != null || result.activeScryState != null) return result
        }
        return result
    }

    private fun resolvePlayerPostCombat(
        game: GameState,
        playerPower: Int,
        opponentPower: Int
    ): GameState {
        var updatedGame = game
        val playerWonCombat = playerPower > opponentPower
        val playerLostCombat = playerPower < opponentPower

        // Soin différé de Tique : peut s'appliquer même si la carte révélée
        // a été détruite entretemps.
        if (updatedGame.playerTiqueHealPending) {
            updatedGame = updatedGame.copy(playerTiqueHealPending = false)
            val amount = maxOf(0, playerPower - opponentPower)
            if (amount > 0) updatedGame = TokenManager.heal(
                game = updatedGame, target = ChoiceOwner.PLAYER, amount = amount
            )
        }
        val playerCard = updatedGame.lastPlayerCard ?: return updatedGame
        if (updatedGame.playerEffectBlockedBySentinelle) return updatedGame

        if (!updatedGame.playerPostCombatCardEffectResolved) {
                // Effets automatiques ici
                // TISSEUR DE MENSONGES — Poison selon la différence de Force
                if (playerWonCombat && playerCard.id == CardId.TISSEURDEMENSONGES) {
                    updatedGame = CardEffectManager.addPoisonToken(
                        game = updatedGame,
                        target = ChoiceOwner.OPPONENT,
                        source = ChoiceOwner.PLAYER,
                        amount = playerPower - opponentPower
                    )
                }
                // Necromancien — ajout de squelette
                if (playerWonCombat && playerCard.id == CardId.NECROMANCIEN) {
                    updatedGame = updatedGame.copy(
                        opponentDiscard = updatedGame.opponentDiscard + CardEffectManager.createRandomSkeletonCard()
                    )
                }
                // ECUMEURS - Brulure
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
                // TIQUE - sang
                if (playerLostCombat && playerCard.id == CardId.TIQUE) {
                    updatedGame = CardEffectManager.addBloodToken(
                        game = updatedGame,
                        target = ChoiceOwner.PLAYER
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
                // LAME ERRANTE — BRETTEUR 2
                if (playerWonCombat && playerCard.id == CardId.LAMEERRANTE) {
                    updatedGame = CardEffectManager.applyBretteur(
                        game = updatedGame,
                        owner = ChoiceOwner.PLAYER,
                        amount = 2
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
                // DUR a cuire
                if (playerLostCombat && playerCard.id == CardId.DURACUIRE) {
                    updatedGame = updatedGame.copy(
                        playerNextCardPowerBonus = updatedGame.playerNextCardPowerBonus + 2,
                        playerNextCardDamageBonus = updatedGame.playerNextCardDamageBonus + 1
                    )
                }
                // Costaud
                if (playerLostCombat && playerCard.id == CardId.COSTAUD) {
                    updatedGame = updatedGame.copy(
                        opponentNextCardPowerBonus = updatedGame.opponentNextCardPowerBonus - 2
                    )
                }
                // Epine noire
                if (playerLostCombat && playerCard.id == CardId.EPINENOIRE) {
                    updatedGame = updatedGame.copy(
                        opponentHp = maxOf(0, updatedGame.opponentHp - 1)
                    )
                }
                // Danseusemacabre
                if (playerLostCombat && playerCard.id == CardId.DANSEUSEMACABRE) {
                    updatedGame = updatedGame.copy(
                        playerGold = updatedGame.playerGold + 2
                    )
                }
                // Sangpacte
                if (!playerLostCombat && playerCard.id == CardId.SANGPACTE) {
                    updatedGame = updatedGame.copy(
                        playerHp = maxOf(0, updatedGame.playerHp - 1),
                        playerGold = updatedGame.playerGold + 2
                    )
                }
                // Bulldozer
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
                // Porteur d'egide
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


            updatedGame = updatedGame.copy(playerPostCombatCardEffectResolved = true)
        }
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

            if (
                !updatedGame.playerPostCombatSacrificeHandled &&
                (
                        playerCard.id == CardId.DEVINDELUMIERE ||
                                playerCard.id == CardId.DEVINDESTENEBRES ||
                                playerCard.id == CardId.AGILE ||
                                playerCard.id == CardId.GARDEVAUDOU ||
                                playerCard.id == CardId.CONTAGIEUX ||
                                playerCard.id == CardId.OEIL

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

        return updatedGame
    }

    private fun resolveOpponentPostCombat(
        game: GameState,
        playerPower: Int,
        opponentPower: Int
    ): GameState {
        var updatedGame = game
        val opponentWonCombat = opponentPower > playerPower
        val opponentLostCombat = opponentPower < playerPower

        if (updatedGame.opponentTiqueHealPending) {
            updatedGame = updatedGame.copy(opponentTiqueHealPending = false)
            val amount = maxOf(0, opponentPower - playerPower)
            if (amount > 0) updatedGame = TokenManager.heal(
                game = updatedGame, target = ChoiceOwner.OPPONENT, amount = amount
            )
        }
        val opponentCard = updatedGame.lastOpponentCard ?: return updatedGame
        if (updatedGame.opponentEffectBlockedBySentinelle) return updatedGame

        if (!updatedGame.opponentPostCombatCardEffectResolved) {
            if (opponentWonCombat && opponentCard.id == CardId.TISSEURDEMENSONGES) {
                updatedGame = CardEffectManager.addPoisonToken(
                    game = updatedGame,
                    target = ChoiceOwner.PLAYER,
                    source = ChoiceOwner.OPPONENT,
                    amount = opponentPower - playerPower
                )
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

            // LAME ERRANTE — BRETTEUR 2
            if (opponentWonCombat && opponentCard.id == CardId.LAMEERRANTE) {
                updatedGame = CardEffectManager.applyBretteur(
                    game = updatedGame,
                    owner = ChoiceOwner.OPPONENT,
                    amount = 2
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

            updatedGame = updatedGame.copy(opponentPostCombatCardEffectResolved = true)
        }
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

            if (
                !updatedGame.opponentPostCombatSacrificeHandled &&
                (
                        opponentCard.id == CardId.DEVINDELUMIERE ||
                                opponentCard.id == CardId.DEVINDESTENEBRES ||
                                opponentCard.id == CardId.AGILE ||
                                opponentCard.id == CardId.GARDEVAUDOU ||
                                opponentCard.id == CardId.CONTAGIEUX ||
                                opponentCard.id == CardId.OEIL
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

        return updatedGame
    }
}
