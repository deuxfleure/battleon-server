package com.battleon

import kotlinx.serialization.Serializable

@Serializable
enum class ShopPurchaseStatus {
    NONE,
    PENDING,
    INVALID_LIMIT_REACHED,
    INVALID_OUT_OF_STOCK,
    WAITING_FOR_GOLD,
    ACCEPTED_WAITING_OPPONENT,
    PURCHASE_COMPLETED,
    PURCHASE_SNIPED_BY_PRIORITY
}