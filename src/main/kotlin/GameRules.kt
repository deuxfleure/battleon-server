package com.battleon

data class GameOutcome(
    val isFinished: Boolean,
    val result: String? = null
)

fun evaluateGameResult(
    playerHp: Int,
    opponentHp: Int,
    playerGold: Int,
    opponentGold: Int
): GameOutcome {
    return when {
        opponentHp <= 0 -> GameOutcome(
            isFinished = true,
            result = "WIN"
        )

        playerHp <= 0 -> GameOutcome(
            isFinished = true,
            result = "LOSS"
        )

        playerGold >= 50 -> GameOutcome(
            isFinished = true,
            result = "WIN"
        )

        opponentGold >= 50 -> GameOutcome(
            isFinished = true,
            result = "LOSS"
        )

        playerHp >= 40 -> GameOutcome(
            isFinished = true,
            result = "WIN"
        )

        opponentHp >= 40 -> GameOutcome(
            isFinished = true,
            result = "LOSS"
        )

        else -> GameOutcome(
            isFinished = false,
            result = null
        )
    }
}

enum class ResolutionOrder {
    PLAYER_FIRST,
    OPPONENT_FIRST
}

fun determineResolutionOrder(
    playerHasPrems: Boolean,
    opponentHasPrems: Boolean,
    playerPower: Int,
    opponentPower: Int,
    playerHp: Int,
    opponentHp: Int,
    playerGold: Int,
    opponentGold: Int
): ResolutionOrder {
    // 1. PREMS
    if (playerHasPrems && !opponentHasPrems) {
        return ResolutionOrder.PLAYER_FIRST
    }
    if (opponentHasPrems && !playerHasPrems) {
        return ResolutionOrder.OPPONENT_FIRST
    }

    // 2. plus faible force agit d'abord
    if (playerPower < opponentPower) {
        return ResolutionOrder.PLAYER_FIRST
    }
    if (opponentPower < playerPower) {
        return ResolutionOrder.OPPONENT_FIRST
    }

    // 3. moins de PV agit d'abord
    if (playerHp < opponentHp) {
        return ResolutionOrder.PLAYER_FIRST
    }
    if (opponentHp < playerHp) {
        return ResolutionOrder.OPPONENT_FIRST
    }

    // 4. plus d'or agit d'abord
    if (playerGold > opponentGold) {
        return ResolutionOrder.PLAYER_FIRST
    }
    if (opponentGold > playerGold) {
        return ResolutionOrder.OPPONENT_FIRST
    }

    // 5. aléatoire
    return if ((0..1).random() == 0) {
        ResolutionOrder.PLAYER_FIRST
    } else {
        ResolutionOrder.OPPONENT_FIRST
    }
}