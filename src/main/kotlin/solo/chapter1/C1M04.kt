package com.battleon.solo.chapter1

import com.battleon.CardId
import com.battleon.solo.FixedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloMissionReward
import com.battleon.solo.SoloShopDefinition

object C1M04 : SoloMissionDefinition {

    override val id: String = "c1_m04"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c1_m04.cultists",
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
                CardId.WARRIOR,
                CardId.COLLECTOR,
                CardId.COLLECTOR,
                CardId.CURSED,
                CardId.HEALER
            ),

            shopDefinition = SoloShopDefinition(
                slots = listOf(
                    FixedShopCard(CardId.DUC),
                    FixedShopCard(CardId.REVENDEUR),
                    FixedShopCard(CardId.BARBAREVIKING),
                    FixedShopCard(CardId.AMBASSADRICE),
                    FixedShopCard(CardId.SENTINELLE),
                    FixedShopCard(CardId.MAUVAISGENIE),
                    FixedShopCard(CardId.DANSEUSEMACABRE),
                    FixedShopCard(CardId.PYROMANCIEN),
                    FixedShopCard(CardId.EPINENOIRE),
                    FixedShopCard(CardId.GRACIEUSEROBUSTE)
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
            opponentNameKey = "opponent.c1_m04.cultists_hard"
        )
    }
}