package com.battleon.solo.chapter1

import com.battleon.CardId
import com.battleon.solo.EmptyShopSlot
import com.battleon.solo.FixedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloShopDefinition

object C1M01 : SoloMissionDefinition {

    override val id: String = "c1_m01"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c1_m01.bandits",
            aiType = SoloAiType.STANDARD,

            playerHp = 10,
            opponentHp = 6,

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
                    FixedShopCard(CardId.SANGUINAIRE),
                    FixedShopCard(CardId.GRACIEUSEROBUSTE),
                    EmptyShopSlot,
                    EmptyShopSlot,
                    EmptyShopSlot,
                    EmptyShopSlot,
                    EmptyShopSlot,
                    EmptyShopSlot
                )
            ),

            selectedRuneIds = selectedRuneIds,
            selectedCardIds = selectedCardIds
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
            opponentNameKey = "opponent.c1_m01.bandits_hard",
            playerHp = 10,
            opponentHp = 12
        )
    }
}