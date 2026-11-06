
package com.battleon

/**
 * BattleOn 0.2.1
 *
 * Gestion centralisée des durées et des règles
 * temporelles du nouveau moteur de phases.
 *
 * Le serveur reste l'unique autorité.
 */
object PhaseManager {

    // Durée d'affichage d'une phase déjà résolue.
    const val PHASE_DISPLAY_MILLIS = 1_500L

    // Fenêtre tactique avec au moins une action disponible.
    const val TACTICAL_WINDOW_MILLIS = 2_000L

    // Fenêtre tactique sans aucune action disponible.
    const val EMPTY_TACTICAL_WINDOW_MILLIS = 500L

    // Prolongation demandée par un joueur PvP.
    const val PVP_ACTION_EXTENSION_MILLIS = 10_000L

    // Délai pour un choix obligatoire en PvP.
    const val PVP_PENDING_CHOICE_MILLIS = 15_000L

    // Délais des phases spéciales.
    const val PVP_PRE_START_MILLIS = 90_000L
    const val PVP_SHOP_MILLIS = 20_000L

    /**
     * Détermine si la partie est multijoueur.
     */
    fun isPvpMode(game: GameState): Boolean {
        return game.mode == "DUEL" ||
                game.mode == "SEASON" ||
                game.mode == "RANKED"
    }

    /**
     * Durée d'affichage des phases automatiques.
     *
     * Attention : cette durée ne retarde PAS
     * l'exécution des effets. Elle permet seulement
     * au client de présenter leur résultat.
     */
    fun displayDurationMillis(phase: TurnPhase): Long {
        return when (phase) {
            TurnPhase.COMBAT_CLEANUP -> 0L

            TurnPhase.REVEAL,
            TurnPhase.EFFECTS,
            TurnPhase.COMBAT,
            TurnPhase.POST_COMBAT,
            TurnPhase.END_TURN -> PHASE_DISPLAY_MILLIS

            else -> 0L
        }
    }

    /**
     * Durée d'une fenêtre tactique selon
     * la disponibilité réelle des actions.
     */
    fun tacticalWindowDurationMillis(
        hasAvailableActions: Boolean
    ): Long {
        return if (hasAvailableActions) {
            TACTICAL_WINDOW_MILLIS
        } else {
            EMPTY_TACTICAL_WINDOW_MILLIS
        }
    }

    /**
     * Calcule une deadline à partir d'une durée.
     */
    fun deadlineFrom(
        nowMillis: Long,
        durationMillis: Long
    ): Long {
        return nowMillis + durationMillis.coerceAtLeast(0L)
    }


    /**
     * Détermine la prochaine phase en tenant compte
     * des éventuelles interruptions du cycle normal.
     */
    fun nextPhase(game: GameState): TurnPhase {

        // Esquive : aucun combat ni effet post-combat.
        if (
            game.phase == TurnPhase.EFFECTS &&
            game.combatCancelledByDodge
        ) {
            return TurnPhase.COMBAT_CLEANUP
        }

        return when (game.phase) {

            TurnPhase.PRE_START ->
                TurnPhase.AMBUSH_BEFORE_REVEAL

            TurnPhase.AMBUSH_BEFORE_REVEAL ->
                TurnPhase.REVEAL

            TurnPhase.REVEAL ->
                TurnPhase.AMBUSH_BEFORE_EFFECTS

            TurnPhase.AMBUSH_BEFORE_EFFECTS ->
                TurnPhase.EFFECTS

            TurnPhase.EFFECTS ->
                TurnPhase.AMBUSH_BEFORE_COMBAT

            TurnPhase.AMBUSH_BEFORE_COMBAT ->
                TurnPhase.COMBAT

            TurnPhase.COMBAT ->
                TurnPhase.AMBUSH_BEFORE_POST_COMBAT

            TurnPhase.AMBUSH_BEFORE_POST_COMBAT ->
                TurnPhase.POST_COMBAT

            TurnPhase.POST_COMBAT ->
                TurnPhase.COMBAT_CLEANUP

            TurnPhase.COMBAT_CLEANUP ->
                TurnPhase.AMBUSH_BEFORE_SHOP

            TurnPhase.AMBUSH_BEFORE_SHOP ->
                TurnPhase.SHOP_RESOLUTION

            TurnPhase.SHOP_RESOLUTION ->
                TurnPhase.END_TURN

            TurnPhase.END_TURN ->
                TurnPhase.AMBUSH_BEFORE_REVEAL
        }
    }


    /**
     * Indique si une phase correspond à une fenêtre tactique.
     *
     * Les noms AMBUSH_BEFORE_* sont conservés pour
     * l'instant afin de préserver le contrat existant.
     */
    fun isTacticalWindow(phase: TurnPhase): Boolean {
        return when (phase) {
            TurnPhase.AMBUSH_BEFORE_REVEAL,
            TurnPhase.AMBUSH_BEFORE_EFFECTS,
            TurnPhase.AMBUSH_BEFORE_COMBAT,
            TurnPhase.AMBUSH_BEFORE_POST_COMBAT,
            TurnPhase.AMBUSH_BEFORE_SHOP -> true

            else -> false
        }
    }


    /**
     * Vérifie si le délai de la phase est écoulé.
     *
     * Une phase sans deadline peut progresser
     * immédiatement.
     */
    fun canResolveCurrentPhase(
        game: GameState,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val deadline = game.phaseDeadlineAtMillis
            ?: return true

        return nowMillis >= deadline
    }

    /**
     * Vérifie si la phase attend encore
     * l'expiration de son délai.
     */
    fun isWaitingForPhaseDeadline(
        game: GameState,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val deadline = game.phaseDeadlineAtMillis
            ?: return false

        return nowMillis < deadline
    }

}
