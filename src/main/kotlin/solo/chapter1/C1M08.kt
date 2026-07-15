package com.battleon.solo.chapter1

import com.battleon.CardId
import com.battleon.solo.FixedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloMissionReward
import com.battleon.solo.SoloShopDefinition

object C1M08 : SoloMissionDefinition {

    override val id: String = "c1_m08"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c1_m08.council_guard",
            aiType = SoloAiType.STANDARD,

            playerHp = 20,
            opponentHp = 22,

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
                    FixedShopCard(CardId.MAGE),
                    FixedShopCard(CardId.DUC),
                    FixedShopCard(CardId.THERMOGUERRIER),
                    FixedShopCard(CardId.DEVINDELUMIERE),
                    FixedShopCard(CardId.PYROMANCIEN),
                    FixedShopCard(CardId.GARDIENDESENFERS),
                    FixedShopCard(CardId.MAUVAISGENIE),
                    FixedShopCard(CardId.AGILE),
                    FixedShopCard(CardId.CHAMANE),
                    FixedShopCard(CardId.EPINENOIRE)
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
            opponentNameKey = "opponent.c1_m08.council_guard_hard"
        )
    }
}