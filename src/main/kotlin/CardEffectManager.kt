package com.battleon

object CardEffectManager {

    // =========================================================
    // 1. COMPTEURS / RÈGLES GÉNÉRALES
    // =========================================================

    fun countCards(
        game: GameState,
        isPlayer: Boolean
    ): Int {
        val deck = if (isPlayer) game.playerDeck else game.opponentDeck
        val discard = if (isPlayer) game.playerDiscard else game.opponentDiscard
        val ambush = if (isPlayer) game.playerAmbush else game.opponentAmbush
        val revealedCard = if (isPlayer) game.lastPlayerCard else game.lastOpponentCard

        val revealedCount = if (revealedCard != null) 1 else 0

        return deck.size +
                discard.size +
                ambush.size +
                revealedCount
    }

    fun canDestroyOneCardWithFiveCardRule(
        game: GameState,
        isPlayer: Boolean
    ): Boolean {
        return countCards(game, isPlayer) > 5
    }

    fun destroyOneCardFromDiscardById(
        discard: List<Card>,
        targetCardId: String
    ): List<Card> {
        var removed = false

        return discard.filter { card ->
            if (!removed && card.id.name == targetCardId) {
                removed = true
                false
            } else {
                true
            }
        }
    }

    //-----------------------
    // ------ Scrutage-------
    //-----------------------

    fun buildScryActionPendingChoice(
        game: GameState
    ): PendingChoice {
        val scryState = game.activeScryState
            ?: error("Impossible de construire un choix d'action sans activeScryState")

        val options = mutableListOf("SCRY_RETURN")

        if (scryState.canDiscardViewedCards) {
            options += "SCRY_DISCARD"
        }

        val message = if (scryState.resolver == scryState.target) {
            "Scrutage : choisissez quoi faire avec cette carte."
        } else {
            "Scrutage sombre : choisissez quoi faire avec cette carte."
        }

        return PendingChoice(
            type = "SCRY_CHOOSE_ACTION",
            cardId = scryState.sourceCardId,
            options = options,
            message = message,
            owner = scryState.resolver
        )
    }

    fun buildScrySelectCardPendingChoice(
        game: GameState
    ): PendingChoice {
        val scryState = game.activeScryState
            ?: error("Impossible de construire un choix de scrutage sans activeScryState")

        val options = scryState.revealedCards.indices.map { index ->
            "SCRY_CARD:$index"
        }

        val message = if (scryState.resolver == scryState.target) {
            "Scrutage : choisissez une carte de votre pile à traiter, ou terminez."
        } else {
            "Scrutage sombre : choisissez une carte de la pile adverse à traiter, ou terminez."
        }

        return PendingChoice(
            type = "SCRY_SELECT_CARD",
            cardId = scryState.sourceCardId,
            options = options,
            message = message,
            owner = scryState.resolver
        )
    }

    fun startScry(
        game: GameState,
        sourceCardId: CardId,
        resolver: ChoiceOwner,
        target: ChoiceOwner,
        amount: Int,
        canDiscardViewedCards: Boolean = true
    ): GameState {

        var playerDeck = game.playerDeck
        var opponentDeck = game.opponentDeck
        var playerDiscard = game.playerDiscard
        var opponentDiscard = game.opponentDiscard
        var playerGold = game.playerGold
        var opponentGold = game.opponentGold

        val revealedCards = mutableListOf<Card>()

        repeat(amount) {
            if (target == ChoiceOwner.PLAYER) {
                // Si le deck est vide, on mélange la défausse dans le deck et on gagne +1 or
                if (playerDeck.isEmpty() && playerDiscard.isNotEmpty()) {
                    playerDeck = playerDiscard.shuffled()
                    playerDiscard = emptyList()
                    playerGold += 1
                }

                val nextCard = playerDeck.firstOrNull()
                if (nextCard != null) {
                    revealedCards += nextCard
                    playerDeck = playerDeck.drop(1)
                }
            } else {
                // Même logique sur le deck adverse
                if (opponentDeck.isEmpty() && opponentDiscard.isNotEmpty()) {
                    opponentDeck = opponentDiscard.shuffled()
                    opponentDiscard = emptyList()
                    opponentGold += 1
                }

                val nextCard = opponentDeck.firstOrNull()
                if (nextCard != null) {
                    revealedCards += nextCard
                    opponentDeck = opponentDeck.drop(1)
                }
            }
        }

        val newScryState = ScryState(
            sourceCardId = sourceCardId.name,
            resolver = resolver,
            target = target,
            amount = amount,
            canDiscardViewedCards = canDiscardViewedCards,
            revealedCards = revealedCards,
            cardsToReturnOnTop = emptyList(),
            cardsToDiscard = emptyList(),
            selectedCardIndex = null
        )

        val intermediateGame = game.copy(
            playerDeck = playerDeck,
            opponentDeck = opponentDeck,
            playerDiscard = playerDiscard,
            opponentDiscard = opponentDiscard,
            playerGold = playerGold,
            opponentGold = opponentGold,
            activeScryState = newScryState,
            infoMessage = null
        )

        return intermediateGame.copy(
            pendingChoice = buildScrySelectCardPendingChoice(intermediateGame)
        )
    }

    fun finalizeScry(game: GameState): GameState {
        val scryState = game.activeScryState ?: return game

        val resolverIsPlayer = scryState.resolver == ChoiceOwner.PLAYER

        return if (scryState.target == ChoiceOwner.PLAYER) {
            game.copy(
                // cardsToReturnOnTop garde l'ordre des clics sur "Reposer".
                // On inverse pour que la dernière carte reposée soit la plus profonde,
                // et que la première piochée soit celle choisie en dernier.
                playerDeck = scryState.cardsToReturnOnTop.reversed() + game.playerDeck,
                playerDiscard = game.playerDiscard + scryState.cardsToDiscard,
                activeScryState = null,
                pendingChoice = null,

                playerEffectResolved = if (resolverIsPlayer) true else game.playerEffectResolved,
                opponentEffectResolved = if (!resolverIsPlayer) true else game.opponentEffectResolved,

                infoMessage = null
            )
        } else {
            game.copy(
                opponentDeck = scryState.cardsToReturnOnTop.reversed() + game.opponentDeck,
                opponentDiscard = game.opponentDiscard + scryState.cardsToDiscard,
                activeScryState = null,
                pendingChoice = null,

                playerEffectResolved = if (resolverIsPlayer) true else game.playerEffectResolved,
                opponentEffectResolved = if (!resolverIsPlayer) true else game.opponentEffectResolved,

                infoMessage = null
            )
        }
    }

    // =========================================================
    // 2. EFFETS DIFFÉRÉS
    // =========================================================

    fun addDelayedEffect(
        game: GameState,
        effect: DelayedEffect
    ): GameState {
        return game.copy(
            delayedEffects = game.delayedEffects + effect
        )
    }

    // =========================================================
    // 3. BONUS PERSISTANTS / CALCUL DE PUISSANCE / Helper généraux
    // =========================================================

    fun addBurnToken(
        game: GameState,
        target: ChoiceOwner
    ): GameState {
        // Placeholder :
        // plus tard, cette fonction ajoutera un jeton Brûlure
        // au joueur ciblé.
        //
        // target peut être :
        // - ChoiceOwner.PLAYER
        // - ChoiceOwner.OPPONENT

        return game
    }

    fun addPoisonToken(
        game: GameState,
        target: ChoiceOwner,
        amount: Int = 1
    ): GameState {
        // Placeholder :
        // plus tard, cette fonction ajoutera des jetons Poison
        // au joueur ciblé.
        //
        // Différence avec Brûlure :
        // - Brûlure sera probablement unique
        // - Poison pourra s'empiler avec amount

        return game
    }

    fun createRandomSkeletonCard(): Card {
        val skeletonId = listOf(
            CardId.SQUELETTE_HANTE,
            CardId.SQUELETTE_MALEDICTION,
            CardId.SQUELETTE_FAIBLESSE
        ).random()

        return CardCatalog.getCard(skeletonId)
    }

    fun discardTopCardWithReshuffleIfNeeded(
        game: GameState,
        targetIsPlayer: Boolean
    ): GameState {
        var workingDeck = if (targetIsPlayer) game.playerDeck else game.opponentDeck
        var workingDiscard = if (targetIsPlayer) game.playerDiscard else game.opponentDiscard
        val originalGold = if (targetIsPlayer) game.playerGold else game.opponentGold

        var gainedGoldFromReshuffle = false

        if (workingDeck.isEmpty() && workingDiscard.isNotEmpty()) {
            workingDeck = workingDiscard.shuffled()
            workingDiscard = emptyList()
            gainedGoldFromReshuffle = true
        }

        val discardedCard = workingDeck.firstOrNull()
        val finalDeck = if (discardedCard != null) workingDeck.drop(1) else workingDeck
        val finalDiscard = if (discardedCard != null) workingDiscard + discardedCard else workingDiscard
        val finalGold = if (gainedGoldFromReshuffle) originalGold + 1 else originalGold

        return if (targetIsPlayer) {
            game.copy(
                playerDeck = finalDeck,
                playerDiscard = finalDiscard,
                playerGold = finalGold
            )
        } else {
            game.copy(
                opponentDeck = finalDeck,
                opponentDiscard = finalDiscard,
                opponentGold = finalGold
            )
        }
    }

    fun applyBretteur(
        game: GameState,
        owner: ChoiceOwner,
        amount: Int = 1
    ): GameState {
        var updatedGame = game

        val targetIsPlayer = owner != ChoiceOwner.PLAYER

        repeat(amount) {
            updatedGame = discardTopCardWithReshuffleIfNeeded(
                game = updatedGame,
                targetIsPlayer = targetIsPlayer
            )
        }

        return updatedGame
    }

    fun countDemonFrenzyBonus(
        game: GameState,
        isPlayer: Boolean
    ): Int {
        val discard = if (isPlayer) {
            game.playerDiscard
        } else {
            game.opponentDiscard
        }

        return discard.count { it.id == CardId.ROIDEMON }
    }
    fun countHumanLinkBonus(
        game: GameState,
        isPlayer: Boolean
    ): Int {
        val discard = if (isPlayer) {
            game.playerDiscard
        } else {
            game.opponentDiscard
        }

        return discard.count { it.faction == CardFaction.HUMAN }
    }

    fun countAmbassadricePowerBonus(
        game: GameState,
        isPlayer: Boolean
    ): Int {
        val discard = if (isPlayer) {
            game.playerDiscard
        } else {
            game.opponentDiscard
        }

        val ambassadriceCount = discard.count { it.id == CardId.AMBASSADRICE }
        return ambassadriceCount * 2
    }

    fun getEffectivePower(
        game: GameState,
        card: Card,
        ownerGold: Int,
        nextCardPowerBonus: Int,
        isPlayer: Boolean
    ): Int {
        val basePower = when (card.id) {
            CardId.THERMOGUERRIER -> card.power + (ownerGold / 2)
            CardId.BARBAREVIKING -> card.power + countHumanLinkBonus(game, isPlayer)
            CardId.AMBASSADRICE -> card.power + countAmbassadricePowerBonus(game, isPlayer)
            else -> card.power
        }

        var totalPower = basePower + nextCardPowerBonus

        // ROI DÉMON — FRÉNÉSIE
        // Tant qu’un Roi Démon est dans la défausse,
        // les démons gagnent +1 force par exemplaire présent.
        val frenzyBonus = countDemonFrenzyBonus(game, isPlayer)

        if (card.faction == CardFaction.DEMON) {
            totalPower += frenzyBonus
        }

        return maxOf(1, totalPower)
    }

    fun hasBrute(
        card: Card,
        nextCardHasBrute: Boolean
    ): Boolean {
        return when (card.id) {
            CardId.VIOLENT,
            CardId.BARBAREVIKING -> true
            else -> nextCardHasBrute
        }
    }

    fun getCombatDamageDealt(
        attacker: Card,
        defender: Card,
        attackerPower: Int,
        defenderPower: Int,
        nextCardDamageBonus: Int,
        attackerHasBrute: Boolean
    ): Int {
        if (attackerPower <= defenderPower) {
            return 0
        }

        val baseDamage = if (attackerHasBrute) {
            attackerPower - defenderPower
        } else {
            1
        }

        return baseDamage + nextCardDamageBonus
    }

    // =========================================================
    // 4. PENDING CHOICES — CONSTRUCTION
    // =========================================================

    fun buildAraigneeSoldatPendingChoice(
        owner: ChoiceOwner
    ): PendingChoice {
        return PendingChoice(
            type = "ARAIGNEE_SOLDAT_CHOICE",
            cardId = CardId.ARAIGNEESOLDAT.name,
            options = listOf("BURN_OPPONENT", "COMBATIF"),
            message = "Choisissez un effet pour l'Araignée Soldat.",
            owner = owner
        )
    }

    fun buildOeufAraigneePendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): PendingChoice {

        val availableSpiders = if (owner == ChoiceOwner.PLAYER) {
            game.playerAvailableSpiderCardIds
        } else {
            game.opponentAvailableSpiderCardIds
        }

        val options = availableSpiders
            .filter { spiderId ->
                spiderId != CardId.OEUFDARAIGNEE &&
                        CardCatalog.getCard(spiderId).isSpider
            }
            .map { spiderId ->
                "SPIDER:${spiderId.name}"
            }

        return PendingChoice(
            type = "OEUF_ARAIGNEE_CHOICE",
            cardId = CardId.OEUFDARAIGNEE.name,
            options = options,
            message = "Choisissez une Araignée à faire éclore.",
            owner = owner
        )
    }

    fun buildNourriceAraigneePendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): PendingChoice {
        val ownerIsPlayer = owner == ChoiceOwner.PLAYER
        val ownerGold = if (ownerIsPlayer) game.playerGold else game.opponentGold

        val options = if (ownerGold >= 2) {
            listOf("HEAL_SELF", "CREATE_SPIDER_EGG")
        } else {
            listOf("HEAL_SELF")
        }

        val message = if (ownerGold >= 2) {
            "Choisissez un effet pour la Nourrice Araignée."
        } else {
            "Vous n'avez pas assez d'Or pour créer un Œuf d'Araignée."
        }

        return PendingChoice(
            type = "NOURRICE_ARAIGNEE_CHOICE",
            cardId = CardId.NOURRICEARAIGNEE.name,
            options = options,
            message = message,
            owner = owner
        )
    }

    fun startChamaneSubterfuge(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {
        val ownerIsPlayer = owner == ChoiceOwner.PLAYER

        var playerDeck = game.playerDeck
        var opponentDeck = game.opponentDeck
        var playerDiscard = game.playerDiscard
        var opponentDiscard = game.opponentDiscard
        var playerGold = game.playerGold
        var opponentGold = game.opponentGold

        if (playerDeck.isEmpty() && playerDiscard.isNotEmpty()) {
            playerDeck = playerDiscard.shuffled()
            playerDiscard = emptyList()
            playerGold += 1
        }

        if (opponentDeck.isEmpty() && opponentDiscard.isNotEmpty()) {
            opponentDeck = opponentDiscard.shuffled()
            opponentDiscard = emptyList()
            opponentGold += 1
        }

        val ownTopCard = if (ownerIsPlayer) {
            playerDeck.firstOrNull()
        } else {
            opponentDeck.firstOrNull()
        }

        val opponentTopCard = if (ownerIsPlayer) {
            opponentDeck.firstOrNull()
        } else {
            playerDeck.firstOrNull()
        }

        val canDestroyOwnTopCard = canDestroyOneCardWithFiveCardRule(
            game = game.copy(
                playerDeck = playerDeck,
                opponentDeck = opponentDeck,
                playerDiscard = playerDiscard,
                opponentDiscard = opponentDiscard
            ),
            isPlayer = ownerIsPlayer
        ) && ownTopCard != null

        val options = if (canDestroyOwnTopCard) {
            listOf("DISCARD_OPPONENT_TOP", "DESTROY_OWN_TOP")
        } else {
            listOf("DISCARD_OPPONENT_TOP")
        }

        val message = if (canDestroyOwnTopCard) {
            "Subterfuge : choisissez quelle carte traiter."
        } else {
            "Subterfuge : nombre de cartes insuffisant pour détruire votre carte. Vous devez défausser la carte adverse."
        }

        return game.copy(
            playerDeck = playerDeck,
            opponentDeck = opponentDeck,
            playerDiscard = playerDiscard,
            opponentDiscard = opponentDiscard,
            playerGold = playerGold,
            opponentGold = opponentGold,
            pendingChoice = PendingChoice(
                type = "CHAMANE_SUBTERFUGE",
                cardId = CardId.CHAMANE.name,
                options = options,
                message = message,
                owner = owner,
                previewOwnTopCard = ownTopCard,
                previewOpponentTopCard = opponentTopCard
            ),
            infoMessage = null
        )
    }

    fun buildPostCombatSacrificePendingChoice(
        cardId: CardId,
        owner: ChoiceOwner
    ): PendingChoice {
        val message = when (cardId) {
            CardId.DEVINDELUMIERE ->
                "Sacrifier Le Devin de Lumière après le combat pour gagner 1 PV ?"

            CardId.DEVINDESTENEBRES ->
                "Sacrifier Le Devin des Ténèbres après le combat pour infliger 1 dégât à l'adversaire ?"

            CardId.AGILE ->
                "Sacrifier L'Agile après le combat pour gagner 2 Or ?"

            else ->
                "Voulez-vous sacrifier cette carte après le combat ?"
        }

        return PendingChoice(
            type = "POST_COMBAT_SACRIFICE",
            cardId = cardId.name,
            options = listOf("SACRIFICE_YES", "SACRIFICE_NO"),
            message = message,
            owner = owner
        )
    }


    fun buildMasterOfCarnagesPendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): PendingChoice {

        // Cartes actuellement visibles dans le shop
        val shopCardOptions = game.shopEntries.map { entry ->
            "CARD:${entry.card.id.name}"
        }

        // Les 4 cartes de base sont toujours disponibles comme choix
        val baseCardOptions = listOf(
            "CARD:${CardId.COLLECTOR.name}",
            "CARD:${CardId.WARRIOR.name}",
            "CARD:${CardId.CURSED.name}",
            "CARD:${CardId.HEALER.name}"
        )

        val availableOptions = (shopCardOptions + baseCardOptions)
            .distinct()
            .sorted()

        return PendingChoice(
            type = "MASTER_OF_CARNAGES_CHOICE",
            cardId = CardId.MAITREDESCARNAGES.name,
            options = availableOptions,
            message = "Choisissez un nom de carte pour Le Maître des Carnages",
            owner = owner
        )
    }
    fun buildAgilePendingChoice(
        owner: ChoiceOwner
    ): PendingChoice {
        return PendingChoice(
            type = "AGILE_DECK_DISCARD",
            cardId = CardId.AGILE.name,
            options = listOf("DISCARD_DECK", "PASS"),
            message = "Voulez-vous défausser votre deck ?",
            owner = owner
        )
    }
    fun buildBadGeniePendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): PendingChoice {

        val discard = if (owner == ChoiceOwner.PLAYER) {
            game.playerDiscard
        } else {
            game.opponentDiscard
        }

        val discardOptions = discard.map { card ->
            "CARD:${card.id.name}"
        }

        val selfOption = if (canDestroyOneCardWithFiveCardRule(game, owner == ChoiceOwner.PLAYER)) {
            listOf("SELF")
        } else {
            emptyList()
        }

        val options = discardOptions + selfOption + "PASS"

        return PendingChoice(
            type = "BAD_GENIE_WEAPON",
            cardId = CardId.MAUVAISGENIE.name,
            options = options,
            message = "Choisissez une carte à détruire dans votre défausse, Mauvais Génie lui-même, ou passez.",
            owner = owner
        )
    }

    fun buildEnvouteusePendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): PendingChoice {

        val discard = if (owner == ChoiceOwner.PLAYER) {
            game.playerDiscard
        } else {
            game.opponentDiscard
        }

        val discardOptions = discard.map { card ->
            "CARD:${card.id.name}"
        }

        val options = discardOptions + "PASS"

        return PendingChoice(
            type = "ENVOUTEUSE_HEAL",
            cardId = CardId.ENVOUTEUSE.name,
            options = options,
            message = "Choisissez une carte à détruire dans votre défausse, ou passez.",
            owner = owner
        )
    }

    fun buildMagePendingChoice(
        owner: ChoiceOwner
    ): PendingChoice{
        return PendingChoice(
            type = "MAGE_CHOICE",
            cardId = CardId.MAGE.name,
            options = listOf(
                "BRUTE",
                "POWER",
                "DESTROY_RESERVE"
            ),
            message = "Choisissez un effet pour Le Mage",
            owner = owner
        )
    }

    fun buildTacticienPendingChoice(
        owner: ChoiceOwner
    ): PendingChoice {
        return PendingChoice(
            type = "TACTICIEN_REPLACE_REVEALED",
            cardId = CardId.TACTICIEN.name,
            options = listOf("REPLACE", "PASS"),
            message = "Vous pouvez faire défausser la carte révélée adverse. Si vous le faites, elle est remplacée par une nouvelle carte.",
            owner = owner
        )
    }
    fun buildAraigneeGeantePendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): PendingChoice {

        val discard = if (owner == ChoiceOwner.PLAYER) {
            game.playerDiscard
        } else {
            game.opponentDiscard
        }

        val options = discard.mapIndexed { index, _ ->
            "DISCARD:$index"
        } + "PASS"

        return PendingChoice(
            type = "ARAIGNEE_GEANTE_CHOICE",
            cardId = CardId.ARAIGNEEGEANTE.name,
            options = options,
            message = "Remplacer une carte de la défausse par un Œuf d'Araignée ?",
            owner = owner
        )
    }

    fun buildSentinelleBuyDestroyPendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): PendingChoice {
        val discard = if (owner == ChoiceOwner.PLAYER) {
            game.playerDiscard
        } else {
            game.opponentDiscard
        }

        // On exclut la Sentinelle elle-même des choix possibles
        val validOptions = discard
            .mapIndexedNotNull { index, card ->
                if (card.id == CardId.SENTINELLE) null else "DISCARD:$index"
            }

        return PendingChoice(
            type = "SENTINELLE_BUY_DESTROY",
            cardId = CardId.SENTINELLE.name,
            options = validOptions + "PASS",
            message = "La Sentinelle : choisissez une carte de votre défausse à détruire.",
            owner = owner
        )
    }
    private fun buildMageDestroyReservePendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): GameState{
        val availableOptions = game.shopEntries
            .mapIndexedNotNull { index, entry ->
                if (entry.copiesRemaining > 0) "SHOP:$index" else null
            }

        return game.copy(
            pendingChoice = PendingChoice(
                type = "MAGE_DESTROY_RESERVE",
                cardId = CardId.MAGE.name,
                options = availableOptions,
                message = "Choisissez une carte de la réserve à détruire.",
                owner = owner
            ),
            infoMessage = null
        )
    }

    // =========================================================
    // 5. PENDING CHOICES — RÉSOLUTION
    // =========================================================

    fun resolvePendingChoice(game: GameState, choice: String): GameState {
        val pendingChoice = game.pendingChoice ?: return game
        val ownerIsPlayer = pendingChoice.owner == ChoiceOwner.PLAYER

        return when (pendingChoice.type) {

            // -------------------------------------------------
            // ARAIGNÉE GÉANTE
            // -------------------------------------------------
            "ARAIGNEE_GEANTE_CHOICE" -> {
                when {

                    choice == "PASS" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                pendingChoice = null,
                                playerPostCombatSacrificeHandled = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                pendingChoice = null,
                                opponentPostCombatSacrificeHandled = true,
                                infoMessage = null
                            )
                        }
                    }

                    choice.startsWith("DISCARD:") -> {
                        val index = choice.removePrefix("DISCARD:").toIntOrNull()

                        if (index == null) {
                            game.copy(infoMessage = "Choix invalide")
                        } else {
                            val egg = CardCatalog.getCard(CardId.OEUFDARAIGNEE)

                            if (ownerIsPlayer) {
                                if (index !in game.playerDiscard.indices) {
                                    game.copy(infoMessage = "Choix invalide")
                                } else {
                                    game.copy(
                                        playerDiscard = game.playerDiscard
                                            .filterIndexed { i, _ -> i != index } + egg,
                                        pendingChoice = null,
                                        playerPostCombatSacrificeHandled = true,
                                        infoMessage = null
                                    )
                                }
                            } else {
                                if (index !in game.opponentDiscard.indices) {
                                    game.copy(infoMessage = "Choix invalide")
                                } else {
                                    game.copy(
                                        opponentDiscard = game.opponentDiscard
                                            .filterIndexed { i, _ -> i != index } + egg,
                                        pendingChoice = null,
                                        opponentPostCombatSacrificeHandled = true,
                                        infoMessage = null
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        game.copy(infoMessage = "Choix invalide")
                    }
                }
            }

            // -------------------------------------------------
            // OEUF D'ARAIGNÉE
            // -------------------------------------------------
            "OEUF_ARAIGNEE_CHOICE" -> {

                if (!choice.startsWith("SPIDER:")) {
                    return game.copy(infoMessage = "Choix invalide")
                }

                val spiderName = choice.removePrefix("SPIDER:")
                val spiderId = try {
                    CardId.valueOf(spiderName)
                } catch (e: Exception) {
                    return game.copy(infoMessage = "Araignée invalide")
                }

                val spiderCard = CardCatalog.getCard(spiderId)

                return if (ownerIsPlayer) {
                    game.copy(
                        playerDiscard = game.playerDiscard + spiderCard,

                        // L'œuf est détruit : il ne doit PAS partir en défausse
                        lastPlayerCard = null,
                        playerDisplayedTurnCard = null,

                        pendingChoice = null,
                        playerPostCombatSacrificeHandled = true,
                        infoMessage = null
                    )
                } else {
                    game.copy(
                        opponentDiscard = game.opponentDiscard + spiderCard,

                        // L'œuf est détruit : il ne doit PAS partir en défausse
                        lastOpponentCard = null,
                        opponentDisplayedTurnCard = null,

                        pendingChoice = null,
                        opponentPostCombatSacrificeHandled = true,
                        infoMessage = null
                    )
                }
            }

            // -------------------------------------------------
            // NOURRICE ARAIGNÉE
            // -------------------------------------------------
            "NOURRICE_ARAIGNEE_CHOICE" -> {
                when (choice) {

                    "HEAL_SELF" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                playerHp = game.playerHp + 1,
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                opponentHp = game.opponentHp + 1,
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    "CREATE_SPIDER_EGG" -> {
                        val egg = CardCatalog.getCard(CardId.OEUFDARAIGNEE)

                        if (ownerIsPlayer) {
                            if (game.playerGold < 2) {
                                game.copy(infoMessage = "Or insuffisant")
                            } else {
                                game.copy(
                                    playerGold = game.playerGold - 2,
                                    playerDiscard = game.playerDiscard + egg,
                                    pendingChoice = null,
                                    playerEffectResolved = true,
                                    infoMessage = null
                                )
                            }
                        } else {
                            if (game.opponentGold < 2) {
                                game.copy(infoMessage = "Or insuffisant")
                            } else {
                                game.copy(
                                    opponentGold = game.opponentGold - 2,
                                    opponentDiscard = game.opponentDiscard + egg,
                                    pendingChoice = null,
                                    opponentEffectResolved = true,
                                    infoMessage = null
                                )
                            }
                        }
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }

            // -------------------------------------------------
            // ARAIGNÉE SOLDAT
            // -------------------------------------------------
            "ARAIGNEE_SOLDAT_CHOICE" -> {
                when (choice) {

                    "COMBATIF" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                playerNextCardPowerBonus = game.playerNextCardPowerBonus + 1,
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                opponentNextCardPowerBonus = game.opponentNextCardPowerBonus + 1,
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    "BURN_OPPONENT" -> {
                        val target = if (ownerIsPlayer) {
                            ChoiceOwner.OPPONENT
                        } else {
                            ChoiceOwner.PLAYER
                        }

                        val burnedGame = addBurnToken(
                            game = game,
                            target = target
                        )

                        if (ownerIsPlayer) {
                            burnedGame.copy(
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            burnedGame.copy(
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }


            // -------------------------------------------------
            // AGILE
            // -------------------------------------------------
            "AGILE_DECK_DISCARD" -> {
                when (choice) {

                    "PASS" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    "DISCARD_DECK" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                playerDiscard = game.playerDiscard + game.playerDeck,
                                playerDeck = emptyList(),
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                opponentDiscard = game.opponentDiscard + game.opponentDeck,
                                opponentDeck = emptyList(),
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }

            // -------------------------------------------------
            // CHAMANE
            // -------------------------------------------------
            "CHAMANE_SUBTERFUGE" -> {

                val ownerIsPlayer = pendingChoice.owner == ChoiceOwner.PLAYER

                when (choice) {

                    "DISCARD_OPPONENT_TOP" -> {
                        val updatedGame = CardEffectManager.discardTopCardWithReshuffleIfNeeded(
                            game = game,
                            targetIsPlayer = !ownerIsPlayer
                        )

                        if (ownerIsPlayer) {
                            updatedGame.copy(
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            updatedGame.copy(
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    "DESTROY_OWN_TOP" -> {
                        if (!canDestroyOneCardWithFiveCardRule(game, ownerIsPlayer)) {
                            return game.copy(
                                infoMessage = "Règle des 5 cartes"
                            )
                        }

                        val updatedGame = if (ownerIsPlayer) {

                            var deck = game.playerDeck
                            var discard = game.playerDiscard
                            var gold = game.playerGold

                            if (deck.isEmpty() && discard.isNotEmpty()) {
                                deck = discard.shuffled()
                                discard = emptyList()
                                gold += 1
                            }

                            if (deck.isNotEmpty()) {
                                deck = deck.drop(1)
                            }

                            game.copy(
                                playerDeck = deck,
                                playerDiscard = discard,
                                playerGold = gold
                            )

                        } else {

                            var deck = game.opponentDeck
                            var discard = game.opponentDiscard
                            var gold = game.opponentGold

                            if (deck.isEmpty() && discard.isNotEmpty()) {
                                deck = discard.shuffled()
                                discard = emptyList()
                                gold += 1
                            }

                            if (deck.isNotEmpty()) {
                                deck = deck.drop(1)
                            }

                            game.copy(
                                opponentDeck = deck,
                                opponentDiscard = discard,
                                opponentGold = gold
                            )
                        }

                        if (ownerIsPlayer) {
                            updatedGame.copy(
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            updatedGame.copy(
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }

            // -------------------------------------------------
            // SENTINELLE — destruction à l'achat
            // -------------------------------------------------
            "SENTINELLE_BUY_DESTROY" -> {
                when {
                    choice == "PASS" -> {
                        game.copy(
                            pendingChoice = null,
                            infoMessage = null
                        )
                    }

                    choice.startsWith("DISCARD:") -> {
                        val index = choice.removePrefix("DISCARD:").toIntOrNull()

                        if (index == null) {
                            game.copy(
                                infoMessage = "Choix invalide"
                            )
                        } else if (ownerIsPlayer) {
                            if (index !in game.playerDiscard.indices) {
                                game.copy(
                                    infoMessage = "Choix invalide"
                                )
                            } else {
                                val cardToDestroy = game.playerDiscard[index]

                                if (cardToDestroy.id == CardId.SENTINELLE) {
                                    game.copy(
                                        infoMessage = "Choix invalide"
                                    )
                                } else {
                                    game.copy(
                                        playerDiscard = game.playerDiscard.filterIndexed { i, _ -> i != index },
                                        pendingChoice = null,
                                        infoMessage = null
                                    )
                                }
                            }
                        } else {
                            if (index !in game.opponentDiscard.indices) {
                                game.copy(
                                    infoMessage = "Choix invalide"
                                )
                            } else {
                                val cardToDestroy = game.opponentDiscard[index]

                                if (cardToDestroy.id == CardId.SENTINELLE) {
                                    game.copy(
                                        infoMessage = "Choix invalide"
                                    )
                                } else {
                                    game.copy(
                                        opponentDiscard = game.opponentDiscard.filterIndexed { i, _ -> i != index },
                                        pendingChoice = null,
                                        infoMessage = null
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }

            //-------------------------------------------------
            // SCRUTER
            //-------------------------------------------------
            "SCRY_SELECT_CARD" -> {
                val scryState = game.activeScryState
                    ?: return game.copy(infoMessage = "Aucun scrutage en cours")

                when {

                    choice.startsWith("SCRY_CARD:") -> {
                        val index = choice.removePrefix("SCRY_CARD:").toIntOrNull()
                            ?: return game.copy(infoMessage = "Choix invalide")

                        if (index !in scryState.revealedCards.indices) {
                            return game.copy(infoMessage = "Choix invalide")
                        }

                        val updatedScryState = scryState.copy(
                            selectedCardIndex = index
                        )

                        val updatedGame = game.copy(
                            activeScryState = updatedScryState,
                            infoMessage = null
                        )

                        updatedGame.copy(
                            pendingChoice = buildScryActionPendingChoice(updatedGame)
                        )
                    }

                    else -> {
                        game.copy(infoMessage = "Choix invalide")
                    }
                }
            }
            "SCRY_CHOOSE_ACTION" -> {
                val scryState = game.activeScryState
                    ?: return game.copy(infoMessage = "Aucun scrutage en cours")

                val selectedIndex = scryState.selectedCardIndex
                    ?: return game.copy(infoMessage = "Aucune carte scrutée sélectionnée")

                if (selectedIndex !in scryState.revealedCards.indices) {
                    return game.copy(infoMessage = "Choix invalide")
                }

                val selectedCard = scryState.revealedCards[selectedIndex]

                val remainingRevealedCards = scryState.revealedCards.filterIndexed { index, _ ->
                    index != selectedIndex
                }

                when (choice) {
                    "SCRY_RETURN" -> {
                        val updatedScryState = scryState.copy(
                            revealedCards = remainingRevealedCards,
                            cardsToReturnOnTop = scryState.cardsToReturnOnTop + selectedCard,
                            selectedCardIndex = null
                        )

                        val updatedGame = game.copy(
                            activeScryState = updatedScryState,
                            infoMessage = null
                        )

                        updatedGame.copy(
                            pendingChoice = if (updatedScryState.revealedCards.isEmpty()) {
                                null
                            } else {
                                buildScrySelectCardPendingChoice(updatedGame)
                            }
                        ).let { intermediate ->
                            if (intermediate.pendingChoice == null) {
                                finalizeScry(intermediate)
                            } else {
                                intermediate
                            }
                        }
                    }

                    "SCRY_DISCARD" -> {
                        if (!scryState.canDiscardViewedCards) {
                            return game.copy(infoMessage = "Cette carte ne peut pas être défaussée")
                        }

                        val updatedScryState = scryState.copy(
                            revealedCards = remainingRevealedCards,
                            cardsToDiscard = scryState.cardsToDiscard + selectedCard,
                            selectedCardIndex = null
                        )

                        val updatedGame = game.copy(
                            activeScryState = updatedScryState,
                            infoMessage = null
                        )

                        updatedGame.copy(
                            pendingChoice = if (updatedScryState.revealedCards.isEmpty()) {
                                null
                            } else {
                                buildScrySelectCardPendingChoice(updatedGame)
                            }
                        ).let { intermediate ->
                            if (intermediate.pendingChoice == null) {
                                finalizeScry(intermediate)
                            } else {
                                intermediate
                            }
                        }
                    }

                    else -> {
                        game.copy(infoMessage = "Choix invalide")
                    }
                }
            }
            // -------------------------------------------------
            // SACRIFICE POST-COMBAT
            // -------------------------------------------------
            "POST_COMBAT_SACRIFICE" -> {
                when (choice) {

                    "SACRIFICE_NO" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                pendingChoice = null,
                                playerPostCombatSacrificeHandled = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                pendingChoice = null,
                                opponentPostCombatSacrificeHandled = true,
                                infoMessage = null
                            )
                        }
                    }

                    "SACRIFICE_YES" -> {
                        if (!canDestroyOneCardWithFiveCardRule(game, ownerIsPlayer)) {
                            if (ownerIsPlayer) {
                                game.copy(
                                    pendingChoice = null,
                                    playerPostCombatSacrificeHandled = true,
                                    infoMessage = "Règle des 5 cartes"
                                )
                            } else {
                                game.copy(
                                    pendingChoice = null,
                                    opponentPostCombatSacrificeHandled = true,
                                    infoMessage = "Règle des 5 cartes"
                                )
                            }
                        } else {
                            val rewardedGame = when (pendingChoice.cardId) {
                                CardId.AGILE.name -> {
                                    if (ownerIsPlayer) {
                                        game.copy(playerGold = game.playerGold + 2)
                                    } else {
                                        game.copy(opponentGold = game.opponentGold + 2)
                                    }
                                }

                                CardId.DEVINDELUMIERE.name -> {
                                    if (ownerIsPlayer) {
                                        game.copy(playerHp = game.playerHp + 1)
                                    } else {
                                        game.copy(opponentHp = game.opponentHp + 1)
                                    }
                                }

                                CardId.DEVINDESTENEBRES.name -> {
                                    if (ownerIsPlayer) {
                                        game.copy(opponentHp = maxOf(0, game.opponentHp - 1))
                                    } else {
                                        game.copy(playerHp = maxOf(0, game.playerHp - 1))
                                    }
                                }

                                else -> game
                            }

                            if (ownerIsPlayer) {
                                rewardedGame.copy(
                                    lastPlayerCard = null,
                                    playerDisplayedTurnCard = null,
                                    pendingChoice = null,
                                    playerPostCombatSacrificeHandled = true,
                                    infoMessage = null
                                )
                            } else {
                                rewardedGame.copy(
                                    lastOpponentCard = null,
                                    opponentDisplayedTurnCard = null,
                                    pendingChoice = null,
                                    opponentPostCombatSacrificeHandled = true,
                                    infoMessage = null
                                )
                            }
                        }
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }

            // -------------------------------------------------
            // MAITRE DES CARNAGES
            // -------------------------------------------------
            "MASTER_OF_CARNAGES_CHOICE" -> {
                if (choice !in pendingChoice.options) {
                    game.copy(
                        infoMessage = "Choix invalide"
                    )
                } else {
                    // On reçoit un choix sous forme "CARD:WARRIOR"
                    // On enlève le préfixe pour ne garder que le vrai nom technique de la carte
                    val selectedCardName = choice.removePrefix("CARD:")

                    val predictionEffect = NextRevealPredictionEffect(
                        sourceCardId = CardId.MAITREDESCARNAGES,
                        predictedCardName = selectedCardName,
                        powerBonusOnMatch = 5
                    )

                    if (ownerIsPlayer) {
                        game.copy(
                            playerNextRevealPredictionEffect = predictionEffect,
                            pendingChoice = null,
                            playerEffectResolved = true,
                            infoMessage = null
                        )
                    } else {
                        game.copy(
                            opponentNextRevealPredictionEffect = predictionEffect,
                            pendingChoice = null,
                            opponentEffectResolved = true,
                            infoMessage = null
                        )
                    }
                }
            }
            // -------------------------------------------------
            // MAUVAIS GÉNIE
            // -------------------------------------------------
            "BAD_GENIE_WEAPON" -> {
                when {
                    choice == "PASS" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    choice == "SELF" -> {
                        if (!canDestroyOneCardWithFiveCardRule(game, ownerIsPlayer)) {
                            game.copy(
                                infoMessage = "Règle des 5 cartes"
                            )
                        } else {
                            addDelayedEffect(
                                if (ownerIsPlayer) {
                                    game.copy(
                                        pendingChoice = null,
                                        playerEffectResolved = true,
                                        infoMessage = null
                                    )
                                } else {
                                    game.copy(
                                        pendingChoice = null,
                                        opponentEffectResolved = true,
                                        infoMessage = null
                                    )
                                },
                                DelayedEffect(
                                    timing = DelayedEffectTiming.END_TURN,
                                    type = DelayedEffectType.DESTROY_REVEALED_CARD,
                                    target = if (ownerIsPlayer) {
                                        DelayedEffectTarget.PLAYER
                                    } else {
                                        DelayedEffectTarget.OPPONENT
                                    }
                                )
                            )
                        }
                    }

                    choice.startsWith("CARD:") -> {
                        val targetCardId = choice.removePrefix("CARD:")

                        if (!canDestroyOneCardWithFiveCardRule(game, ownerIsPlayer)) {
                            game.copy(
                                infoMessage = "Règle des 5 cartes"
                            )
                        } else {
                            val sourceDiscard = if (ownerIsPlayer) {
                                game.playerDiscard
                            } else {
                                game.opponentDiscard
                            }

                            val newDiscard = destroyOneCardFromDiscardById(
                                discard = sourceDiscard,
                                targetCardId = targetCardId
                            )

                            if (ownerIsPlayer) {
                                game.copy(
                                    playerDiscard = newDiscard,
                                    pendingChoice = null,
                                    playerEffectResolved = true,
                                    infoMessage = null
                                )
                            } else {
                                game.copy(
                                    opponentDiscard = newDiscard,
                                    pendingChoice = null,
                                    opponentEffectResolved = true,
                                    infoMessage = null
                                )
                            }
                        }
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }
            // -------------------------------------------------
            // ENVOUTEUSE
            // -------------------------------------------------
            "ENVOUTEUSE_HEAL" -> {
                when {
                    choice == "PASS" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    choice.startsWith("CARD:") -> {
                        val targetCardId = choice.removePrefix("CARD:")

                        if (!canDestroyOneCardWithFiveCardRule(game, ownerIsPlayer)) {
                            game.copy(
                                infoMessage = "Règle des 5 cartes"
                            )
                        } else {
                            val sourceDiscard = if (ownerIsPlayer) {
                                game.playerDiscard
                            } else {
                                game.opponentDiscard
                            }

                            val destroyedCard = sourceDiscard.firstOrNull { it.id.name == targetCardId }
                                ?: return game.copy(infoMessage = "Choix invalide")

                            val newDiscard = destroyOneCardFromDiscardById(
                                discard = sourceDiscard,
                                targetCardId = targetCardId
                            )

                            val healAmount = destroyedCard.power / 2

                            if (ownerIsPlayer) {
                                game.copy(
                                    playerDiscard = newDiscard,
                                    playerHp = game.playerHp + healAmount,
                                    pendingChoice = null,
                                    playerEffectResolved = true,
                                    infoMessage = null
                                )
                            } else {
                                game.copy(
                                    opponentDiscard = newDiscard,
                                    opponentHp = game.opponentHp + healAmount,
                                    pendingChoice = null,
                                    opponentEffectResolved = true,
                                    infoMessage = null
                                )
                            }
                        }
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }


            // -------------------------------------------------
            // MAGE — CHOIX PRINCIPAL
            // -------------------------------------------------
            "MAGE_CHOICE" -> {
                when (choice) {
                    "BRUTE" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                playerNextCardHasBrute = true,
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                opponentNextCardHasBrute = true,
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    "POWER" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                playerNextCardPowerBonus = game.playerNextCardPowerBonus + 2,
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                opponentNextCardPowerBonus = game.opponentNextCardPowerBonus + 2,
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    "DESTROY_RESERVE" -> {
                        buildMageDestroyReservePendingChoice(
                            game = game,
                            owner = pendingChoice.owner
                        )
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }

            // -------------------------------------------------
            // MAGE — DESTRUCTION DANS LA RÉSERVE
            // -------------------------------------------------
            "MAGE_DESTROY_RESERVE" -> {
                if (!choice.startsWith("SHOP:")) {
                    return game.copy(
                        infoMessage = "Choix invalide"
                    )
                }

                val index = choice.removePrefix("SHOP:").toIntOrNull()
                    ?: return game.copy(
                        infoMessage = "Choix invalide"
                    )

                if (index !in game.shopEntries.indices) {
                    return game.copy(
                        infoMessage = "Choix invalide"
                    )
                }

                val targetEntry = game.shopEntries[index]

                if (targetEntry.copiesRemaining <= 0) {
                    return game.copy(
                        infoMessage = "Pile vide"
                    )
                }

                val newShopEntries = game.shopEntries.mapIndexed { i, entry ->
                    if (i == index) {
                        entry.copy(copiesRemaining = entry.copiesRemaining - 1)
                    } else {
                        entry
                    }
                }

                if (ownerIsPlayer) {
                    game.copy(
                        shopEntries = newShopEntries,
                        pendingChoice = null,
                        playerEffectResolved = true,
                        infoMessage = null
                    )
                } else {
                    game.copy(
                        shopEntries = newShopEntries,
                        pendingChoice = null,
                        opponentEffectResolved = true,
                        infoMessage = null
                    )
                }
            }

            // -------------------------------------------------
            // TACTICIEN
            // -------------------------------------------------
            "TACTICIEN_REPLACE_REVEALED" -> {
                when (choice) {
                    "PASS" -> {
                        if (ownerIsPlayer) {
                            game.copy(
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            game.copy(
                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    "REPLACE" -> {
                        if (ownerIsPlayer) {
                            val oldOpponentCard = game.lastOpponentCard
                                ?: return game.copy(infoMessage = "Aucune carte adverse à remplacer")

                            var opponentDeck = game.opponentDeck
                            var opponentDiscard = game.opponentDiscard + oldOpponentCard
                            var newOpponentGold = game.opponentGold

                            if (opponentDeck.isEmpty() && opponentDiscard.isNotEmpty()) {
                                opponentDeck = opponentDiscard.shuffled()
                                opponentDiscard = emptyList()
                                newOpponentGold += 1
                            }

                            val newOpponentCard = opponentDeck.firstOrNull()
                                ?: return game.copy(infoMessage = "Impossible de révéler une nouvelle carte")

                            game.copy(
                                opponentDeck = opponentDeck.drop(1),
                                opponentDiscard = opponentDiscard,
                                opponentGold = newOpponentGold,
                                lastOpponentCard = newOpponentCard,
                                opponentDisplayedTurnCard = newOpponentCard,

                                opponentCurrentCardPowerBonus = 0,
                                opponentCurrentCardDamageBonus = 0,
                                opponentCurrentCardHasBruteBonus = false,

                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            val oldPlayerCard = game.lastPlayerCard
                                ?: return game.copy(infoMessage = "Aucune carte adverse à remplacer")

                            var playerDeck = game.playerDeck
                            var playerDiscard = game.playerDiscard + oldPlayerCard
                            var newPlayerGold = game.playerGold

                            if (playerDeck.isEmpty() && playerDiscard.isNotEmpty()) {
                                playerDeck = playerDiscard.shuffled()
                                playerDiscard = emptyList()
                                newPlayerGold += 1
                            }

                            val newPlayerCard = playerDeck.firstOrNull()
                                ?: return game.copy(infoMessage = "Impossible de révéler une nouvelle carte")

                            game.copy(
                                playerDeck = playerDeck.drop(1),
                                playerDiscard = playerDiscard,
                                playerGold = newPlayerGold,
                                lastPlayerCard = newPlayerCard,
                                playerDisplayedTurnCard = newPlayerCard,

                                playerCurrentCardPowerBonus = 0,
                                playerCurrentCardDamageBonus = 0,
                                playerCurrentCardHasBruteBonus = false,

                                pendingChoice = null,
                                opponentEffectResolved = true,
                                infoMessage = null
                            )
                        }
                    }

                    else -> {
                        game.copy(
                            infoMessage = "Choix invalide"
                        )
                    }
                }
            }

            // -------------------------------------------------
            // NON GÉRÉ
            // -------------------------------------------------
            else -> {
                game.copy(
                    infoMessage = "Choix non géré"
                )
            }
        }
    }


}