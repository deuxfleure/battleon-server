package com.battleon

object GameLogManager {

    fun add(
        game: GameState,
        key: String,
        params: Map<String, String> = emptyMap()
    ): GameState {
        val entry = GameLogEntry(
            turnNumber = game.turnNumber,
            key = key,
            params = params
        )

        return game.copy(
            logEntries = game.logEntries + entry
        )
    }

    fun turnStart(game: GameState): GameState {
        return add(
            game = game,
            key = "TURN_START",
            params = mapOf("turn" to game.turnNumber.toString())
        )
    }

    fun cardEffect(game: GameState, owner: String, cardId: String): GameState {
        return add(
            game = game,
            key = "CARD_EFFECT",
            params = mapOf(
                "owner" to owner,
                "cardId" to cardId
            )
        )
    }

    fun combatDamage(
        game: GameState,
        attacker: String,
        defender: String,
        damage: Int,
        attackerPower: Int,
        defenderPower: Int
    ): GameState {
        return add(
            game = game,
            key = "COMBAT_DAMAGE",
            params = mapOf(
                "attacker" to attacker,
                "defender" to defender,
                "damage" to damage.toString(),
                "attackerPower" to attackerPower.toString(),
                "defenderPower" to defenderPower.toString()
            )
        )
    }

    fun purchase(game: GameState, buyer: String, cardId: String): GameState {
        return add(
            game = game,
            key = "CARD_PURCHASE",
            params = mapOf(
                "buyer" to buyer,
                "cardId" to cardId
            )
        )
    }
}