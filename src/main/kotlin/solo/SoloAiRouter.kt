package com.battleon.solo

import com.battleon.GameState

object SoloAiRouter {

    fun assignShopIntent(
        game: GameState,
        standardShopIntent: (GameState) -> GameState
    ): GameState {
        val aiType = game.soloAiType
            ?.let { value ->
                runCatching {
                    SoloAiType.valueOf(value)
                }.getOrNull()
            }
            ?: SoloAiType.STANDARD

        return when (aiType) {

            SoloAiType.STANDARD -> {
                standardShopIntent(game)
            }

            SoloAiType.BARBARE_VIKING -> {
                // TODO : stratégie spécifique Barbare Viking.
                // En attendant, comportement identique à l'IA standard.
                standardShopIntent(game)
            }

            SoloAiType.MIRROR_PLAYER_PURCHASES -> {
                // TODO : stratégie "mêmes achats que le joueur".
                // En attendant, comportement identique à l'IA standard.
                standardShopIntent(game)
            }
        }
    }
}