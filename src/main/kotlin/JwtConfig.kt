package com.battleon

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    private const val secret = "battleon-super-secret-key-change-this-later"
    private const val issuer = "battleon-server"
    private const val audience = "battleon-client"
    private const val validityInMs = 1000L * 60L * 60L * 24L // 24h

    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(userId: Int, username: String): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
            .sign(algorithm)
    }

    fun verifier() = JWT
        .require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()
}