package com.battleon

import kotlin.random.Random

/**
 * Point central des DESTRUCTIONS de cartes.
 *
 * Ne gère PAS : défausse normale, déplacement en embuscade/préparation,
 * recyclage de la taverne, ni l'ordre des effets post-combat.
 *
 * L'intégration dans les résolveurs se fera progressivement.
 */
object CardDestroyManager {

    enum class Side { PLAYER, OPPONENT }
    enum class Zone { DECK, DISCARD, AMBUSH, REVEALED }
    enum class Cause {
        OPPONENT_EFFECT,
        OWN_EFFECT,
        SACRIFICE,
        REPLACEMENT
    }
    enum class Failure {
        NO_TARGET,
        CARD_IMMUNE,
        PROTECTED_FROM_OPPONENT,
        FIVE_CARD_MINIMUM,
        CONDITION_NOT_MET,
        ROLL_FAILED
    }

    data class Result(
        val game: GameState,
        val destroyed: Boolean,
        val destroyedCard: Card? = null,
        val owner: Side? = null,
        val zone: Zone? = null,
        val failure: Failure? = null,
        /** Résultat d'un jet de mot-clé, s'il y en a eu un. */
        val roll: Int? = null
    )

    private fun other(side: Side): Side =
        if (side == Side.PLAYER) Side.OPPONENT else Side.PLAYER

    /** GARDIENSACRE : immunité absolue, y compris à ses propres effets. */
    fun canBeDestroyed(card: Card): Boolean =
        card.id != CardId.GARDIENSACRE

    /** SANGUINAIRE : protégé des destructions adverses, mais pas des siennes. */
    fun canBeDestroyedByOpponent(card: Card): Boolean =
        canBeDestroyed(card) && card.id != CardId.SANGUINAIRE

    fun countOwnedCards(game: GameState, owner: Side): Int =
        if (owner == Side.PLAYER) {
            game.playerDeck.size + game.playerDiscard.size +
                game.playerAmbush.size + (if (game.lastPlayerCard != null) 1 else 0)
        } else {
            game.opponentDeck.size + game.opponentDiscard.size +
                game.opponentAmbush.size + (if (game.lastOpponentCard != null) 1 else 0)
        }

    fun passesFiveCardRule(game: GameState, owner: Side): Boolean =
        countOwnedCards(game, owner) > 5

    /**
     * bypassFiveCardRule est réservé aux mécaniques qui l'autorisent
     * explicitement (ex. remplacement par la Sentinelle).
     */
    fun canDestroy(
        game: GameState,
        owner: Side,
        card: Card,
        cause: Cause,
        bypassFiveCardRule: Boolean = false
    ): Boolean = failureFor(game, owner, card, cause, bypassFiveCardRule) == null

    private fun failureFor(
        game: GameState,
        owner: Side,
        card: Card,
        cause: Cause,
        bypassFiveCardRule: Boolean
    ): Failure? = when {
        !canBeDestroyed(card) -> Failure.CARD_IMMUNE
        cause == Cause.OPPONENT_EFFECT && !canBeDestroyedByOpponent(card) ->
            Failure.PROTECTED_FROM_OPPONENT
        !bypassFiveCardRule && !passesFiveCardRule(game, owner) ->
            Failure.FIVE_CARD_MINIMUM
        else -> null
    }

    private fun cards(game: GameState, owner: Side, zone: Zone): List<Card> =
        when (zone) {
            Zone.DECK -> if (owner == Side.PLAYER) game.playerDeck else game.opponentDeck
            Zone.DISCARD -> if (owner == Side.PLAYER) game.playerDiscard else game.opponentDiscard
            Zone.AMBUSH -> (if (owner == Side.PLAYER) game.playerAmbush else game.opponentAmbush)
                .map { it.card }
            Zone.REVEALED -> listOfNotNull(
                if (owner == Side.PLAYER) game.lastPlayerCard else game.lastOpponentCard
            )
        }

    /** Indices valides au moment présent : ne pas les mémoriser entre deux effets. */
    fun eligibleIndices(
        game: GameState,
        owner: Side,
        zone: Zone,
        cause: Cause,
        bypassFiveCardRule: Boolean = false
    ): List<Int> = cards(game, owner, zone).indices.filter { index ->
        canDestroy(game, owner, cards(game, owner, zone)[index], cause, bypassFiveCardRule)
    }

    /**
     * Détruit UNE occurrence par indice (évite les ambiguïtés entre copies).
     * Pour une carte révélée, index doit être 0.
     *
     * Une destruction ne met JAMAIS la carte dans la défausse.
     */
    fun destroyAt(
        game: GameState,
        owner: Side,
        zone: Zone,
        index: Int,
        cause: Cause,
        bypassFiveCardRule: Boolean = false
    ): Result {
        val target = cards(game, owner, zone).getOrNull(index)
            ?: return Result(game, false, failure = Failure.NO_TARGET)
        val failure = failureFor(game, owner, target, cause, bypassFiveCardRule)
        if (failure != null) return Result(game, false, failure = failure)

        val changed = when (zone) {
            Zone.DECK -> if (owner == Side.PLAYER)
                game.copy(playerDeck = game.playerDeck.filterIndexed { i, _ -> i != index })
            else
                game.copy(opponentDeck = game.opponentDeck.filterIndexed { i, _ -> i != index })

            Zone.DISCARD -> if (owner == Side.PLAYER)
                game.copy(playerDiscard = game.playerDiscard.filterIndexed { i, _ -> i != index })
            else
                game.copy(opponentDiscard = game.opponentDiscard.filterIndexed { i, _ -> i != index })

            Zone.AMBUSH -> if (owner == Side.PLAYER)
                game.copy(playerAmbush = game.playerAmbush.filterIndexed { i, _ -> i != index })
            else
                game.copy(opponentAmbush = game.opponentAmbush.filterIndexed { i, _ -> i != index })

            Zone.REVEALED -> if (owner == Side.PLAYER)
                game.copy(lastPlayerCard = null, playerDisplayedTurnCard = null)
            else
                game.copy(lastOpponentCard = null, opponentDisplayedTurnCard = null)
        }
        return Result(changed, true, target, owner, zone)
    }

    /** Détruit la première occurrence portant cet ID, dans une zone précise. */
    fun destroyById(
        game: GameState,
        owner: Side,
        zone: Zone,
        cardId: CardId,
        cause: Cause,
        bypassFiveCardRule: Boolean = false
    ): Result {
        val index = cards(game, owner, zone).indexOfFirst { it.id == cardId }
        return destroyAt(game, owner, zone, index, cause, bypassFiveCardRule)
    }

    fun destroyRevealed(
        game: GameState,
        owner: Side,
        cause: Cause,
        bypassFiveCardRule: Boolean = false
    ): Result = destroyAt(game, owner, Zone.REVEALED, 0, cause, bypassFiveCardRule)

    fun destroyTopOfDeck(
        game: GameState,
        owner: Side,
        cause: Cause,
        bypassFiveCardRule: Boolean = false
    ): Result = destroyAt(game, owner, Zone.DECK, 0, cause, bypassFiveCardRule)

    fun sacrifice(
        game: GameState,
        owner: Side,
        zone: Zone,
        index: Int,
        bypassFiveCardRule: Boolean = false
    ): Result = destroyAt(game, owner, zone, index, Cause.SACRIFICE, bypassFiveCardRule)

    /**
     * CHARGE X : seul un gagnant avec un écart >= X peut détruire.
     * La cible doit encore être révélée AU MOMENT où l'effet est résolu.
     * Le résolveur doit également vérifier que la source est encore active.
     */
    fun resolveCharge(
        game: GameState,
        actor: Side,
        threshold: Int,
        actorPower: Int,
        defenderPower: Int
    ): Result {
        if (threshold < 1 || actorPower <= defenderPower ||
            actorPower - defenderPower < threshold
        ) return Result(game, false, failure = Failure.CONDITION_NOT_MET)
        if (cards(game, actor, Zone.REVEALED).isEmpty())
            return Result(game, false, failure = Failure.NO_TARGET)
        return destroyRevealed(game, other(actor), Cause.OPPONENT_EFFECT)
    }

    /**
     * ÉCRASEMENT : 6 sur 1d6.
     * DOUBLE ÉCRASEMENT : 5 ou 6 sur 1d6.
     * Aucun prérequis de victoire ; l'ordre est piloté par le résolveur.
     *
     * IMPORTANT : appeler UNE SEULE FOIS par déclenchement.
     * Le résolveur conserve la responsabilité de mémoriser l'effet résolu
     * pour ne pas relancer le dé après un PendingChoice.
     */
    fun resolveCrushing(
        game: GameState,
        actor: Side,
        doubleCrushing: Boolean = false,
        rollD6: () -> Int = { Random.nextInt(1, 7) }
    ): Result {
        if (cards(game, actor, Zone.REVEALED).isEmpty() ||
            cards(game, other(actor), Zone.REVEALED).isEmpty()
        ) return Result(game, false, failure = Failure.NO_TARGET)

        val roll = rollD6()
        require(roll in 1..6) { "Le dé doit retourner un entier entre 1 et 6." }
        val minimum = if (doubleCrushing) 5 else 6
        if (roll < minimum) return Result(game, false, failure = Failure.ROLL_FAILED, roll = roll)
        return destroyRevealed(game, other(actor), Cause.OPPONENT_EFFECT).copy(roll = roll)
    }

    /**
     * Pour les effets de remplacement : seule la DESTRUCTION est gérée ici.
     * La création de la carte de remplacement reste à la charge de l'effet.
     * Le bypass de la règle des cinq cartes doit être explicite.
     */
    fun destroyForReplacement(
        game: GameState,
        owner: Side,
        zone: Zone,
        index: Int,
        bypassFiveCardRule: Boolean = false
    ): Result = destroyAt(
        game, owner, zone, index, Cause.REPLACEMENT, bypassFiveCardRule
    )
}