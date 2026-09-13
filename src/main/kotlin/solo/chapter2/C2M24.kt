package com.battleon.solo.chapter2

import com.battleon.CardId
import com.battleon.TokenManager
import com.battleon.TokenStack
import com.battleon.solo.FixedShopCard
import com.battleon.solo.SoloAiType
import com.battleon.solo.SoloMissionDefinition
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloMissionGameConfig
import com.battleon.solo.SoloMissionReward
import com.battleon.solo.SoloShopDefinition

object C2M24 : SoloMissionDefinition {

    override val id: String = "c2_m24"

    override fun buildCampaignConfig(
        selectedRuneIds: List<String>,
        selectedCardIds: List<String>
    ): SoloMissionGameConfig {
        return SoloMissionGameConfig(
            missionId = id,
            difficulty = SoloMissionDifficulty.CAMPAIGN,
            opponentNameKey = "opponent.c2_m24.normal",
            aiType = SoloAiType.STANDARD,

            playerHp = 20,
            opponentHp = 20,

            playerGold = 1,
            opponentGold = 2,

            playerStartingTokens = listOf(
                TokenStack(
                    tokenId = TokenManager.TokenIds.POISON,
                    amount = 2
                )
            ),

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
                    FixedShopCard(CardId.ENCHAINE),
                    FixedShopCard(CardId.ECUMEDESMERS),
                    FixedShopCard(CardId.BULLDOZER),
                    FixedShopCard(CardId.REPRESAILLES),
                    FixedShopCard(CardId.GARDIENSACRE),
                    FixedShopCard(CardId.PORTEPAROLE),
                    FixedShopCard(CardId.DISCIPLEDELAFLAMME),
                    FixedShopCard(CardId.LAMEERRANTE),
                    FixedShopCard(CardId.ONIFLEAUDEGIVRE),
                    FixedShopCard(CardId.CONTAGIEUX)
                )
            ),

            selectedRuneIds = selectedRuneIds,
            selectedCardIds = selectedCardIds,

            reward = SoloMissionReward(
                gems = 200,
                runeIds = listOf("rune_minor_4")
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
            opponentNameKey = "opponent.c2_m24.hard"
        )
    }
}