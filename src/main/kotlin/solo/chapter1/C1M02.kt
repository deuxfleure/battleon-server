package com.battleon.solo.chapter1

import com.battleon.CardId
import com.battleon.solo.EmptyShopSlot
import com.battleon.solo.FixedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloMissionReward
import com.battleon.solo.SoloShopDefinition

object C1M02 : SoloMissionDefinition {

    override val id: String = "c1_m02"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c1_m02.fanatics",
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
                    FixedShopCard(CardId.SANGPACTE),
                    FixedShopCard(CardId.PYROMANCIEN),
                    FixedShopCard(CardId.ROIBAMBOU),
                    FixedShopCard(CardId.DURACUIRE),
                    FixedShopCard(CardId.DEVINDELUMIERE),
                    FixedShopCard(CardId.THERMOGUERRIER),
                    EmptyShopSlot,
                    EmptyShopSlot,
                    EmptyShopSlot,
                    EmptyShopSlot
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
            opponentNameKey = "opponent.c1_m02.fanatics_hard"
        )
    }
}