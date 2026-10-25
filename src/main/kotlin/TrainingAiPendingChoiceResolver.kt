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

    private fun chooseAiExperienceLaboratoire(
        game: GameState,
        pendingChoice: PendingChoice
    ): String {
        val ownerIsPlayer = pendingChoice.owner == ChoiceOwner.PLAYER

        val opponentCard = if (ownerIsPlayer) {
            game.lastOpponentCard
        } else {
            game.lastPlayerCard
        }

        if (opponentCard?.id == CardId.SENTINELLE) {
            return "LAB_CLOSE"
        }

        val aiHp = if (ownerIsPlayer) {
            game.playerHp
        } else {
            game.opponentHp
        }

        val aiGold = if (ownerIsPlayer) {
            game.playerGold
        } else {
            game.opponentGold
        }

        val aiCurrentPower = pendingChoice.ownerCurrentPower
            ?: return "LAB_CLOSE"

        val opponentCurrentPower = pendingChoice.opponentCurrentPower
            ?: return "LAB_CLOSE"

        val aiAlreadyHasBrute = if (ownerIsPlayer) {
            game.playerCurrentCardHasBruteBonus
        } else {
            game.opponentCurrentCardHasBruteBonus
        }

        val canBuyPower =
            "LAB_POWER" in pendingChoice.options &&
                    aiGold >= 1

        val canBuyBrute =
            "LAB_BRUTE" in pendingChoice.options &&
                    aiGold >= 2 &&
                    !aiAlreadyHasBrute


        // =====================================================
        // PRIORITÉ 1 — IA en danger : 5 PV ou moins
        // =====================================================
        if (aiHp <= 5) {

            // Avant de dépenser quoi que ce soit, l'IA vérifie
            // qu'elle peut au minimum atteindre l'égalité.
            if (aiCurrentPower < opponentCurrentPower) {
                val goldNeededToTie =
                    opponentCurrentPower - aiCurrentPower

                if (aiGold < goldNeededToTie) {
                    return "LAB_CLOSE"
                }

                return if (canBuyPower) {
                    "LAB_POWER"
                } else {
                    "LAB_CLOSE"
                }
            }

            // À égalité, elle dépense encore 1 Or si possible
            // afin de gagner le combat.
            if (aiCurrentPower == opponentCurrentPower) {
                return if (canBuyPower) {
                    "LAB_POWER"
                } else {
                    "LAB_CLOSE"
                }
            }

            // À partir d'ici, l'IA gagne déjà le combat.

            // Si BRUTE est déjà actif, notamment grâce au Mage,
            // elle dépense tout son Or restant pour augmenter les dégâts.
            if (aiAlreadyHasBrute) {
                return if (canBuyPower) {
                    "LAB_POWER"
                } else {
                    "LAB_CLOSE"
                }
            }

            // Sans BRUTE, elle ne l'achète que s'il lui reste au moins
            // 3 Or : 2 pour BRUTE et au moins 1 pour augmenter ensuite
            // la différence de force.
            if (aiGold >= 3 && canBuyBrute) {
                return "LAB_BRUTE"
            }

            return "LAB_CLOSE"
        }

        // =====================================================
        // PRIORITÉ 2 — IA au-dessus de 5 PV
        // =====================================================

        // Une fois BRUTE acheté, elle dépense tout l'Or restant en force.
        if (aiAlreadyHasBrute) {
            return if (canBuyPower) {
                "LAB_POWER"
            } else {
                "LAB_CLOSE"
            }
        }

        // Force imprimée de L'Expérience de Laboratoire : 2.
        //
        // Le budget demandé garantit :
        // - 2 Or pour obtenir BRUTE ;
        // - assez de bonus de force pour terminer avec
        //   au moins 3 points d'avance.
        val requiredGold =
            (opponentCurrentPower - 2) + 5

        return if (
            aiGold >= requiredGold &&
            canBuyBrute
        ) {
            "LAB_BRUTE"
        } else {
            "LAB_CLOSE"
        }
    }

    private fun chooseAiEnrage(
        game: GameState,
        pendingChoice: PendingChoice
    ): String {
        val ownerIsPlayer = pendingChoice.owner == ChoiceOwner.PLAYER

        val aiHp = if (ownerIsPlayer) game.playerHp else game.opponentHp
        val enemyHp = if (ownerIsPlayer) game.opponentHp else game.playerHp
        val aiGold = if (ownerIsPlayer) game.playerGold else game.opponentGold

        val aiPower = if (ownerIsPlayer) {
            game.playerEffectivePower
        } else {
            game.opponentEffectivePower
        }

        val enemyPower = if (ownerIsPlayer) {
            game.opponentEffectivePower
        } else {
            game.playerEffectivePower
        }

        val aiCard = if (ownerIsPlayer) game.lastPlayerCard else game.lastOpponentCard

        val hasBrute = aiCard != null && CardEffectManager.hasBrute(
            card = aiCard,
            nextCardHasBrute = if (ownerIsPlayer) {
                game.playerCurrentCardHasBruteBonus
            } else {
                game.opponentCurrentCardHasBruteBonus
            }
        )

        val currentCombatDamage = if (ownerIsPlayer) {
            game.playerEnrageCombatDamage
        } else {
            game.opponentEnrageCombatDamage
        }

        val bonusDamage = if (ownerIsPlayer) {
            game.playerCurrentCardDamageBonus
        } else {
            game.opponentCurrentCardDamageBonus
        }

        val winsCombat = aiPower > enemyPower

        val available = pendingChoice.options

        fun canBuy(option: String): Boolean = option in available

        val combatDamage = if (!winsCombat) {
            0
        } else if (hasBrute) {
            aiPower - enemyPower + bonusDamage
        } else {
            currentCombatDamage + bonusDamage
        }

        // -------------------------------------------------
        // PRIORITÉ 1 — Chercher une victoire immédiate
        // -------------------------------------------------

        val fatalOptions = listOf(
            "ENRAGE_COMBAT_3",
            "ENRAGE_DIRECT_2",
            "ENRAGE_DIRECT_1"
        )

        val costs = mapOf(
            "ENRAGE_COMBAT_3" to 3,
            "ENRAGE_DIRECT_2" to 3,
            "ENRAGE_DIRECT_1" to 2
        )

        val availableFatalOptions = fatalOptions.filter { canBuy(it) }

        val combinations = (0 until (1 shl availableFatalOptions.size)).map { mask ->
            availableFatalOptions.filterIndexed { index, _ ->
                mask and (1 shl index) != 0
            }
        }

        val cheapestFatalCombination = combinations
            .filter { combination ->
                val cost = combination.sumOf { costs.getValue(it) }

                if (cost > aiGold) {
                    false
                } else {
                    val directDamage =
                        (if ("ENRAGE_DIRECT_1" in combination) 1 else 0) +
                                (if ("ENRAGE_DIRECT_2" in combination) 2 else 0)

                    val finalCombatDamage = if (
                        winsCombat &&
                        !hasBrute &&
                        "ENRAGE_COMBAT_3" in combination
                    ) {
                        3 + bonusDamage
                    } else {
                        combatDamage
                    }

                    finalCombatDamage + directDamage >= enemyHp
                }
            }
            .minWithOrNull(
                compareBy<List<String>>(
                    { combination -> combination.sumOf { costs.getValue(it) } },
                    { combination -> combination.size }
                )
            )

        if (cheapestFatalCombination != null &&
            cheapestFatalCombination.isNotEmpty()
        ) {
            return cheapestFatalCombination.first()
        }

        // -------------------------------------------------
        // PRIORITÉ 2 — IA en avance et hors danger
        // -------------------------------------------------

        if (aiHp > 10 && aiHp >= enemyHp) {
            return "ENRAGE_CLOSE"
        }

        // -------------------------------------------------
        // PRIORITÉ 3 — IA derrière mais hors danger
        // -------------------------------------------------

        if (aiHp > 10) {
            if (winsCombat && !hasBrute && canBuy("ENRAGE_COMBAT_3")) {
                return "ENRAGE_COMBAT_3"
            }

            if (canBuy("ENRAGE_DIRECT_2")) {
                return "ENRAGE_DIRECT_2"
            }

            return "ENRAGE_CLOSE"
        }

        // -------------------------------------------------
        // PRIORITÉ 4 — IA en danger (10 PV ou moins)
        // -------------------------------------------------

        if (winsCombat && !hasBrute && canBuy("ENRAGE_COMBAT_3")) {
            return "ENRAGE_COMBAT_3"
        }

        if (canBuy("ENRAGE_DIRECT_2")) {
            return "ENRAGE_DIRECT_2"
        }

        if (canBuy("ENRAGE_DIRECT_1")) {
            return "ENRAGE_DIRECT_1"
        }

        return "ENRAGE_CLOSE"
    }

    fun chooseOption(
        game: GameState,
        pendingChoice: PendingChoice
    ): String {

        return when (pendingChoice.type) {

            // -----------------------------------
            // MALIN — deuxième effet
            // IA de base : toujours +1 Force
            // -----------------------------------
            "MALIN_SECOND_CHOICE" -> {
                "NEXT_POWER_1"
            }

            // -----------------------------------
            // MALIN — Débusquer
            // Priorité au Trappeur, sinon aléatoire
            // -----------------------------------
            "DEBUSQUER_CHOICE" -> {

                val enemyAmbush = if (pendingChoice.owner == ChoiceOwner.OPPONENT) {
                    game.playerAmbush
                } else {
                    game.opponentAmbush
                }

                val trapperIndex = enemyAmbush.indexOfFirst {
                    it.id == CardId.TRAPPEUR
                }

                val trapperOption = "DISCARD:$trapperIndex"

                if (
                    trapperIndex >= 0 &&
                    trapperOption in pendingChoice.options
                ) {
                    trapperOption
                } else {
                    val validTargets = pendingChoice.options.filter {
                        it.startsWith("DISCARD:")
                    }

                    validTargets.randomOrNull() ?: "PASS"
                }
            }

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
            // ENRAGÉ
            // -----------------------------------
            "ENRAGE_CHOICE" -> {
                chooseAiEnrage(
                    game = game,
                    pendingChoice = pendingChoice
                )
            }

            // -----------------------------------
            // EXPÉRIENCE DE LABORATOIRE
            // -----------------------------------
            "EXPERIENCE_LABORATOIRE_CHOICE" -> {
                chooseAiExperienceLaboratoire(
                    game = game,
                    pendingChoice = pendingChoice
                )
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