package com.battleon

object ScenarioAiPendingChoiceResolver {

    fun chooseOption(
        game: GameState,
        pendingChoice: PendingChoice
    ): String {

        // Placeholder temporaire :
        // pour l'instant on réutilise la logique training.
        // Plus tard logique scénario dédiée.

        return TrainingAiPendingChoiceResolver.chooseOption(
            game = game,
            pendingChoice = pendingChoice
        )
    }
}