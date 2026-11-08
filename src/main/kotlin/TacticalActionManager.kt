
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
     * Liste des cartes activables depuis la défausse.
     *
     * Chaque nouvelle carte disposant d'une action
     * de défausse sera ajoutée ici.
     */
    fun getAvailableDiscardCardIds(
        game: GameState,
        owner: ChoiceOwner
    ): Set<CardId> {

        val availableCards = mutableSetOf<CardId>()

        if (hasAvailableDiscardCardAction(game, owner)) {
            availableCards.add(CardId.TIQUE)
        }

        return availableCards
    }


    /**
     * Détecte les cartes pouvant agir depuis
     * la défausse pendant la fenêtre actuelle.
     *
     * et des futures cartes concernées.
     */
    fun hasAvailableDiscardCardAction(
        game: GameState,
        owner: ChoiceOwner
    ): Boolean {

        // TIQUE — activable depuis la défausse
        // avant les Effets ou avant le Combat.
        if (
            game.phase != TurnPhase.AMBUSH_BEFORE_EFFECTS &&
            game.phase != TurnPhase.AMBUSH_BEFORE_COMBAT
        ) {
            return false
        }

        val currentCard = when (owner) {
            ChoiceOwner.PLAYER -> game.lastPlayerCard
            ChoiceOwner.OPPONENT -> game.lastOpponentCard
        }

        if (currentCard == null) {
            return false
        }

        val discard = when (owner) {
            ChoiceOwner.PLAYER -> game.playerDiscard
            ChoiceOwner.OPPONENT -> game.opponentDiscard
        }

        val ownerSide = if (owner == ChoiceOwner.PLAYER) {
            CardDestroyManager.Side.PLAYER
        } else {
            CardDestroyManager.Side.OPPONENT
        }

        val canDestroyTique = CardDestroyManager.eligibleIndices(
            game = game,
            owner = ownerSide,
            zone = CardDestroyManager.Zone.DISCARD,
            cause = CardDestroyManager.Cause.SACRIFICE
        ).any { index ->
            discard[index].id == CardId.TIQUE
        }

        if (!canDestroyTique) {
            return false
        }

        return TokenManager.getTokenAmount(
            game = game,
            target = owner,
            tokenId = TokenManager.TokenIds.BLOOD
        ) > 0
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

    /**
     * Enregistre le passage d'un joueur pour la fenêtre tactique.
     *
     * Un passage reste valable tant qu'aucune nouvelle
     * opportunité tactique n'est créée pour ce joueur.
     */
    fun markPassed(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {
        return when (owner) {
            ChoiceOwner.PLAYER -> game.copy(
                playerPassedCurrentAmbushWindow = true
            )

            ChoiceOwner.OPPONENT -> game.copy(
                opponentPassedCurrentAmbushWindow = true
            )
        }
    }

    fun reopenForNewOpportunity(
        game: GameState,
        owner: ChoiceOwner
    ): GameState {
        if (!PhaseManager.isTacticalWindow(game.phase)) {
            return game
        }

        return when (owner) {
            ChoiceOwner.PLAYER -> {
                if (!game.playerPassedCurrentAmbushWindow) {
                    game
                } else {
                    game.copy(
                        playerPassedCurrentAmbushWindow = false,
                        playerReactivatedCurrentAmbushWindow = true
                    )
                }
            }

            ChoiceOwner.OPPONENT -> {
                if (!game.opponentPassedCurrentAmbushWindow) {
                    game
                } else {
                    game.copy(
                        opponentPassedCurrentAmbushWindow = false,
                        opponentReactivatedCurrentAmbushWindow = true
                    )
                }
            }
        }
    }

    /**
     * Détermine le prochain acteur après une action
     * ou un passage.
     *
     * Après une action :
     * - priorité à l'adversaire ;
     * - sinon, le joueur actuel peut continuer.
     *
     * Après un passage :
     * - seul l'adversaire peut reprendre la main.
     *
     * Un joueur ayant passé ne peut revenir que si
     * une nouvelle opportunité a rouvert son droit d'agir.
     */
    fun nextActor(
        game: GameState,
        currentActor: ChoiceOwner,
        currentActorPassed: Boolean,
        playerHasAction: Boolean,
        opponentHasAction: Boolean
    ): ChoiceOwner? {

        val otherActor = when (currentActor) {
            ChoiceOwner.PLAYER -> ChoiceOwner.OPPONENT
            ChoiceOwner.OPPONENT -> ChoiceOwner.PLAYER
        }

        fun canAct(owner: ChoiceOwner): Boolean {
            return when (owner) {
                ChoiceOwner.PLAYER ->
                    !game.playerPassedCurrentAmbushWindow &&
                            playerHasAction

                ChoiceOwner.OPPONENT ->
                    !game.opponentPassedCurrentAmbushWindow &&
                            opponentHasAction
            }
        }

        // L'adversaire est toujours prioritaire.
        if (canAct(otherActor)) {
            return otherActor
        }

        // Après un passage, on ne redonne pas
        // immédiatement la main au même joueur.
        if (!currentActorPassed && canAct(currentActor)) {
            return currentActor
        }

        // Plus personne ne peut agir.
        return null
    }


}
