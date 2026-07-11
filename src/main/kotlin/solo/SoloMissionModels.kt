package com.battleon.solo

import com.battleon.CardId
import kotlinx.serialization.Serializable


enum class SoloMissionDifficulty {
    CAMPAIGN,
    HARD
}

enum class SoloAiType {
    STANDARD
}

data class SoloMissionReward(
    val gems: Int = 0,
    val dust: Int = 0,
    val avatarIds: List<String> = emptyList(),
    val titleIds: List<String> = emptyList(),
    val skinIds: List<String> = emptyList(),
    val cardIds: List<CardId> = emptyList(),
    val runeIds: List<String> = emptyList()
)

data class SoloMissionGameConfig(
    val missionId: String,
    val difficulty: SoloMissionDifficulty,

    val opponentNameKey: String,
    val aiType: SoloAiType = SoloAiType.STANDARD,

    val playerHp: Int,
    val opponentHp: Int,

    val playerGold: Int = 1,
    val opponentGold: Int = 1,

    val playerStartingDeck: List<CardId>,
    val opponentStartingDeck: List<CardId>,

    val shopDefinition: SoloShopDefinition,

    val selectedRuneIds: List<String>,
    val selectedCardIds: List<String>,

    val reward: SoloMissionReward = SoloMissionReward(),
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

@Serializable
data class SoloMissionProgressResponse(
    val missionId: String,
    val campaignCompleted: Boolean,
    val campaignRewardClaimed: Boolean,
    val hardCompleted: Boolean,
    val hardRewardClaimed: Boolean
)