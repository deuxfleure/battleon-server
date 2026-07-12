package com.battleon

import kotlinx.serialization.Serializable

@Serializable
enum class TurnPhase {
    PRE_START,
    AMBUSH_BEFORE_REVEAL,
    REVEAL,
    AMBUSH_BEFORE_EFFECTS,
    EFFECTS,
    AMBUSH_BEFORE_COMBAT,
    COMBAT,
    POST_COMBAT,
    SHOP_RESOLUTION,
    END_TURN
}

@Serializable
data class TokenStack(
    val tokenId: String,
    val amount: Int
)

@Serializable
data class GameLogEntry(
    val turnNumber: Int,
    val key: String,
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class PendingChoice(
    val type: String,
    val cardId: String,
    val options: List<String>,
    val message: String,
    val owner: ChoiceOwner,

    // Utilisé par certains choix spéciaux, comme le Chaman,
    // pour afficher les cartes regardées sans les déplacer immédiatement.
    val previewOwnTopCard: Card? = null,
    val previewOpponentTopCard: Card? = null
)

@Serializable
data class NextRevealPredictionEffect(
    // Carte qui a créé cet effet (utile pour le futur)
    val sourceCardId: CardId,

    // Nom de la carte pariée par le joueur
    val predictedCardName: String,

    // Bonus de force à donner si la prédiction est correcte
    val powerBonusOnMatch: Int = 0
)

@Serializable
data class ScryState(
    // Carte source de l'effet
    val sourceCardId: String,

    // Qui résout l'effet
    val resolver: ChoiceOwner,

    // Quelle pile est ciblée
    // PLAYER = sa propre pile
    // OPPONENT = pile adverse
    val target: ChoiceOwner,

    // Nombre de cartes que l'on voulait regarder à l'origine
    val amount: Int,

    // Est-ce que les cartes scrutées peuvent être défaussées ?
    // true = Scruter / Scruter sombre
    // false = regarder et remettre uniquement
    val canDiscardViewedCards: Boolean = true,

    // Cartes encore en attente de traitement
    val revealedCards: List<Card>,

    // Cartes que le joueur a choisi de remettre sur le deck
    // IMPORTANT :
    // l'ordre de cette liste correspond à l'ordre des clics sur "Reposer"
    val cardsToReturnOnTop: List<Card> = emptyList(),

    // Cartes que le joueur a choisi de défausser
    val cardsToDiscard: List<Card> = emptyList(),

    // Carte actuellement sélectionnée par le joueur dans le scrutage
    val selectedCardIndex: Int? = null
)
@Serializable
data class GameState(

    // =========================================================
    // 1. IDENTITÉ DE LA PARTIE
    // =========================================================
    val gameId: String,
    val mode: String,
    val soloMissionId: String? = null,
    val soloDifficulty: String? = null,

    // =========================================================
    // 2. AVANCEMENT GLOBAL DE LA PARTIE
    // =========================================================
    val turnNumber: Int = 0,
    val phase: TurnPhase = TurnPhase.PRE_START,

    val isFinished: Boolean = false,
    val result: String? = null,
    val resultRecorded: Boolean = false,

    val soloRewardGranted: Boolean = false,

    val soloRewardGems: Int = 0,
    val soloRewardDust: Int = 0,

    val soloRewardRuneIds: List<String> = emptyList(),
    val soloRewardCardIds: List<String> = emptyList(),
    val soloRewardTitleIds: List<String> = emptyList(),
    val soloRewardAvatarIds: List<String> = emptyList(),
    val soloRewardSkinIds: List<String> = emptyList(),

    // =========================================================
    // 3. ÉTAT DE PRÉPARATION DES JOUEURS
    // =========================================================
    val playerReady: Boolean = false,
    val opponentReady: Boolean = false,

    // Utilisé en multijoueur pour savoir qui a cliqué sur "Phase suivante"
    val playerAdvanceReady: Boolean = false,
    val opponentAdvanceReady: Boolean = false,

    // variable de hearthbeat
    val playerLastSeenAtMillis: Long? = null,
    val opponentLastSeenAtMillis: Long? = null,

    // =========================================================
    // 4. IDENTITÉ DES JOUEURS
    // =========================================================
    val playerName: String,
    val opponentName: String,

    // Identifiants des vrais utilisateurs.
    // En mode TRAINING, opponentUserId reste null car l'adversaire est l'IA.
    val playerUserId: Int? = null,
    val opponentUserId: Int? = null,

    // =========================================================
    // 5. RESSOURCES PRINCIPALES
    // =========================================================
    val playerHp: Int,
    val opponentHp: Int,

    val playerGold: Int,
    val opponentGold: Int,

    val playerAmbassadriceTurnStartHp: Int? = null,
    val opponentAmbassadriceTurnStartHp: Int? = null,

    // =========================================================
    // 6. ZONES DE CARTES
    // =========================================================
    val playerDeck: List<Card>,
    val opponentDeck: List<Card>,

    val playerDiscard: List<Card>,
    val opponentDiscard: List<Card>,

    val playerAmbush: List<Card> = emptyList(),
    val opponentAmbush: List<Card> = emptyList(),

    // =========================================================
    // 7. JETONS
    // =========================================================
    val playerTokens: List<TokenStack> = emptyList(),
    val opponentTokens: List<TokenStack> = emptyList(),

    // =========================================================
    // 8. BONUS TEMPORAIRES "PROCHAINE CARTE"
    // =========================================================
    val playerNextCardPowerBonus: Int = 0,
    val opponentNextCardPowerBonus: Int = 0,

    val playerNextCardDamageBonus: Int = 0,
    val opponentNextCardDamageBonus: Int = 0,

    val playerNextCardHasBrute: Boolean = false,
    val opponentNextCardHasBrute: Boolean = false,

    // Bonus transférés depuis "prochaine carte" vers la carte actuellement révélée.
    // Ils sont consommés pendant ce tour, puis remis à zéro en POST_COMBAT.
    val playerCurrentCardPowerBonus: Int = 0,
    val opponentCurrentCardPowerBonus: Int = 0,

    val playerCurrentCardDamageBonus: Int = 0,
    val opponentCurrentCardDamageBonus: Int = 0,

    val playerCurrentCardHasBruteBonus: Boolean = false,
    val opponentCurrentCardHasBruteBonus: Boolean = false,

// =========================================================
// 8.b EFFETS DE PRÉDICTION SUR LA PROCHAINE RÉVÉLATION
// Exemple : Le Maître des Carnages parie sur le nom de la
// prochaine carte révélée. Si la prédiction est correcte,
// un bonus est appliqué.
// =========================================================
    val playerNextRevealPredictionEffect: NextRevealPredictionEffect? = null,
    val opponentNextRevealPredictionEffect: NextRevealPredictionEffect? = null,

    // =========================================================
    // 9. CARTES ACTUELLEMENT RÉVÉLÉES
    // =========================================================
    val lastPlayerCard: Card? = null,
    val lastOpponentCard: Card? = null,

    val playerDisplayedTurnCard: Card? = null,
    val opponentDisplayedTurnCard: Card? = null,

    // =========================================================
    // 9b. effet visuel
    // =========================================================

    val combatDamageToPlayer: Int = 0,
    val combatDamageToOpponent: Int = 0,

    val effectAnimationOrder: List<String> = emptyList(),

    // =========================================================
    // 10. EFFETS DIFFÉRÉS
    // =========================================================
    val delayedEffects: List<DelayedEffect> = emptyList(),

    // =========================================================
    // 11. SHOP : CONTENU DU MARCHÉ
    // =========================================================
    val shopEntries: List<ShopEntry> = emptyList(),

    // 11b. araignée disponible
    val playerAvailableSpiderCardIds: List<CardId> = emptyList(),
    val opponentAvailableSpiderCardIds: List<CardId> = emptyList(),

    // =========================================================
    // 12. SHOP : HISTORIQUE D’ACHATS
    // Sert pour les règles de type LIMITATION 1 / LIMITATION 2
    // On compte combien d’exemplaires ont été achetés dans la partie
    // même si les cartes ont ensuite été détruites.
    // =========================================================
    val playerPurchasedCardCounts: Map<String, Int> = emptyMap(),
    val opponentPurchasedCardCounts: Map<String, Int> = emptyMap(),

    // =========================================================
    // 12.b : mercenaires
    // =========================================================
    val seasonMercenaryDeck: List<CardId> = emptyList(),
    val playerBoughtSeasonMercenary: Boolean = false,
    val opponentBoughtSeasonMercenary: Boolean = false,

    // =========================================================
    // 13. SHOP : ACHATS EN ATTENTE
    // Carte actuellement demandée pour le prochain passage en shop
    // =========================================================
    val playerPendingShopPurchaseCardId: String? = null,
    val opponentPendingShopPurchaseCardId: String? = null,

    // =========================================================
    // 14. SHOP : STATUT DE LA DEMANDE D’ACHAT
    // Utilisé pour afficher au joueur si sa demande est :
    // - en attente
    // - invalide
    // - acceptée
    // - effectuée
    // =========================================================
    val playerShopStatus: ShopPurchaseStatus = ShopPurchaseStatus.NONE,
    val opponentShopStatus: ShopPurchaseStatus = ShopPurchaseStatus.NONE,

    // =========================================================
    // 15. SHOP : ORDRE ET ALTERNANCE D’ACHAT
    // shopPriorityPlayerFirst :
    //   indique qui commence la phase shop ce tour
    //
    // currentShopBuyerIsPlayer :
    //   indique à qui c’est le tour de faire son choix maintenant
    //
    // playerPassedShop / opponentPassedShop :
    //   indique si le joueur a décidé de ne plus acheter ce tour
    // =========================================================
    val shopPriorityPlayerFirst: Boolean? = null,
    val currentShopBuyerIsPlayer: Boolean? = null,

    val playerPassedShop: Boolean = false,
    val opponentPassedShop: Boolean = false,

    // =========================================================
    // 16. CHOIX INTERACTIFS
    // Sert pour les cartes à choix : Mauvais Génie, Mage, etc.
    // =========================================================
    val pendingChoice: PendingChoice? = null,
    val activeScryState: ScryState? = null,
    val playerEffectResolved: Boolean = false,
    val opponentEffectResolved: Boolean = false,

    // blockeur d'effet
    val playerEffectBlockedBySentinelle: Boolean = false,
    val opponentEffectBlockedBySentinelle: Boolean = false,

    // Sacrifice déjà proposé / résolu pendant le post-combat en cours
    val playerPostCombatSacrificeHandled: Boolean = false,
    val opponentPostCombatSacrificeHandled: Boolean = false,

    // =========================================================
    // 17. JOURNAL DE PARTIE
    // =========================================================
    val logEntries: List<GameLogEntry> = emptyList(),
    val playerEffectivePower: Int = 0,
    val opponentEffectivePower: Int = 0,

    // =========================================================
    // 18. MESSAGE D’INFO / DEBUG / ERREUR
    // Sert à renvoyer un message lisible au client
    // =========================================================
    val infoMessage: String? = null

)