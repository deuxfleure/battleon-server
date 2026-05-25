package com.battleon

object TrainingAiPendingChoiceResolver {

    private fun chooseAiScryCardIndex(game: GameState): Int? {
        val scryState = game.activeScryState ?: return null
        val cards = scryState.revealedCards

        if (cards.isEmpty()) return null

        return when (scryState.sourceCardId) {

            // -------------------------------------------------
            // DEVIN DE LUMIÈRE
            // Priorité :
            // 1) défausser CURSED si présent
            // 2) défausser COLLECTOR si l'IA a moins de 10 PV
            // 3) sinon prendre la première carte restante
            // -------------------------------------------------
            CardId.DEVINDELUMIERE.name -> {
                val cursedIndex = cards.indexOfFirst { it.id == CardId.CURSED }
                if (cursedIndex != -1) return cursedIndex

                val aiHp = if (scryState.resolver == ChoiceOwner.OPPONENT) {
                    game.opponentHp
                } else {
                    game.playerHp
                }

                if (aiHp < 10) {
                    val collectorIndex = cards.indexOfFirst { it.id == CardId.COLLECTOR }
                    if (collectorIndex != -1) return collectorIndex
                }

                0
            }

            // -------------------------------------------------
            // DEVIN DES TÉNÈBRES
            // Si PV adverses > 10 :
            //   défausser tout sauf CURSED
            // Si PV adverses <= 10 :
            //   défausser les cartes de force > 2
            // Sinon prendre la première carte restante
            // -------------------------------------------------
            CardId.DEVINDESTENEBRES.name -> {
                val opponentHp = if (scryState.resolver == ChoiceOwner.OPPONENT) {
                    game.playerHp
                } else {
                    game.opponentHp
                }

                if (opponentHp > 10) {
                    val discardableIndex = cards.indexOfFirst { it.id != CardId.CURSED }
                    if (discardableIndex != -1) return discardableIndex
                } else {
                    val discardableIndex = cards.indexOfFirst { it.power > 2 }
                    if (discardableIndex != -1) return discardableIndex
                }

                0
            }

            CardId.REVENDEUR.name -> 0

            else -> 0
        }
    }

    private fun chooseAiScryAction(game: GameState): String {
        val scryState = game.activeScryState ?: return "SCRY_RETURN"
        val selectedIndex = scryState.selectedCardIndex ?: return "SCRY_RETURN"
        val selectedCard = scryState.revealedCards.getOrNull(selectedIndex) ?: return "SCRY_RETURN"

        return when (scryState.sourceCardId) {
            // -------------------------------------------------
            // REVENDEUR
            // -------------------------------------------------
            CardId.REVENDEUR.name -> {
                if (selectedCard.id == CardId.CURSED && scryState.canDiscardViewedCards) {
                    "SCRY_DISCARD"
                } else {
                    "SCRY_RETURN"
                }
            }

            // -------------------------------------------------
            // DEVIN DE LUMIÈRE
            // -------------------------------------------------
            CardId.DEVINDELUMIERE.name -> {
                val aiHp = if (scryState.resolver == ChoiceOwner.OPPONENT) {
                    game.opponentHp
                } else {
                    game.playerHp
                }

                when {
                    selectedCard.id == CardId.CURSED && scryState.canDiscardViewedCards -> "SCRY_DISCARD"
                    selectedCard.id == CardId.COLLECTOR && aiHp < 10 && scryState.canDiscardViewedCards -> "SCRY_DISCARD"
                    else -> "SCRY_RETURN"
                }
            }

            // -------------------------------------------------
            // DEVIN DES TÉNÈBRES
            // -------------------------------------------------
            CardId.DEVINDESTENEBRES.name -> {
                val opponentHp = if (scryState.resolver == ChoiceOwner.OPPONENT) {
                    game.playerHp
                } else {
                    game.opponentHp
                }

                when {
                    opponentHp > 10 &&
                            selectedCard.id != CardId.CURSED &&
                            scryState.canDiscardViewedCards -> "SCRY_DISCARD"

                    opponentHp <= 10 &&
                            selectedCard.power > 2 &&
                            scryState.canDiscardViewedCards -> "SCRY_DISCARD"

                    else -> "SCRY_RETURN"
                }
            }

            else -> "SCRY_RETURN"
        }
    }

    private fun chooseAiPostCombatSacrifice(
        game: GameState,
        pendingChoice: PendingChoice
    ): String {
        val ownerIsPlayer = pendingChoice.owner == ChoiceOwner.PLAYER

        return when (pendingChoice.cardId) {

            // Sacrifie si l'IA a moins de 5 PV
            CardId.DEVINDELUMIERE.name -> {
                val aiHp = if (ownerIsPlayer) game.playerHp else game.opponentHp
                if (aiHp < 5) "SACRIFICE_YES" else "SACRIFICE_NO"
            }

            // Sacrifie si l'adversaire a moins de 5 PV
            CardId.DEVINDESTENEBRES.name -> {
                val opponentHp = if (ownerIsPlayer) game.opponentHp else game.playerHp
                if (opponentHp < 5) "SACRIFICE_YES" else "SACRIFICE_NO"
            }

            else -> "SACRIFICE_NO"
        }
    }

    private fun chooseAiSentinelleBuyDestroy(game: GameState, pendingChoice: PendingChoice): String {
        val discard = if (pendingChoice.owner == ChoiceOwner.OPPONENT) {
            game.opponentDiscard
        } else {
            game.playerDiscard
        }

        // Priorité 1 : détruire CURSED
        val cursedIndex = discard.indexOfFirst { it.id == CardId.CURSED }
        if (cursedIndex != -1) {
            return "DISCARD:$cursedIndex"
        }

        // Priorité 2 : détruire WARRIOR
        val warriorIndex = discard.indexOfFirst { it.id == CardId.WARRIOR }
        if (warriorIndex != -1) {
            return "DISCARD:$warriorIndex"
        }

        return "PASS"
    }

    fun chooseOption(
        game: GameState,
        pendingChoice: PendingChoice
    ): String {

        return when (pendingChoice.type) {

            // -----------------------------------
            // MAUVAIS GÉNIE
            // -----------------------------------
            "BAD_GENIE_WEAPON" -> {

                val opponentStillHasCursed =
                    game.opponentDeck.any { it.id == CardId.CURSED } ||
                            game.opponentDiscard.any { it.id == CardId.CURSED }

                val opponentStillHasWarrior =
                    game.opponentDeck.any { it.id == CardId.WARRIOR } ||
                            game.opponentDiscard.any { it.id == CardId.WARRIOR }

                if (
                    opponentStillHasCursed ||
                    opponentStillHasWarrior
                ) {

                    when {

                        pendingChoice.options.contains("CARD:CURSED") ->
                            "CARD:CURSED"

                        pendingChoice.options.contains("CARD:WARRIOR") ->
                            "CARD:WARRIOR"

                        else ->
                            "PASS"
                    }

                } else {

                    if (pendingChoice.options.contains("SELF")) {
                        "SELF"
                    } else {
                        "PASS"
                    }
                }
            }

            // -----------------------------------
            // ENVOUTEUSE
            // -----------------------------------
            "ENVOUTEUSE_HEAL" -> {

                when {

                    pendingChoice.options.contains("CARD:CURSED") ->
                        "CARD:CURSED"

                    pendingChoice.options.contains("CARD:WARRIOR") ->
                        "CARD:WARRIOR"

                    else -> {
                        val aiHp = if (pendingChoice.owner == ChoiceOwner.OPPONENT) {
                            game.opponentHp
                        } else {
                            game.playerHp
                        }

                        val discard = if (pendingChoice.owner == ChoiceOwner.OPPONENT) {
                            game.opponentDiscard
                        } else {
                            game.playerDiscard
                        }

                        if (aiHp < 5) {
                            val bestCard = discard
                                .filter { it.power >= 2 }
                                .maxByOrNull { it.power }

                            if (bestCard != null &&
                                pendingChoice.options.contains("CARD:${bestCard.id.name}")
                            ) {
                                "CARD:${bestCard.id.name}"
                            } else {
                                "PASS"
                            }
                        } else if (aiHp < 10) {
                            val firstValidCard = discard.firstOrNull { it.power >= 2 }

                            if (firstValidCard != null &&
                                pendingChoice.options.contains("CARD:${firstValidCard.id.name}")
                            ) {
                                "CARD:${firstValidCard.id.name}"
                            } else {
                                "PASS"
                            }
                        } else {
                            "PASS"
                        }
                    }
                }
            }

            // -----------------------------------
            // MAGE
            // -----------------------------------
            "MAGE_CHOICE" -> {

                val simpleChoices =
                    pendingChoice.options.filter {
                        it == "BRUTE" || it == "POWER"
                    }

                if (simpleChoices.isNotEmpty()) {
                    simpleChoices.random()
                } else {
                    pendingChoice.options.first()
                }
            }

            // -----------------------------------
            // MAGE DESTROY RESERVE
            // (simple : choix aléatoire)
            // -----------------------------------
            "MAGE_DESTROY_RESERVE" -> {

                if (pendingChoice.options.isNotEmpty()) {
                    pendingChoice.options.random()
                } else {
                    "PASS"
                }
            }

            // -----------------------------------
            // TACTICIEN
            // -----------------------------------
            "TACTICIEN_REPLACE_REVEALED" -> {

                val aiIsOpponent = pendingChoice.owner == ChoiceOwner.OPPONENT

                val aiCard = if (aiIsOpponent) {
                    game.lastOpponentCard
                } else {
                    game.lastPlayerCard
                }

                val enemyCard = if (aiIsOpponent) {
                    game.lastPlayerCard
                } else {
                    game.lastOpponentCard
                }

                if (aiCard == null || enemyCard == null) {
                    "PASS"
                } else {
                    val aiPower = CardEffectManager.getEffectivePower(
                        game = game,
                        card = aiCard,
                        ownerGold = if (aiIsOpponent) game.opponentGold else game.playerGold,
                        nextCardPowerBonus = if (aiIsOpponent) {
                            game.opponentNextCardPowerBonus
                        } else {
                            game.playerNextCardPowerBonus
                        },
                        isPlayer = !aiIsOpponent
                    )

                    val enemyPower = CardEffectManager.getEffectivePower(
                        game = game,
                        card = enemyCard,
                        ownerGold = if (aiIsOpponent) game.playerGold else game.opponentGold,
                        nextCardPowerBonus = if (aiIsOpponent) {
                            game.playerNextCardPowerBonus
                        } else {
                            game.opponentNextCardPowerBonus
                        },
                        isPlayer = aiIsOpponent
                    )

                    val aiWillLoseCombat = aiPower < enemyPower

                    val enemyHasAmbush = enemyCard.hasAmbush

                    val tacticienExceptionCards = setOf(
                        CardId.PYROMANCIEN
                    )

                    val enemyIsExceptionCard = enemyCard.id in tacticienExceptionCards

                    if (
                        aiWillLoseCombat ||
                        enemyHasAmbush ||
                        enemyIsExceptionCard
                    ) {
                        "REPLACE"
                    } else {
                        "PASS"
                    }
                }
            }

            // -----------------------------------
            // CHAMANE
            // -----------------------------------
            "CHAMANE_SUBTERFUGE" -> {
                val ownPreviewCard = pendingChoice.previewOwnTopCard

                val shouldDestroyOwnCard =
                    ownPreviewCard?.id == CardId.CURSED ||
                            ownPreviewCard?.id == CardId.WARRIOR

                if (
                    shouldDestroyOwnCard &&
                    pendingChoice.options.contains("DESTROY_OWN_TOP")
                ) {
                    "DESTROY_OWN_TOP"
                } else {
                    "DISCARD_OPPONENT_TOP"
                }
            }

            // -----------------------------------
            // SCRUTER — choix de la carte à traiter
            // -----------------------------------
            "SCRY_SELECT_CARD" -> {
                val index = chooseAiScryCardIndex(game)

                if (index == null) {
                    "SCRY_FINISH"
                } else {
                    "SCRY_CARD:$index"
                }
            }

            // -----------------------------------
            // SCRUTER — action sur la carte choisie
            // -----------------------------------
            "SCRY_CHOOSE_ACTION" -> {
                chooseAiScryAction(game)
            }

            // -----------------------------------
            // SACRIFICE POST-COMBAT
            // -----------------------------------
            "POST_COMBAT_SACRIFICE" -> {
                chooseAiPostCombatSacrifice(game, pendingChoice)
            }

            // -----------------------------------
            // Sentinelle Buy
            // -----------------------------------
            "SENTINELLE_BUY_DESTROY" -> {
                chooseAiSentinelleBuyDestroy(game, pendingChoice)
            }

            else -> {
                pendingChoice.options.firstOrNull() ?: "PASS"
            }
        }
    }
}