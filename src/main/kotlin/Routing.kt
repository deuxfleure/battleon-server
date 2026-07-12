package com.battleon

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.UUID
import com.battleon.SoloMissionStartRequest
import com.battleon.solo.SoloMissionDifficulty
import com.battleon.solo.SoloProgressService
import com.battleon.solo.SoloProgressResponse
import com.battleon.solo.SoloRuneLoadoutUpdateRequest
import com.battleon.solo.SoloRuneLoadoutUpdateResult


// !!!!!!!!!!!!!!!!!!!! A METTRE A JOUR A CHAQUE NOUVELLE VERSION !!!!!!!!!!!!!!!!!!!!!!!!!!!!
private const val MIN_SUPPORTED_APP_VERSION_CODE = 17

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Battleon server is running")
        }

        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        post("/register") {
            val user = call.receive<User>()

            val username = user.username.trim()
            val displayName = user.displayName.trim()

            // règle sécurité améliorée
            if (user.password.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Password cannot be empty")
                )
                return@post
            }
            if (user.password.length < 6) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Password must be at least 6 characters long")
                )
                return@post
            }

            if (username.isBlank() || displayName.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Username and display name cannot be empty")
                )
                return@post
            }

            if (username.equals(displayName, ignoreCase = true)) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Username and display name must be different")
                )
                return@post
            }

            if (UserService.userExists(username)) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "User already exists")
                )
                return@post
            }

            UserService.createUser(
                user.copy(
                    username = username,
                    displayName = displayName
                )
            )

            val authUser = UserService.getAuthUserByUsername(username)
            if (authUser != null) {
                UserService.initializePlayerCollection(authUser.id)
            }

            call.respond(mapOf("message" to "User $username registered"))
        }

        post("/login") {
            val loginRequest = call.receive<LoginRequest>()

            val appVersionCode = loginRequest.appVersionCode ?: 0L

            if (appVersionCode < MIN_SUPPORTED_APP_VERSION_CODE) {
                call.respond(
                    HttpStatusCode.UpgradeRequired,
                    mapOf("error" to "Votre version de BattleOn est trop ancienne. Merci de mettre l'application à jour.")
                )
                return@post
            }

            val authUser = UserService.validateUser(
                loginRequest.username,
                loginRequest.password
            )

            if (authUser != null) {
                val token = JwtConfig.generateToken(
                    userId = authUser.id,
                    username = authUser.username
                )

                call.respond(
                    mapOf(
                        "message" to "Login successful",
                        "token" to token
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid username or password")
                )
            }
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val me = UserService.getMe(userId)

                if (me != null) {
                    call.respond(me)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "User not found")
                    )
                }
            }

            get("/leaderboard") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val leaderboard = LeaderboardService.getLeaderboard(
                    currentUserId = userId
                )

                if (leaderboard != null) {
                    call.respond(leaderboard)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Player not found in leaderboard")
                    )
                }
            }

            post("/me/profile") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val request = call.receive<UpdateProfileRequest>()

                val success = UserService.updateProfile(
                    userId = userId,
                    request = request
                )

                if (!success) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "User not found")
                    )
                    return@post
                }

                val updatedMe = UserService.getMe(userId)

                if (updatedMe == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "User not found")
                    )
                    return@post
                }

                call.respond(updatedMe)
            }

            get("/me/profile/cosmetics") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val response = UserService.getUnlockedProfileCosmetics(userId)

                call.respond(response)
            }

            get("/solo/progress") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val response = SoloProgressResponse(
                    missions = SoloProgressService.getAllProgress(userId),
                    runeIds = SoloProgressService.getUnlockedRuneIds(userId),
                    activeRunes = SoloProgressService.getRuneLoadout(userId)
                )

                call.respond(response)
            }

            post("/solo/runes/loadout") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val request = call.receive<SoloRuneLoadoutUpdateRequest>()

                when (
                    val result = SoloProgressService.updateRuneLoadout(
                        userId = userId,
                        request = request
                    )
                ) {
                    is SoloRuneLoadoutUpdateResult.Success -> {
                        call.respond(result.loadout)
                    }

                    is SoloRuneLoadoutUpdateResult.Invalid -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to result.reason)
                        )
                    }
                }
            }

            get("/collection") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val collection = UserService.getPlayerCollection(userId)

                call.respond(mapOf("cards" to collection))
            }

            post("/profile/buy-battlepass") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val success = UserService.buyBattlePass(userId)

                if (success) {
                    call.respond(mapOf("message" to "Battlepass acheté"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Impossible d'acheter le battlepass"))
                }
            }

            post("/shop/redeem-code") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val request = call.receive<RedeemPromoCodeRequest>()

                val response = UserService.redeemPromoCode(
                    userId = userId,
                    rawCode = request.code
                )

                if (response.success) {
                    call.respond(response)
                } else {
                    call.respond(HttpStatusCode.BadRequest, response)
                }
            }

            post("/matchmaking/join") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()
                val request = call.receive<MatchmakingJoinRequest>()
                val mode = parseMatchmakingMode(request.mode)

                val me = UserService.getMe(userId)

                if (me == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "User not found")
                    )
                    return@post
                }

                val ownedCardIds = UserService.getOwnedCardIds(userId)

                val response = MatchmakingManager.joinQueue(
                    userId = userId,
                    displayName = me.displayName,
                    ownedCardIds = ownedCardIds,
                    mode = mode
                )

                call.respond(response)
            }

            get("/matchmaking/status") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()
                val mode = parseMatchmakingMode(call.request.queryParameters["mode"])

                val response = MatchmakingManager.getStatus(
                    userId = userId,
                    mode = mode
                )

                call.respond(response)
            }

            post("/matchmaking/cancel") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()
                val mode = parseMatchmakingMode(call.request.queryParameters["mode"])

                val response = MatchmakingManager.cancelSearch(
                    userId = userId,
                    mode = mode
                )

                call.respond(response)
            }

            post("/duel/training/start") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val me = UserService.getMe(userId)

                if (me == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "User not found")
                    )
                    return@post
                }

                val ownedCardIds = UserService.getOwnedCardIds(userId)

                val gameState = GameManager.createTrainingGame(
                    playerName = me.displayName,
                    ownedCardIds = ownedCardIds
                )

                call.respond(gameState)
            }


            post("/duel/training/start-with-shop-card") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val me = UserService.getMe(userId)

                if (me == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "User not found")
                    )
                    return@post
                }

                val request = call.receive<TrainingShopCardRequest>()

                val ownedCardIds = UserService.getOwnedCardIds(userId)

                val gameState = GameManager.createTrainingGameWithForcedShopCard(
                    playerName = me.displayName,
                    ownedCardIds = ownedCardIds,
                    forcedCardId = request.cardId
                )

                call.respond(gameState)
            }

            post("/duel/solo/start") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val me = UserService.getMe(userId)

                if (me == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "User not found")
                    )
                    return@post
                }

                val request = call.receive<SoloMissionStartRequest>()

                val difficulty = try {
                    SoloMissionDifficulty.valueOf(request.difficulty)
                } catch (_: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid solo difficulty")
                    )
                    return@post
                }

                val gameState = GameManager.createSoloMissionGame(
                    playerUserId = userId,
                    playerName = me.displayName,
                    missionId = request.missionId,
                    difficulty = difficulty,
                    selectedRuneIds = request.selectedRuneIds,
                    selectedCardIds = request.selectedCardIds
                )

                if (gameState == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Solo mission not found")
                    )
                    return@post
                }

                call.respond(gameState)
            }

            get("/duel/{gameId}/state") {
                val gameId = call.parameters["gameId"]
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                if (gameId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing gameId")
                    )
                    return@get
                }

                val gameState = GameManager.getGame(gameId)

                if (gameState == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@get
                }
                val checkedGameState = GameManager.applyConnectionTimeoutIfNeeded(
                    gameId = gameId,
                    checkingUserId = userId
                ) ?: gameState

                if (checkedGameState.isFinished) {
                    MatchmakingManager.clearMatchedGameForGame(checkedGameState)
                }

                call.respond(checkedGameState)

                if (gameState.isFinished) {
                    MatchmakingManager.clearMatchedGameForGame(gameState)
                }
                call.respond(gameState)
            }
            post("/duel/{gameId}/advance") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val gameId = call.parameters["gameId"]

                if (gameId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing gameId")
                    )
                    return@post
                }

                val existingGame = GameManager.getGame(gameId)

                if (existingGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }
                val isPlayer = when {
                    existingGame.mode == "TRAINING" -> true
                    existingGame.playerUserId == userId -> true
                    existingGame.opponentUserId == userId -> false
                    else -> true
                }

                val updatedGame = GameManager.advanceUntilDecision(
                    gameId = gameId,
                    isPlayer = isPlayer
                )

                if (updatedGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                if (updatedGame.isFinished) {
                    MatchmakingManager.clearMatchedGameForGame(updatedGame)
                }

                call.respond(updatedGame)
            }
            post("/duel/{gameId}/resolve-choice") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val gameId = call.parameters["gameId"]

                if (gameId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing gameId")
                    )
                    return@post
                }

                val existingGame = GameManager.getGame(gameId)

                if (existingGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                val isPlayer = when {
                    existingGame.mode == "TRAINING" -> true
                    existingGame.playerUserId == userId -> true
                    existingGame.opponentUserId == userId -> false
                    else -> true
                }

                val request = call.receive<ResolveChoiceRequest>()

                val updatedGame = GameManager.resolvePendingChoice(
                    gameId = gameId,
                    choice = request.choice
                )

                if (updatedGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                call.respond(updatedGame)
            }

            post("/duel/{gameId}/shop/buy/{cardId}") {
                val gameId = call.parameters["gameId"]
                val cardId = call.parameters["cardId"]
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                if (gameId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing gameId")
                    )
                    return@post
                }

                if (cardId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing cardId")
                    )
                    return@post
                }

                val existingGame = GameManager.getGame(gameId)

                if (existingGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }
                val isPlayer = when {
                    existingGame.mode == "TRAINING" -> true
                    existingGame.playerUserId == userId -> true
                    existingGame.opponentUserId == userId -> false
                    else -> true
                }

                val requestedGame = GameManager.requestShopPurchase(
                    gameId = gameId,
                    cardId = cardId,
                    isPlayer = isPlayer
                )
                if (requestedGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }
                val shopStatus = if (isPlayer) {
                    requestedGame.playerShopStatus
                } else {
                    requestedGame.opponentShopStatus
                }

                if (shopStatus == ShopPurchaseStatus.INVALID_LIMIT_REACHED) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Vous avez déjà atteint la limite d'achat de cette carte.")
                    )
                    return@post
                }

                if (requestedGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                val updatedGame = GameManager.advanceUntilDecision(
                    gameId = gameId,
                    isPlayer = isPlayer
                )

                if (updatedGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                call.respond(updatedGame)
            }

            post("/duel/{gameId}/shop/pass") {
                val gameId = call.parameters["gameId"]
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                if (gameId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing gameId")
                    )
                    return@post
                }

                val existingGame = GameManager.getGame(gameId)

                if (existingGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                val isPlayer = when {
                    existingGame.mode == "TRAINING" -> true
                    existingGame.playerUserId == userId -> true
                    existingGame.opponentUserId == userId -> false
                    else -> true
                }

                val passedGame = GameManager.passShop(
                    gameId = gameId,
                    isPlayer = isPlayer
                )

                if (passedGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                val updatedGame = GameManager.advanceUntilDecision(
                    gameId = gameId,
                    isPlayer = isPlayer
                )

                if (updatedGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                call.respond(updatedGame)
            }

            post("/duel/{gameId}/forfeit") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val gameId = call.parameters["gameId"]

                if (gameId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing gameId")
                    )
                    return@post
                }

                val updatedGame = GameManager.forfeitGame(
                    gameId = gameId,
                    forfeitingUserId = userId
                )

                if (updatedGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                if (updatedGame.isFinished) {
                    MatchmakingManager.clearMatchedGameForGame(updatedGame)
                }

                call.respond(updatedGame)
            }

            post("/duel/{gameId}/heartbeat") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val gameId = call.parameters["gameId"]

                if (gameId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing gameId")
                    )
                    return@post
                }

                val updatedGame = GameManager.heartbeat(
                    gameId = gameId,
                    userId = userId
                )

                if (updatedGame == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Game not found")
                    )
                    return@post
                }

                call.respond(updatedGame)
            }

            // test a supprimé quand propre
            post("/test/win") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val success = UserService.addWin(userId)

                if (success) {
                    call.respond(mapOf("message" to "Win added"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }

            post("/test/loss") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asInt()

                val success = UserService.addLoss(userId)

                if (success) {
                    call.respond(mapOf("message" to "Loss added"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            }
        }
    }
}