package com.battleon

object AiPendingChoiceManager {

    fun chooseOption(game: GameState): String? {

        val pendingChoice = game.pendingChoice ?: return null

        return when (game.mode) {

            "TRAINING" -> {
                TrainingAiPendingChoiceResolver.chooseOption(
                    game = game,
                    pendingChoice = pendingChoice
                )
            }

            "SOLO" -> {
                ScenarioAiPendingChoiceResolver.chooseOption(
                    game = game,
                    pendingChoice = pendingChoice
                )
            }

            else -> null
        }
    }
}