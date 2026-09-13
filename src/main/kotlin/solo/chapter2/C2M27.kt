package com.battleon.solo.chapter2

import com.battleon.CardId
import com.battleon.solo.PlayerSelectedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloMissionReward
import com.battleon.solo.SoloShopDefinition

object C2M27 : SoloMissionDefinition {

    override val id: String = "c2_m27"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c2_m27.normal",
            aiType = SoloAiType.STANDARD,

            playerHp = 20,
            opponentHp = 20,

            playerGold = 1,
            opponentGold = 1,

            playerStartingDeck = listOf(
                CardId.COLLECTOR,
                CardId.COLLECTOR,
                CardId.WARRIOR,
                CardId.CURSED,
                CardId.HEALER
            ),

            opponentStartingDeck = listOf(
                CardId.WARRIOR,
                CardId.COLLECTOR,
                CardId.COLLECTOR,
                CardId.CURSED,
                CardId.HEALER
            ),

            shopDefinition = SoloShopDefinition(
                slots = listOf(
                    PlayerSelectedShopCard(selectionIndex = 0),
                    PlayerSelectedShopCard(selectionIndex = 1),
                    PlayerSelectedShopCard(selectionIndex = 2),
                    PlayerSelectedShopCard(selectionIndex = 3),
                    PlayerSelectedShopCard(selectionIndex = 4),
                    PlayerSelectedShopCard(selectionIndex = 5),
                    PlayerSelectedShopCard(selectionIndex = 6),
                    PlayerSelectedShopCard(selectionIndex = 7),
                    PlayerSelectedShopCard(selectionIndex = 8),
                    PlayerSelectedShopCard(selectionIndex = 9)
                )
            ),

            selectedRuneIds = selectedRuneIds,
            selectedCardIds = selectedCardIds,

            reward = SoloMissionReward(
                gems = 200
            )
        )
    }

    override fun buildHardConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return buildCampaignConfig(
            selectedRuneIds = selectedRuneIds,
            selectedCardIds = selectedCardIds
        ).copy(
            difficulty = SoloMissionDifficulty.HARD,
            opponentNameKey = "opponent.c2_m26.hard"
        )
    }
}