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

object C1M03 : SoloMissionDefinition {

    override val id: String = "c1_m03"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c1_m03.fanatic",
            aiType = SoloAiType.STANDARD,

            playerHp = 15,
            opponentHp = 10,

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
                    FixedShopCard(CardId.AGILE),
                    FixedShopCard(CardId.SANGUINAIRE),
                    FixedShopCard(CardId.EPINENOIRE),
                    FixedShopCard(CardId.MAUVAISGENIE),
                    FixedShopCard(CardId.ROIDEMON),
                    FixedShopCard(CardId.DEVINDESTENEBRES),
                    FixedShopCard(CardId.DUC),
                    FixedShopCard(CardId.THERMOGUERRIER),
                    FixedShopCard(CardId.MAGE),
                    EmptyShopSlot
                )
            ),

            selectedRuneIds = selectedRuneIds,
            selectedCardIds = selectedCardIds,

            reward = SoloMissionReward(
                gems = 200,
                runeIds = listOf("rune_minor_1")
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
            opponentNameKey = "opponent.c1_m03.fanatic_hard"
        )
    }
}