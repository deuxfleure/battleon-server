package com.battleon

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtConfig.verifier())

            validate { credential ->
                val userId = credential.payload.getClaim("userId").asInt()
                val username = credential.payload.getClaim("username").asString()

                if (userId != null && !username.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}