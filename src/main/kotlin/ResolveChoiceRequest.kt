package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class ResolveChoiceRequest(
    val choice: String
)