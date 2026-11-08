package com.battleon

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.battleon.solo.EmptyShopSlot
import com.battleon.solo.FixedShopCard
import com.battleon.solo.PlayerSelectedShopCard
import com.battleon.solo.SoloMissionCatalog
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloProgressService
import com.battleon.solo.SoloAiRouter
import com.battleon.solo.AmbushWindow
import com.battleon.solo.SoloRuneCatalog
import com.battleon.solo.SoloRuneDefinition
import com.battleon.solo.SoloRuneEffectType


object GameManager {

    // =========================================================
    // 1. STOCKAGE DES PARTIES EN MÉMOIRE
    // =========================================================

    private val games = ConcurrentHashMap<String, GameState>()

    private val gameLocks = ConcurrentHashMap<String, Any>()

    private fun getGameLock(gameId: String): Any {
        return gameLocks.computeIfAbsent(gameId) { Any() }
    }

    private inline fun <T> withGameLock(
        gameId: String,
        block: () -> T
    ): T {
        return synchronized(getGameLock(gameId)) {
            block()
        }
    }

    //délai de timeout
    private const val FORFEIT_TIMEOUT_MILLIS = 120_000L

    // =========================================================
    // 2. CRÉATION DU SHOP
    // =========================================================

    fun createTrainingShop(ownedCardIds: List<String>): List<ShopEntry> {
        val eligibleCardIds = ownedCardIds
            .filterNot { cardId ->
                val enumCardId = try {
                    CardId.valueOf(cardId)
                } catch (_: Exception) {
                    null
                }

                enumCardId != null && CardCatalog.isExcludedFromShop(enumCardId)
            }
            .distinct()
            .shuffled()
            .take(10)

        return eligibleCardIds.mapNotNull { cardId ->
            val enumCardId = try {
                CardId.valueOf(cardId)
            } catch (_: IllegalArgumentException) {
                null
            }

            enumCardId?.let {
                ShopEntry(
                    card = CardCatalog.getCard(it),
                    copiesRemaining = 4
                )
            }
        }
    }

    fun createSeasonMercenaryDeck(): List<CardId> {
        return listOf(
            CardId.MERCENARY_TNT,
            CardId.MERCENARY_ASSASSIN,
            CardId.MERCENARY_COLOSSE,
            CardId.MERCENARY_DIPLOMATE,
            CardId.MERCENARY_FANATIQUE,
            CardId.MERCENARY_MAITRE_D_ARMES,
            CardId.MERCENARY_PORTEUR_DE_PESTE,
            CardId.MERCENARY_PSYCHOPATHE,
            CardId.MERCENARY_VOLEUR
        ).shuffled()
    }

    fun createTrainingShopWithForcedCard(
        ownedCardIds: List<String>,
        forcedCardId: String
    ): List<ShopEntry> {
        val forcedEnumCardId = try {
            CardId.valueOf(forcedCardId)
        } catch (_: IllegalArgumentException) {
            null
        }

        val forcedCardIsEligible = forcedEnumCardId != null &&
                !CardCatalog.isExcludedFromShop(forcedEnumCardId)

        if (!forcedCardIsEligible) {
            return createTrainingShop(ownedCardIds)
        }

        val otherEligibleCardIds = ownedCardIds
            .filterNot { it == forcedCardId }
            .filterNot { cardId ->
                val enumCardId = try {
                    CardId.valueOf(cardId)
                } catch (_: Exception) {
                    null
                }

                enumCardId != null && CardCatalog.isExcludedFromShop(enumCardId)
            }
            .distinct()
            .shuffled()
            .take(9)

        val finalCardIds = listOf(forcedCardId) + otherEligibleCardIds

        return finalCardIds.mapNotNull { cardId ->
            val enumCardId = try {
                CardId.valueOf(cardId)
            } catch (_: IllegalArgumentException) {
                null
            }

            enumCardId?.let {
                ShopEntry(
                    card = CardCatalog.getCard(it),
                    copiesRemaining = 4
                )
            }
        }
    }

    fun createDuelShop(
        playerOwnedCardIds: List<String>,
        opponentOwnedCardIds: List<String>
    ): List<ShopEntry> {
        val playerEligibleCards = playerOwnedCardIds.filterEligibleForShop()
        val opponentEligibleCards = opponentOwnedCardIds.filterEligibleForShop()

        val sharedCards = playerEligibleCards
            .intersect(opponentEligibleCards.toSet())
            .toList()

        val playerRandomCard = playerEligibleCards.randomOrNull()
        val opponentRandomCard = opponentEligibleCards.randomOrNull()

        val selectedCards = mutableListOf<String>()

        playerRandomCard?.let { selectedCards.add(it) }
        opponentRandomCard?.let { selectedCards.add(it) }

        val sharedCardsToAdd = sharedCards
            .filterNot { it in selectedCards }
            .shuffled()
            .take(10 - selectedCards.size)

        selectedCards.addAll(sharedCardsToAdd)

        val missingCardsCount = 10 - selectedCards.size

        if (missingCardsCount > 0) {
            val additionalSharedCards = sharedCards
                .filterNot { it in selectedCards }
                .shuffled()
                .take(missingCardsCount)

            selectedCards.addAll(additionalSharedCards)
        }

        val finalCardIds = selectedCards
            .distinct()
            .shuffled()

        return finalCardIds.mapNotNull { cardId ->
            val enumCardId = try {
                CardId.valueOf(cardId)
            } catch (_: IllegalArgumentException) {
                null
            }

            enumCardId?.let {
                ShopEntry(
                    card = CardCatalog.getCard(it),
                    copiesRemaining = 4
                )
            }
        }
    }
    fun createSeasonShop(
        playerOwnedCardIds: List<String>,
        opponentOwnedCardIds: List<String>,
        mercenaryDeck: List<CardId>
    ): List<ShopEntry> {
        val playerEligibleCards = playerOwnedCardIds.filterEligibleForShop()
        val opponentEligibleCards = opponentOwnedCardIds.filterEligibleForShop()

        val sharedCards = playerEligibleCards
            .intersect(opponentEligibleCards.toSet())
            .toList()
            .shuffled()
            .take(9)

        val normalEntries = sharedCards.mapNotNull { cardId ->
            val enumCardId = try {
                CardId.valueOf(cardId)
            } catch (_: IllegalArgumentException) {
                null
            }

            enumCardId?.let {
                ShopEntry(
                    card = CardCatalog.getCard(it),
                    copiesRemaining = 4
                )
            }
        }

        val mercenaryEntry = mercenaryDeck.firstOrNull()?.let { mercenaryId ->
            ShopEntry(
                card = CardCatalog.getCard(mercenaryId),
                copiesRemaining = 1
            )
        }

        return if (mercenaryEntry != null) {
            normalEntries + mercenaryEntry
        } else {
            normalEntries
        }
    }

    private fun List<String>.filterEligibleForShop(): List<String> {
        return this
            .filterNot { cardId ->
                val enumCardId = try {
                    CardId.valueOf(cardId)
                } catch (_: Exception) {
                    null
                }

                enumCardId != null && CardCatalog.isExcludedFromShop(enumCardId)
            }
            .distinct()
    }

    // =========================================================
    // 3. CRÉATION DE PARTIE
    // =========================================================

    fun createTrainingGame(
        playerName: String,
        ownedCardIds: List<String>
    ): GameState {
        val shopEntries = createTrainingShop(ownedCardIds)
        val initialOpponentTargetCardId = shopEntries
            .randomOrNull()
            ?.card
            ?.id
            ?.name

        val availableSpiderCardIds = ownedCardIds.mapNotNull { cardId ->
            try {
                CardId.valueOf(cardId)
            } catch (_: Exception) {
                null
            }
        }.filter { cardId ->
            CardCatalog.getCard(cardId).isSpider
        }

        val game = GameState(

            gameId = UUID.randomUUID().toString(),
            mode = "TRAINING",

            turnNumber = 0,
            phase = TurnPhase.PRE_START,
            isFinished = false,
            result = null,

            playerReady = false,
            opponentReady = true,

            playerName = playerName,
            opponentName = "Entraînement IA",

            playerHp = 20,
            opponentHp = 20,
            playerGold = 1,
            opponentGold = 1,

            playerDeck = createStartingDeck(),
            opponentDeck = createStartingDeck(),
            playerDiscard = emptyList(),
            opponentDiscard = emptyList(),
            playerAmbush = emptyList(),
            opponentAmbush = emptyList(),

            playerTokens = emptyList(),
            opponentTokens = emptyList(),

            playerNextCardPowerBonus = 0,
            opponentNextCardPowerBonus = 0,
            playerNextCardDamageBonus = 0,
            opponentNextCardDamageBonus = 0,
            playerNextCardHasBrute = false,
            opponentNextCardHasBrute = false,

            lastPlayerCard = null,
            lastOpponentCard = null,

            delayedEffects = emptyList(),

            shopEntries = shopEntries,
            playerAvailableSpiderCardIds = availableSpiderCardIds,
            opponentAvailableSpiderCardIds = availableSpiderCardIds,

            playerPurchasedCardCounts = emptyMap(),
            opponentPurchasedCardCounts = emptyMap(),

            playerPendingShopPurchaseCardId = null,
            opponentPendingShopPurchaseCardId = initialOpponentTargetCardId,
            playerShopStatus = ShopPurchaseStatus.NONE,
            opponentShopStatus = ShopPurchaseStatus.NONE,
            shopPriorityPlayerFirst = null,
            currentShopBuyerIsPlayer = null,
            playerPassedShop = false,
            opponentPassedShop = false,

            pendingChoice = null,
            playerEffectResolved = false,
            opponentEffectResolved = false,
            infoMessage = null
        )

        games[game.gameId] = game
        return game
    }

    fun createTrainingGameWithForcedShopCard(
        playerName: String,
        ownedCardIds: List<String>,
        forcedCardId: String
    ): GameState {
        val shopEntries = createTrainingShopWithForcedCard(
            ownedCardIds = ownedCardIds,
            forcedCardId = forcedCardId
        )
        val initialOpponentTargetCardId = shopEntries
            .randomOrNull()
            ?.card
            ?.id
            ?.name
        val availableSpiderCardIds = ownedCardIds.mapNotNull { cardId ->
            try {
                CardId.valueOf(cardId)
            } catch (_: Exception) {
                null
            }
        }.filter { cardId ->
            CardCatalog.getCard(cardId).isSpider
        }

        val game = GameState(
            gameId = UUID.randomUUID().toString(),
            mode = "TRAINING",

            turnNumber = 0,
            phase = TurnPhase.PRE_START,
            isFinished = false,
            result = null,

            playerReady = false,
            opponentReady = true,

            playerName = playerName,
            opponentName = "Entraînement IA",

            playerHp = 20,
            opponentHp = 20,
            playerGold = 1,
            opponentGold = 1,

            playerDeck = createStartingDeck(),
            opponentDeck = createStartingDeck(),
            playerDiscard = emptyList(),
            opponentDiscard = emptyList(),
            playerAmbush = emptyList(),
            opponentAmbush = emptyList(),

            playerTokens = emptyList(),
            opponentTokens = emptyList(),

            playerNextCardPowerBonus = 0,
            opponentNextCardPowerBonus = 0,
            playerNextCardDamageBonus = 0,
            opponentNextCardDamageBonus = 0,
            playerNextCardHasBrute = false,
            opponentNextCardHasBrute = false,

            lastPlayerCard = null,
            lastOpponentCard = null,

            delayedEffects = emptyList(),

            shopEntries = shopEntries,
            playerAvailableSpiderCardIds = availableSpiderCardIds,
            opponentAvailableSpiderCardIds = availableSpiderCardIds,
            playerPurchasedCardCounts = emptyMap(),
            opponentPurchasedCardCounts = emptyMap(),

            playerPendingShopPurchaseCardId = null,
            opponentPendingShopPurchaseCardId = initialOpponentTargetCardId,
            playerShopStatus = ShopPurchaseStatus.NONE,
            opponentShopStatus = ShopPurchaseStatus.NONE,
            shopPriorityPlayerFirst = null,
            currentShopBuyerIsPlayer = null,
            playerPassedShop = false,
            opponentPassedShop = false,

            pendingChoice = null,
            playerEffectResolved = false,
            opponentEffectResolved = false,
            infoMessage = null
        )

        games[game.gameId] = game
        return game
    }
    fun createDuelGame(
        playerUserId: Int,
        playerName: String,
        playerOwnedCardIds: List<String>,

        opponentUserId: Int,
        opponentName: String,
        opponentOwnedCardIds: List<String>

    ): GameState {
        val shopEntries = createDuelShop(
            playerOwnedCardIds = playerOwnedCardIds,
            opponentOwnedCardIds = opponentOwnedCardIds
        )

        val sharedSpiderCardIds = playerOwnedCardIds
            .intersect(opponentOwnedCardIds.toSet())
            .mapNotNull { cardId ->
                try {
                    CardId.valueOf(cardId)
                } catch (_: Exception) {
                    null
                }
            }
            .filter { cardId ->
                CardCatalog.getCard(cardId).isSpider
            }

        val game = GameState(
            gameId = UUID.randomUUID().toString(),
            mode = "DUEL",

            turnNumber = 0,
            phase = TurnPhase.PRE_START,
            isFinished = false,
            result = null,

            playerReady = false,
            opponentReady = false,

            playerLastSeenAtMillis = System.currentTimeMillis(),
            opponentLastSeenAtMillis = System.currentTimeMillis(),

            playerName = playerName,
            opponentName = opponentName,

            playerUserId = playerUserId,
            opponentUserId = opponentUserId,

            playerHp = 20,
            opponentHp = 20,
            playerGold = 1,
            opponentGold = 1,

            playerDeck = createStartingDeck(),
            opponentDeck = createStartingDeck(),
            playerDiscard = emptyList(),
            opponentDiscard = emptyList(),
            playerAmbush = emptyList(),
            opponentAmbush = emptyList(),

            playerTokens = emptyList(),
            opponentTokens = emptyList(),

            playerNextCardPowerBonus = 0,
            opponentNextCardPowerBonus = 0,
            playerNextCardDamageBonus = 0,
            opponentNextCardDamageBonus = 0,
            playerNextCardHasBrute = false,
            opponentNextCardHasBrute = false,

            lastPlayerCard = null,
            lastOpponentCard = null,

            delayedEffects = emptyList(),

            shopEntries = shopEntries,
            playerAvailableSpiderCardIds = sharedSpiderCardIds,
            opponentAvailableSpiderCardIds = sharedSpiderCardIds,

            playerPurchasedCardCounts = emptyMap(),
            opponentPurchasedCardCounts = emptyMap(),

            playerPendingShopPurchaseCardId = null,
            opponentPendingShopPurchaseCardId = null,
            playerShopStatus = ShopPurchaseStatus.NONE,
            opponentShopStatus = ShopPurchaseStatus.NONE,
            shopPriorityPlayerFirst = null,
            currentShopBuyerIsPlayer = null,
            playerPassedShop = false,
            opponentPassedShop = false,

            pendingChoice = null,
            playerEffectResolved = false,
            opponentEffectResolved = false,
            infoMessage = null
        )

        games[game.gameId] = game
        return game
    }
    fun createSeasonGame(
        playerUserId: Int,
        playerName: String,
        playerOwnedCardIds: List<String>,

        opponentUserId: Int,
        opponentName: String,
        opponentOwnedCardIds: List<String>

    ): GameState {
        val mercenaryDeck = createSeasonMercenaryDeck()

        val shopEntries = createSeasonShop(
            playerOwnedCardIds = playerOwnedCardIds,
            opponentOwnedCardIds = opponentOwnedCardIds,
            mercenaryDeck = mercenaryDeck
        )

        val sharedSpiderCardIds = playerOwnedCardIds
            .intersect(opponentOwnedCardIds.toSet())
            .mapNotNull { cardId ->
                try {
                    CardId.valueOf(cardId)
                } catch (_: Exception) {
                    null
                }
            }
            .filter { cardId ->
                CardCatalog.getCard(cardId).isSpider
            }

        val game = GameState(
            gameId = UUID.randomUUID().toString(),
            mode = "SEASON",
            shopEntries = shopEntries,
            seasonMercenaryDeck = mercenaryDeck,
            playerBoughtSeasonMercenary = false,
            opponentBoughtSeasonMercenary = false,

            turnNumber = 0,
            phase = TurnPhase.PRE_START,
            isFinished = false,
            result = null,

            playerReady = false,
            opponentReady = false,

            playerLastSeenAtMillis = System.currentTimeMillis(),
            opponentLastSeenAtMillis = System.currentTimeMillis(),

            playerName = playerName,
            opponentName = opponentName,

            playerUserId = playerUserId,
            opponentUserId = opponentUserId,

            playerHp = 20,
            opponentHp = 20,
            playerGold = 1,
            opponentGold = 1,

            playerDeck = createStartingDeck(),
            opponentDeck = createStartingDeck(),
            playerDiscard = emptyList(),
            opponentDiscard = emptyList(),
            playerAmbush = emptyList(),
            opponentAmbush = emptyList(),

            playerTokens = emptyList(),
            opponentTokens = emptyList(),

            playerNextCardPowerBonus = 0,
            opponentNextCardPowerBonus = 0,
            playerNextCardDamageBonus = 0,
            opponentNextCardDamageBonus = 0,
            playerNextCardHasBrute = false,
            opponentNextCardHasBrute = false,

            lastPlayerCard = null,
            lastOpponentCard = null,

            delayedEffects = emptyList(),

            playerAvailableSpiderCardIds = sharedSpiderCardIds,
            opponentAvailableSpiderCardIds = sharedSpiderCardIds,

            playerPurchasedCardCounts = emptyMap(),
            opponentPurchasedCardCounts = emptyMap(),

            playerPendingShopPurchaseCardId = null,
            opponentPendingShopPurchaseCardId = null,
            playerShopStatus = ShopPurchaseStatus.NONE,
            opponentShopStatus = ShopPurchaseStatus.NONE,
            shopPriorityPlayerFirst = null,
            currentShopBuyerIsPlayer = null,
            playerPassedShop = false,
            opponentPassedShop = false,

            pendingChoice = null,
            playerEffectResolved = false,
            opponentEffectResolved = false,
            infoMessage = null
        )

        games[game.gameId] = game
        return game
    }

    private fun createSoloDeck(
        cardIds: List<CardId>
    ): MutableList<Card> {
        return cardIds
            .map { cardId -> CardCatalog.getCard(cardId) }
            .toMutableList()
            .also { it.shuffle() }
    }

    private fun createSoloShop(
        config: SoloMissionGameConfig
    ): List<ShopEntry> {
        return config.shopDefinition.slots.mapNotNull { slot ->
            when (slot) {
                is FixedShopCard -> ShopEntry(
                    card = CardCatalog.getCard(slot.cardId),
                    copiesRemaining = config.shopDefinition.copiesRemainingPerCard
                )

                is PlayerSelectedShopCard -> {
                    val selectedCardId = config.selectedCardIds.getOrNull(slot.selectionIndex)

                    val enumCardId = selectedCardId?.let {
                        try {
                            CardId.valueOf(it)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    enumCardId?.let {
                        ShopEntry(
                            card = CardCatalog.getCard(it),
                            copiesRemaining = config.shopDefinition.copiesRemainingPerCard
                        )
                    }
                }

                EmptyShopSlot -> null
            }
        }
    }

    fun createSoloMissionGame(
        playerUserId: Int,
        playerName: String,
        missionId: String,
        difficulty: SoloMissionDifficulty,
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): GameState? {
        val mission = SoloMissionCatalog.findMission(missionId)
            ?: return null

        val config = when (difficulty) {
            SoloMissionDifficulty.CAMPAIGN -> mission.buildCampaignConfig(
                selectedRuneIds = selectedRuneIds,
                selectedCardIds = selectedCardIds
            )

            SoloMissionDifficulty.HARD -> mission.buildHardConfig(
                selectedRuneIds = selectedRuneIds,
                selectedCardIds = selectedCardIds
            )
        }

        val requiredSelectedCardCount = config.shopDefinition.slots.count { slot ->
            slot is PlayerSelectedShopCard
        }

        if (selectedCardIds.size != requiredSelectedCardCount) {
            return null
        }

        val shopEntries = createSoloShop(config)

        val game = GameState(
            gameId = UUID.randomUUID().toString(),
            mode = "SOLO",
            soloMissionId = config.missionId,
            soloDifficulty = config.difficulty.name,
            soloAiType = config.aiType.name,

            turnNumber = 0,
            phase = TurnPhase.PRE_START,
            isFinished = false,
            result = null,

            playerReady = false,
            opponentReady = true,

            playerName = playerName,
            opponentName = config.opponentNameKey,

            playerUserId = playerUserId,
            opponentUserId = null,

            playerHp = config.playerHp,
            opponentHp = config.opponentHp,
            playerGold = config.playerGold,
            opponentGold = config.opponentGold,

            playerDeck = createSoloDeck(config.playerStartingDeck),
            opponentDeck = createSoloDeck(config.opponentStartingDeck),
            playerDiscard = emptyList(),
            opponentDiscard = emptyList(),
            playerAmbush = emptyList(),
            opponentAmbush = emptyList(),

            playerAvailableRuneIds = config.selectedRuneIds,
            opponentAvailableRuneIds = emptyList(),

            playerTokens = config.playerStartingTokens,
            opponentTokens = config.opponentStartingTokens,

            playerNextCardPowerBonus = 0,
            opponentNextCardPowerBonus = 0,
            playerNextCardDamageBonus = 0,
            opponentNextCardDamageBonus = 0,
            playerNextCardHasBrute = false,
            opponentNextCardHasBrute = false,

            lastPlayerCard = null,
            lastOpponentCard = null,

            delayedEffects = emptyList(),

            shopEntries = shopEntries,

            playerAvailableSpiderCardIds = emptyList(),
            opponentAvailableSpiderCardIds = emptyList(),

            playerPurchasedCardCounts = emptyMap(),
            opponentPurchasedCardCounts = emptyMap(),

            playerPendingShopPurchaseCardId = null,
            opponentPendingShopPurchaseCardId = shopEntries.randomOrNull()?.card?.id?.name,

            playerShopStatus = ShopPurchaseStatus.NONE,
            opponentShopStatus = ShopPurchaseStatus.NONE,
            shopPriorityPlayerFirst = null,
            currentShopBuyerIsPlayer = null,
            playerPassedShop = false,
            opponentPassedShop = false,

            pendingChoice = null,
            playerEffectResolved = false,
            opponentEffectResolved = false,
            infoMessage = null
        )

        games[game.gameId] = game
        return game
    }

    fun createStartingDeck(): MutableList<Card> {
        val deck = mutableListOf(
            CardCatalog.getCard(CardId.COLLECTOR),
            CardCatalog.getCard(CardId.COLLECTOR),
            CardCatalog.getCard(CardId.WARRIOR),
            CardCatalog.getCard(CardId.CURSED),
            CardCatalog.getCard(CardId.HEALER)
        )

        deck.shuffle()
        return deck
    }

    // =========================================================
    // 4. ACCÈS AUX PARTIES
    // =========================================================

    fun getGame(gameId: String): GameState? {
        return games[gameId]
    }

    // =========================================================
    // 5. HELPERS GÉNÉRAUX
    // =========================================================

    private const val MIN_PHASE_DISPLAY_MILLIS = 2_000L
    private const val EMPTY_AMBUSH_DISPLAY_MILLIS = 500L
    // Temps pour déclarer une action tactique.
    private const val AMBUSH_DECISION_TIMEOUT_MILLIS = 2_000L

    // Après une réactivation, le délai de déclaration est réduit.
    private const val AMBUSH_REACTIVATED_DECISION_TIMEOUT_MILLIS = 1_000L

    // Temps pour terminer une action déjà déclarée.
    // Ces deux constantes seront raccordées à l'étape suivante.
    private const val AMBUSH_ACTION_TIMEOUT_MILLIS = 10_000L
    private const val AMBUSH_REACTIVATED_ACTION_TIMEOUT_MILLIS = 5_000L

    private const val PVP_DECISION_TIMEOUT_MILLIS = 20_000L
    private const val PVP_SHOP_TIMEOUT_MILLIS = 20_000L
    private const val PVP_PRE_START_TIMEOUT_MILLIS = 90_000L


    private fun enterPhase(
        game: GameState,
        phase: TurnPhase
    ): GameState {
        val now = System.currentTimeMillis()

        val deadlineAtMillis = PhaseManager.initialPhaseDeadline(
            game = game,
            phase = phase,
            nowMillis = now
        )


        val enteredGame = game.copy(
            phase = phase,

            phaseEnteredAtMillis = now,
            phaseResolutionRequested = false,
            phaseDeadlineAtMillis = deadlineAtMillis,

            playerAdvanceReady = false,
            opponentAdvanceReady = false,

            infoMessage = null
        )

        return if (isAmbushPhase(phase)) {
            startAmbushWindow(enteredGame)
        } else {
            enteredGame
        }
    }


    private fun canResolveCurrentPhase(game: GameState): Boolean {
        return PhaseManager.canResolveCurrentPhase(game)
    }



    private fun isWaitingForPhaseDeadline(game: GameState): Boolean {
        return PhaseManager.isWaitingForPhaseDeadline(game)
    }


    fun openAmbushActionMenu(
        gameId: String,
        owner: ChoiceOwner
    ): GameState? = withGameLock(gameId) {
        val game = games[gameId] ?: return@withGameLock null

        // Le menu Action n'existe que pendant une fenêtre tactique.
        if (!isAmbushPhase(game.phase)) {
            return@withGameLock game
        }

        // Seul le joueur prioritaire peut ouvrir son menu.
        if (game.currentAmbushActor != owner) {
            return@withGameLock game
        }

        // Une action déjà en cours doit être terminée.
        if (game.pendingChoice != null) {
            return@withGameLock game
        }

        // Ne pas rouvrir un menu déjà ouvert.
        if (game.ambushActionMenuOpen) {
            return@withGameLock game
        }

        // Le joueur doit encore disposer d'une action.
        if (!hasAvailableAmbushAction(game, owner)) {
            return@withGameLock game
        }

        // L'appui sur Action doit arriver avant la fin
        // de la fenêtre de déclaration de 2/1 seconde.
        val now = System.currentTimeMillis()
        val deadline = game.phaseDeadlineAtMillis

        if (deadline == null || now >= deadline) {
            return@withGameLock game
        }

        val reactivated = when (owner) {
            ChoiceOwner.PLAYER ->
                game.playerReactivatedCurrentAmbushWindow

            ChoiceOwner.OPPONENT ->
                game.opponentReactivatedCurrentAmbushWindow
        }

        val duration = if (reactivated) {
            AMBUSH_REACTIVATED_ACTION_TIMEOUT_MILLIS
        } else {
            AMBUSH_ACTION_TIMEOUT_MILLIS
        }

        val updatedGame = game.copy(
            ambushActionMenuOpen = true,
            phaseDeadlineAtMillis = if (isAiControlledOpponent(game)) {
                null
            } else {
                now + duration
            },
            infoMessage = null
        )

        games[gameId] = updatedGame
        updatedGame
    }


    private fun startAmbushActionResolutionTimer(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {
        if (!isAmbushPhase(game.phase)) return game
        if (game.currentAmbushActor != owner) return game
        if (game.pendingChoice == null) return game

        // Le chronomètre a déjà démarré à l'ouverture du menu Action.
        // Ne jamais le réinitialiser lorsqu'un effet crée un pendingChoice.
        if (game.ambushActionMenuOpen) return game

        val hasBeenReactivated = when (owner) {
            ChoiceOwner.PLAYER ->
                game.playerReactivatedCurrentAmbushWindow

            ChoiceOwner.OPPONENT ->
                game.opponentReactivatedCurrentAmbushWindow
        }

        val duration = if (hasBeenReactivated) {
            AMBUSH_REACTIVATED_ACTION_TIMEOUT_MILLIS
        } else {
            AMBUSH_ACTION_TIMEOUT_MILLIS
        }

        return game.copy(
            phaseDeadlineAtMillis = if (isAiControlledOpponent(game)) {
                null
            } else {
                System.currentTimeMillis() + duration
            },
            infoMessage = null
        )
    }


    private fun giveAmbushPriorityTo(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {
        val now = System.currentTimeMillis()

        val hasBeenReactivated = when (owner) {
            ChoiceOwner.PLAYER ->
                game.playerReactivatedCurrentAmbushWindow

            ChoiceOwner.OPPONENT ->
                game.opponentReactivatedCurrentAmbushWindow
        }

        val decisionTimeout = if (hasBeenReactivated) {
            AMBUSH_REACTIVATED_DECISION_TIMEOUT_MILLIS
        } else {
            AMBUSH_DECISION_TIMEOUT_MILLIS
        }

        return game.copy(
            currentAmbushActor = owner,
            phaseDeadlineAtMillis = now + decisionTimeout,
            infoMessage = null,
            ambushActionMenuOpen = false
        )
    }

    private fun getAmbushWindowForPhase(
        phase: TurnPhase
    ): AmbushWindow? {
        return when (phase) {
            TurnPhase.AMBUSH_BEFORE_REVEAL ->
                AmbushWindow.BEFORE_REVEAL

            TurnPhase.AMBUSH_BEFORE_EFFECTS ->
                AmbushWindow.BEFORE_EFFECTS

            TurnPhase.AMBUSH_BEFORE_COMBAT ->
                AmbushWindow.BEFORE_COMBAT

            TurnPhase.AMBUSH_BEFORE_POST_COMBAT ->
                AmbushWindow.BEFORE_POST_COMBAT

            TurnPhase.AMBUSH_BEFORE_SHOP ->
                AmbushWindow.BEFORE_SHOP

            else -> null
        }
    }

    private fun resolveDiscardCardEffect(
        game: GameState,
        owner: ChoiceOwner,
        cardId: CardId
    ): GameState? {

        return when (cardId) {
            CardId.TIQUE -> resolveTiqueDiscardEffect(game, owner)
            else -> null
        }
    }

    private fun resolveTiqueDiscardEffect(
        game: GameState,
        owner: ChoiceOwner
    ): GameState? {

        val bloodAmount = TokenManager.getTokenAmount(
            game = game,
            target = owner,
            tokenId = TokenManager.TokenIds.BLOOD
        )

        val ownerSide = if (owner == ChoiceOwner.PLAYER) {
            CardDestroyManager.Side.PLAYER
        } else {
            CardDestroyManager.Side.OPPONENT
        }

        val discard = if (owner == ChoiceOwner.PLAYER) {
            game.playerDiscard
        } else {
            game.opponentDiscard
        }

        val tiqueIndex = CardDestroyManager.eligibleIndices(
            game = game,
            owner = ownerSide,
            zone = CardDestroyManager.Zone.DISCARD,
            cause = CardDestroyManager.Cause.SACRIFICE
        ).firstOrNull { index ->
            discard[index].id == CardId.TIQUE
        } ?: return null

        val destruction = CardDestroyManager.destroyAt(
            game = game,
            owner = ownerSide,
            zone = CardDestroyManager.Zone.DISCARD,
            index = tiqueIndex,
            cause = CardDestroyManager.Cause.SACRIFICE
        )

        if (!destruction.destroyed) {
            return null
        }

        val updatedGame = if (owner == ChoiceOwner.PLAYER) {
            destruction.game.copy(
                playerCurrentCardPowerBonus =
                    destruction.game.playerCurrentCardPowerBonus + bloodAmount,
                playerTiqueHealPending = true,
                infoMessage = null
            )
        } else {
            destruction.game.copy(
                opponentCurrentCardPowerBonus =
                    destruction.game.opponentCurrentCardPowerBonus + bloodAmount,
                opponentTiqueHealPending = true,
                infoMessage = null
            )
        }

        return TokenManager.removeAllToken(
            game = updatedGame,
            target = owner,
            tokenId = TokenManager.TokenIds.BLOOD
        )
    }

    fun activateDiscardCard(
        gameId: String,
        owner: ChoiceOwner,
        cardId: CardId
    ): GameState? = withGameLock(gameId) {

        val game = games[gameId] ?: return@withGameLock null

        // Le menu Action doit avoir été ouvert par le joueur.
        if (!game.ambushActionMenuOpen) {
            return@withGameLock game
        }

        // L'activation doit arriver avant la fin du délai.
        // En Solo / Training, une deadline null est autorisée.
        val actionDeadline = game.phaseDeadlineAtMillis

        if (
            actionDeadline != null &&
            System.currentTimeMillis() >= actionDeadline
        ) {
            return@withGameLock game
        }

        if (game.pendingChoice != null) {
            return@withGameLock game
        }

        if (
            cardId !in TacticalActionManager.getAvailableDiscardCardIds(
                game = game,
                owner = owner
            )
        ) {
            return@withGameLock game
        }

        if (!TacticalActionManager.hasAvailableDiscardCardAction(game, owner)) {
            return@withGameLock game
        }

        val updatedGame = resolveDiscardCardEffect(
            game = game,
            owner = owner,
            cardId = cardId
        ) ?: return@withGameLock game

        val resumedGame = resumeAmbushAfterPendingChoice(
            game = updatedGame,
            owner = owner
        )

        games[gameId] = resumedGame
        resumedGame
    }

    private fun startAmbushWindow(game: GameState): GameState {
        val playerFirst = determineAmbushPriorityPlayerFirst(game)

        val firstOwner = if (playerFirst) {
            ChoiceOwner.PLAYER
        } else {
            ChoiceOwner.OPPONENT
        }

        val secondOwner = oppositeOwner(firstOwner)

        val initializedGame = game.copy(
            ambushPriorityPlayerFirst = playerFirst,
            currentAmbushActor = null,

            playerPassedCurrentAmbushWindow = false,
            opponentPassedCurrentAmbushWindow = false,

            playerReactivatedCurrentAmbushWindow = false,
            opponentReactivatedCurrentAmbushWindow = false,

            infoMessage = null
        )

        val startedGame = when {
            hasAvailableAmbushAction(initializedGame, firstOwner) -> {
                giveAmbushPriorityTo(
                    game = initializedGame,
                    owner = firstOwner
                )
            }

            hasAvailableAmbushAction(initializedGame, secondOwner) -> {
                giveAmbushPriorityTo(
                    game = initializedGame,
                    owner = secondOwner
                )
            }

            else -> {
                // Aucune action réelle disponible.
                // On reste dans la fenêtre Ambush jusqu'à la deadline
                // minimale de 500 ms définie par enterPhase().
                initializedGame
            }
        }

        return resolveAiAmbushActionsIfNeeded(startedGame)
    }


    private fun resumeAmbushAfterPendingChoice(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {

        // Ne jamais transférer la priorité tant qu'un choix
        // interactif est encore en attente.
        if (game.pendingChoice != null) {
            return game
        }

        // L'action est terminée : fermeture du menu Action.
        val updatedGame = game.copy(
            ambushActionMenuOpen = false
        )

        return resolveAiAmbushActionsIfNeeded(
            moveToNextAmbushActorOrFinish(
                game = updatedGame,
                currentActor = owner
            )
        )
    }


    private fun moveToNextAmbushActorOrFinish(
        game: GameState,
        currentActor: ChoiceOwner
    ): GameState {

        val currentActorPassed = hasPassedCurrentAmbushWindow(
            game = game,
            owner = currentActor
        )

        val otherActor = oppositeOwner(currentActor)

        // Après une action réellement terminée,
        // l'adversaire ayant passé peut être réactivé,
        // uniquement s'il possède une action disponible.
        //
        // Un simple passage ne réactive personne.
        val updatedGame = if (
            !currentActorPassed &&
            hasAvailableAmbushAction(game, otherActor)
        ) {
            TacticalActionManager.reopenForNewOpportunity(
                game = game,
                owner = otherActor
            )
        } else {
            game
        }

        val nextOwner = TacticalActionManager.nextActor(
            game = updatedGame,
            currentActor = currentActor,
            currentActorPassed = currentActorPassed,
            playerHasAction = hasAvailableAmbushAction(
                updatedGame,
                ChoiceOwner.PLAYER
            ),
            opponentHasAction = hasAvailableAmbushAction(
                updatedGame,
                ChoiceOwner.OPPONENT
            )
        )

        return if (nextOwner != null) {
            giveAmbushPriorityTo(
                game = updatedGame,
                owner = nextOwner
            )
        } else {
            advancePastAmbushWindow(updatedGame)
        }
    }

    private fun passAmbushWindowInternal(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {
        if (!isAmbushPhase(game.phase)) {
            return game
        }

        if (game.currentAmbushActor != owner) {
            return game
        }

        val afterPass = TacticalActionManager.markPassed(
            game = game,
            owner = owner
        ).copy(
            // Fermeture du menu Action lors du passage.
            ambushActionMenuOpen = false,

            // Nettoyage des choix abandonnés, notamment
            // lors d'une expiration du délai serveur.
            pendingChoice = null,

            currentAmbushActor = null,
            infoMessage = null
        )


        return moveToNextAmbushActorOrFinish(
            game = afterPass,
            currentActor = owner
        )
    }
    private fun resolveAiAmbushActionsIfNeeded(
        game: GameState
    ): GameState {
        var workingGame = game

        while (
            isAiControlledOpponent(workingGame) &&
            workingGame.currentAmbushActor == ChoiceOwner.OPPONENT
        ) {
            workingGame = passAmbushWindowInternal(
                game = workingGame,
                owner = ChoiceOwner.OPPONENT
            )
        }

        return workingGame
    }

    fun passAmbushWindow(
        gameId: String,
        owner: ChoiceOwner
    ): GameState? = withGameLock(gameId) {
        val game = games[gameId] ?: return@withGameLock null

        // Un Pass volontaire ne peut pas interrompre
        // une action tactique déjà déclarée.
        if (
            isAmbushPhase(game.phase) &&
            game.currentAmbushActor == owner &&
            game.pendingChoice != null
        ) {
            return@withGameLock game
        }

        val updatedGame = resolveAiAmbushActionsIfNeeded(
            passAmbushWindowInternal(
                game = game,
                owner = owner
            )
        )

        games[gameId] = updatedGame
        updatedGame
    }

    private fun clearAmbushWindowState(game: GameState): GameState {
        return game.copy(
            ambushPriorityPlayerFirst = null,
            currentAmbushActor = null,

            playerPassedCurrentAmbushWindow = false,
            opponentPassedCurrentAmbushWindow = false,

            ambushActionMenuOpen = false,

            playerReactivatedCurrentAmbushWindow = false,
            opponentReactivatedCurrentAmbushWindow = false
        )
    }

    private fun getAvailableRuneIdsForCurrentWindow(
        game: GameState,
        owner: ChoiceOwner
    ): List<String> {
        val currentWindow = getAmbushWindowForPhase(game.phase)
            ?: return emptyList()

        val availableRuneIds = when (owner) {
            ChoiceOwner.PLAYER -> game.playerAvailableRuneIds
            ChoiceOwner.OPPONENT -> game.opponentAvailableRuneIds
        }

        return availableRuneIds.filter { runeId ->
            val rune = SoloRuneCatalog.findById(runeId)
                ?: return@filter false

            currentWindow in rune.activationWindows
        }
    }

    private fun oppositeOwner(owner: ChoiceOwner): ChoiceOwner {
        return when (owner) {
            ChoiceOwner.PLAYER -> ChoiceOwner.OPPONENT
            ChoiceOwner.OPPONENT -> ChoiceOwner.PLAYER
        }
    }

    private fun hasPassedCurrentAmbushWindow(
        game: GameState,
        owner: ChoiceOwner
    ): Boolean {
        return when (owner) {
            ChoiceOwner.PLAYER -> game.playerPassedCurrentAmbushWindow
            ChoiceOwner.OPPONENT -> game.opponentPassedCurrentAmbushWindow
        }
    }

    private fun getAvailableTacticalCardIndices(
        game: GameState,
        owner: ChoiceOwner
    ): List<Int> {
        if (!isAmbushPhase(game.phase)) {
            return emptyList()
        }

        val tacticalCards = when (owner) {
            ChoiceOwner.PLAYER -> game.playerAmbush
            ChoiceOwner.OPPONENT -> game.opponentAmbush
        }

        return tacticalCards.indices.filter { index ->
            val tacticalCard = tacticalCards[index]

            when (tacticalCard.card.id) {

                CardId.MUSICIEN -> {
                    tacticalCard.entryType == TacticalEntryType.AMBUSH
                }

                CardId.REINEARAIGNEE -> {
                    tacticalCard.entryType == TacticalEntryType.PREPARATION
                }

                CardId.PALADIN -> {
                    tacticalCard.entryType == TacticalEntryType.PREPARATION &&
                            (
                            game.phase == TurnPhase.AMBUSH_BEFORE_COMBAT ||
                            game.phase == TurnPhase.AMBUSH_BEFORE_POST_COMBAT
                            )
                }

                CardId.FORCETRANQUILLE -> {
                    tacticalCard.entryType == TacticalEntryType.AMBUSH &&
                            (
                            game.phase == TurnPhase.AMBUSH_BEFORE_EFFECTS ||
                            game.phase == TurnPhase.AMBUSH_BEFORE_COMBAT
                            )
                }

                CardId.PERFIDE -> {
                    tacticalCard.entryType == TacticalEntryType.AMBUSH &&
                            (
                                    game.phase == TurnPhase.AMBUSH_BEFORE_EFFECTS ||
                                            game.phase == TurnPhase.AMBUSH_BEFORE_COMBAT
                                    )
                }

                CardId.TRAPPEUR -> {
                    tacticalCard.entryType == TacticalEntryType.PREPARATION
                }

                else -> false
            }
        }
    }

    private fun getPromptableTacticalCardIndices(
        game: GameState,
        owner: ChoiceOwner
    ): List<Int> {
        val tacticalCards = when (owner) {
            ChoiceOwner.PLAYER -> game.playerAmbush
            ChoiceOwner.OPPONENT -> game.opponentAmbush
        }

        return getAvailableTacticalCardIndices(
            game = game,
            owner = owner
        ).filter { index ->
            !tacticalCards[index].autoSkip
        }
    }


    private fun hasAvailableAmbushAction(
        game: GameState,
        owner: ChoiceOwner
    ): Boolean {

        val hasRuneAction =
            getAvailableRuneIdsForCurrentWindow(
                game = game,
                owner = owner
            ).isNotEmpty()

        val hasTacticalCardAction =
            getPromptableTacticalCardIndices(
                game = game,
                owner = owner
            ).isNotEmpty()

        val hasDiscardCardAction =
            TacticalActionManager.hasAvailableDiscardCardAction(
                game = game,
                owner = owner
            )

        val hasTokenAction =
            TacticalActionManager.hasAvailableTokenAction(
                game = game,
                owner = owner
            )


        return TacticalActionManager.hasAvailableAction(
            hasRuneAction = hasRuneAction,
            hasTacticalCardAction = hasTacticalCardAction,
            hasTokenAction = hasTokenAction,
            hasDiscardCardAction = hasDiscardCardAction
        )

    }


    private fun canActivateRune(
        game: GameState,
        owner: ChoiceOwner,
        runeId: String
    ): Boolean {
        // Une décision en attente doit être terminée
        // avant toute nouvelle activation tactique.
        if (game.pendingChoice != null) {
            return false
        }
        if (game.mode != "SOLO" && game.mode != "TRAINING") {
            return false
        }

        if (!isAmbushPhase(game.phase)) {
            return false
        }

        if (game.currentAmbushActor != owner) {
            return false
        }

        val availableRuneIds = when (owner) {
            ChoiceOwner.PLAYER -> game.playerAvailableRuneIds
            ChoiceOwner.OPPONENT -> game.opponentAvailableRuneIds
        }

        if (runeId !in availableRuneIds) {
            return false
        }

        val rune = SoloRuneCatalog.findById(runeId)
            ?: return false

        val currentWindow = getAmbushWindowForPhase(game.phase)
            ?: return false

        if (currentWindow !in rune.activationWindows) {
            return false
        }

        // Rune majeure IV : destruction d'une carte Embuscade / Préparation adverse.
        //
        // Règle des 5 cartes :
        // l'adversaire doit posséder au moins 6 cartes avant la destruction.
        //
        // Les fragments de rune ne sont PAS des cartes :
        // ils ne sont ni comptés dans cette règle, ni considérés comme des cibles.
        if (rune.effectType == SoloRuneEffectType.DESTROY_OPPONENT_AMBUSH_OR_PREPARATION) {
            val targetSide = if (owner == ChoiceOwner.PLAYER) {
                CardDestroyManager.Side.OPPONENT
            } else {
                CardDestroyManager.Side.PLAYER
            }

            if (CardDestroyManager.eligibleIndices(
                    game = game,
                    owner = targetSide,
                    zone = CardDestroyManager.Zone.AMBUSH,
                    cause = CardDestroyManager.Cause.OPPONENT_EFFECT
                ).isEmpty()
            ) {
                return false
            }
        }

        return true
    }

    private fun canActivateTacticalCard(
        game: GameState,
        owner: ChoiceOwner,
        tacticalCardIndex: Int
    ): Boolean {

        // Une décision en attente doit être terminée
        // avant toute nouvelle activation tactique.
        if (game.pendingChoice != null) {
            return false
        }
        if (!isAmbushPhase(game.phase)) {
            return false
        }

        if (game.currentAmbushActor != owner) {
            return false
        }

        val tacticalCards = when (owner) {
            ChoiceOwner.PLAYER -> game.playerAmbush
            ChoiceOwner.OPPONENT -> game.opponentAmbush
        }

        if (tacticalCardIndex !in tacticalCards.indices) {
            return false
        }

        return tacticalCardIndex in getAvailableTacticalCardIndices(
            game = game,
            owner = owner
        )
    }

    private fun consumeRune(
        game: GameState,
        owner: ChoiceOwner,
        runeId: String
    ): GameState {
        return when (owner) {
            ChoiceOwner.PLAYER -> game.copy(
                playerAvailableRuneIds =
                    game.playerAvailableRuneIds - runeId
            )

            ChoiceOwner.OPPONENT -> game.copy(
                opponentAvailableRuneIds =
                    game.opponentAvailableRuneIds - runeId
            )
        }
    }

    fun activateToken(
        gameId: String,
        owner: ChoiceOwner,
        tokenId: String,
        option: String? = null
    ): GameState? = withGameLock(gameId) {

        val game = games[gameId] ?: return@withGameLock null


        // Une action tactique doit être déclarée avant son activation.
        // Le joueur doit avoir ouvert le menu Action pendant
        // sa fenêtre de décision de 2/1 seconde.
        if (!game.ambushActionMenuOpen) {
            return@withGameLock game
        }


        // Une activation reçue après la fin du délai
        // de résolution doit être refusée.
        val actionDeadline = game.phaseDeadlineAtMillis

        if (
            actionDeadline != null &&
            System.currentTimeMillis() >= actionDeadline
        ) {
            return@withGameLock game
        }



        // Compatibilité temporaire avec l'ancien système
        // de priorité des fenêtres tactiques.
        if (game.currentAmbushActor != owner) {
            return@withGameLock game
        }

        // Vérifie que ce jeton est activable dans l'état actuel.
        val canActivate = when (tokenId) {
            TokenManager.TokenIds.EMBLEM ->
                TacticalActionManager.canActivateEmblem(
                    game = game,
                    owner = owner
                )

            else -> false
        }

        if (!canActivate) {
            return@withGameLock game
        }

        val updatedGame = TokenManager.activateToken(
            game = game,
            owner = owner,
            tokenId = tokenId,
            option = option
        )

        // Une activation valide consomme une occasion d'agir.
        // Si l'état est inchangé (option invalide, par exemple),
        // on ne transfère pas la priorité.
        if (updatedGame == game) {
            return@withGameLock game
        }

        val finalGame = if (updatedGame.pendingChoice != null) {
            startAmbushActionResolutionTimer(
                game = updatedGame,
                owner = owner
            )
        } else {
            resumeAmbushAfterPendingChoice(
                game = updatedGame,
                owner = owner
            )
        }

        games[gameId] = finalGame
        finalGame
    }

    fun activateRune(
        gameId: String,
        owner: ChoiceOwner,
        runeId: String
    ): GameState? = withGameLock(gameId) {
        val game = games[gameId] ?: return@withGameLock null


        // Le joueur doit avoir ouvert son menu Action
        // avant de pouvoir activer une rune tactique.
        if (!game.ambushActionMenuOpen) {
            return@withGameLock game
        }


        // Une rune ne peut pas être activée
        // après expiration du délai de résolution.
        val actionDeadline = game.phaseDeadlineAtMillis

        if (
            actionDeadline != null &&
            System.currentTimeMillis() >= actionDeadline
        ) {
            return@withGameLock game
        }



        if (!canActivateRune(
                game = game,
                owner = owner,
                runeId = runeId
            )
        ) {
            return@withGameLock game
        }

        val rune = SoloRuneCatalog.findById(runeId)
            ?: return@withGameLock game

        var updatedGame = applyRuneEffect(
            game = game,
            owner = owner,
            rune = rune
        )

        updatedGame = consumeRune(
            game = updatedGame,
            owner = owner,
            runeId = runeId
        )

        updatedGame = if (updatedGame.pendingChoice != null) {
            startAmbushActionResolutionTimer(
                game = updatedGame,
                owner = owner
            )
        } else {
            resumeAmbushAfterPendingChoice(
                game = updatedGame,
                owner = owner
            )
        }

        games[gameId] = updatedGame
        updatedGame
    }

    fun setTacticalCardAutoSkip(
        gameId: String,
        owner: ChoiceOwner,
        tacticalCardIndex: Int,
        autoSkip: Boolean
    ): GameState? = withGameLock(gameId) {
        val game = games[gameId] ?: return@withGameLock null

        val tacticalCards = when (owner) {
            ChoiceOwner.PLAYER -> game.playerAmbush
            ChoiceOwner.OPPONENT -> game.opponentAmbush
        }

        if (tacticalCardIndex !in tacticalCards.indices) {
            return@withGameLock game
        }

        val updatedCards = tacticalCards.mapIndexed { index, tacticalCard ->
            if (index == tacticalCardIndex) {
                tacticalCard.copy(autoSkip = autoSkip)
            } else {
                tacticalCard
            }
        }

        val updatedGame = when (owner) {
            ChoiceOwner.PLAYER -> {
                game.copy(playerAmbush = updatedCards)
            }

            ChoiceOwner.OPPONENT -> {
                game.copy(opponentAmbush = updatedCards)
            }
        }

        games[gameId] = updatedGame
        updatedGame
    }

    fun activateTacticalCard(
        gameId: String,
        owner: ChoiceOwner,
        tacticalCardIndex: Int
    ): GameState? = withGameLock(gameId) {
        val game = games[gameId] ?: return@withGameLock null


        // Une carte tactique ne peut être activée
        // qu'après ouverture du menu Action.
        if (!game.ambushActionMenuOpen) {
            return@withGameLock game
        }


        // Refuser une activation arrivée après la fin
        // du délai de résolution de l'action.
        // En Solo / Training, une deadline null est autorisée.
        val actionDeadline = game.phaseDeadlineAtMillis

        if (
            actionDeadline != null &&
            System.currentTimeMillis() >= actionDeadline
        ) {
            return@withGameLock game
        }



        if (!canActivateTacticalCard(
                game = game,
                owner = owner,
                tacticalCardIndex = tacticalCardIndex
            )
        ) {
            return@withGameLock game
        }

        val tacticalCards = when (owner) {
            ChoiceOwner.PLAYER -> game.playerAmbush
            ChoiceOwner.OPPONENT -> game.opponentAmbush
        }

        val tacticalCard = tacticalCards[tacticalCardIndex]

        var updatedGame = when (tacticalCard.card.id) {

            CardId.MUSICIEN -> {
                when (owner) {
                    ChoiceOwner.PLAYER -> {
                        game.copy(
                            playerGold = game.playerGold + 1,
                            playerAmbush = game.playerAmbush.filterIndexed { index, _ ->
                                index != tacticalCardIndex
                            },
                            playerDiscard = game.playerDiscard + tacticalCard.card,
                            infoMessage = null
                        )
                    }

                    ChoiceOwner.OPPONENT -> {
                        game.copy(
                            opponentGold = game.opponentGold + 1,
                            opponentAmbush = game.opponentAmbush.filterIndexed { index, _ ->
                                index != tacticalCardIndex
                            },
                            opponentDiscard = game.opponentDiscard + tacticalCard.card,
                            infoMessage = null
                        )
                    }
                }
            }

            CardId.REINEARAIGNEE -> {
                val spiderEgg = CardCatalog.getCard(CardId.OEUFDARAIGNEE)

                when (owner) {
                    ChoiceOwner.PLAYER -> {
                        game.copy(
                            playerAmbush =
                                game.playerAmbush.filterIndexed { index, _ ->
                                    index != tacticalCardIndex
                                },

                            playerDiscard =
                                game.playerDiscard + tacticalCard.card + spiderEgg,

                            infoMessage = null
                        )
                    }

                    ChoiceOwner.OPPONENT -> {
                        game.copy(
                            opponentAmbush =
                                game.opponentAmbush.filterIndexed { index, _ ->
                                    index != tacticalCardIndex
                                },

                            opponentDiscard =
                                game.opponentDiscard + tacticalCard.card + spiderEgg,

                            infoMessage = null
                        )
                    }
                }
            }

            CardId.PALADIN -> {
                when (owner) {
                    ChoiceOwner.PLAYER -> {
                        game.copy(
                            playerAmbush =
                                game.playerAmbush.filterIndexed { index, _ ->
                                    index != tacticalCardIndex
                                },

                            playerDiscard =
                                game.playerDiscard + tacticalCard.card,

                            playerPaladinProtectionActive = true,

                            infoMessage = null
                        )
                    }

                    ChoiceOwner.OPPONENT -> {
                        game.copy(
                            opponentAmbush =
                                game.opponentAmbush.filterIndexed { index, _ ->
                                    index != tacticalCardIndex
                                },

                            opponentDiscard =
                                game.opponentDiscard + tacticalCard.card,

                            opponentPaladinProtectionActive = true,

                            infoMessage = null
                        )
                    }
                }
            }

            CardId.PERFIDE -> {
                when (owner) {
                    ChoiceOwner.PLAYER -> {
                        game.copy(
                            opponentCurrentCardPowerBonus =
                                game.opponentCurrentCardPowerBonus - 2,

                            playerAmbush =
                                game.playerAmbush.filterIndexed { index, _ ->
                                    index != tacticalCardIndex
                                },

                            playerDiscard =
                                game.playerDiscard + tacticalCard.card,

                            infoMessage = null
                        )
                    }

                    ChoiceOwner.OPPONENT -> {
                        game.copy(
                            playerCurrentCardPowerBonus =
                                game.playerCurrentCardPowerBonus - 2,

                            opponentAmbush =
                                game.opponentAmbush.filterIndexed { index, _ ->
                                    index != tacticalCardIndex
                                },

                            opponentDiscard =
                                game.opponentDiscard + tacticalCard.card,

                            infoMessage = null
                        )
                    }
                }
            }

            CardId.FORCETRANQUILLE -> {
                when (owner) {
                    ChoiceOwner.PLAYER -> {
                        game.copy(
                            playerCurrentCardPowerBonus =
                                game.playerCurrentCardPowerBonus + 2,

                            playerAmbush =
                                game.playerAmbush.filterIndexed { index, _ ->
                                    index != tacticalCardIndex
                                },

                            playerDiscard =
                                game.playerDiscard + tacticalCard.card,

                            infoMessage = null
                        )
                    }

                    ChoiceOwner.OPPONENT -> {
                        game.copy(
                            opponentCurrentCardPowerBonus =
                                game.opponentCurrentCardPowerBonus + 2,

                            opponentAmbush =
                                game.opponentAmbush.filterIndexed { index, _ ->
                                    index != tacticalCardIndex
                                },

                            opponentDiscard =
                                game.opponentDiscard + tacticalCard.card,

                            infoMessage = null
                        )
                    }
                }
            }

            CardId.TRAPPEUR -> {
                game.copy(
                    pendingChoice = PendingChoice(
                        type = "TRAPPEUR_ACTION_CHOICE",
                        cardId = CardId.TRAPPEUR.name,
                        options = listOf(
                            "COUNTER",
                            "DEBUSQUER",
                            "PASS"
                        ),
                        message = "Trappeur : choisissez une action",
                        owner = owner
                    ),
                    infoMessage = null
                )
            }

            else -> game
        }

        if (updatedGame.pendingChoice != null) {
            // L'action a été déclarée et attend un choix.
            // On démarre le délai de résolution de 10 ou 5 secondes.
            updatedGame = startAmbushActionResolutionTimer(
                game = updatedGame,
                owner = owner
            )
        } else {
            // Action instantanée : on poursuit la fenêtre tactique.
            updatedGame = resumeAmbushAfterPendingChoice(
                game = updatedGame,
                owner = owner
            )
        }

        games[gameId] = updatedGame
        updatedGame
    }

    private fun applyRuneEffect(
        game: GameState,
        owner: ChoiceOwner,
        rune: SoloRuneDefinition
    ): GameState {
        return when (rune.effectType) {

            SoloRuneEffectType.CURRENT_CARD_POWER_BONUS -> {
                when (owner) {
                    ChoiceOwner.PLAYER -> game.copy(
                        playerCurrentCardPowerBonus =
                            game.playerCurrentCardPowerBonus + rune.value
                    )

                    ChoiceOwner.OPPONENT -> game.copy(
                        opponentCurrentCardPowerBonus =
                            game.opponentCurrentCardPowerBonus + rune.value
                    )
                }
            }

            SoloRuneEffectType.GAIN_GOLD_AND_SCRY -> {
                val gameWithGold = when (owner) {
                    ChoiceOwner.PLAYER -> game.copy(
                        playerGold = game.playerGold + 1
                    )

                    ChoiceOwner.OPPONENT -> game.copy(
                        opponentGold = game.opponentGold + 1
                    )
                }

                CardEffectManager.startScry(
                    game = gameWithGold,
                    sourceId = rune.id,
                    resolver = owner,
                    target = owner,
                    amount = rune.value,
                    completionContext = ScryCompletionContext.AMBUSH,
                    canDiscardViewedCards = true
                )
            }

            SoloRuneEffectType.GAIN_GOLD -> {
                when (owner) {
                    ChoiceOwner.PLAYER -> game.copy(
                        playerGold = game.playerGold + rune.value
                    )

                    ChoiceOwner.OPPONENT -> game.copy(
                        opponentGold = game.opponentGold + rune.value
                    )
                }
            }

            SoloRuneEffectType.BLOCK_OPPONENT_CARD_EFFECT -> {
                when (owner) {
                    ChoiceOwner.PLAYER -> game.copy(
                        opponentCardEffectsBlockedByAmbush = true
                    )

                    ChoiceOwner.OPPONENT -> game.copy(
                        playerCardEffectsBlockedByAmbush = true
                    )
                }
            }

            SoloRuneEffectType.HEAL -> {
                TokenManager.heal(
                    game = game,
                    target = owner,
                    amount = rune.value
                )
            }

            SoloRuneEffectType.GIVE_BURN_AND_DISEASE -> {
                val target = oppositeOwner(owner)

                var updatedGame = CardEffectManager.addBurnToken(
                    game = game,
                    target = target
                )

                updatedGame = CardEffectManager.addDiseaseToken(
                    game = updatedGame,
                    target = target
                )

                updatedGame
            }

            SoloRuneEffectType.OPPONENT_LOSE_GOLD_AND_DARK_SCRY -> {
                val target = oppositeOwner(owner)

                // L'adversaire perd 1 Or.
                // On ne descend jamais sous 0.
                val gameAfterGoldLoss = when (target) {
                    ChoiceOwner.PLAYER -> game.copy(
                        playerGold = maxOf(0, game.playerGold - 1)
                    )

                    ChoiceOwner.OPPONENT -> game.copy(
                        opponentGold = maxOf(0, game.opponentGold - 1)
                    )
                }

                // Scrutage sombre :
                // le propriétaire de la rune prend les décisions,
                // mais regarde/manipule les cartes du deck adverse.
                CardEffectManager.startScry(
                    game = gameAfterGoldLoss,
                    sourceId = rune.id,
                    resolver = owner,
                    target = target,
                    amount = rune.value,
                    completionContext = ScryCompletionContext.AMBUSH,
                    canDiscardViewedCards = true
                )
            }

            SoloRuneEffectType.DESTROY_OPPONENT_AMBUSH_OR_PREPARATION -> {
                val targetSide = if (owner == ChoiceOwner.PLAYER) {
                    CardDestroyManager.Side.OPPONENT
                } else {
                    CardDestroyManager.Side.PLAYER
                }

                val eligibleIndices = CardDestroyManager.eligibleIndices(
                    game = game,
                    owner = targetSide,
                    zone = CardDestroyManager.Zone.AMBUSH,
                    cause = CardDestroyManager.Cause.OPPONENT_EFFECT
                )

                if (eligibleIndices.isEmpty()) {
                    game
                } else {
                    game.copy(
                        pendingChoice = PendingChoice(
                            type = "RUNE_DESTROY_OPPONENT_AMBUSH",
                            cardId = rune.id,
                            options = eligibleIndices.map { index -> "AMBUSH:$index" },
                            message = "Choisissez une carte Embuscade ou Préparation adverse à détruire.",
                            owner = owner
                        ),
                        infoMessage = null
                    )
                }
            }


            else -> game
        }
    }


    private fun isPvpMode(game: GameState): Boolean {
        return PhaseManager.isPvpMode(game)
    }

    private fun isSeasonMercenary(card: Card): Boolean {
        return card.faction == CardFaction.MERCENARY
    }


    private fun isAmbushPhase(phase: TurnPhase): Boolean {
        return PhaseManager.isTacticalWindow(phase)
    }


    private fun advancePastAmbushWindow(game: GameState): GameState {
        val clearedGame = clearAmbushWindowState(game)

        return when (game.phase) {
            TurnPhase.AMBUSH_BEFORE_REVEAL -> {
                enterPhase(
                    game = clearedGame,
                    phase = TurnPhase.REVEAL
                )
            }

            TurnPhase.AMBUSH_BEFORE_EFFECTS -> {
                enterPhase(
                    game = clearedGame,
                    phase = TurnPhase.EFFECTS
                )
            }

            TurnPhase.AMBUSH_BEFORE_COMBAT -> {
                enterPhase(
                    game = clearedGame,
                    phase = TurnPhase.COMBAT
                )
            }

            TurnPhase.AMBUSH_BEFORE_POST_COMBAT -> {
                enterPhase(
                    game = clearedGame,
                    phase = TurnPhase.POST_COMBAT
                )
            }

            TurnPhase.AMBUSH_BEFORE_SHOP -> {
                startShopResolution(
                    clearedGame.copy(
                        infoMessage = null
                    )
                )
            }

            else -> clearedGame
        }
    }

    fun heartbeat(
        gameId: String,
        userId: Int
    ): GameState? = withGameLock(gameId) {

        val game = games[gameId] ?: return@withGameLock null

        if (game.isFinished) {
            return@withGameLock game
        }

        val now = System.currentTimeMillis()

        val updatedGame = when (userId) {
            game.playerUserId -> game.copy(
                playerLastSeenAtMillis = now
            )

            game.opponentUserId -> game.copy(
                opponentLastSeenAtMillis = now
            )

            else -> game.copy(
                infoMessage = "Joueur non autorisé pour cette partie"
            )
        }

        games[gameId] = updatedGame
        updatedGame
    }

    private fun addLog(
        game: GameState,
        key: String,
        params: Map<String, String> = emptyMap()
    ): GameState {
        val newEntry = GameLogEntry(
            turnNumber = game.turnNumber,
            key = key,
            params = params
        )

        return game.copy(
            logEntries = game.logEntries + newEntry
        )
    }

    fun applyConnectionTimeoutIfNeeded(
        gameId: String,
        checkingUserId: Int
    ): GameState? = withGameLock(gameId) {
        val game = games[gameId] ?: return@withGameLock null

        if (game.isFinished) return@withGameLock game
        if (!shouldRecordCompetitiveResult(game)) return@withGameLock game

        val now = System.currentTimeMillis()

        val isCheckingPlayer = checkingUserId == game.playerUserId
        val isCheckingOpponent = checkingUserId == game.opponentUserId

        if (!isCheckingPlayer && !isCheckingOpponent) {
            return@withGameLock game.copy(
                infoMessage = "Joueur non autorisé pour cette partie"
            )
        }

        val opponentLastSeen = if (isCheckingPlayer) {
            game.opponentLastSeenAtMillis
        } else {
            game.playerLastSeenAtMillis
        }

        if (opponentLastSeen == null) {
            return@withGameLock game
        }

        val elapsed = now - opponentLastSeen

        if (elapsed < FORFEIT_TIMEOUT_MILLIS) {
            return@withGameLock game
        }

        val result = if (isCheckingPlayer) {
            "WIN" // l'adversaire a disparu, player gagne
        } else {
            "LOSS" // l'adversaire est le player, donc player perd
        }

        val finishedGame = game.copy(
            isFinished = true,
            result = result,
            phase = TurnPhase.POST_COMBAT,
            pendingChoice = null,
            activeScryState = null,
            infoMessage = "Victoire par forfait : adversaire déconnecté."
        )

        val recordedGame = recordDuelResultIfNeeded(finishedGame)

        games[gameId] = recordedGame
        recordedGame
    }

    private fun isAiControlledOpponent(game: GameState): Boolean {
        return when (game.mode) {
            "TRAINING" -> true
            "SOLO" -> true
            else -> false
        }
    }

    private fun shouldRecordCompetitiveResult(game: GameState): Boolean {
        return game.mode in listOf(
            "DUEL",
            "RANKED",
            "SEASON"
        )
    }

    private fun recordSoloResultIfNeeded(game: GameState): GameState {
        if (game.mode != "SOLO") return game
        if (!game.isFinished) return game
        if (game.resultRecorded) return game
        if (game.result != "WIN") return game

        val playerUserId = game.playerUserId ?: return game
        val missionId = game.soloMissionId ?: return game
        val difficultyName = game.soloDifficulty ?: return game

        val difficulty = try {
            SoloMissionDifficulty.valueOf(difficultyName)
        } catch (_: Exception) {
            return game
        }

        val mission = SoloMissionCatalog.findMission(missionId)
            ?: return game

        val config = when (difficulty) {
            SoloMissionDifficulty.CAMPAIGN -> mission.buildCampaignConfig(
                selectedRuneIds = emptyList(),
                selectedCardIds = emptyList()
            )

            SoloMissionDifficulty.HARD -> mission.buildHardConfig(
                selectedRuneIds = emptyList(),
                selectedCardIds = emptyList()
            )
        }

        return when (difficulty) {
            SoloMissionDifficulty.CAMPAIGN -> {
                val claimResult =
                    SoloProgressService.completeCampaignAndClaimReward(
                        userId = playerUserId,
                        missionId = missionId,
                        reward = config.reward
                    )

                if (!claimResult.success) {
                    game.copy(
                        infoMessage = "Impossible d’enregistrer la progression Solo."
                    )
                } else {
                    game.copy(
                        resultRecorded = true,

                        soloRewardGranted = claimResult.rewardGranted,

                        soloRewardGems = claimResult.gems,
                        soloRewardDust = claimResult.dust,

                        soloRewardRuneIds = claimResult.runeIds,
                        soloRewardCardIds = claimResult.cardIds,
                        soloRewardTitleIds = claimResult.titleIds,
                        soloRewardAvatarIds = claimResult.avatarIds,
                        soloRewardSkinIds = claimResult.skinIds
                    )
                }
            }

            SoloMissionDifficulty.HARD -> {
                // Le Hard Mode n’est pas encore accessible.
                // On enregistrera sa progression et ses récompenses
                // lorsque son fonctionnement sera défini.
                game.copy(
                    resultRecorded = true
                )
            }
        }

    }

    private fun recordDuelResultIfNeeded(game: GameState): GameState {
        if (!shouldRecordCompetitiveResult(game)) return game
        if (!game.isFinished) return game
        if (game.resultRecorded) return game

        val playerUserId = game.playerUserId
        val opponentUserId = game.opponentUserId

        if (playerUserId == null || opponentUserId == null) {
            return game.copy(
                infoMessage = "Résultat non enregistré : joueur manquant"
            )
        }

        when (game.result) {
            "WIN" -> {
                UserService.addWin(playerUserId)
                UserService.addLoss(opponentUserId)
            }

            "LOSS" -> {
                UserService.addLoss(playerUserId)
                UserService.addWin(opponentUserId)
            }

            else -> {
                return game
            }
        }

        return game.copy(
            resultRecorded = true
        )
    }

    private fun chooseRandomShopTargetCardId(
        game: GameState,
        excludeCardId: String? = null
    ): String? {
        return game.shopEntries
            .filter { entry ->
                entry.copiesRemaining > 0 &&
                        entry.card.id.name != excludeCardId
            }
            .randomOrNull()
            ?.card
            ?.id
            ?.name
    }

    private fun resolveAiPendingChoiceIfNeeded(game: GameState): GameState {
        var workingGame = game

        while (true) {
            val pendingChoice = workingGame.pendingChoice ?: return workingGame

            // On ne résout automatiquement que les choix appartenant à l'IA adverse
            if (pendingChoice.owner != ChoiceOwner.OPPONENT) {
                return workingGame
            }

            if (!isAiControlledOpponent(workingGame)) {
                return workingGame
            }

            val aiChoice = AiPendingChoiceManager.chooseOption(workingGame) ?: return workingGame.copy(
                infoMessage = "Aucun choix IA disponible"
            )

            workingGame = CardEffectManager.resolvePendingChoice(workingGame, aiChoice)
        }
    }
    private fun hasReachedPurchaseLimit(
        game: GameState,
        card: Card,
        isPlayer: Boolean
    ): Boolean {
        val limit = card.purchaseLimit ?: return false

        val purchasedMap = if (isPlayer) {
            game.playerPurchasedCardCounts
        } else {
            game.opponentPurchasedCardCounts
        }

        val alreadyBought = purchasedMap[card.id.name] ?: 0

        return alreadyBought >= limit
    }

    private fun incrementPurchaseCount(
        game: GameState,
        cardId: String,
        isPlayer: Boolean
    ): GameState {
        val currentMap = if (isPlayer) {
            game.playerPurchasedCardCounts
        } else {
            game.opponentPurchasedCardCounts
        }

        val currentCount = currentMap[cardId] ?: 0
        val updatedMap = currentMap + (cardId to (currentCount + 1))

        return if (isPlayer) {
            game.copy(playerPurchasedCardCounts = updatedMap)
        } else {
            game.copy(opponentPurchasedCardCounts = updatedMap)
        }
    }

    private fun getShopEntryByCardId(
        game: GameState,
        cardId: String
    ): ShopEntry? {
        return game.shopEntries.find { it.card.id.name == cardId }
    }

    private fun determineShopPriorityPlayerFirst(game: GameState): Boolean {
        return when {
            game.playerHp < game.opponentHp -> true
            game.opponentHp < game.playerHp -> false

            game.playerGold < game.opponentGold -> true
            game.opponentGold < game.playerGold -> false

            else -> (0..1).random() == 0
        }
    }

    private fun determineAmbushPriorityPlayerFirst(game: GameState): Boolean {
        return when {
            game.playerHp < game.opponentHp -> true
            game.opponentHp < game.playerHp -> false

            game.playerGold < game.opponentGold -> true
            game.opponentGold < game.playerGold -> false

            else -> (0..1).random() == 0
        }
    }

    private fun evaluatePendingShopPurchaseStatus(
        game: GameState,
        cardId: String?,
        isPlayer: Boolean
    ): ShopPurchaseStatus {
        if (cardId == null) {
            return ShopPurchaseStatus.NONE
        }

        val entry = getShopEntryByCardId(game, cardId)
            ?: return ShopPurchaseStatus.INVALID_OUT_OF_STOCK

        val ownerGold = if (isPlayer) game.playerGold else game.opponentGold

        return when {
            entry.copiesRemaining <= 0 -> ShopPurchaseStatus.INVALID_OUT_OF_STOCK
            hasReachedPurchaseLimit(game, entry.card, isPlayer) -> ShopPurchaseStatus.INVALID_LIMIT_REACHED
            ownerGold < entry.card.cost -> ShopPurchaseStatus.WAITING_FOR_GOLD
            else -> ShopPurchaseStatus.ACCEPTED_WAITING_OPPONENT
        }
    }

    private fun clearShopStateForNewRound(game: GameState): GameState {
        return game.copy(
            playerShopStatus = ShopPurchaseStatus.NONE,
            opponentShopStatus = ShopPurchaseStatus.NONE,
            shopPriorityPlayerFirst = null,
            currentShopBuyerIsPlayer = null,
            playerPassedShop = false,
            opponentPassedShop = false
        )
    }

    private fun resolveUniqueOnReveal(
        game: GameState,
        revealedCards: List<Card>
    ): GameState {
        val uniqueRevealedCardIds = revealedCards
            .filter { it.isUnique }
            .map { it.id }
            .toSet()

        if (uniqueRevealedCardIds.isEmpty()) {
            return game
        }

        val playerCardsToDiscard = game.playerAmbush
            .filter { tacticalCard ->
                tacticalCard.card.id in uniqueRevealedCardIds
            }
            .map { tacticalCard ->
                tacticalCard.card
            }

        val opponentCardsToDiscard = game.opponentAmbush
            .filter { tacticalCard ->
                tacticalCard.card.id in uniqueRevealedCardIds
            }
            .map { tacticalCard ->
                tacticalCard.card
            }

        return game.copy(
            playerAmbush = game.playerAmbush.filterNot { tacticalCard ->
                tacticalCard.card.id in uniqueRevealedCardIds
            },

            opponentAmbush = game.opponentAmbush.filterNot { tacticalCard ->
                tacticalCard.card.id in uniqueRevealedCardIds
            },

            playerDiscard = game.playerDiscard + playerCardsToDiscard,
            opponentDiscard = game.opponentDiscard + opponentCardsToDiscard
        )
    }

    private fun debugPower(game: GameState, label: String) {
        val playerCard = game.lastPlayerCard
        val opponentCard = game.lastOpponentCard

        if (playerCard == null || opponentCard == null) {
            println("[$label] Pas de cartes révélées")
            return
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

        val playerHasBrute = CardEffectManager.hasBrute(
            card = playerCard,
            nextCardHasBrute = game.playerCurrentCardHasBruteBonus
        )

        val opponentHasBrute = CardEffectManager.hasBrute(
            card = opponentCard,
            nextCardHasBrute = game.opponentCurrentCardHasBruteBonus
        )

        val playerDamage = CardEffectManager.getCombatDamageDealt(
            attacker = playerCard,
            defender = opponentCard,
            attackerPower = playerPower,
            defenderPower = opponentPower,
            nextCardDamageBonus = game.playerCurrentCardDamageBonus,
            attackerHasBrute = playerHasBrute,
            enrageCombatDamage = game.playerEnrageCombatDamage
        )

        val opponentDamage = CardEffectManager.getCombatDamageDealt(
            attacker = opponentCard,
            defender = playerCard,
            attackerPower = opponentPower,
            defenderPower = playerPower,
            nextCardDamageBonus = game.opponentCurrentCardDamageBonus,
            attackerHasBrute = opponentHasBrute,
            enrageCombatDamage = game.opponentEnrageCombatDamage
        )

        println(
            "[$label] " +
                    "Player: ${playerCard.id} (PP=$playerPower) | " +
                    "Opponent: ${opponentCard.id} (OP=$opponentPower) | " +
                    "DMG: Player->$playerDamage / Opponent->$opponentDamage"
        )
    }

    // =========================================================
    // 6. SHOP — DEMANDES / PASS / ACHAT IMMÉDIAT TEMPORAIRE
    // =========================================================

    private fun assignSoloAiShopIntent(game: GameState): GameState {
        return SoloAiRouter.assignShopIntent(
            game = game,
            standardShopIntent = { currentGame ->
                assignTrainingAiShopIntent(currentGame)
            }
        )
    }

    private fun assignAiShopIntentIfNeeded(game: GameState): GameState {
        return when (game.mode) {
            "TRAINING" -> assignTrainingAiShopIntent(game)
            "SOLO" -> assignSoloAiShopIntent(game)
            else -> game
        }
    }

    private fun assignTrainingAiShopIntent(game: GameState): GameState {
        return when (val decision = TrainingAiShopManager.chooseTrainingAiShopDecision(game)) {

            TrainingAiShopDecision.PassTurn -> {
                println("IA SHOP passe son tour")
                game.copy(
                    opponentPassedShop = true,
                    opponentShopStatus = ShopPurchaseStatus.NONE
                )
            }

            TrainingAiShopDecision.NoTargetAvailable -> {
                println("IA SHOP n'a aucune cible valide")
                game.copy(
                    opponentPassedShop = true,
                    opponentPendingShopPurchaseCardId = null,
                    opponentShopStatus = ShopPurchaseStatus.NONE
                )
            }

            is TrainingAiShopDecision.BuyTarget -> {
                val targetCardId = decision.cardId
                println("IA SHOP cible choisie: $targetCardId")
                println("IA SHOP or adverse: ${game.opponentGold}")

                game.copy(
                    opponentPendingShopPurchaseCardId = targetCardId,
                    opponentShopStatus = evaluatePendingShopPurchaseStatus(
                        game = game,
                        cardId = targetCardId,
                        isPlayer = false
                    )
                )
            }
        }
    }

    private fun applyAutomaticPurchaseEffects(
        game: GameState,
        purchasedCard: Card,
        owner: ChoiceOwner
    ): GameState {
        return when (purchasedCard.id) {

            CardId.JONGLEURDEJANTE -> {
                val cannonball = CardCatalog.getCard(CardId.BOULETDECANON)

                when (owner) {
                    ChoiceOwner.PLAYER -> {
                        game.copy(
                            playerDiscard = game.playerDiscard + cannonball
                        )
                    }

                    ChoiceOwner.OPPONENT -> {
                        game.copy(
                            opponentDiscard = game.opponentDiscard + cannonball
                        )
                    }
                }
            }

            CardId.ARAIGNEEJUVENILE -> {
                val spiderEgg = CardCatalog.getCard(CardId.OEUFDARAIGNEE)

                when (owner) {
                    ChoiceOwner.PLAYER -> {
                        game.copy(
                            playerDiscard = game.playerDiscard + spiderEgg
                        )
                    }

                    ChoiceOwner.OPPONENT -> {
                        game.copy(
                            opponentDiscard = game.opponentDiscard + spiderEgg
                        )
                    }
                }
            }

            CardId.GARDIENSACRE -> {
                val golemdocre = CardCatalog.getCard(CardId.GOLEMDOCRE)
                val squeletton = CardEffectManager.createRandomSkeletonCard()

                when (owner) {
                    ChoiceOwner.PLAYER -> {
                        game.copy(
                            playerDiscard = game.playerDiscard + golemdocre + squeletton
                        )
                    }

                    ChoiceOwner.OPPONENT -> {
                        game.copy(
                            opponentDiscard = game.opponentDiscard + golemdocre + squeletton
                        )
                    }
                }
            }

            else -> game
        }
    }

    private fun tryResolvePendingPurchase(
        game: GameState,
        isPlayer: Boolean,
        snipedByPriority: Boolean = false
    ): GameState {
        val pendingCardId = if (isPlayer) {
            game.playerPendingShopPurchaseCardId
        } else {
            game.opponentPendingShopPurchaseCardId
        }

        if (pendingCardId == null) {
            return if (isPlayer) {
                game.copy(playerShopStatus = ShopPurchaseStatus.NONE)
            } else {
                game.copy(opponentShopStatus = ShopPurchaseStatus.NONE)
            }
        }

        val entry = getShopEntryByCardId(game, pendingCardId)

        if (entry == null || entry.copiesRemaining <= 0) {

            val failedStatus =
                if (snipedByPriority) {
                    ShopPurchaseStatus.PURCHASE_SNIPED_BY_PRIORITY
                } else {
                    ShopPurchaseStatus.INVALID_OUT_OF_STOCK
                }

            return if (isPlayer) {
                game.copy(
                    playerShopStatus = failedStatus
                )
            } else {
                game.copy(
                    opponentShopStatus = failedStatus
                )
            }
        }

        if (hasReachedPurchaseLimit(game, entry.card, isPlayer)) {
            return if (isPlayer) {
                game.copy(playerShopStatus = ShopPurchaseStatus.INVALID_LIMIT_REACHED)
            } else {
                game.copy(opponentShopStatus = ShopPurchaseStatus.INVALID_LIMIT_REACHED)
            }
        }

        if (
            game.mode == "SEASON" &&
            isSeasonMercenary(entry.card) &&
            (
                    isPlayer && game.playerBoughtSeasonMercenary ||
                            !isPlayer && game.opponentBoughtSeasonMercenary
                    )
        ) {
            return if (isPlayer) {
                game.copy(
                    playerShopStatus = ShopPurchaseStatus.INVALID_LIMIT_REACHED,
                    infoMessage = "Vous avez déjà recruté un mercenaire cette saison."
                )
            } else {
                game.copy(
                    opponentShopStatus = ShopPurchaseStatus.INVALID_LIMIT_REACHED,
                    infoMessage = "L'adversaire a déjà recruté un mercenaire cette saison."
                )
            }
        }

        val ownerGold = if (isPlayer) game.playerGold else game.opponentGold

        if (ownerGold < entry.card.cost) {
            return if (isPlayer) {
                game.copy(playerShopStatus = ShopPurchaseStatus.WAITING_FOR_GOLD)
            } else {
                game.copy(opponentShopStatus = ShopPurchaseStatus.WAITING_FOR_GOLD)
            }
        }

        val boughtCardIsSeasonMercenary =
            game.mode == "SEASON" && isSeasonMercenary(entry.card)

        val remainingMercenaryDeck =
            if (boughtCardIsSeasonMercenary) {
                game.seasonMercenaryDeck.drop(1)
            } else {
                game.seasonMercenaryDeck
            }

        val nextMercenaryCard =
            remainingMercenaryDeck.firstOrNull()?.let { nextMercenaryId ->
                CardCatalog.getCard(nextMercenaryId)
            }

        val updatedShopEntries = game.shopEntries.mapNotNull { shopEntry ->
            if (shopEntry.card.id.name == pendingCardId) {
                if (boughtCardIsSeasonMercenary) {
                    if (nextMercenaryCard != null) {
                        shopEntry.copy(
                            card = nextMercenaryCard,
                            copiesRemaining = 1
                        )
                    } else {
                        null
                    }
                } else {
                    val remainingCopies = shopEntry.copiesRemaining - 1

                    if (remainingCopies > 0) {
                        shopEntry.copy(copiesRemaining = remainingCopies)
                    } else {
                        shopEntry.copy(copiesRemaining = 0)
                    }
                }
            } else {
                shopEntry
            }
        }

        val afterCountIncrement = incrementPurchaseCount(game, pendingCardId, isPlayer)

        val afterPurchaseWithoutLog = if (isPlayer) {
            afterCountIncrement.copy(
                playerGold = game.playerGold - entry.card.cost,
                playerDiscard = game.playerDiscard + entry.card,
                shopEntries = updatedShopEntries,
                seasonMercenaryDeck = remainingMercenaryDeck,
                playerBoughtSeasonMercenary =
                    if (boughtCardIsSeasonMercenary) true else game.playerBoughtSeasonMercenary,
                playerShopStatus = ShopPurchaseStatus.PURCHASE_COMPLETED,
                playerPendingShopPurchaseCardId = null
            )
        } else {
            val shouldKeepAiTarget =
                game.mode == "TRAINING" || game.mode == "SOLO"

            afterCountIncrement.copy(
                opponentGold = game.opponentGold - entry.card.cost,
                opponentDiscard = game.opponentDiscard + entry.card,
                shopEntries = updatedShopEntries,
                seasonMercenaryDeck = remainingMercenaryDeck,
                opponentBoughtSeasonMercenary =
                    if (boughtCardIsSeasonMercenary) true else game.opponentBoughtSeasonMercenary,
                opponentShopStatus = ShopPurchaseStatus.PURCHASE_COMPLETED,
                opponentPendingShopPurchaseCardId = if (shouldKeepAiTarget) {
                    chooseRandomShopTargetCardId(
                        game = afterCountIncrement.copy(
                            opponentGold = game.opponentGold - entry.card.cost,
                            opponentDiscard = game.opponentDiscard + entry.card,
                            shopEntries = updatedShopEntries,
                            opponentShopStatus = ShopPurchaseStatus.PURCHASE_COMPLETED
                        ),
                        excludeCardId = pendingCardId
                    )
                } else {
                    null
                }
            )
        }

        val afterPurchase = GameLogManager.purchase(
            game = afterPurchaseWithoutLog,
            buyer = if (isPlayer) "PLAYER" else "OPPONENT",
            cardId = entry.card.id.name
        )

        val owner = if (isPlayer) {
            ChoiceOwner.PLAYER
        } else {
            ChoiceOwner.OPPONENT
        }

        val afterAutomaticPurchaseEffects = applyAutomaticPurchaseEffects(
            game = afterPurchase,
            purchasedCard = entry.card,
            owner = owner
        )


        val discardAfterPurchase = if (isPlayer) {
            afterAutomaticPurchaseEffects.playerDiscard
        } else {
            afterAutomaticPurchaseEffects.opponentDiscard
        }

        return if (entry.card.id == CardId.SENTINELLE) {

            val ownerSide = if (isPlayer) {
                CardDestroyManager.Side.PLAYER
            } else {
                CardDestroyManager.Side.OPPONENT
            }

            // Seules les cartes réellement destructibles sont proposées.
            // La Sentinelle nouvellement achetée est exclue.
            val targetIndices = CardDestroyManager.eligibleIndices(
                game = afterAutomaticPurchaseEffects,
                owner = ownerSide,
                zone = CardDestroyManager.Zone.DISCARD,
                cause = CardDestroyManager.Cause.REPLACEMENT,
                bypassFiveCardRule = true
            ).filter { it != discardAfterPurchase.lastIndex }

            if (targetIndices.isEmpty()) {
                afterAutomaticPurchaseEffects
            } else {
                afterAutomaticPurchaseEffects.copy(
                    pendingChoice = PendingChoice(
                        type = "SENTINELLE_BUY_DESTROY",
                        cardId = CardId.SENTINELLE.name,
                        options = targetIndices.map { "DISCARD:$it" } + "PASS",
                        message = "La Sentinelle : choisissez une carte de votre défausse à détruire.",
                        owner = owner
                    ),
                    infoMessage = null
                )
            }
        } else {
            afterAutomaticPurchaseEffects
        }

    }

    private fun haveBothPlayersPassedShop(game: GameState): Boolean {
        return game.playerPassedShop && game.opponentPassedShop
    }

    private fun isShopRoundReadyToResolve(game: GameState): Boolean {
        val playerReadyForShopRound =
            game.playerPassedShop || game.playerPendingShopPurchaseCardId != null

        val opponentReadyForShopRound =
            game.opponentPassedShop || game.opponentPendingShopPurchaseCardId != null

        return playerReadyForShopRound && opponentReadyForShopRound
    }

    private fun resolveShopRound(game: GameState): GameState {

        val firstIsPlayer = game.shopPriorityPlayerFirst ?: true

        var workingGame = game

        if (firstIsPlayer) {

            if (!workingGame.playerPassedShop) {
                workingGame = tryResolvePendingPurchase(
                    workingGame,
                    isPlayer = true
                )
            }

            if (!workingGame.opponentPassedShop) {
                workingGame = tryResolvePendingPurchase(
                    workingGame,
                    isPlayer = false,
                    snipedByPriority = true
                )
            }

        } else {

            if (!workingGame.opponentPassedShop) {
                workingGame = tryResolvePendingPurchase(
                    workingGame,
                    isPlayer = false
                )
            }

            if (!workingGame.playerPassedShop) {
                workingGame = tryResolvePendingPurchase(
                    workingGame,
                    isPlayer = true,
                    snipedByPriority = true
                )
            }
        }

        return workingGame
    }

    fun requestShopPurchase(
        gameId: String,
        cardId: String,
        isPlayer: Boolean = true
    ): GameState? = withGameLock(gameId) {
        val game = games[gameId] ?: return@withGameLock null

        val shopEntry = game.shopEntries.find { it.card.id.name == cardId }
        val card = shopEntry?.card

        if (card == null) {
            return@withGameLock game.copy(
                infoMessage = "Carte introuvable dans le shop."
            )
        }

        if (hasReachedPurchaseLimit(game, card, isPlayer)) {
            val updatedGame = if (isPlayer) {
                game.copy(
                    playerShopStatus = ShopPurchaseStatus.INVALID_LIMIT_REACHED,
                    infoMessage = "Vous avez déjà atteint la limite d'achat de cette carte."
                )
            } else {
                game.copy(
                    opponentShopStatus = ShopPurchaseStatus.INVALID_LIMIT_REACHED,
                    infoMessage = "Vous avez déjà atteint la limite d'achat de cette carte."
                )
            }

            games[gameId] = updatedGame
            return@withGameLock updatedGame
        }
        if (
            game.mode == "SEASON" &&
            card.faction == CardFaction.MERCENARY &&
            (
                    isPlayer && game.playerBoughtSeasonMercenary ||
                            !isPlayer && game.opponentBoughtSeasonMercenary
                    )
        ) {
            val updatedGame = if (isPlayer) {
                game.copy(
                    playerShopStatus = ShopPurchaseStatus.INVALID_LIMIT_REACHED,
                    infoMessage = "Vous avez déjà recruté un mercenaire cette saison."
                )
            } else {
                game.copy(
                    opponentShopStatus = ShopPurchaseStatus.INVALID_LIMIT_REACHED,
                    infoMessage = "L'adversaire a déjà recruté un mercenaire cette saison."
                )
            }

            games[gameId] = updatedGame
            return@withGameLock updatedGame
        }

        val updatedGame = if (isPlayer) {
            game.copy(
                playerPendingShopPurchaseCardId = cardId,
                playerPassedShop = false,
                playerShopStatus = evaluatePendingShopPurchaseStatus(
                    game = game,
                    cardId = cardId,
                    isPlayer = true
                ),
                infoMessage = if (game.phase == TurnPhase.SHOP_RESOLUTION) {
                    "Demande d'achat mise à jour"
                } else {
                    "Achat en attente pour la prochaine phase d'achat"
                }
            )
        } else {
            game.copy(
                opponentPendingShopPurchaseCardId = cardId,
                opponentPassedShop = false,
                opponentShopStatus = evaluatePendingShopPurchaseStatus(
                    game = game,
                    cardId = cardId,
                    isPlayer = false
                ),
                infoMessage = if (game.phase == TurnPhase.SHOP_RESOLUTION) {
                    "Demande d'achat adverse mise à jour"
                } else {
                    "Achat adverse en attente pour la prochaine phase d'achat"
                }
            )
        }

        games[gameId] = updatedGame
        updatedGame
    }

    fun passShop(
        gameId: String,
        isPlayer: Boolean
    ): GameState? = withGameLock(gameId) {

        val game = games[gameId] ?: return@withGameLock null

        val updatedGame = if (isPlayer) {
            game.copy(
                playerPassedShop = true,
                infoMessage = "Vous passez pour cette phase d'achat"
            )
        } else {
            game.copy(
                opponentPassedShop = true,
                infoMessage = "L'adversaire passe"
            )
        }

        games[gameId] = updatedGame
        updatedGame
    }


    // =========================================================
    // 7. RÉSOLUTION D’UNE PHASE DE SHOP
    // =========================================================
    // Pour l’instant :
    // - on initialise bien la phase
    // - on calcule qui commence
    // - on met à jour les statuts affichables
    // - puis on laisse la main au client
    //
    // =========================================================

    private fun startShopResolution(game: GameState): GameState {
        val playerFirst = determineShopPriorityPlayerFirst(game)

        return game.copy(
            phase = TurnPhase.SHOP_RESOLUTION,
            shopPriorityPlayerFirst = playerFirst,
            currentShopBuyerIsPlayer = playerFirst,
            playerPassedShop = false,
            opponentPassedShop = false,
            playerShopStatus = evaluatePendingShopPurchaseStatus(
                game = game,
                cardId = game.playerPendingShopPurchaseCardId,
                isPlayer = true
            ),
            opponentShopStatus = evaluatePendingShopPurchaseStatus(
                game = game,
                cardId = game.opponentPendingShopPurchaseCardId,
                isPlayer = false
            ),
            infoMessage = "Phase d'achat"
        )
    }

    // =========================================================
    // 8. AVANCE DE PARTIE — FLOW PRINCIPAL
    // =========================================================

    fun advanceTrainingGame(
        gameId: String,
        isPlayer: Boolean = true
    ): GameState? = withGameLock(gameId) {
        var game = games[gameId] ?: return@withGameLock null

        if (game.isFinished) {
            return@withGameLock game
        }

        var updatedGame = game.copy(infoMessage = null)

        val phaseRequiresTimedResolution = when (game.phase) {
            TurnPhase.REVEAL,
            TurnPhase.EFFECTS,
            TurnPhase.COMBAT,
            TurnPhase.POST_COMBAT -> true

            else -> false
        }

        if (
            phaseRequiresTimedResolution &&
            !canResolveCurrentPhase(game)
        ) {
            return@withGameLock game
        }

        when (game.phase) {

            // -------------------------------------------------
            // PRE_START
            // -------------------------------------------------
            TurnPhase.PRE_START -> {
                val readyGame = if (isPlayer) {
                    game.copy(playerReady = true)
                } else {
                    game.copy(opponentReady = true)
                }

                updatedGame = if (readyGame.playerReady && readyGame.opponentReady) {
                    enterPhase(
                        game = readyGame.copy(
                            turnNumber = 1
                        ),
                        phase = TurnPhase.AMBUSH_BEFORE_REVEAL
                    )
                } else {
                    readyGame.copy(
                        infoMessage = "En attente de l'autre joueur"
                    )
                }
            }

            // -------------------------------------------------
            // REVEAL
            // -------------------------------------------------
            TurnPhase.REVEAL -> {

                game = addLog(
                    game = game,
                    key = "TURN_START",
                    params = mapOf("turn" to game.turnNumber.toString())
                )

                var playerDeck = game.playerDeck
                var opponentDeck = game.opponentDeck
                var playerDiscard = game.playerDiscard
                var opponentDiscard = game.opponentDiscard

                // sécurité : si d’anciennes cartes révélées traînent, on les range
                game.lastPlayerCard?.let { playerDiscard = playerDiscard + it }
                game.lastOpponentCard?.let { opponentDiscard = opponentDiscard + it }

                var recycleGame = game.copy(
                    playerDeck = playerDeck,
                    opponentDeck = opponentDeck,
                    playerDiscard = playerDiscard,
                    opponentDiscard = opponentDiscard
                )

                recycleGame = DeckManager.reshuffleDiscardIntoDeckIfNeeded(
                    game = recycleGame,
                    target = ChoiceOwner.PLAYER
                )

                recycleGame = DeckManager.reshuffleDiscardIntoDeckIfNeeded(
                    game = recycleGame,
                    target = ChoiceOwner.OPPONENT
                )

                playerDeck = recycleGame.playerDeck
                opponentDeck = recycleGame.opponentDeck
                playerDiscard = recycleGame.playerDiscard
                opponentDiscard = recycleGame.opponentDiscard

                val newPlayerGold = recycleGame.playerGold
                val newOpponentGold = recycleGame.opponentGold

                val playerCard = playerDeck.firstOrNull()
                val opponentCard = opponentDeck.firstOrNull()

                if (playerCard != null || opponentCard != null) {
                    recycleGame = resolveUniqueOnReveal(
                        game = recycleGame,
                        revealedCards = listOfNotNull(
                            playerCard,
                            opponentCard
                        )
                    )

                    playerDiscard = recycleGame.playerDiscard
                    opponentDiscard = recycleGame.opponentDiscard
                }

                // -------------------------------------------------
                // EFFETS DE PRÉDICTION SUR LA PROCHAINE RÉVÉLATION
                // Exemple : Le Maître des Carnages
                // -------------------------------------------------
                val playerPredictionEffect = game.playerNextRevealPredictionEffect
                val opponentPredictionEffect = game.opponentNextRevealPredictionEffect

                val playerGetsPredictionBonus =
                    playerCard != null &&
                            playerPredictionEffect != null &&
                            playerPredictionEffect.predictedCardName == playerCard.id.name

                val opponentGetsPredictionBonus =
                    opponentCard != null &&
                            opponentPredictionEffect != null &&
                            opponentPredictionEffect.predictedCardName == opponentCard.id.name

                val playerPredictionPowerBonus =
                    if (playerGetsPredictionBonus) playerPredictionEffect?.powerBonusOnMatch ?: 0 else 0

                val opponentPredictionPowerBonus =
                    if (opponentGetsPredictionBonus) opponentPredictionEffect?.powerBonusOnMatch ?: 0 else 0


                updatedGame = if (playerCard == null || opponentCard == null) {
                    recycleGame.copy(
                        playerDeck = playerDeck,
                        opponentDeck = opponentDeck,
                        playerDiscard = playerDiscard,
                        opponentDiscard = opponentDiscard,
                        playerGold = newPlayerGold,
                        opponentGold = newOpponentGold,
                        lastPlayerCard = null,
                        lastOpponentCard = null,
                        playerEffectResolved = false,
                        opponentEffectResolved = false,
                        infoMessage = "Impossible de révéler les cartes"
                    )
                } else {
                    enterPhase(
                        game = recycleGame.copy(
                        playerDeck = playerDeck.drop(1),
                        opponentDeck = opponentDeck.drop(1),
                        playerDiscard = playerDiscard,
                        opponentDiscard = opponentDiscard,
                        playerGold = newPlayerGold,
                        opponentGold = newOpponentGold,
                        lastPlayerCard = playerCard,
                        lastOpponentCard = opponentCard,

                        playerEnragePurchasedOptions = emptySet(),
                        opponentEnragePurchasedOptions = emptySet(),

                        playerEnrageCombatDamage = 1,
                        opponentEnrageCombatDamage = 1,

                        playerDisplayedTurnCard = playerCard,
                        opponentDisplayedTurnCard = opponentCard,
                        playerAmbassadriceTurnStartHp = game.playerHp,
                        opponentAmbassadriceTurnStartHp = game.opponentHp,

                        playerCurrentCardPowerBonus = game.playerNextCardPowerBonus + playerPredictionPowerBonus,
                        opponentCurrentCardPowerBonus = game.opponentNextCardPowerBonus + opponentPredictionPowerBonus,

                        playerCurrentCardDamageBonus = game.playerNextCardDamageBonus,
                        opponentCurrentCardDamageBonus = game.opponentNextCardDamageBonus,

                        // VENIMEUX — transfert des bonus à la carte révélée
                        playerCurrentCardVenomousBonus = game.playerNextCardVenomousBonus,
                        opponentCurrentCardVenomousBonus = game.opponentNextCardVenomousBonus,

                        playerCurrentCardHasBruteBonus = game.playerNextCardHasBrute,
                        opponentCurrentCardHasBruteBonus = game.opponentNextCardHasBrute,

                        playerNextCardPowerBonus = 0,
                        opponentNextCardPowerBonus = 0,

                        playerNextCardDamageBonus = 0,
                        opponentNextCardDamageBonus = 0,

                        // VENIMEUX — bonus consommés lors de la révélation
                        playerNextCardVenomousBonus = 0,
                        opponentNextCardVenomousBonus = 0,

                        playerNextCardHasBrute = false,
                        opponentNextCardHasBrute = false,

                        // L'effet de prédiction ne dure qu'une seule révélation.
                        // Bonne ou mauvaise prédiction : il disparaît après cette révélation.
                        playerNextRevealPredictionEffect = null,
                        opponentNextRevealPredictionEffect = null,

                        playerEffectResolved = false,
                        opponentEffectResolved = false,

                        playerAbilitiesBlocked = false,
                        opponentAbilitiesBlocked = false,

                        playerCardEffectsBlockedByAmbush = false,
                        opponentCardEffectsBlockedByAmbush = false,

                        playerPostCombatSacrificeHandled = false,
                        opponentPostCombatSacrificeHandled = false,

                        // VENIMEUX — nouveau combat, nouveaux déclenchements possibles
                        playerPostCombatVenomousHandled = false,
                        opponentPostCombatVenomousHandled = false,

                        // POST-COMBAT — nouveaux effets automatiques à résoudre
                        playerPostCombatCardEffectResolved = false,
                        opponentPostCombatCardEffectResolved = false,

                        playerPostCombatEffectProgress = null,
                        opponentPostCombatEffectProgress = null,

                        postCombatResolutionOrder = null,

                        combatCancelledByDodge = false,

                        ),
                        phase = TurnPhase.AMBUSH_BEFORE_EFFECTS
                    )
                }

                debugPower(updatedGame, "REVEAL")
            }

            // -------------------------------------------------
            // EFFECTS
            // -------------------------------------------------
            TurnPhase.EFFECTS -> {
                val playerCard = game.lastPlayerCard
                val opponentCard = game.lastOpponentCard

                if (playerCard == null || opponentCard == null) {
                    updatedGame = game.copy(
                        infoMessage = "Aucune carte révélée pour les effets"
                    )
                } else if (game.pendingChoice != null) {
                    updatedGame = game
                } else {
                    val playerEffectivePower = CardEffectManager.getEffectivePower(
                        game = game,
                        card = playerCard,
                        ownerGold = game.playerGold,
                        nextCardPowerBonus = game.playerCurrentCardPowerBonus,
                        isPlayer = true
                    )

                    val opponentEffectivePower = CardEffectManager.getEffectivePower(
                        game = game,
                        card = opponentCard,
                        ownerGold = game.opponentGold,
                        nextCardPowerBonus = game.opponentCurrentCardPowerBonus,
                        isPlayer = false
                    )

                    val resolutionOrder = determineResolutionOrder(
                        playerHasPrems = hasPrems(playerCard),
                        opponentHasPrems = hasPrems(opponentCard),
                        playerPower = playerEffectivePower,
                        opponentPower = opponentEffectivePower,
                        playerHp = game.playerHp,
                        opponentHp = game.opponentHp,
                        playerGold = game.playerGold,
                        opponentGold = game.opponentGold
                    )

                    val effectAnimationOrder = when (resolutionOrder) {
                        ResolutionOrder.PLAYER_FIRST -> listOf("PLAYER", "OPPONENT")
                        ResolutionOrder.OPPONENT_FIRST -> listOf("OPPONENT", "PLAYER")
                    }

                    fun applyPlayerEffect(currentGame: GameState): GameState {
                        return EffectPhaseCardResolver.applyEffect(
                            game = currentGame,
                            owner = ChoiceOwner.PLAYER
                        )
                    }

                    fun applyOpponentEffect(currentGame: GameState): GameState {
                        return EffectPhaseCardResolver.applyEffect(
                            game = currentGame,
                            owner = ChoiceOwner.OPPONENT
                        )
                    }

                    var workingGame = game.copy(
                        effectAnimationOrder = effectAnimationOrder
                    )


                    fun activateSentinelle(
                        currentGame: GameState,
                        owner: ChoiceOwner
                    ): GameState {
                        val isPlayer = owner == ChoiceOwner.PLAYER

                        val sentinelleCard = if (isPlayer) playerCard else opponentCard
                        if (sentinelleCard.id != CardId.SENTINELLE) return currentGame

                        // Une Sentinelle dont les capacités sont bloquées
                        // ne peut pas bloquer celles de son adversaire.
                        val ownerBlocked = if (isPlayer) {
                            currentGame.playerAbilitiesBlocked ||
                                    currentGame.playerCardEffectsBlockedByAmbush
                        } else {
                            currentGame.opponentAbilitiesBlocked ||
                                    currentGame.opponentCardEffectsBlockedByAmbush
                        }

                        if (ownerBlocked) return currentGame

                        val sentinellePower = if (isPlayer) {
                            CardEffectManager.getEffectivePower(
                                game = currentGame,
                                card = sentinelleCard,
                                ownerGold = currentGame.playerGold,
                                nextCardPowerBonus = currentGame.playerCurrentCardPowerBonus,
                                isPlayer = true
                            )
                        } else {
                            CardEffectManager.getEffectivePower(
                                game = currentGame,
                                card = sentinelleCard,
                                ownerGold = currentGame.opponentGold,
                                nextCardPowerBonus = currentGame.opponentCurrentCardPowerBonus,
                                isPlayer = false
                            )
                        }

                        val targetCard = if (isPlayer) opponentCard else playerCard

                        val targetPower = CardEffectManager.getEffectivePower(
                            game = currentGame,
                            card = targetCard,
                            ownerGold = if (isPlayer) currentGame.opponentGold else currentGame.playerGold,
                            nextCardPowerBonus = if (isPlayer) {
                                currentGame.opponentCurrentCardPowerBonus
                            } else {
                                currentGame.playerCurrentCardPowerBonus
                            },
                            isPlayer = !isPlayer
                        )

                        if (targetPower < sentinellePower) return currentGame

                        return if (isPlayer) {
                            currentGame.copy(opponentAbilitiesBlocked = true)
                        } else {
                            currentGame.copy(playerAbilitiesBlocked = true)
                        }
                    }


                    when (resolutionOrder) {
                        ResolutionOrder.PLAYER_FIRST -> {
                            if (!workingGame.playerEffectResolved) {
                                val blockedBySentinelle =
                                    workingGame.playerAbilitiesBlocked

                                val blockedByAmbush =
                                    workingGame.playerCardEffectsBlockedByAmbush

                                if (blockedBySentinelle || blockedByAmbush) {
                                    workingGame = workingGame.copy(
                                        playerEffectResolved = true,
                                        infoMessage = null
                                    )
                                } else {
                                    workingGame = applyPlayerEffect(workingGame)

                                    if (workingGame.pendingChoice != null) {
                                        workingGame = resolveAiPendingChoiceIfNeeded(workingGame)

                                        if (workingGame.pendingChoice != null) {
                                            updatedGame = workingGame
                                            games[gameId] = updatedGame
                                            return@withGameLock updatedGame
                                        }
                                    }

                                    workingGame = workingGame.copy(
                                        playerEffectResolved = true
                                    )

                                    workingGame = activateSentinelle(
                                        currentGame = workingGame,
                                        owner = ChoiceOwner.PLAYER
                                    )
                                }
                            }

                            if (!workingGame.opponentEffectResolved) {
                                val blockedBySentinelle =
                                    workingGame.opponentAbilitiesBlocked

                                val blockedByAmbush =
                                    workingGame.opponentCardEffectsBlockedByAmbush

                                if (blockedBySentinelle || blockedByAmbush) {
                                    workingGame = workingGame.copy(
                                        opponentEffectResolved = true,
                                        infoMessage = null
                                    )
                                } else {
                                    workingGame = applyOpponentEffect(workingGame)

                                    if (workingGame.pendingChoice != null) {
                                        workingGame = resolveAiPendingChoiceIfNeeded(workingGame)

                                        if (workingGame.pendingChoice != null) {
                                            updatedGame = workingGame
                                            games[gameId] = updatedGame
                                            return@withGameLock updatedGame
                                        }
                                    }

                                    workingGame = workingGame.copy(
                                        opponentEffectResolved = true
                                    )
                                    workingGame = activateSentinelle(
                                        currentGame = workingGame,
                                        owner = ChoiceOwner.OPPONENT
                                    )
                                }
                            }
                        }

                        ResolutionOrder.OPPONENT_FIRST -> {
                            if (!workingGame.opponentEffectResolved) {
                                val blockedBySentinelle =
                                    workingGame.opponentAbilitiesBlocked

                                val blockedByAmbush =
                                    workingGame.opponentCardEffectsBlockedByAmbush

                                if (blockedBySentinelle || blockedByAmbush) {
                                    workingGame = workingGame.copy(
                                        opponentEffectResolved = true,
                                        infoMessage = null
                                    )
                                } else {
                                    workingGame = applyOpponentEffect(workingGame)

                                    if (workingGame.pendingChoice != null) {
                                        workingGame = resolveAiPendingChoiceIfNeeded(workingGame)

                                        if (workingGame.pendingChoice != null) {
                                            updatedGame = workingGame
                                            games[gameId] = updatedGame
                                            return@withGameLock updatedGame
                                        }
                                    }

                                    workingGame = workingGame.copy(opponentEffectResolved = true)

                                    workingGame = activateSentinelle(
                                        currentGame = workingGame,
                                        owner = ChoiceOwner.OPPONENT
                                    )
                                }
                            }

                            if (!workingGame.playerEffectResolved) {
                                val blockedBySentinelle =
                                    workingGame.playerAbilitiesBlocked

                                val blockedByAmbush =
                                    workingGame.playerCardEffectsBlockedByAmbush

                                if (blockedBySentinelle || blockedByAmbush) {
                                    workingGame = workingGame.copy(
                                        playerEffectResolved = true,
                                        infoMessage = null
                                    )
                                } else {
                                    workingGame = applyPlayerEffect(workingGame)

                                    if (workingGame.pendingChoice != null) {
                                        workingGame = resolveAiPendingChoiceIfNeeded(workingGame)

                                        if (workingGame.pendingChoice != null) {
                                            updatedGame = workingGame
                                            games[gameId] = updatedGame
                                            return@withGameLock updatedGame
                                        }
                                    }

                                    workingGame = workingGame.copy(playerEffectResolved = true)

                                    workingGame = activateSentinelle(
                                        currentGame = workingGame,
                                        owner = ChoiceOwner.PLAYER
                                    )
                                }
                            }
                        }
                    }

                    updatedGame = if (
                        workingGame.playerEffectResolved &&
                        workingGame.opponentEffectResolved &&
                        workingGame.pendingChoice == null
                    ) {
                        enterPhase(
                            game = workingGame,
                            phase = TurnPhase.AMBUSH_BEFORE_COMBAT
                        )
                    } else {
                        workingGame
                    }
                }
            }

            // -------------------------------------------------
            // COMBAT
            // -------------------------------------------------
            TurnPhase.COMBAT -> {
                val playerCard = game.lastPlayerCard
                val opponentCard = game.lastOpponentCard

                if (playerCard == null || opponentCard == null) {
                    updatedGame = game.copy(
                        infoMessage = "Aucune carte révélée pour le combat"
                    )
                } else if (game.combatCancelledByDodge) {
                    updatedGame = enterPhase(
                        game = game.copy(
                            combatDamageToPlayer = 0,
                            combatDamageToOpponent = 0,

                            playerEffectivePower = 0,
                            opponentEffectivePower = 0,

                            playerCurrentCardPowerBonus = 0,
                            opponentCurrentCardPowerBonus = 0,

                            playerCurrentCardDamageBonus = 0,
                            opponentCurrentCardDamageBonus = 0,

                            playerCurrentCardBretteurBonus = 0,
                            opponentCurrentCardBretteurBonus = 0,

                            playerCurrentCardVenomousBonus = 0,
                            opponentCurrentCardVenomousBonus = 0,

                            playerCurrentCardHasBruteBonus = false,
                            opponentCurrentCardHasBruteBonus = false,

                            infoMessage = null
                        ),
                        phase = TurnPhase.COMBAT_CLEANUP
                    )
                } else {
                    var newPlayerHp = game.playerHp
                    var newOpponentHp = game.opponentHp

                    val playerHasBrute =
                        !game.playerAbilitiesBlocked &&
                                CardEffectManager.hasBrute(
                                    card = playerCard,
                                    nextCardHasBrute = game.playerCurrentCardHasBruteBonus
                                )

                    val opponentHasBrute =
                        !game.opponentAbilitiesBlocked &&
                                CardEffectManager.hasBrute(
                                    card = opponentCard,
                                    nextCardHasBrute = game.opponentCurrentCardHasBruteBonus
                                )

                    val playerEffectivePower = CardEffectManager.getEffectivePower(
                        game = game,
                        card = playerCard,
                        ownerGold = game.playerGold,
                        nextCardPowerBonus = game.playerCurrentCardPowerBonus,
                        isPlayer = true
                    )

                    val opponentEffectivePower = CardEffectManager.getEffectivePower(
                        game = game,
                        card = opponentCard,
                        ownerGold = game.opponentGold,
                        nextCardPowerBonus = game.opponentCurrentCardPowerBonus,
                        isPlayer = false
                    )

                    val playerSauvageDamage =
                        if (
                            playerCard.id == CardId.SAUVAGE &&
                            !game.playerAbilitiesBlocked &&
                            !game.playerCardEffectsBlockedByAmbush &&
                            playerEffectivePower > opponentEffectivePower
                        ) {
                            CardEffectManager.countBeastLink(game, ChoiceOwner.PLAYER)
                                .takeIf { it >= 6 }
                                ?.div(2)
                        } else {
                            null
                        }

                    val opponentSauvageDamage =
                        if (
                            opponentCard.id == CardId.SAUVAGE &&
                            !game.opponentAbilitiesBlocked &&
                            !game.opponentCardEffectsBlockedByAmbush &&
                            opponentEffectivePower > playerEffectivePower
                        ) {
                            CardEffectManager.countBeastLink(game, ChoiceOwner.OPPONENT)
                                .takeIf { it >= 6 }
                                ?.div(2)
                        } else {
                            null
                        }

                    val playerDamageDealt =
                        if (
                            playerCard.id == CardId.TRAPPEUR &&
                            playerEffectivePower > opponentEffectivePower
                        ) {
                            0
                        } else {
                            CardEffectManager.getCombatDamageDealt(
                                attacker = playerCard,
                                defender = opponentCard,
                                attackerPower = playerEffectivePower,
                                defenderPower = opponentEffectivePower,
                                nextCardDamageBonus = game.playerCurrentCardDamageBonus,
                                attackerHasBrute = playerHasBrute,
                                enrageCombatDamage = game.playerEnrageCombatDamage,
                                replacementDamage = playerSauvageDamage
                            )
                        }

                    val opponentDamageDealt =
                        if (
                            opponentCard.id == CardId.TRAPPEUR &&
                            opponentEffectivePower > playerEffectivePower
                        ) {
                            0
                        } else {
                            CardEffectManager.getCombatDamageDealt(
                                attacker = opponentCard,
                                defender = playerCard,
                                attackerPower = opponentEffectivePower,
                                defenderPower = playerEffectivePower,
                                nextCardDamageBonus = game.opponentCurrentCardDamageBonus,
                                attackerHasBrute = opponentHasBrute,
                                enrageCombatDamage = game.opponentEnrageCombatDamage,
                                replacementDamage = opponentSauvageDamage
                            )
                        }

                    val playerHasBurn =
                        TokenManager.hasToken(
                            game = game,
                            target = ChoiceOwner.PLAYER,
                            tokenId = TokenManager.TokenIds.BURN
                        )

                    val opponentHasBurn =
                        TokenManager.hasToken(
                            game = game,
                            target = ChoiceOwner.OPPONENT,
                            tokenId = TokenManager.TokenIds.BURN
                        )

                    val playerLostCombat =
                        opponentEffectivePower > playerEffectivePower

                    val opponentLostCombat =
                        playerEffectivePower > opponentEffectivePower

                    val burnDamageToPlayer =
                        if (playerHasBurn && playerLostCombat) 1 else 0

                    val burnDamageToOpponent =
                        if (opponentHasBurn && opponentLostCombat) 1 else 0

                    newOpponentHp = maxOf(
                        0,
                        newOpponentHp - playerDamageDealt - burnDamageToOpponent
                    )

                    newPlayerHp = maxOf(
                        0,
                        newPlayerHp - opponentDamageDealt - burnDamageToPlayer
                    )

                    updatedGame = enterPhase(
                        game = game.copy(
                            playerHp = newPlayerHp,
                            opponentHp = newOpponentHp,
                            combatDamageToPlayer = opponentDamageDealt,
                            combatDamageToOpponent = playerDamageDealt,

                            playerEffectivePower = playerEffectivePower,
                            opponentEffectivePower = opponentEffectivePower
                        ),
                        phase = TurnPhase.AMBUSH_BEFORE_POST_COMBAT
                    )

                    // =====================================================
                    // ROI — soigne autant de PV que les dégâts qu'il inflige
                    // =====================================================

                    if (
                        playerCard.id == CardId.ROI &&
                        playerDamageDealt > 0
                    ) {
                        updatedGame = TokenManager.heal(
                            game = updatedGame,
                            target = ChoiceOwner.PLAYER,
                            amount = playerDamageDealt
                        )
                    }

                    if (
                        opponentCard.id == CardId.ROI &&
                        opponentDamageDealt > 0
                    ) {
                        updatedGame = TokenManager.heal(
                            game = updatedGame,
                            target = ChoiceOwner.OPPONENT,
                            amount = opponentDamageDealt
                        )
                    }

                    if (playerHasBurn && !playerLostCombat) {
                        updatedGame = TokenManager.removeAllToken(
                            game = updatedGame,
                            target = ChoiceOwner.PLAYER,
                            tokenId = TokenManager.TokenIds.BURN
                        )
                    }

                    if (opponentHasBurn && !opponentLostCombat) {
                        updatedGame = TokenManager.removeAllToken(
                            game = updatedGame,
                            target = ChoiceOwner.OPPONENT,
                            tokenId = TokenManager.TokenIds.BURN
                        )
                    }

                    if (burnDamageToPlayer > 0) {
                        updatedGame = GameLogManager.tokenTriggered(
                            game = updatedGame,
                            owner = ChoiceOwner.PLAYER,
                            tokenId = TokenManager.TokenIds.BURN,
                            value = burnDamageToPlayer
                        )
                    }

                    if (burnDamageToOpponent > 0) {
                        updatedGame = GameLogManager.tokenTriggered(
                            game = updatedGame,
                            owner = ChoiceOwner.OPPONENT,
                            tokenId = TokenManager.TokenIds.BURN,
                            value = burnDamageToOpponent
                        )
                    }

                    updatedGame = if (playerDamageDealt > 0) {
                        GameLogManager.combatDamage(
                            game = updatedGame,
                            attacker = "PLAYER",
                            defender = "OPPONENT",
                            damage = playerDamageDealt,
                            attackerPower = playerEffectivePower,
                            defenderPower = opponentEffectivePower
                        )
                    } else {
                        updatedGame
                    }

                    updatedGame = if (opponentDamageDealt > 0) {
                        GameLogManager.combatDamage(
                            game = updatedGame,
                            attacker = "OPPONENT",
                            defender = "PLAYER",
                            damage = opponentDamageDealt,
                            attackerPower = opponentEffectivePower,
                            defenderPower = playerEffectivePower
                        )
                    } else {
                        updatedGame
                    }

                    debugPower(updatedGame, "POST_COMBAT")
                }
            }

            // -------------------------------------------------
            // POST_COMBAT
            // -------------------------------------------------
            TurnPhase.POST_COMBAT -> {
                var workingGame = game

                if (workingGame.pendingChoice != null) {
                    updatedGame = workingGame
                } else {
                    // Une décision de l'IA peut se terminer immédiatement.
                    // Reprendre alors le post-combat pour résoudre le second camp
                    // avant de passer à la défausse de fin de tour.
                    for (attempt in 0 until 8) {
                        workingGame = PostCombatEffectResolver.apply(workingGame)
                        if (workingGame.pendingChoice == null && workingGame.activeScryState == null) {
                            break
                        }
                        if (workingGame.pendingChoice != null) {
                            workingGame = resolveAiPendingChoiceIfNeeded(workingGame)
                        }
                        if (workingGame.pendingChoice != null || workingGame.activeScryState != null) {
                            updatedGame = workingGame.copy(phase = TurnPhase.POST_COMBAT)
                            games[gameId] = updatedGame
                            return@withGameLock updatedGame
                        }
                    }

                    // Si un sacrifice vient d'être résolu, on relance POST_COMBAT
                    // pour laisser l'autre camp déclencher son éventuel sacrifice.
                    val stillHasMorePostCombatChoices = workingGame.pendingChoice != null

                    if (stillHasMorePostCombatChoices) {
                        updatedGame = workingGame.copy(
                            phase = TurnPhase.POST_COMBAT
                        )
                    } else {
                        // consommation des bonus "prochaine carte"
                        workingGame = workingGame.copy(
                            playerCurrentCardPowerBonus = 0,
                            opponentCurrentCardPowerBonus = 0,

                            playerCurrentCardDamageBonus = 0,
                            opponentCurrentCardDamageBonus = 0,

                            playerCurrentCardBretteurBonus = 0,
                            opponentCurrentCardBretteurBonus = 0,

                            playerCurrentCardVenomousBonus = 0,
                            opponentCurrentCardVenomousBonus = 0,

                            playerCurrentCardHasBruteBonus = false,
                            opponentCurrentCardHasBruteBonus = false
                        )
                        val playerHasAmbassadriceInDiscard =
                            workingGame.playerDiscard.any { it.id == CardId.AMBASSADRICE }

                        val opponentHasAmbassadriceInDiscard =
                            workingGame.opponentDiscard.any { it.id == CardId.AMBASSADRICE }

                        val playerTurnStartHp = workingGame.playerAmbassadriceTurnStartHp
                        val opponentTurnStartHp = workingGame.opponentAmbassadriceTurnStartHp

                        val protectedPlayerHp = if (
                            (playerHasAmbassadriceInDiscard || workingGame.playerPaladinProtectionActive) &&
                            playerTurnStartHp != null
                        ) {
                            maxOf(
                                workingGame.playerHp,
                                playerTurnStartHp - 1
                            )
                        } else {
                            workingGame.playerHp
                        }

                        val protectedOpponentHp = if (
                            (opponentHasAmbassadriceInDiscard || workingGame.opponentPaladinProtectionActive) &&
                            opponentTurnStartHp != null
                        ) {
                            maxOf(
                                workingGame.opponentHp,
                                opponentTurnStartHp - 1
                            )
                        } else {
                            workingGame.opponentHp
                        }

                        workingGame = workingGame.copy(
                            playerHp = protectedPlayerHp,
                            opponentHp = protectedOpponentHp
                        )

                        val outcome = evaluateGameResult(
                            playerHp = workingGame.playerHp,
                            opponentHp = workingGame.opponentHp,
                            playerGold = workingGame.playerGold,
                            opponentGold = workingGame.opponentGold
                        )

                        updatedGame = if (outcome.isFinished) {
                            val finishedGame = workingGame.copy(
                                isFinished = true,
                                result = outcome.result,
                                phase = TurnPhase.POST_COMBAT
                            )

                            var recordedGame = recordDuelResultIfNeeded(finishedGame)
                            recordedGame = recordSoloResultIfNeeded(recordedGame)
                            recordedGame

                        } else {
                            enterPhase(
                                game = workingGame.copy(
                                    isFinished = false,
                                    result = null
                                ),
                                phase = TurnPhase.COMBAT_CLEANUP
                            )
                        }
                    }
                }
            }

                // -------------------------------------------------
                // COMBAT_CLEANUP
                // -------------------------------------------------
                TurnPhase.COMBAT_CLEANUP -> {

                    if (game.combatCancelledByDodge) {

                        // ESQUIVE : aucun combat n'a eu lieu.
                        // Les deux cartes vont directement à la défausse.
                        updatedGame = enterPhase(
                            game = game.copy(
                                playerDiscard = game.playerDiscard +
                                        listOfNotNull(game.lastPlayerCard),

                                opponentDiscard = game.opponentDiscard +
                                        listOfNotNull(game.lastOpponentCard),

                                lastPlayerCard = null,
                                lastOpponentCard = null,

                                combatCancelledByDodge = false,

                                delayedEffects = game.delayedEffects.filter {
                                    it.timing != DelayedEffectTiming.END_TURN
                                }
                            ),
                            phase = TurnPhase.AMBUSH_BEFORE_SHOP
                        )

                    } else {

                        // Les destructions et déplacements tactiques ont déjà
                        // été résolus pendant POST_COMBAT.

                        updatedGame = enterPhase(
                            game = game.copy(
                                playerDiscard = game.playerDiscard +
                                        listOfNotNull(game.lastPlayerCard),

                                opponentDiscard = game.opponentDiscard +
                                        listOfNotNull(game.lastOpponentCard),

                                lastPlayerCard = null,
                                lastOpponentCard = null,

                                delayedEffects = game.delayedEffects.filter {
                                    it.timing != DelayedEffectTiming.END_TURN
                                }
                            ),
                            phase = TurnPhase.AMBUSH_BEFORE_SHOP
                        )
                    }
                }

            // -------------------------------------------------
            // SHOP_RESOLUTION
            // -------------------------------------------------
            TurnPhase.SHOP_RESOLUTION -> {

                var workingGame = game

                if (haveBothPlayersPassedShop(workingGame)) {
                    updatedGame = workingGame.copy(
                        phase = TurnPhase.END_TURN,
                        infoMessage = "Fin de la phase d'achat"
                    )

                } else {

                    // IA d'entraînement temporaire :
                    // si l'adversaire n'a pas encore d'intention, il s'en donne une.
                    if (isAiControlledOpponent(workingGame)) {
                        workingGame = assignAiShopIntentIfNeeded(workingGame)
                    }

                    if (haveBothPlayersPassedShop(workingGame)) {
                        updatedGame = workingGame.copy(
                            phase = TurnPhase.END_TURN,
                            infoMessage = "Fin de la phase d'achat"
                        )

                    } else if (!isShopRoundReadyToResolve(workingGame)) {
                        updatedGame = workingGame.copy(
                            phase = TurnPhase.SHOP_RESOLUTION,
                            infoMessage = "Phase d'achat : en attente des choix"
                        )

                    } else {
                        updatedGame = resolveShopRound(workingGame)

                        if (updatedGame.pendingChoice != null) {
                            updatedGame = resolveAiPendingChoiceIfNeeded(updatedGame)

                            if (updatedGame.pendingChoice != null) {
                                updatedGame = updatedGame.copy(
                                    phase = TurnPhase.SHOP_RESOLUTION,
                                    infoMessage = null
                                )
                                games[gameId] = updatedGame
                                return@withGameLock updatedGame
                            }
                        }

                        if (haveBothPlayersPassedShop(updatedGame)) {
                            updatedGame = updatedGame.copy(
                                phase = TurnPhase.END_TURN,
                                infoMessage = "Fin de la phase d'achat"
                            )
                        } else {
                            updatedGame = updatedGame.copy(
                                phase = TurnPhase.SHOP_RESOLUTION,
                                infoMessage = "Phase d'achat en cours"
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------
            // END_TURN
            // -------------------------------------------------
            TurnPhase.END_TURN -> {

                val cleanedGame = clearShopStateForNewRound(
                    game.copy(
                        turnNumber = game.turnNumber + 1,
                        playerReady = false,
                        opponentReady = true,

                        lastPlayerCard = null,
                        lastOpponentCard = null,
                        playerDisplayedTurnCard = null,
                        opponentDisplayedTurnCard = null,

                        delayedEffects = game.delayedEffects.filter {
                            it.timing != DelayedEffectTiming.END_TURN
                        },

                        pendingChoice = null,
                        playerAmbassadriceTurnStartHp = null,
                        opponentAmbassadriceTurnStartHp = null,
                        playerPaladinProtectionActive = false,
                        opponentPaladinProtectionActive = false,
                        combatDamageToPlayer = 0,
                        combatDamageToOpponent = 0
                    )
                )

                updatedGame = enterPhase(
                    game = cleanedGame,
                    phase = TurnPhase.AMBUSH_BEFORE_REVEAL
                )
            }

            // -------------------------------------------------
            // AMBUSH WINDOWS
            // -------------------------------------------------
            TurnPhase.AMBUSH_BEFORE_REVEAL,
            TurnPhase.AMBUSH_BEFORE_EFFECTS,
            TurnPhase.AMBUSH_BEFORE_COMBAT,
            TurnPhase.AMBUSH_BEFORE_POST_COMBAT,
            TurnPhase.AMBUSH_BEFORE_SHOP -> {

                val deadlineExpired = game.phaseDeadlineAtMillis?.let {
                    System.currentTimeMillis() >= it
                } ?: false

                updatedGame = when {
                    // Tant que le délai n'est pas écoulé,
                    // on conserve la fenêtre tactique et ses choix.
                    !deadlineExpired -> {
                        game
                    }

                    // Le délai de déclaration ou de résolution a expiré.
                    // Le passage annule également un éventuel choix inachevé.
                    game.currentAmbushActor != null -> {
                        resolveAiAmbushActionsIfNeeded(
                            passAmbushWindowInternal(
                                game = game,
                                owner = game.currentAmbushActor
                            )
                        )
                    }

                    // Aucun joueur ne pouvait agir :
                    // les 0,5 seconde d'affichage sont écoulées.
                    else -> {
                        advancePastAmbushWindow(game)
                    }
                }
            }

        }

        games[gameId] = updatedGame
        updatedGame
    }


    // =========================================================
    // 9. CHOIX INTERACTIFS
    // =========================================================
    private fun resolveRunePendingChoice(
        game: GameState,
        choice: String
    ): GameState? {
        val pendingChoice = game.pendingChoice ?: return null

        if (pendingChoice.type != "RUNE_DESTROY_OPPONENT_AMBUSH") {
            return null
        }

        val owner = pendingChoice.owner

        val targetSide = if (owner == ChoiceOwner.PLAYER) {
            CardDestroyManager.Side.OPPONENT
        } else {
            CardDestroyManager.Side.PLAYER
        }

        if (!choice.startsWith("AMBUSH:")) {
            return game.copy(infoMessage = "Choix invalide")
        }

        val index = choice.removePrefix("AMBUSH:").toIntOrNull()
            ?: return game.copy(infoMessage = "Choix invalide")

// Sécurité : le choix doit faire partie des options proposées.
        if ("AMBUSH:$index" !in pendingChoice.options) {
            return game.copy(infoMessage = "Choix invalide")
        }

        val result = CardDestroyManager.destroyAt(
            game = game,
            owner = targetSide,
            zone = CardDestroyManager.Zone.AMBUSH,
            index = index,
            cause = CardDestroyManager.Cause.OPPONENT_EFFECT
        )

        if (!result.destroyed) {
            return resumeAmbushAfterPendingChoice(
                game = result.game.copy(
                    pendingChoice = null,
                    infoMessage = "Destruction impossible : ${result.failure}"
                ),
                owner = owner
            )
        }

        return resumeAmbushAfterPendingChoice(
            game = result.game.copy(
                pendingChoice = null,
                infoMessage = null
            ),
            owner = owner
        )
    }

    fun resolvePendingChoice(
        gameId: String,
        choice: String
    ): GameState? = withGameLock(gameId) {

        val game = games[gameId] ?: return@withGameLock null


        // Sécurité des choix interactifs pendant une embuscade.
        // Le délai est celui démarré à l'ouverture du menu Action :
        // aucun nouveau délai n'est accordé pour un pendingChoice.
        if (
            isAmbushPhase(game.phase) &&
            game.ambushActionMenuOpen &&
            game.pendingChoice != null
        ) {
            val deadline = game.phaseDeadlineAtMillis

            if (
                deadline != null &&
                System.currentTimeMillis() >= deadline
            ) {
                // Le moteur traitera l'expiration et le passage
                // lors de sa prochaine résolution de phase.
                return@withGameLock game
            }
        }


        val runeResolvedGame = resolveRunePendingChoice(
            game = game,
            choice = choice
        )

        if (runeResolvedGame != null) {
            games[gameId] = runeResolvedGame
            return@withGameLock runeResolvedGame
        }

        val activeScryBeforeResolution = game.activeScryState

        val pendingChoiceBeforeResolution = game.pendingChoice

        var updatedGame = CardEffectManager.resolvePendingChoice(
            game = game,
            choice = choice
        )

        // TRAPPEUR — reprise de la fenêtre tactique
        // uniquement lorsque le dernier choix est terminé.
        val trapperChoiceJustFinished =
            isAmbushPhase(game.phase) &&
                    pendingChoiceBeforeResolution?.type in listOf(
                "TRAPPEUR_ACTION_CHOICE",
                "TRAPPEUR_COUNTER_CHOICE",
                "TRAPPEUR_DEBUSQUER_CHOICE"
            ) &&
                    updatedGame.pendingChoice == null

        if (trapperChoiceJustFinished) {
            updatedGame = resumeAmbushAfterPendingChoice(
                game = updatedGame,
                owner = pendingChoiceBeforeResolution!!.owner
            )
        }

        val ambushScryJustFinished =
            activeScryBeforeResolution?.completionContext == ScryCompletionContext.AMBUSH &&
                    updatedGame.activeScryState == null &&
                    updatedGame.pendingChoice == null

        if (ambushScryJustFinished && !trapperChoiceJustFinished) {
            updatedGame = resumeAmbushAfterPendingChoice(
                game = updatedGame,
                owner = activeScryBeforeResolution.resolver
            )
        }

        games[gameId] = updatedGame
        updatedGame
    }

    // =========================================================
    // 10. AUTO-ADVANCE JUSQU’AU PROCHAIN POINT DE DÉCISION
    // =========================================================

    fun isPlayerDecisionPoint(game: GameState): Boolean {
        if (game.isFinished) return true
        if (game.pendingChoice != null) return true

        return when (game.phase) {
            TurnPhase.PRE_START -> true
            TurnPhase.SHOP_RESOLUTION -> true
            TurnPhase.END_TURN -> true

            TurnPhase.AMBUSH_BEFORE_REVEAL,
            TurnPhase.AMBUSH_BEFORE_EFFECTS,
            TurnPhase.AMBUSH_BEFORE_COMBAT,
            TurnPhase.AMBUSH_BEFORE_POST_COMBAT,
            TurnPhase.AMBUSH_BEFORE_SHOP ->
                game.currentAmbushActor != null

            TurnPhase.REVEAL,
            TurnPhase.EFFECTS,
            TurnPhase.COMBAT,
            TurnPhase.POST_COMBAT,
            TurnPhase.COMBAT_CLEANUP -> false
        }
    }

    fun advanceUntilDecision(
        gameId: String,
        isPlayer: Boolean = true
    ): GameState? = withGameLock(gameId) {
        var game = games[gameId] ?: return@withGameLock null

        var safetyCounter = 0

            if (
                isAmbushPhase(game.phase) &&
                isPlayerDecisionPoint(game) &&
                !canResolveCurrentPhase(game)
            ) {
                return@withGameLock game
            }

            if (
                isPvpMode(game) &&
                isPlayerDecisionPoint(game) &&
                !isAmbushPhase(game.phase)
            ) {
            val markedGame = if (isPlayer) {
                game.copy(
                    playerAdvanceReady = true,
                    infoMessage = "En attente de l'autre joueur"
                )
            } else {
                game.copy(
                    opponentAdvanceReady = true,
                    infoMessage = "En attente de l'autre joueur"
                )
            }

            // Si un seul des deux joueurs a validé, on s'arrête ici.
            if (!markedGame.playerAdvanceReady || !markedGame.opponentAdvanceReady) {
                games[gameId] = markedGame
                return@withGameLock markedGame
            }

            // Les deux joueurs ont validé : on réinitialise l'attente
            // puis on laisse le moteur avancer réellement.
            game = markedGame.copy(
                playerAdvanceReady = false,
                opponentAdvanceReady = false,
                playerReady = true,
                opponentReady = true,
                infoMessage = null
            )

            games[gameId] = game
        }

        if (isPlayerDecisionPoint(game)) {
            val next = advanceTrainingGame(gameId) ?: return@withGameLock null
            game = next
        }

        while (!isPlayerDecisionPoint(game)) {

            // Une phase chronométrée est actuellement ouverte :
            // le serveur attend simplement sa deadline au lieu
            // d'appeler le moteur en boucle inutilement.
            if (isWaitingForPhaseDeadline(game)) {
                break
            }

            val next = advanceTrainingGame(gameId) ?: return@withGameLock null
            game = next

            safetyCounter++

            if (safetyCounter > 20) {
                break
            }
        }

        game
    }

    //===========================================================
    //======== Forfait ==========================================
    //===========================================================
    fun forfeitGame(
        gameId: String,
        forfeitingUserId: Int
    ): GameState? = withGameLock(gameId) {

        val game = games[gameId] ?: return@withGameLock null

        if (game.isFinished) {
            return@withGameLock game
        }

        val result = when (forfeitingUserId) {
            game.playerUserId -> "LOSS"
            game.opponentUserId -> "WIN"
            else -> "LOSS"
        }

        val finishedGame = game.copy(
            isFinished = true,
            result = result,
            phase = TurnPhase.POST_COMBAT,
            pendingChoice = null,
            activeScryState = null,
            infoMessage = "Partie terminée par abandon"
        )

        val recordedGame = recordDuelResultIfNeeded(finishedGame)

        games[gameId] = recordedGame
        recordedGame
    }
}