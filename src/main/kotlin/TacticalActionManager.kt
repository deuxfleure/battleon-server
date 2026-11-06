
package com.battleon

object TacticalActionManager {

    /**
     * Ouvre une nouvelle fenêtre tactique.
     *
     * Les disponibilités sont fournies par le moteur.
     * Leur détection sera centralisée ici ensuite.
     */
    fun openWindow(
        game: GameState,
        playerHasAction: Boolean,
        opponentHasAction: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): GameState {

        require(PhaseManager.isTacticalWindow(game.phase)) {
            "Cannot open a tactical window outside a tactical phase"
        }

        val hasAnyAction =
            playerHasAction || opponentHasAction

        val duration =
            PhaseManager.tacticalWindowDurationMillis(
                hasAvailableActions = hasAnyAction
            )

        return game.copy(
            tacticalWindowStatus = TacticalWindowStatus.OPEN,
            tacticalWindowId = game.tacticalWindowId + 1L,

            playerHasTacticalAction = playerHasAction,
            opponentHasTacticalAction = opponentHasAction,

            playerTacticalExtensionUsed = false,
            opponentTacticalExtensionUsed = false,

            phaseDeadlineAtMillis =
                PhaseManager.deadlineFrom(
                    nowMillis = nowMillis,
                    durationMillis = duration
                )
        )
    }

    /**
     * Ferme la fenêtre tactique et réinitialise
     * les indicateurs associés.
     */
    fun closeWindow(game: GameState): GameState {
        return game.copy(
            tacticalWindowStatus = TacticalWindowStatus.CLOSED,

            playerHasTacticalAction = false,
            opponentHasTacticalAction = false,

            playerTacticalExtensionUsed = false,
            opponentTacticalExtensionUsed = false,

            phaseDeadlineAtMillis = null
        )
    }

    /**
     * Indique si le joueur possède une action
     * pendant la fenêtre actuellement ouverte.
     */
    fun hasAction(
        game: GameState,
        owner: ChoiceOwner
    ): Boolean {

        if (game.tacticalWindowStatus != TacticalWindowStatus.OPEN) {
            return false
        }

        return when (owner) {
            ChoiceOwner.PLAYER ->
                game.playerHasTacticalAction

            ChoiceOwner.OPPONENT ->
                game.opponentHasTacticalAction
        }
    }


    /**
     * Détecte les cartes pouvant agir depuis
     * la défausse pendant la fenêtre actuelle.
     *
     * TODO : intégrer les règles du Phénix
     * et des futures cartes concernées.
     */
    fun hasAvailableDiscardCardAction(
        game: GameState,
        owner: ChoiceOwner
    ): Boolean {
        return false
    }



    /**
     * Vérifie si un joueur peut activer un Emblème.
     */
    fun canActivateEmblem(
        game: GameState,
        owner: ChoiceOwner
    ): Boolean {

        if (!isEmblemActivationWindow(game.phase)) {
            return false
        }

        if (game.pendingChoice != null) {
            return false
        }

        val currentCard = when (owner) {
            ChoiceOwner.PLAYER -> game.lastPlayerCard
            ChoiceOwner.OPPONENT -> game.lastOpponentCard
        }

        if (currentCard == null) {
            return false
        }

        return TokenManager.hasToken(
            game = game,
            target = owner,
            tokenId = TokenManager.TokenIds.EMBLEM
        )
    }

    /**
     * Détecte les actions disponibles via les jetons.
     */
    fun hasAvailableTokenAction(
        game: GameState,
        owner: ChoiceOwner
    ): Boolean {
        return canActivateEmblem(game, owner)
    }


    /**
     * Détermine si un joueur possède au moins
     * une action tactique utilisable.
     *
     * Les disponibilités sont calculées par les
     * gestionnaires spécialisés.
     */

    fun hasAvailableAction(
        hasRuneAction: Boolean,
        hasTacticalCardAction: Boolean,
        hasTokenAction: Boolean,
        hasDiscardCardAction: Boolean
    ): Boolean {
        return hasRuneAction ||
                hasTacticalCardAction ||
                hasTokenAction ||
                hasDiscardCardAction
    }


    /**
     * Fenêtres autorisant la dépense d'un Emblème.
     * Communes aux bonus Force et Bretteur.
     */
    fun isEmblemActivationWindow(
        phase: TurnPhase
    ): Boolean {
        return phase == TurnPhase.AMBUSH_BEFORE_EFFECTS ||
                phase == TurnPhase.AMBUSH_BEFORE_COMBAT
    }





}
