package com.battleon.solo.chapter1

import com.battleon.CardId
import com.battleon.solo.FixedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloMissionReward
import com.battleon.solo.SoloShopDefinition

object C1M07 : SoloMissionDefinition {

    override val id: String = "c1_m07"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c1_m07.cultists",
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
                    FixedShopCard(CardId.ENVOUTEUSE),
                    FixedShopCard(CardId.DEVINDELUMIERE),
                    FixedShopCard(CardId.MAGE),
                    FixedShopCard(CardId.TACTICIEN),
                    FixedShopCard(CardId.SANGUINAIRE),
                    FixedShopCard(CardId.AGILE),
                    FixedShopCard(CardId.BULLDOZER),
                    FixedShopCard(CardId.NECROMANCIEN),
                    FixedShopCard(CardId.ROIDEMON),
                    FixedShopCard(CardId.DANSEUSEMACABRE)
                )
            ),

            selectedRuneIds = selectedRuneIds,
            selectedCardIds = selectedCardIds,

            reward = SoloMissionReward(
                gems = 200,
                runeIds = listOf("rune_minor_2")
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
            opponentNameKey = "opponent.c1_m07.cultists_hard"
        )
    }
}