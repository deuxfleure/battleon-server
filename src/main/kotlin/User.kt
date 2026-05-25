package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val username: String,
    val password: String,
    val displayName: String,
    val language: String = "FR"
)