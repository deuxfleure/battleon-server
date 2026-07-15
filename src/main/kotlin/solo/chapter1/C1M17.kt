package com.battleon.solo.chapter1

import com.battleon.CardId
import com.battleon.solo.FixedShopCard
import com.battleon.solo.PlayerSelectedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloMissionReward
import com.battleon.solo.SoloShopDefinition

object C1M17 : SoloMissionDefinition {

    override val id: String = "c1_m17"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c1_m17.duke_champion",
            aiType = SoloAiType.STANDARD,

            playerHp = 20,
            opponentHp = 25,

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
                CardId.COLLECTOR,
                CardId.COLLECTOR,
                CardId.WARRIOR,
                CardId.WARRIOR,
                CardId.CURSED
            ),

            shopDefinition = SoloShopDefinition(
                slots = listOf(
                    FixedShopCard(CardId.DUC),
                    FixedShopCard(CardId.THERMOGUERRIER),
                    FixedShopCard(CardId.MAGE),
                    FixedShopCard(CardId.DEVINDELUMIERE),
                    FixedShopCard(CardId.GRACIEUSEROBUSTE),
                    FixedShopCard(CardId.AGILE),
                    FixedShopCard(CardId.ROIBAMBOU),
                    FixedShopCard(CardId.SANGPACTE),
                    FixedShopCard(CardId.MAUVAISGENIE),
                    PlayerSelectedShopCard(selectionIndex = 0)
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
            opponentNameKey = "opponent.c1_m17.duke_champion_hard"
        )
    }
}