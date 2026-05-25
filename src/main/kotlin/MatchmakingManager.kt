package com.battleon

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MatchmakingStatusResponse(
    val status: String,
    val gameId: String? = null,
    val message: String
)

@Serializable
data class MatchmakingJoinRequest(
    val mode: String = "QUICK"
)

enum class MatchmakingMode {
    QUICK,
    RANKED,
    SEASON
}

fun parseMatchmakingMode(mode: String?): MatchmakingMode {
    return when (mode?.uppercase()) {
        "RANKED" -> MatchmakingMode.RANKED
        "SEASON" -> MatchmakingMode.SEASON
        else -> MatchmakingMode.QUICK
    }
}


private data class WaitingPlayer(
    val userId: Int,
    val displayName: String,
    val ownedCardIds: List<String>,
    val collectionSize: Int,
    val joinedAtMillis: Long,
    val mode: MatchmakingMode
)

object MatchmakingManager {

    private val waitingPlayersByMode =
        ConcurrentHashMap<MatchmakingMode, ConcurrentHashMap<Int, WaitingPlayer>>()

    private val matchedGames = ConcurrentHashMap<Int, String>()

    private fun getQueue(mode: MatchmakingMode): ConcurrentHashMap<Int, WaitingPlayer> {
        return waitingPlayersByMode.getOrPut(mode) { ConcurrentHashMap() }
    }

    private fun getActiveMatchedGameIdOrClear(userId: Int): String? {
        val gameId = matchedGames[userId] ?: return null

        val game = GameManager.getGame(gameId)

        if (game == null || game.isFinished) {
            matchedGames.remove(userId)
            return null
        }

        return gameId
    }

    fun joinQueue(
        userId: Int,
        displayName: String,
        ownedCardIds: List<String>,
        mode: MatchmakingMode
    ): MatchmakingStatusResponse {
        val waitingPlayers = getQueue(mode)


        getActiveMatchedGameIdOrClear(userId)?.let { gameId ->
            return MatchmakingStatusResponse(
                status = "MATCH_FOUND",
                gameId = gameId,
                message = "Adversaire trouvé"
            )
        }
        matchedGames.remove(userId)
        waitingPlayers.remove(userId)

        val newPlayer = WaitingPlayer(
            userId = userId,
            displayName = displayName,
            ownedCardIds = ownedCardIds,
            collectionSize = ownedCardIds.size,
            joinedAtMillis = System.currentTimeMillis(),
            mode = mode
        )

        val opponent = findCompatibleOpponent(newPlayer, waitingPlayers)

        if (opponent != null) {
            waitingPlayers.remove(opponent.userId)

            val game = when (newPlayer.mode) {
                MatchmakingMode.SEASON -> GameManager.createSeasonGame(
                    playerUserId = newPlayer.userId,
                    playerName = newPlayer.displayName,
                    playerOwnedCardIds = newPlayer.ownedCardIds,

                    opponentUserId = opponent.userId,
                    opponentName = opponent.displayName,
                    opponentOwnedCardIds = opponent.ownedCardIds
                )

                else -> GameManager.createDuelGame(
                    playerUserId = newPlayer.userId,
                    playerName = newPlayer.displayName,
                    playerOwnedCardIds = newPlayer.ownedCardIds,

                    opponentUserId = opponent.userId,
                    opponentName = opponent.displayName,
                    opponentOwnedCardIds = opponent.ownedCardIds
                )
            }

            matchedGames[newPlayer.userId] = game.gameId
            matchedGames[opponent.userId] = game.gameId

            return MatchmakingStatusResponse(
                status = "MATCH_FOUND",
                gameId = game.gameId,
                message = "Adversaire trouvé"
            )
        }

        waitingPlayers[userId] = newPlayer

        return MatchmakingStatusResponse(
            status = "SEARCHING",
            gameId = null,
            message = "Recherche d'adversaire en cours"
        )
    }

    fun getStatus(
        userId: Int,
        mode: MatchmakingMode
    ): MatchmakingStatusResponse {
        val waitingPlayers = getQueue(mode)

        getActiveMatchedGameIdOrClear(userId)?.let { gameId ->
            return MatchmakingStatusResponse(
                status = "MATCH_FOUND",
                gameId = gameId,
                message = "Adversaire trouvé"
            )
        }

        val waitingPlayer = waitingPlayers[userId]

        if (waitingPlayer == null) {
            return MatchmakingStatusResponse(
                status = "NOT_SEARCHING",
                gameId = null,
                message = "Aucune recherche en cours"
            )
        }

        val opponent = findCompatibleOpponent(waitingPlayer, waitingPlayers)

        if (opponent != null) {
            waitingPlayers.remove(waitingPlayer.userId)
            waitingPlayers.remove(opponent.userId)

            val game = when (waitingPlayer.mode) {
                MatchmakingMode.SEASON -> GameManager.createSeasonGame(
                    playerUserId = waitingPlayer.userId,
                    playerName = waitingPlayer.displayName,
                    playerOwnedCardIds = waitingPlayer.ownedCardIds,

                    opponentUserId = opponent.userId,
                    opponentName = opponent.displayName,
                    opponentOwnedCardIds = opponent.ownedCardIds
                )

                else -> GameManager.createDuelGame(
                    playerUserId = waitingPlayer.userId,
                    playerName = waitingPlayer.displayName,
                    playerOwnedCardIds = waitingPlayer.ownedCardIds,

                    opponentUserId = opponent.userId,
                    opponentName = opponent.displayName,
                    opponentOwnedCardIds = opponent.ownedCardIds
                )
            }

            matchedGames[waitingPlayer.userId] = game.gameId
            matchedGames[opponent.userId] = game.gameId

            return MatchmakingStatusResponse(
                status = "MATCH_FOUND",
                gameId = game.gameId,
                message = "Adversaire trouvé"
            )
        }

        val elapsedSeconds = ((System.currentTimeMillis() - waitingPlayer.joinedAtMillis) / 1000).toInt()

        return MatchmakingStatusResponse(
            status = "SEARCHING",
            gameId = null,
            message = "Recherche en cours depuis $elapsedSeconds secondes"
        )
    }

    fun cancelSearch(
        userId: Int,
        mode: MatchmakingMode
    ): MatchmakingStatusResponse {
        val waitingPlayers = getQueue(mode)
        waitingPlayers.remove(userId)

        return MatchmakingStatusResponse(
            status = "CANCELLED",
            gameId = null,
            message = "Recherche annulée"
        )
    }

    private fun findCompatibleOpponent(
        player: WaitingPlayer,
        waitingPlayers: ConcurrentHashMap<Int, WaitingPlayer>
    ): WaitingPlayer? {
        return waitingPlayers.values
            .filter { opponent -> opponent.userId != player.userId }
            .filter { opponent -> opponent.mode == player.mode }
            .filter { opponent -> areCompatible(player, opponent) }
            .minByOrNull { opponent ->
                kotlin.math.abs(opponent.collectionSize - player.collectionSize)
            }
    }

    private fun areCompatible(
        playerA: WaitingPlayer,
        playerB: WaitingPlayer
    ): Boolean {
        val difference = kotlin.math.abs(playerA.collectionSize - playerB.collectionSize)

        val toleranceA = getTolerance(playerA.joinedAtMillis)
        val toleranceB = getTolerance(playerB.joinedAtMillis)

        return difference <= toleranceA && difference <= toleranceB
    }

    private fun getTolerance(joinedAtMillis: Long): Int {
        val elapsedSeconds = ((System.currentTimeMillis() - joinedAtMillis) / 1000).toInt()

        return when {
            elapsedSeconds < 30 -> 10
            elapsedSeconds < 60 -> 25
            elapsedSeconds < 90 -> 50
            elapsedSeconds < 120 -> 75
            else -> Int.MAX_VALUE
        }
    }

    fun clearMatchedGame(userId: Int) {
        matchedGames.remove(userId)
    }

    fun clearMatchedGameForGame(game: GameState) {
        waitingPlayersByMode.values.forEach { queue ->
            game.playerUserId?.let { queue.remove(it) }
            game.opponentUserId?.let { queue.remove(it) }
        }
    }
}