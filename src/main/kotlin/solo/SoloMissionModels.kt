package com.battleon.solo

import com.battleon.CardId

enum class SoloMissionDifficulty {
    CAMPAIGN,
    HARD
}

data class SoloMissionGameConfig(
    val missionId: String,
    val difficulty: SoloMissionDifficulty,

    val opponentName: String,

    val playerHp: Int,
    val opponentHp: Int,

    val playerGold: Int = 1,
    val opponentGold: Int = 1,

    val playerStartingDeck: List<CardId>,
    val opponentStartingDeck: List<CardId>,

    val shopDefinition: SoloShopDefinition,

    val selectedRuneIds: List<String>,
    val selectedCardIds: List<String>
)

sealed interface SoloShopSlotDefinition

data class FixedShopCard(
    val cardId: CardId
) : SoloShopSlotDefinition

data class PlayerSelectedShopCard(
    val selectionIndex: Int
) : SoloShopSlotDefinition

data object EmptyShopSlot : SoloShopSlotDefinition

data class SoloShopDefinition(
    val slots: List<SoloShopSlotDefinition>,
    val copiesRemainingPerCard: Int = 4
)

interface SoloMissionDefinition {
    val id: String

    fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig

    fun buildHardConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig
}