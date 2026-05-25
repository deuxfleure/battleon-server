package com.battleon

data class AuthUser(
    val id: Int,
    val username: String,
    val passwordHash: String
)