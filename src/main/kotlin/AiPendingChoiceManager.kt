
package com.battleon

object AiPendingChoiceManager {

    fun chooseOption(game: GameState): String? {
        val pendingChoice = game.pendingChoice ?: return null

        val selectedOption = when (game.mode) {
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

            else -> return null
        }

        if (selectedOption !in pendingChoice.options) {
            System.err.println(
                "ERREUR IA : option non autorisée. " +
                        "Mode=${game.mode}, " +
                        "Type=${pendingChoice.type}, " +
                        "Carte=${pendingChoice.cardId}, " +
                        "Option=$selectedOption, " +
                        "Options=${pendingChoice.options}"
            )

            return null
        }

        return selectedOption
    }
}
