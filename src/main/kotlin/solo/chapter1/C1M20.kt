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

object C1M20 : SoloMissionDefinition {

    override val id: String = "c1_m20"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c1_m20.duke",
            aiType = SoloAiType.STANDARD,

            playerHp = 20,
            opponentHp = 28,

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
                CardId.CURSED,
                CardId.EXPERIENCEDELABORATOIRE
            ),

            shopDefinition = SoloShopDefinition(
                slots = listOf(
                    FixedShopCard(CardId.REVENDEUR),
                    FixedShopCard(CardId.DUC),
                    FixedShopCard(CardId.SENTINELLE),
                    FixedShopCard(CardId.PYROMANCIEN),
                    FixedShopCard(CardId.NECROMANCIEN),
                    FixedShopCard(CardId.CHAMANE),
                    FixedShopCard(CardId.VIOLENT),
                    FixedShopCard(CardId.SANGUINAIRE),
                    PlayerSelectedShopCard(selectionIndex = 0),
                    PlayerSelectedShopCard(selectionIndex = 1)
                )
            ),

            selectedRuneIds = selectedRuneIds,
            selectedCardIds = selectedCardIds,

            reward = SoloMissionReward(
                gems = 500,
                runeIds = listOf("rune_major_2"),
                avatarIds = listOf("AVATAR_MALVARIS")
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
            opponentNameKey = "opponent.c1_m20.duke_hard"
        )
    }
}