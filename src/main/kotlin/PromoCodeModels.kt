package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class RedeemPromoCodeRequest(
    val code: String
)

@Serializable
data class RedeemPromoCodeResponse(
    val success: Boolean,
    val message: String,
    val rewardText: String? = null,
    val error: String? = null
)