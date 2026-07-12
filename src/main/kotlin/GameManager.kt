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

object GameManager {

    // =========================================================
    // 1. STOCKAGE DES PARTIES EN MÉMOIRE
    // =========================================================

    private val games = ConcurrentHashMap<String, GameState>()

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

        val forcedCardIsOwned = ownedCardIds.contains(forcedCardId)

        val forcedCardIsEligible = forcedEnumCardId != null &&
                forcedCardIsOwned &&
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

        val shopEntries = createSoloShop(config)

        val game = GameState(
            gameId = UUID.randomUUID().toString(),
            mode = "SOLO",
            soloMissionId = config.missionId,
            soloDifficulty = config.difficulty.name,

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

    fun updateGame(game: GameState) {
        games[game.gameId] = game
    }

    // =========================================================
    // 5. HELPERS GÉNÉRAUX
    // =========================================================

    private fun isPvpMode(game: GameState): Boolean {
        return game.mode == "DUEL" || game.mode == "SEASON" || game.mode == "RANKED"
    }

    private fun isSeasonMercenary(card: Card): Boolean {
        return card.faction == CardFaction.MERCENARY
    }

    fun heartbeat(
        gameId: String,
        userId: Int
    ): GameState? {
        val game = games[gameId] ?: return null

        if (game.isFinished) return game

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
        return updatedGame
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
    ): GameState? {
        val game = games[gameId] ?: return null

        if (game.isFinished) return game
        if (!shouldRecordCompetitiveResult(game)) return game

        val now = System.currentTimeMillis()

        val isCheckingPlayer = checkingUserId == game.playerUserId
        val isCheckingOpponent = checkingUserId == game.opponentUserId

        if (!isCheckingPlayer && !isCheckingOpponent) {
            return game.copy(infoMessage = "Joueur non autorisé pour cette partie")
        }

        val opponentLastSeen = if (isCheckingPlayer) {
            game.opponentLastSeenAtMillis
        } else {
            game.playerLastSeenAtMillis
        }

        if (opponentLastSeen == null) {
            return game
        }

        val elapsed = now - opponentLastSeen

        if (elapsed < FORFEIT_TIMEOUT_MILLIS) {
            return game
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
        return recordedGame
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

        when (difficulty) {
            SoloMissionDifficulty.CAMPAIGN -> {
                val rewardRecorded = SoloProgressService.completeCampaignAndClaimReward(
                    userId = playerUserId,
                    missionId = missionId,
                    reward = config.reward
                )

                if (!rewardRecorded) {
                    return game.copy(
                        infoMessage = "Impossible d’enregistrer la progression Solo."
                    )
                }
            }

            SoloMissionDifficulty.HARD -> {
                // À coder quand on branchera les récompenses hardmode.
            }
        }

        return game.copy(resultRecorded = true)
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

            game.playerGold > game.opponentGold -> true
            game.opponentGold > game.playerGold -> false

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
            attackerHasBrute = playerHasBrute
        )

        val opponentDamage = CardEffectManager.getCombatDamageDealt(
            attacker = opponentCard,
            defender = playerCard,
            attackerPower = opponentPower,
            defenderPower = playerPower,
            nextCardDamageBonus = game.opponentCurrentCardDamageBonus,
            attackerHasBrute = opponentHasBrute
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

        val owner = if (isPlayer) ChoiceOwner.PLAYER else ChoiceOwner.OPPONENT

        val discardAfterPurchase = if (isPlayer) {
            afterPurchase.playerDiscard
        } else {
            afterPurchase.opponentDiscard
        }

        val hasValidDestroyTarget = discardAfterPurchase.any { it.id != CardId.SENTINELLE }

        return if (
            entry.card.id == CardId.SENTINELLE &&
            hasValidDestroyTarget
        ) {
            afterPurchase.copy(
                pendingChoice = CardEffectManager.buildSentinelleBuyDestroyPendingChoice(
                    game = afterPurchase,
                    owner = owner
                ),
                infoMessage = null
            )
        } else {
            afterPurchase
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
    ): GameState? {
        val game = games[gameId] ?: return null

        val shopEntry = game.shopEntries.find { it.card.id.name == cardId }
        val card = shopEntry?.card

        if (card == null) {
            return game.copy(
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
            return updatedGame
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
            return updatedGame
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
        return updatedGame
    }

    fun passShop(gameId: String, isPlayer: Boolean): GameState? {
        val game = games[gameId] ?: return null

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
        return updatedGame
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
    ): GameState? {
        var game = games[gameId] ?: return null

        if (game.isFinished) {
            return game
        }

        var updatedGame = game.copy(infoMessage = null)

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
                    readyGame.copy(
                        turnNumber = 1,
                        phase = TurnPhase.REVEAL,
                        infoMessage = null
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

                var newPlayerGold = game.playerGold
                var newOpponentGold = game.opponentGold

                if (playerDeck.isEmpty() && playerDiscard.isNotEmpty()) {
                    playerDeck = playerDiscard.shuffled()
                    playerDiscard = emptyList()
                    newPlayerGold += 1
                }

                if (opponentDeck.isEmpty() && opponentDiscard.isNotEmpty()) {
                    opponentDeck = opponentDiscard.shuffled()
                    opponentDiscard = emptyList()
                    newOpponentGold += 1
                }

                val playerCard = playerDeck.firstOrNull()
                val opponentCard = opponentDeck.firstOrNull()

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
                    game.copy(
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
                    game.copy(
                        playerDeck = playerDeck.drop(1),
                        opponentDeck = opponentDeck.drop(1),
                        playerDiscard = playerDiscard,
                        opponentDiscard = opponentDiscard,
                        playerGold = newPlayerGold,
                        opponentGold = newOpponentGold,
                        lastPlayerCard = playerCard,
                        lastOpponentCard = opponentCard,
                        playerDisplayedTurnCard = playerCard,
                        opponentDisplayedTurnCard = opponentCard,
                        playerAmbassadriceTurnStartHp = game.playerHp,
                        opponentAmbassadriceTurnStartHp = game.opponentHp,

                        playerCurrentCardPowerBonus = game.playerNextCardPowerBonus + playerPredictionPowerBonus,
                        opponentCurrentCardPowerBonus = game.opponentNextCardPowerBonus + opponentPredictionPowerBonus,

                        playerCurrentCardDamageBonus = game.playerNextCardDamageBonus,
                        opponentCurrentCardDamageBonus = game.opponentNextCardDamageBonus,

                        playerCurrentCardHasBruteBonus = game.playerNextCardHasBrute,
                        opponentCurrentCardHasBruteBonus = game.opponentNextCardHasBrute,

                        playerNextCardPowerBonus = 0,
                        opponentNextCardPowerBonus = 0,

                        playerNextCardDamageBonus = 0,
                        opponentNextCardDamageBonus = 0,

                        playerNextCardHasBrute = false,
                        opponentNextCardHasBrute = false,

                        // L'effet de prédiction ne dure qu'une seule révélation.
                        // Bonne ou mauvaise prédiction : il disparaît après cette révélation.
                        playerNextRevealPredictionEffect = null,
                        opponentNextRevealPredictionEffect = null,

                        playerEffectResolved = false,
                        opponentEffectResolved = false,

                        playerEffectBlockedBySentinelle = false,
                        opponentEffectBlockedBySentinelle = false,

                        playerPostCombatSacrificeHandled = false,
                        opponentPostCombatSacrificeHandled = false,

                        phase = TurnPhase.EFFECTS,
                        infoMessage = null
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
                        playerHasPrems = false,
                        opponentHasPrems = false,
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

                    fun doesPlayerSentinelleBlockOpponentEffect(): Boolean {
                        if (playerCard.id != CardId.SENTINELLE) return false

                        return when {
                            opponentEffectivePower > playerEffectivePower -> true
                            opponentEffectivePower < playerEffectivePower -> false

                            // égalité parfaite : on regarde qui agit en premier
                            else -> resolutionOrder != ResolutionOrder.OPPONENT_FIRST
                        }
                    }

                    fun doesOpponentSentinelleBlockPlayerEffect(): Boolean {
                        if (opponentCard.id != CardId.SENTINELLE) return false

                        return when {
                            playerEffectivePower > opponentEffectivePower -> true
                            playerEffectivePower < opponentEffectivePower -> false

                            // égalité parfaite : on regarde qui agit en premier
                            else -> resolutionOrder != ResolutionOrder.PLAYER_FIRST
                        }
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

                    when (resolutionOrder) {
                        ResolutionOrder.PLAYER_FIRST -> {
                            if (!workingGame.playerEffectResolved) {
                                if (doesOpponentSentinelleBlockPlayerEffect()) {
                                    workingGame = workingGame.copy(
                                        playerEffectResolved = true,
                                        playerEffectBlockedBySentinelle = true,
                                        infoMessage = null
                                    )
                                } else {
                                    workingGame = applyPlayerEffect(workingGame)

                                    if (workingGame.pendingChoice != null) {
                                        workingGame = resolveAiPendingChoiceIfNeeded(workingGame)

                                        if (workingGame.pendingChoice != null) {
                                            updatedGame = workingGame
                                            games[gameId] = updatedGame
                                            return updatedGame
                                        }
                                    }

                                    workingGame = workingGame.copy(playerEffectResolved = true)
                                }
                            }

                            if (!workingGame.opponentEffectResolved) {
                                if (doesPlayerSentinelleBlockOpponentEffect()) {
                                    workingGame = workingGame.copy(
                                        opponentEffectResolved = true,
                                        opponentEffectBlockedBySentinelle = true,
                                        infoMessage = null
                                    )
                                } else {
                                    workingGame = applyOpponentEffect(workingGame)

                                    if (workingGame.pendingChoice != null) {
                                        workingGame = resolveAiPendingChoiceIfNeeded(workingGame)

                                        if (workingGame.pendingChoice != null) {
                                            updatedGame = workingGame
                                            games[gameId] = updatedGame
                                            return updatedGame
                                        }
                                    }

                                    workingGame = workingGame.copy(opponentEffectResolved = true)
                                }
                            }
                        }

                        ResolutionOrder.OPPONENT_FIRST -> {
                            if (!workingGame.opponentEffectResolved) {
                                if (doesPlayerSentinelleBlockOpponentEffect()) {
                                    workingGame = workingGame.copy(
                                        opponentEffectResolved = true,
                                        opponentEffectBlockedBySentinelle = true,
                                        infoMessage = null
                                    )
                                } else {
                                    workingGame = applyOpponentEffect(workingGame)

                                    if (workingGame.pendingChoice != null) {
                                        workingGame = resolveAiPendingChoiceIfNeeded(workingGame)

                                        if (workingGame.pendingChoice != null) {
                                            updatedGame = workingGame
                                            games[gameId] = updatedGame
                                            return updatedGame
                                        }
                                    }

                                    workingGame = workingGame.copy(opponentEffectResolved = true)
                                }
                            }

                            if (!workingGame.playerEffectResolved) {
                                if (doesOpponentSentinelleBlockPlayerEffect()) {
                                    workingGame = workingGame.copy(
                                        playerEffectResolved = true,
                                        playerEffectBlockedBySentinelle = true,
                                        infoMessage = null
                                    )
                                } else {
                                    workingGame = applyPlayerEffect(workingGame)

                                    if (workingGame.pendingChoice != null) {
                                        workingGame = resolveAiPendingChoiceIfNeeded(workingGame)

                                        if (workingGame.pendingChoice != null) {
                                            updatedGame = workingGame
                                            games[gameId] = updatedGame
                                            return updatedGame
                                        }
                                    }

                                    workingGame = workingGame.copy(playerEffectResolved = true)
                                }
                            }
                        }
                    }

                    updatedGame = if (
                        workingGame.playerEffectResolved &&
                        workingGame.opponentEffectResolved &&
                        workingGame.pendingChoice == null
                    ) {
                        workingGame.copy(
                            phase = TurnPhase.COMBAT,
                            infoMessage = null
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
                } else {
                    var newPlayerHp = game.playerHp
                    var newOpponentHp = game.opponentHp

                    val playerHasBrute =
                        !game.playerEffectBlockedBySentinelle &&
                                CardEffectManager.hasBrute(
                                    card = playerCard,
                                    nextCardHasBrute = game.playerCurrentCardHasBruteBonus
                                )

                    val opponentHasBrute =
                        !game.opponentEffectBlockedBySentinelle &&
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

                    val playerDamageDealt = CardEffectManager.getCombatDamageDealt(
                        attacker = playerCard,
                        defender = opponentCard,
                        attackerPower = playerEffectivePower,
                        defenderPower = opponentEffectivePower,
                        nextCardDamageBonus = game.playerCurrentCardDamageBonus,
                        attackerHasBrute = playerHasBrute
                    )

                    val opponentDamageDealt = CardEffectManager.getCombatDamageDealt(
                        attacker = opponentCard,
                        defender = playerCard,
                        attackerPower = opponentEffectivePower,
                        defenderPower = playerEffectivePower,
                        nextCardDamageBonus = game.opponentCurrentCardDamageBonus,
                        attackerHasBrute = opponentHasBrute
                    )

                    newOpponentHp = maxOf(0, newOpponentHp - playerDamageDealt)
                    newPlayerHp = maxOf(0, newPlayerHp - opponentDamageDealt)

                    updatedGame = game.copy(
                        playerHp = newPlayerHp,
                        opponentHp = newOpponentHp,
                        combatDamageToPlayer = opponentDamageDealt,
                        combatDamageToOpponent = playerDamageDealt,

                        playerEffectivePower = playerEffectivePower,
                        opponentEffectivePower = opponentEffectivePower,

                        phase = TurnPhase.POST_COMBAT,
                        infoMessage = null
                    )

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
                    workingGame = PostCombatEffectResolver.apply(workingGame)

                    if (workingGame.pendingChoice != null) {
                        workingGame = resolveAiPendingChoiceIfNeeded(workingGame)

                        if (workingGame.pendingChoice != null) {
                            updatedGame = workingGame.copy(
                                phase = TurnPhase.POST_COMBAT
                            )
                            games[gameId] = updatedGame
                            return updatedGame
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

                            playerCurrentCardHasBruteBonus = false,
                            opponentCurrentCardHasBruteBonus = false
                        )
                        val playerHasAmbassadriceInDiscard =
                            workingGame.playerDiscard.any { it.id == CardId.AMBASSADRICE }

                        val opponentHasAmbassadriceInDiscard =
                            workingGame.opponentDiscard.any { it.id == CardId.AMBASSADRICE }

                        val protectedPlayerHp = if (
                            playerHasAmbassadriceInDiscard &&
                            workingGame.playerAmbassadriceTurnStartHp != null
                        ) {
                            maxOf(
                                workingGame.playerHp,
                                workingGame.playerAmbassadriceTurnStartHp - 1
                            )
                        } else {
                            workingGame.playerHp
                        }

                        val protectedOpponentHp = if (
                            opponentHasAmbassadriceInDiscard &&
                            workingGame.opponentAmbassadriceTurnStartHp != null
                        ) {
                            maxOf(
                                workingGame.opponentHp,
                                workingGame.opponentAmbassadriceTurnStartHp - 1
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

                            // ===============================
                            // DÉFAUSSE DES CARTES JOUÉES
                            // ===============================
                            val destroyPlayerRevealedCard =
                                workingGame.delayedEffects.any {
                                    it.timing == DelayedEffectTiming.END_TURN &&
                                            it.type == DelayedEffectType.DESTROY_REVEALED_CARD &&
                                            it.target == DelayedEffectTarget.PLAYER
                                }

                            val destroyOpponentRevealedCard =
                                workingGame.delayedEffects.any {
                                    it.timing == DelayedEffectTiming.END_TURN &&
                                            it.type == DelayedEffectType.DESTROY_REVEALED_CARD &&
                                            it.target == DelayedEffectTarget.OPPONENT
                                }

                            val newPlayerDiscard =
                                if (workingGame.lastPlayerCard != null && !destroyPlayerRevealedCard) {
                                    workingGame.playerDiscard + workingGame.lastPlayerCard
                                } else {
                                    workingGame.playerDiscard
                                }

                            val newOpponentDiscard =
                                if (workingGame.lastOpponentCard != null && !destroyOpponentRevealedCard) {
                                    workingGame.opponentDiscard + workingGame.lastOpponentCard
                                } else {
                                    workingGame.opponentDiscard
                                }

                            val cleanedGame = workingGame.copy(
                                playerDiscard = newPlayerDiscard,
                                opponentDiscard = newOpponentDiscard,
                                lastPlayerCard = null,
                                lastOpponentCard = null,
                                delayedEffects = workingGame.delayedEffects.filter {
                                    it.timing != DelayedEffectTiming.END_TURN
                                }
                            )

                            startShopResolution(
                                cleanedGame.copy(
                                    isFinished = false,
                                    result = null
                                )
                            )
                        }
                    }
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
                                return updatedGame
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
                var destroyPlayerCard = false
                var destroyOpponentCard = false

                for (effect in game.delayedEffects) {
                    if (effect.timing == DelayedEffectTiming.END_TURN) {
                        when (effect.type) {
                            DelayedEffectType.DESTROY_REVEALED_CARD -> {
                                when (effect.target) {
                                    DelayedEffectTarget.PLAYER -> destroyPlayerCard = true
                                    DelayedEffectTarget.OPPONENT -> destroyOpponentCard = true
                                }
                            }
                        }
                    }
                }

                val canDestroyPlayerRevealedCard =
                    destroyPlayerCard &&
                            game.lastPlayerCard != null &&
                            (game.playerDeck.size + game.playerDiscard.size + 1) > 5

                val canDestroyOpponentRevealedCard =
                    destroyOpponentCard &&
                            game.lastOpponentCard != null &&
                            (game.opponentDeck.size + game.opponentDiscard.size + 1) > 5

                val playerDiscard = if (game.lastPlayerCard != null && !canDestroyPlayerRevealedCard) {
                    game.playerDiscard + game.lastPlayerCard
                } else {
                    game.playerDiscard
                }

                val opponentDiscard = if (game.lastOpponentCard != null && !canDestroyOpponentRevealedCard) {
                    game.opponentDiscard + game.lastOpponentCard
                } else {
                    game.opponentDiscard
                }

                updatedGame = clearShopStateForNewRound(
                    game.copy(
                        turnNumber = game.turnNumber + 1,
                        playerReady = false,
                        opponentReady = true,
                        playerDiscard = playerDiscard,
                        opponentDiscard = opponentDiscard,
                        lastPlayerCard = null,
                        lastOpponentCard = null,
                        playerDisplayedTurnCard = null,
                        opponentDisplayedTurnCard = null,
                        delayedEffects = game.delayedEffects.filter { it.timing != DelayedEffectTiming.END_TURN },
                        pendingChoice = null,
                        playerAmbassadriceTurnStartHp = null,
                        opponentAmbassadriceTurnStartHp = null,
                        infoMessage = null,
                        combatDamageToPlayer = 0,
                        combatDamageToOpponent = 0,
                        phase = TurnPhase.AMBUSH_BEFORE_REVEAL
                    )
                )
            }

            // -------------------------------------------------
            // AMBUSH WINDOWS
            // -------------------------------------------------
            TurnPhase.AMBUSH_BEFORE_REVEAL -> {
                updatedGame = game.copy(
                    phase = TurnPhase.REVEAL,
                    infoMessage = null
                )
            }

            TurnPhase.AMBUSH_BEFORE_EFFECTS -> {
                updatedGame = game.copy(
                    phase = TurnPhase.EFFECTS,
                    infoMessage = null
                )
            }

            TurnPhase.AMBUSH_BEFORE_COMBAT -> {
                updatedGame = game.copy(
                    phase = TurnPhase.COMBAT,
                    infoMessage = null
                )
            }
        }

        games[gameId] = updatedGame
        return updatedGame
    }

    // =========================================================
    // 9. CHOIX INTERACTIFS
    // =========================================================

    fun resolvePendingChoice(gameId: String, choice: String): GameState? {
        val game = games[gameId] ?: return null
        val updatedGame = CardEffectManager.resolvePendingChoice(game, choice)
        games[gameId] = updatedGame
        return updatedGame
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
            TurnPhase.REVEAL,
            TurnPhase.EFFECTS,
            TurnPhase.COMBAT,
            TurnPhase.POST_COMBAT -> false
        }
    }

    fun advanceUntilDecision(
        gameId: String,
        isPlayer: Boolean = true
    ): GameState? {
        var game = games[gameId] ?: return null
        var safetyCounter = 0

        if (isPvpMode(game) && isPlayerDecisionPoint(game)) {
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
                return markedGame
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
            val next = advanceTrainingGame(gameId) ?: return null
            game = next
        }

        while (!isPlayerDecisionPoint(game)) {
            val next = advanceTrainingGame(gameId) ?: return null
            game = next

            safetyCounter++

            if (safetyCounter > 20) {
                break
            }
        }

        return game
    }

    //===========================================================
    //======== Forfait ==========================================
    //===========================================================
    fun forfeitGame(
        gameId: String,
        forfeitingUserId: Int
    ): GameState? {
        val game = games[gameId] ?: return null

        if (game.isFinished) {
            return game
        }

        val result = when (forfeitingUserId) {
            game.playerUserId -> "LOSS" // player abandonne
            game.opponentUserId -> "WIN" // opponent abandonne, donc player gagne
            else -> "LOSS" // fallback (training)
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
        return recordedGame
    }
}