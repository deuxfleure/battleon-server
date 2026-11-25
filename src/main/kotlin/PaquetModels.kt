
package com.battleon

import kotlinx.serialization.Serializable

@Serializable
data class PaquetCardEntry(
    val cardId: CardId,
    val quantity: Int
)

@Serializable
enum class PaquetStatus {
    INCOMPLETE,
    STANDARD_READY,
    RANKED_READY
}

@Serializable
enum class PaquetValidationErrorType {
    NOT_ENOUGH_CARDS,
    TOO_MANY_CARDS,
    INVALID_QUANTITY,
    DUPLICATE_CARD_ENTRY,
    UNKNOWN_CARD,
    CARD_NOT_ALLOWED,
    INSUFFICIENT_OWNED_COPIES,
    COPY_LIMIT_EXCEEDED,
    PERMANENT_RANKED_BAN,
    WEEKLY_RANKED_BAN
}

@Serializable
data class PaquetValidationError(
    val type: PaquetValidationErrorType,
    val cardId: CardId? = null,
    val actualValue: Int? = null,
    val expectedValue: Int? = null
)

@Serializable
data class PaquetValidationResult(
    val totalCards: Int,
    val standardReady: Boolean,
    val rankedReady: Boolean,
    val status: PaquetStatus,
    val standardErrors: List<PaquetValidationError> = emptyList(),
    val rankedErrors: List<PaquetValidationError> = emptyList()
)

@Serializable
data class PaquetSlotDto(
    val slotNumber: Int,
    val unlockSource: PaquetUnlockSource,
    val isUnlocked: Boolean,
    val paquetName: String?,
    val paquetCards: List<PaquetCardEntry>,
    val validation: PaquetValidationResult,
    val validatedBanVersion: Int,
    val updatedAtMillis: Long
)

@Serializable
data class SavePaquetRequest(
    val paquetName: String?,
    val paquetCards: List<PaquetCardEntry>
)
