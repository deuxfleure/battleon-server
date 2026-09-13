package com.battleon.solo.chapter2

import com.battleon.CardId
import com.battleon.solo.FixedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloMissionReward
import com.battleon.solo.SoloShopDefinition

object C2M22 : SoloMissionDefinition {

    override val id: String = "c2_m22"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c2_m22.standard",
            aiType = SoloAiType.STANDARD,

            playerHp = 20,
            opponentHp = 16,

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
                CardId.WARRIOR,
                CardId.WARRIOR,
                CardId.CURSED,
                CardId.COLLECTOR
            ),

            shopDefinition = SoloShopDefinition(
                slots = listOf(
                    FixedShopCard(CardId.COSTAUD),
                    FixedShopCard(CardId.ECUMEDESMERS),
                    FixedShopCard(CardId.AGILE),
                    FixedShopCard(CardId.TIQUE),
                    FixedShopCard(CardId.EPINENOIRE),
                    FixedShopCard(CardId.ROI),
                    FixedShopCard(CardId.EVEQUE),
                    FixedShopCard(CardId.DEVOREURDESPOIRS),
                    FixedShopCard(CardId.MAUVAISGENIE),
                    FixedShopCard(CardId.PORTEURDEGIDEDECHU)
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
            opponentNameKey = "opponent.c2_m22.standard_hard"
        )
    }
}