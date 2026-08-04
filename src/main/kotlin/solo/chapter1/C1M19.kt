package com.battleon.solo.chapter1

import com.battleon.CardId
import com.battleon.solo.FixedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloMissionReward
import com.battleon.solo.SoloShopDefinition

object C1M19 : SoloMissionDefinition {

    override val id: String = "c1_m19"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c1_m19.guard_captain",
            aiType = SoloAiType.STANDARD,

            playerHp = 20,
            opponentHp = 15,

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
                CardId.DUC,
                CardId.COLLECTOR,
                CardId.HEALER,
                CardId.WARRIOR,
                CardId.HEALER
            ),

            shopDefinition = SoloShopDefinition(
                slots = listOf(
                    FixedShopCard(CardId.REVENDEUR),
                    FixedShopCard(CardId.MAGE),
                    FixedShopCard(CardId.THERMOGUERRIER),
                    FixedShopCard(CardId.DEVINDELUMIERE),
                    FixedShopCard(CardId.TACTICIEN),
                    FixedShopCard(CardId.SANGPACTE),
                    FixedShopCard(CardId.DANSEUSEMACABRE),
                    FixedShopCard(CardId.EPINENOIRE),
                    FixedShopCard(CardId.BULLDOZER),
                    FixedShopCard(CardId.ROIBAMBOU)
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
            opponentNameKey = "opponent.c1_m19.guard_captain_hard"
        )
    }
}