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
        sourceId: String,
        resolver: ChoiceOwner,
        target: ChoiceOwner,
        amount: Int,
        completionContext: ScryCompletionContext = ScryCompletionContext.CARD_EFFECT,
        canDiscardViewedCards: Boolean = true,
        chainedScry: ChainedScry? = null
    ): GameState {

        var workingGame = game
        val revealedCards = mutableListOf<Card>()

        repeat(amount) {

            workingGame = DeckManager.reshuffleDiscardIntoDeckIfNeeded(
                game = workingGame,
                target = target
            )

            when (target) {
                ChoiceOwner.PLAYER -> {
                    val nextCard = workingGame.playerDeck.firstOrNull()

                    if (nextCard != null) {
                        revealedCards += nextCard

                        workingGame = workingGame.copy(
                            playerDeck = workingGame.playerDeck.drop(1)
                        )
                    }
                }

                ChoiceOwner.OPPONENT -> {
                    val nextCard = workingGame.opponentDeck.firstOrNull()

                    if (nextCard != null) {
                        revealedCards += nextCard

                        workingGame = workingGame.copy(
                            opponentDeck = workingGame.opponentDeck.drop(1)
                        )
                    }
                }
            }
        }

        if (revealedCards.isEmpty()) {
            return when (completionContext) {
                ScryCompletionContext.CARD_EFFECT -> {
                    if (resolver == ChoiceOwner.PLAYER) {
                        workingGame.copy(
                            playerEffectResolved = true,
                            activeScryState = null,
                            pendingChoice = null,
                            infoMessage = null
                        )
                    } else {
                        workingGame.copy(
                            opponentEffectResolved = true,
                            activeScryState = null,
                            pendingChoice = null,
                            infoMessage = null
                        )
                    }
                }

                ScryCompletionContext.AMBUSH -> {
                    workingGame.copy(
                        activeScryState = null,
                        pendingChoice = null,
                        infoMessage = null
                    )
                }

                ScryCompletionContext.POST_COMBAT -> {
                    if (resolver == ChoiceOwner.PLAYER) {
                        workingGame.copy(
                            playerPostCombatSacrificeHandled = true,
                            activeScryState = null,
                            pendingChoice = null,
                            infoMessage = null
                        )
                    } else {
                        workingGame.copy(
                            opponentPostCombatSacrificeHandled = true,
                            activeScryState = null,
                            pendingChoice = null,
                            infoMessage = null
                        )
                    }
                }

            }
        }

        val newScryState = ScryState(
            sourceCardId = sourceId,
            resolver = resolver,
            target = target,
            amount = amount,
            completionContext = completionContext,
            canDiscardViewedCards = canDiscardViewedCards,
            chainedScry = chainedScry,
            revealedCards = revealedCards,
            cardsToReturnOnTop = emptyList(),
            cardsToDiscard = emptyList(),
            selectedCardIndex = null
        )

        val intermediateGame = workingGame.copy(
            activeScryState = newScryState,
            infoMessage = null
        )

        return intermediateGame.copy(
            pendingChoice = buildScrySelectCardPendingChoice(
                intermediateGame
            )
        )
    }

    fun finalizeScry(game: GameState): GameState {
        val scryState = game.activeScryState ?: return game

        val chainedScry = scryState.chainedScry

        if (chainedScry != null) {
            val gameAfterCurrentScry = if (scryState.target == ChoiceOwner.PLAYER) {
                game.copy(
                    playerDeck =
                        scryState.cardsToReturnOnTop.reversed() + game.playerDeck,
                    playerDiscard =
                        game.playerDiscard + scryState.cardsToDiscard,
                    activeScryState = null,
                    pendingChoice = null,
                    infoMessage = null
                )
            } else {
                game.copy(
                    opponentDeck =
                        scryState.cardsToReturnOnTop.reversed() + game.opponentDeck,
                    opponentDiscard =
                        game.opponentDiscard + scryState.cardsToDiscard,
                    activeScryState = null,
                    pendingChoice = null,
                    infoMessage = null
                )
            }

            return startScry(
                game = gameAfterCurrentScry,
                sourceId = scryState.sourceCardId,
                resolver = scryState.resolver,
                target = chainedScry.target,
                amount = chainedScry.amount,
                completionContext = scryState.completionContext,
                canDiscardViewedCards = chainedScry.canDiscardViewedCards
            )
        }

        val resolverIsPlayer = scryState.resolver == ChoiceOwner.PLAYER

        val resolvesCardEffect =
            scryState.completionContext == ScryCompletionContext.CARD_EFFECT

        val resolvesPostCombat =
            scryState.completionContext == ScryCompletionContext.POST_COMBAT

        return if (scryState.target == ChoiceOwner.PLAYER) {
            game.copy(
                // cardsToReturnOnTop garde l'ordre des clics sur "Reposer".
                // On inverse pour que la dernière carte reposée soit la plus profonde,
                // et que la première piochée soit celle choisie en dernier.
                playerDeck = scryState.cardsToReturnOnTop.reversed() + game.playerDeck,
                playerDiscard = game.playerDiscard + scryState.cardsToDiscard,
                activeScryState = null,
                pendingChoice = null,

                playerEffectResolved =
                    if (resolvesCardEffect && resolverIsPlayer) true
                    else game.playerEffectResolved,

                opponentEffectResolved =
                    if (resolvesCardEffect && !resolverIsPlayer) true
                    else game.opponentEffectResolved,

                playerPostCombatSacrificeHandled =
                    if (resolvesPostCombat && resolverIsPlayer) true
                    else game.playerPostCombatSacrificeHandled,

                opponentPostCombatSacrificeHandled =
                    if (resolvesPostCombat && !resolverIsPlayer) true
                    else game.opponentPostCombatSacrificeHandled,

                infoMessage = null
            )
        } else {
            game.copy(
                opponentDeck = scryState.cardsToReturnOnTop.reversed() + game.opponentDeck,
                opponentDiscard = game.opponentDiscard + scryState.cardsToDiscard,
                activeScryState = null,
                pendingChoice = null,

                playerEffectResolved =
                    if (resolvesCardEffect && resolverIsPlayer) true
                    else game.playerEffectResolved,

                opponentEffectResolved =
                    if (resolvesCardEffect && !resolverIsPlayer) true
                    else game.opponentEffectResolved,

                playerPostCombatSacrificeHandled =
                    if (resolvesPostCombat && resolverIsPlayer) true
                    else game.playerPostCombatSacrificeHandled,

                opponentPostCombatSacrificeHandled =
                    if (resolvesPostCombat && !resolverIsPlayer) true
                    else game.opponentPostCombatSacrificeHandled,

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
        return TokenManager.addToken(
            game = game,
            target = target,
            tokenId = TokenManager.TokenIds.BURN,
            amount = 1,
            maxAmount = 1
        )
    }

    fun addDiseaseToken(
        game: GameState,
        target: ChoiceOwner
    ): GameState {
        return TokenManager.addToken(
            game = game,
            target = target,
            tokenId = TokenManager.TokenIds.DISEASE,
            amount = 1,
            maxAmount = 1
        )
    }


    // =========================================================
    // VENIMEUX — compétence naturelle et valeur effective
    // =========================================================

    fun getBaseVenomous(card: Card): Int {
        return when (card.id) {
            CardId.SOURNOISE -> 1
            CardId.LAMEERRANTE -> 1
            CardId.CONTAGIEUX -> 1
            CardId.REINEARAIGNEE -> 2
            CardId.ARAIGNEEJUVENILE -> 1

            // Cartes pas encore présentes dans CardId :
            // GRANDEMORSURE -> 2
            // FUNESTE -> 1
            // MAITREDESPOTIONS -> 2
            // BEHEMOTH -> 1
            // OMEGA -> 6

            else -> 0
        }
    }

    // SOURNOISE — VENIMEUX +1 aux cartes du propriétaire
    // tant qu'elle est en Embuscade.
    fun countSournoiseVenomousBonus(
        game: GameState,
        isPlayer: Boolean
    ): Int {
        val tacticalCards = if (isPlayer) {
            game.playerAmbush
        } else {
            game.opponentAmbush
        }

        return tacticalCards.count { tacticalCard ->
            tacticalCard.entryType == TacticalEntryType.AMBUSH &&
                    tacticalCard.card.id == CardId.SOURNOISE
        }
    }

    fun getEffectiveVenomous(
        game: GameState,
        card: Card,
        isPlayer: Boolean,
        currentBonus: Int,
        effectsBlocked: Boolean
    ): Int {
        if (effectsBlocked) return 0

        val sournoiseBonus = countSournoiseVenomousBonus(
            game = game,
            isPlayer = isPlayer
        )

        // REINE ARAIGNÉE — VENIMEUX +1 aux Araignées
        // tant qu'elle est en Préparation.
        val reineAraigneeBonus = if (card.isSpider) {
            countReineAraigneePowerBonus(
                game = game,
                isPlayer = isPlayer
            )
        } else {
            0
        }

        return maxOf(
            0,
            getBaseVenomous(card) +
                    currentBonus +
                    sournoiseBonus +
                    reineAraigneeBonus
        )
    }


    fun addPoisonToken(
        game: GameState,
        target: ChoiceOwner,
        amount: Int = 1,
        source: ChoiceOwner
    ): GameState {

        if (amount <= 0) return game

        val empoisonneuseActive = if (source != target) {
            val tacticalCards = if (source == ChoiceOwner.PLAYER) {
                game.playerAmbush
            } else {
                game.opponentAmbush
            }

            tacticalCards.any { tacticalCard ->
                tacticalCard.entryType == TacticalEntryType.AMBUSH &&
                        tacticalCard.card.id == CardId.EMPOISONNEUSE
            }
        } else {
            false
        }

        val finalAmount = if (empoisonneuseActive) {
            amount * 2
        } else {
            amount
        }

        return TokenManager.addToken(
            game = game,
            target = target,
            tokenId = TokenManager.TokenIds.POISON,
            amount = finalAmount
        )
    }

    fun addBloodToken(
        game: GameState,
        target: ChoiceOwner,
        amount: Int = 1
    ): GameState {
        return TokenManager.addToken(
            game = game,
            target = target,
            tokenId = TokenManager.TokenIds.BLOOD,
            amount = amount,
            maxAmount = 5
        )
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

        val target = if (targetIsPlayer) {
            ChoiceOwner.PLAYER
        } else {
            ChoiceOwner.OPPONENT
        }

        val recycledGame = DeckManager.reshuffleDiscardIntoDeckIfNeeded(
            game = game,
            target = target
        )

        val deck = if (targetIsPlayer) {
            recycledGame.playerDeck
        } else {
            recycledGame.opponentDeck
        }

        val discard = if (targetIsPlayer) {
            recycledGame.playerDiscard
        } else {
            recycledGame.opponentDiscard
        }

        val discardedCard = deck.firstOrNull()
            ?: return recycledGame

        return if (targetIsPlayer) {
            recycledGame.copy(
                playerDeck = deck.drop(1),
                playerDiscard = discard + discardedCard
            )
        } else {
            recycledGame.copy(
                opponentDeck = deck.drop(1),
                opponentDiscard = discard + discardedCard
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

    fun countMusicienCollectorBonus(
        game: GameState,
        isPlayer: Boolean
    ): Int {
        val tacticalCards = if (isPlayer) {
            game.playerAmbush
        } else {
            game.opponentAmbush
        }

        return tacticalCards.count { tacticalCard ->
            tacticalCard.entryType == TacticalEntryType.AMBUSH &&
                    tacticalCard.card.id == CardId.MUSICIEN
        }
    }

    fun countReineAraigneePowerBonus(
        game: GameState,
        isPlayer: Boolean
    ): Int {
        val tacticalCards = if (isPlayer) {
            game.playerAmbush
        } else {
            game.opponentAmbush
        }

        return tacticalCards.count { tacticalCard ->
            tacticalCard.entryType == TacticalEntryType.PREPARATION &&
                    tacticalCard.card.id == CardId.REINEARAIGNEE
        }
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

            CardId.DISCIPLEDELAFLAMME -> {
                val opponent = if (isPlayer) {
                    ChoiceOwner.OPPONENT
                } else {
                    ChoiceOwner.PLAYER
                }

                val opponentHasBurn = TokenManager.hasToken(
                    game = game,
                    target = opponent,
                    tokenId = TokenManager.TokenIds.BURN
                )

                card.power + if (opponentHasBurn) 2 else 0
            }

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

        // MUSICIEN — tant qu'il est en Embuscade,
        // les Collecteurs du propriétaire gagnent +1 Force par Musicien.
        if (card.id == CardId.COLLECTOR) {
            totalPower += countMusicienCollectorBonus(
                game = game,
                isPlayer = isPlayer
            )
        }

        // REINE ARAIGNÉE — tant qu'elle est dans la zone tactique,
        // les Araignées de son propriétaire gagnent +1 Force.
        if (card.isSpider) {
            totalPower += countReineAraigneePowerBonus(
                game = game,
                isPlayer = isPlayer
            )
        }

        return maxOf(1, totalPower)
    }

    fun hasBrute(
        card: Card,
        nextCardHasBrute: Boolean
    ): Boolean {
        return when (card.id) {
            CardId.VIOLENT,
            CardId.ENCHAINE,
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
        attackerHasBrute: Boolean,
        enrageCombatDamage: Int = 1
    ): Int {
        if (attackerPower <= defenderPower) {
            return 0
        }

        val baseDamage = when {
            attackerHasBrute -> attackerPower - defenderPower
            attacker.id == CardId.ENRAGE -> enrageCombatDamage
            else -> 1
        }

        return baseDamage + nextCardDamageBonus
    }

    fun applyFideleEffect(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {
        val ownerIsPlayer = owner == ChoiceOwner.PLAYER

        val ownerHp = if (ownerIsPlayer) {
            game.playerHp
        } else {
            game.opponentHp
        }

        val opponentHp = if (ownerIsPlayer) {
            game.opponentHp
        } else {
            game.playerHp
        }

        val goldGain = if (ownerHp < opponentHp) 2 else 1

        return if (ownerIsPlayer) {
            game.copy(
                playerGold = game.playerGold + goldGain
            )
        } else {
            game.copy(
                opponentGold = game.opponentGold + goldGain
            )
        }
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

    fun buildDebusquerPendingChoice(
        game: GameState,
        owner: ChoiceOwner,
        sourceCardId: CardId
    ): PendingChoice? {

        val opponentAmbush = if (owner == ChoiceOwner.PLAYER) {
            game.opponentAmbush
        } else {
            game.playerAmbush
        }

        // Aucune carte adverse à débusquer.
        if (opponentAmbush.isEmpty()) {
            return null
        }

        return PendingChoice(
            type = "DEBUSQUER_CHOICE",
            cardId = sourceCardId.name,
            options = opponentAmbush.indices.map { index ->
                "DISCARD:$index"
            } + "PASS",
            message = "DEBUSQUER_CHOICE",
            owner = owner
        )
    }

    fun buildEnragePendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): PendingChoice {
        val ownerIsPlayer = owner == ChoiceOwner.PLAYER

        val ownerGold = if (ownerIsPlayer) {
            game.playerGold
        } else {
            game.opponentGold
        }

        val purchasedOptions = if (ownerIsPlayer) {
            game.playerEnragePurchasedOptions
        } else {
            game.opponentEnragePurchasedOptions
        }

        val options = buildList {
            if (ownerGold >= 2 && "ENRAGE_COMBAT_2" !in purchasedOptions) {
                add("ENRAGE_COMBAT_2")
            }

            if (ownerGold >= 3 && "ENRAGE_COMBAT_3" !in purchasedOptions) {
                add("ENRAGE_COMBAT_3")
            }

            if (ownerGold >= 2 && "ENRAGE_DIRECT_1" !in purchasedOptions) {
                add("ENRAGE_DIRECT_1")
            }

            if (ownerGold >= 3 && "ENRAGE_DIRECT_2" !in purchasedOptions) {
                add("ENRAGE_DIRECT_2")
            }

            add("ENRAGE_CLOSE")
        }

        return PendingChoice(
            type = "ENRAGE_CHOICE",
            cardId = CardId.ENRAGE.name,
            options = options,
            message = "Choisissez les améliorations de l'Enragé.",
            owner = owner,
            ownerGold = ownerGold
        )
    }

    fun buildExperienceLaboratoirePendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): PendingChoice {
        val ownerIsPlayer = owner == ChoiceOwner.PLAYER

        val ownerCard = if (ownerIsPlayer) {
            game.lastPlayerCard
        } else {
            game.lastOpponentCard
        } ?: error("L'Expérience de Laboratoire n'est pas révélée")

        val opponentCard = if (ownerIsPlayer) {
            game.lastOpponentCard
        } else {
            game.lastPlayerCard
        } ?: error("La carte adverse n'est pas révélée")

        val ownerGold = if (ownerIsPlayer) {
            game.playerGold
        } else {
            game.opponentGold
        }

        val opponentGold = if (ownerIsPlayer) {
            game.opponentGold
        } else {
            game.playerGold
        }

        val ownerPowerBonus = if (ownerIsPlayer) {
            game.playerCurrentCardPowerBonus
        } else {
            game.opponentCurrentCardPowerBonus
        }

        val opponentPowerBonus = if (ownerIsPlayer) {
            game.opponentCurrentCardPowerBonus
        } else {
            game.playerCurrentCardPowerBonus
        }

        val alreadyHasBrute = if (ownerIsPlayer) {
            game.playerCurrentCardHasBruteBonus
        } else {
            game.opponentCurrentCardHasBruteBonus
        }

        val options = buildList {
            if (ownerGold >= 1) {
                add("LAB_POWER")
            }

            if (ownerGold >= 2 && !alreadyHasBrute) {
                add("LAB_BRUTE")
            }

            add("LAB_CLOSE")
        }

        return PendingChoice(
            type = "EXPERIENCE_LABORATOIRE_CHOICE",
            cardId = CardId.EXPERIENCEDELABORATOIRE.name,
            options = options,
            message = "Améliorez L’Expérience de Laboratoire pour ce tour.",
            owner = owner,
            ownerGold = ownerGold,
            ownerCurrentPower = getEffectivePower(
                game = game,
                card = ownerCard,
                ownerGold = ownerGold,
                nextCardPowerBonus = ownerPowerBonus,
                isPlayer = ownerIsPlayer
            ),
            opponentCurrentPower = getEffectivePower(
                game = game,
                card = opponentCard,
                ownerGold = opponentGold,
                nextCardPowerBonus = opponentPowerBonus,
                isPlayer = !ownerIsPlayer
            )
        )
    }

    fun startChamaneSubterfuge(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {
        val ownerIsPlayer = owner == ChoiceOwner.PLAYER

        var recycledGame = DeckManager.reshuffleDiscardIntoDeckIfNeeded(
            game = game,
            target = ChoiceOwner.PLAYER
        )

        recycledGame = DeckManager.reshuffleDiscardIntoDeckIfNeeded(
            game = recycledGame,
            target = ChoiceOwner.OPPONENT
        )

        val playerDeck = recycledGame.playerDeck
        val opponentDeck = recycledGame.opponentDeck
        val playerDiscard = recycledGame.playerDiscard
        val opponentDiscard = recycledGame.opponentDiscard

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
            game = recycledGame.copy(
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

        return recycledGame.copy(
            playerDeck = playerDeck,
            opponentDeck = opponentDeck,
            playerDiscard = playerDiscard,
            opponentDiscard = opponentDiscard,
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

            CardId.GARDEVAUDOU ->
                "Sacrifier Le Garde Vaudou après le combat pour gagner 3 Or ?"

            CardId.CONTAGIEUX ->
                "Sacrifier Contagieux après le combat pour infliger 2 jetons Poison à l'adversaire ?"

            CardId.OEIL ->
                "Sacrifier L'Œil après le combat pour gagner 2 PV ?"

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

    fun buildLameErrantePendingChoice(
        owner: ChoiceOwner
    ): PendingChoice {
        return PendingChoice(
            type = "LAME_ERRANTE_DODGE",
            cardId = CardId.LAMEERRANTE.name,
            options = listOf("DODGE", "PASS"),
            message = "",
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
            // Lame Errante
            // -------------------------------------------------
            "LAME_ERRANTE_DODGE" -> {
                if (choice !in pendingChoice.options) {
                    game.copy(infoMessage = "Choix invalide")
                } else {
                    val useDodge = choice == "DODGE"

                    game.copy(
                        playerHp = if (ownerIsPlayer && useDodge) {
                            (game.playerHp - 1).coerceAtLeast(0)
                        } else game.playerHp,

                        opponentHp = if (!ownerIsPlayer && useDodge) {
                            (game.opponentHp - 1).coerceAtLeast(0)
                        } else game.opponentHp,

                        combatCancelledByDodge =
                            game.combatCancelledByDodge || useDodge,

                        pendingChoice = null,
                        playerEffectResolved =
                            if (ownerIsPlayer) true else game.playerEffectResolved,
                        opponentEffectResolved =
                            if (!ownerIsPlayer) true else game.opponentEffectResolved,
                        infoMessage = null
                    )
                }
            }

            // -------------------------------------------------
            // Débusquer
            // -------------------------------------------------

            "DEBUSQUER_CHOICE" -> {

                val targetIsPlayer = pendingChoice.owner == ChoiceOwner.OPPONENT

                val targetAmbush = if (targetIsPlayer) {
                    game.playerAmbush
                } else {
                    game.opponentAmbush
                }

                when {
                    choice == "PASS" -> {
                        game.copy(
                            pendingChoice = null,
                            infoMessage = null
                        )
                    }

                    choice.startsWith("DISCARD:") -> {
                        val index = choice.removePrefix("DISCARD:").toIntOrNull()

                        if (index == null || index !in targetAmbush.indices) {
                            game.copy(infoMessage = "Choix invalide")
                        } else {
                            val selectedCard = targetAmbush[index].card

                            if (targetIsPlayer) {
                                game.copy(
                                    playerAmbush = game.playerAmbush.filterIndexed { i, _ ->
                                        i != index
                                    },
                                    playerDiscard = game.playerDiscard + selectedCard,
                                    pendingChoice = null,
                                    infoMessage = null
                                )
                            } else {
                                game.copy(
                                    opponentAmbush = game.opponentAmbush.filterIndexed { i, _ ->
                                        i != index
                                    },
                                    opponentDiscard = game.opponentDiscard + selectedCard,
                                    pendingChoice = null,
                                    infoMessage = null
                                )
                            }
                        }
                    }

                    else -> {
                        game.copy(infoMessage = "Choix invalide")
                    }
                }
            }

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
                        val healedGame = TokenManager.heal(
                            game = game,
                            target = pendingChoice.owner,
                            amount = 1
                        )

                        if (ownerIsPlayer) {
                            healedGame.copy(
                                pendingChoice = null,
                                playerEffectResolved = true,
                                infoMessage = null
                            )
                        } else {
                            healedGame.copy(
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

                        val targetOwner = if (ownerIsPlayer) {
                            ChoiceOwner.PLAYER
                        } else {
                            ChoiceOwner.OPPONENT
                        }

                        val recycledGame = DeckManager.reshuffleDiscardIntoDeckIfNeeded(
                            game = game,
                            target = targetOwner
                        )

                        val updatedGame = if (ownerIsPlayer) {

                            recycledGame.copy(
                                playerDeck = recycledGame.playerDeck.drop(1)
                            )

                        } else {

                            recycledGame.copy(
                                opponentDeck = recycledGame.opponentDeck.drop(1)
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
                                    TokenManager.heal(
                                        game = game,
                                        target = pendingChoice.owner,
                                        amount = 1
                                    )
                                }

                                CardId.DEVINDESTENEBRES.name -> {
                                    if (ownerIsPlayer) {
                                        game.copy(opponentHp = maxOf(0, game.opponentHp - 1))
                                    } else {
                                        game.copy(playerHp = maxOf(0, game.playerHp - 1))
                                    }
                                }

                                CardId.OEIL.name -> {
                                    TokenManager.heal(
                                        game = game,
                                        target = pendingChoice.owner,
                                        amount = 2
                                    )
                                }

                                CardId.GARDEVAUDOU.name -> {
                                    if (ownerIsPlayer) {
                                        game.copy(
                                            playerGold = game.playerGold + 3
                                        )
                                    } else {
                                        game.copy(
                                            opponentGold = game.opponentGold + 3
                                        )
                                    }
                                }

                                CardId.CONTAGIEUX.name -> {
                                    CardEffectManager.addPoisonToken(
                                        game = game,
                                        target = if (ownerIsPlayer) {
                                            ChoiceOwner.OPPONENT
                                        } else {
                                            ChoiceOwner.PLAYER
                                        },
                                        source = pendingChoice.owner,
                                        amount = 2
                                    )
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

                            val gameAfterDestroy = if (ownerIsPlayer) {
                                game.copy(
                                    playerDiscard = newDiscard
                                )
                            } else {
                                game.copy(
                                    opponentDiscard = newDiscard
                                )
                            }

                            val healedGame = TokenManager.heal(
                                game = gameAfterDestroy,
                                target = pendingChoice.owner,
                                amount = healAmount
                            )

                            if (ownerIsPlayer) {
                                healedGame.copy(
                                    pendingChoice = null,
                                    playerEffectResolved = true,
                                    infoMessage = null
                                )
                            } else {
                                healedGame.copy(
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
                                ?: return game.copy(
                                    infoMessage = "Aucune carte adverse à remplacer"
                                )

                            var workingGame = game.copy(
                                opponentDiscard = game.opponentDiscard + oldOpponentCard
                            )

                            workingGame = DeckManager.reshuffleDiscardIntoDeckIfNeeded(
                                game = workingGame,
                                target = ChoiceOwner.OPPONENT
                            )

                            val newOpponentCard = workingGame.opponentDeck.firstOrNull()
                                ?: return workingGame.copy(
                                    infoMessage = "Impossible de révéler une nouvelle carte"
                                )

                            workingGame.copy(
                                opponentDeck = workingGame.opponentDeck.drop(1),
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
                                ?: return game.copy(
                                    infoMessage = "Aucune carte adverse à remplacer"
                                )

                            var workingGame = game.copy(
                                playerDiscard = game.playerDiscard + oldPlayerCard
                            )

                            workingGame = DeckManager.reshuffleDiscardIntoDeckIfNeeded(
                                game = workingGame,
                                target = ChoiceOwner.PLAYER
                            )

                            val newPlayerCard = workingGame.playerDeck.firstOrNull()
                                ?: return workingGame.copy(
                                    infoMessage = "Impossible de révéler une nouvelle carte"
                                )

                            workingGame.copy(
                                playerDeck = workingGame.playerDeck.drop(1),
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
// ENRAGÉ — ACHATS D'OPTIONS
// -------------------------------------------------
            "ENRAGE_CHOICE" -> {
                val costs = mapOf(
                    "ENRAGE_COMBAT_2" to 2,
                    "ENRAGE_COMBAT_3" to 3,
                    "ENRAGE_DIRECT_1" to 2,
                    "ENRAGE_DIRECT_2" to 3
                )

                when {
                    choice == "ENRAGE_CLOSE" -> {
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

                    choice in costs -> {
                        val cost = costs.getValue(choice)

                        val purchasedOptions = if (ownerIsPlayer) {
                            game.playerEnragePurchasedOptions
                        } else {
                            game.opponentEnragePurchasedOptions
                        }

                        val gold = if (ownerIsPlayer) {
                            game.playerGold
                        } else {
                            game.opponentGold
                        }

                        when {
                            choice !in pendingChoice.options ||
                                    choice in purchasedOptions -> {
                                game.copy(infoMessage = "Option indisponible")
                            }

                            gold < cost -> {
                                game.copy(infoMessage = "Or insuffisant")
                            }

                            else -> {
                                val updatedOptions = purchasedOptions + choice

                                val updatedGame = if (ownerIsPlayer) {
                                    game.copy(
                                        playerGold = gold - cost,
                                        playerEnragePurchasedOptions = updatedOptions,
                                        playerEnrageCombatDamage = when (choice) {
                                            "ENRAGE_COMBAT_2" -> 2
                                            "ENRAGE_COMBAT_3" -> 3
                                            else -> game.playerEnrageCombatDamage
                                        },
                                        opponentHp = when (choice) {
                                            "ENRAGE_DIRECT_1" -> maxOf(0, game.opponentHp - 1)
                                            "ENRAGE_DIRECT_2" -> maxOf(0, game.opponentHp - 2)
                                            else -> game.opponentHp
                                        },
                                        infoMessage = null
                                    )
                                } else {
                                    game.copy(
                                        opponentGold = gold - cost,
                                        opponentEnragePurchasedOptions = updatedOptions,
                                        opponentEnrageCombatDamage = when (choice) {
                                            "ENRAGE_COMBAT_2" -> 2
                                            "ENRAGE_COMBAT_3" -> 3
                                            else -> game.opponentEnrageCombatDamage
                                        },
                                        playerHp = when (choice) {
                                            "ENRAGE_DIRECT_1" -> maxOf(0, game.playerHp - 1)
                                            "ENRAGE_DIRECT_2" -> maxOf(0, game.playerHp - 2)
                                            else -> game.playerHp
                                        },
                                        infoMessage = null
                                    )
                                }

                                updatedGame.copy(
                                    pendingChoice = buildEnragePendingChoice(
                                        game = updatedGame,
                                        owner = pendingChoice.owner
                                    )
                                )
                            }
                        }
                    }

                    else -> game.copy(infoMessage = "Choix invalide")
                }
            }

            // -------------------------------------------------
            // EXPÉRIENCE DE LABORATOIRE
            // -------------------------------------------------
            "EXPERIENCE_LABORATOIRE_CHOICE" -> {
                when (choice) {

                    "LAB_POWER" -> {
                        if (choice !in pendingChoice.options) {
                            game.copy(
                                infoMessage = "Choix invalide"
                            )
                        } else if (ownerIsPlayer) {
                            if (game.playerGold < 1) {
                                game.copy(
                                    infoMessage = "Or insuffisant"
                                )
                            } else {
                                val updatedGame = game.copy(
                                    playerGold = game.playerGold - 1,
                                    playerCurrentCardPowerBonus =
                                        game.playerCurrentCardPowerBonus + 1,
                                    infoMessage = null
                                )

                                updatedGame.copy(
                                    pendingChoice = buildExperienceLaboratoirePendingChoice(
                                        game = updatedGame,
                                        owner = pendingChoice.owner
                                    )
                                )
                            }
                        } else {
                            if (game.opponentGold < 1) {
                                game.copy(
                                    infoMessage = "Or insuffisant"
                                )
                            } else {
                                val updatedGame = game.copy(
                                    opponentGold = game.opponentGold - 1,
                                    opponentCurrentCardPowerBonus =
                                        game.opponentCurrentCardPowerBonus + 1,
                                    infoMessage = null
                                )

                                updatedGame.copy(
                                    pendingChoice = buildExperienceLaboratoirePendingChoice(
                                        game = updatedGame,
                                        owner = pendingChoice.owner
                                    )
                                )
                            }
                        }
                    }

                    "LAB_BRUTE" -> {
                        if (choice !in pendingChoice.options) {
                            game.copy(
                                infoMessage = "Choix invalide"
                            )
                        } else if (ownerIsPlayer) {
                            if (game.playerGold < 2) {
                                game.copy(
                                    infoMessage = "Or insuffisant"
                                )
                            } else if (game.playerCurrentCardHasBruteBonus) {
                                game.copy(
                                    infoMessage = "BRUTE déjà actif"
                                )
                            } else {
                                val updatedGame = game.copy(
                                    playerGold = game.playerGold - 2,
                                    playerCurrentCardHasBruteBonus = true,
                                    infoMessage = null
                                )

                                updatedGame.copy(
                                    pendingChoice = buildExperienceLaboratoirePendingChoice(
                                        game = updatedGame,
                                        owner = pendingChoice.owner
                                    )
                                )
                            }
                        } else {
                            if (game.opponentGold < 2) {
                                game.copy(
                                    infoMessage = "Or insuffisant"
                                )
                            } else if (game.opponentCurrentCardHasBruteBonus) {
                                game.copy(
                                    infoMessage = "BRUTE déjà actif"
                                )
                            } else {
                                val updatedGame = game.copy(
                                    opponentGold = game.opponentGold - 2,
                                    opponentCurrentCardHasBruteBonus = true,
                                    infoMessage = null
                                )

                                updatedGame.copy(
                                    pendingChoice = buildExperienceLaboratoirePendingChoice(
                                        game = updatedGame,
                                        owner = pendingChoice.owner
                                    )
                                )
                            }
                        }
                    }

                    "LAB_CLOSE" -> {
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