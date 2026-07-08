package com.battleon.solo

import com.battleon.GameState

object SoloAiRouter {

    fun assignShopIntent(
        game: GameState,
        standardShopIntent: (GameState) -> GameState
    ): GameState {
        return when (game.soloMissionId) {
            "c1_m01" -> standardShopIntent(game)

            else -> standardShopIntent(game)
        }
    }
}