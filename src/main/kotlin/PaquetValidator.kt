
package com.battleon

object PaquetValidator {

    fun validate(
        paquetCards: List<PaquetCardEntry>,
        ownedCopies: Map<CardId, Int>
    ): PaquetValidationResult {

        val structuralErrors = mutableListOf<PaquetValidationError>()
        val standardErrors = mutableListOf<PaquetValidationError>()
        val rankedErrors = mutableListOf<PaquetValidationError>()

        val catalog = CardCatalog.getAllCards().associateBy { it.id }
        val encounteredCards = mutableSetOf<CardId>()

        val totalCards = paquetCards
            .sumOf { it.quantity.coerceAtLeast(0).toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        // ==========================================
        // NOMBRE TOTAL DE CARTES
        // ==========================================

        if (totalCards < PaquetRules.MIN_CARDS) {
            standardErrors += PaquetValidationError(
                type = PaquetValidationErrorType.NOT_ENOUGH_CARDS,
                actualValue = totalCards,
                expectedValue = PaquetRules.MIN_CARDS
            )
        }

        if (totalCards > PaquetRules.MAX_CARDS) {
            structuralErrors += PaquetValidationError(
                type = PaquetValidationErrorType.TOO_MANY_CARDS,
                actualValue = totalCards,
                expectedValue = PaquetRules.MAX_CARDS
            )
        }

        // ==========================================
        // VALIDATION INDIVIDUELLE DES CARTES
        // ==========================================

        for (entry in paquetCards) {

            val cardId = entry.cardId
            val quantity = entry.quantity

            // Une seule entrée par CardId
            if (!encounteredCards.add(cardId)) {
                structuralErrors += PaquetValidationError(
                    type = PaquetValidationErrorType.DUPLICATE_CARD_ENTRY,
                    cardId = cardId
                )
            }

            // Quantité strictement positive
            if (quantity <= 0) {
                structuralErrors += PaquetValidationError(
                    type = PaquetValidationErrorType.INVALID_QUANTITY,
                    cardId = cardId,
                    actualValue = quantity,
                    expectedValue = 1
                )
            }

            // Présence dans le catalogue
            val card = catalog[cardId]

            if (card == null) {
                structuralErrors += PaquetValidationError(
                    type = PaquetValidationErrorType.UNKNOWN_CARD,
                    cardId = cardId
                )
                continue
            }

            // Carte admissible dans un paquet
            if (CardCatalog.isExcludedFromShop(cardId)) {
                structuralErrors += PaquetValidationError(
                    type = PaquetValidationErrorType.CARD_NOT_ALLOWED,
                    cardId = cardId
                )
            }

            // Nombre d'exemplaires possédés
            val owned = ownedCopies[cardId]
                ?.coerceAtLeast(0) ?: 0

            if (quantity > owned) {
                structuralErrors += PaquetValidationError(
                    type = PaquetValidationErrorType.INSUFFICIENT_OWNED_COPIES,
                    cardId = cardId,
                    actualValue = quantity,
                    expectedValue = owned
                )
            }

            // Limite propre à la carte
            val copyLimit = PaquetRules.getCardCopyLimit(card)

            if (quantity > copyLimit) {
                structuralErrors += PaquetValidationError(
                    type = PaquetValidationErrorType.COPY_LIMIT_EXCEEDED,
                    cardId = cardId,
                    actualValue = quantity,
                    expectedValue = copyLimit
                )
            }

            // ======================================
            // BANNISSEMENTS
            // ======================================

            if (RankedBanService.isStandardPvpBanned(cardId)) {
                standardErrors += PaquetValidationError(
                    type = PaquetValidationErrorType.STANDARD_PVP_BAN,
                    cardId = cardId
                )
            }

            if (RankedBanService.isPermanentlyRankedBanned(cardId)) {
                rankedErrors += PaquetValidationError(
                    type = PaquetValidationErrorType.PERMANENT_RANKED_BAN,
                    cardId = cardId
                )
            }

            if (RankedBanService.isWeeklyRankedBanned(cardId)) {
                rankedErrors += PaquetValidationError(
                    type = PaquetValidationErrorType.WEEKLY_RANKED_BAN,
                    cardId = cardId
                )
            }
        }

        // ==========================================
        // RESULTAT FINAL
        // ==========================================

        val allStandardErrors = structuralErrors + standardErrors
        val allRankedErrors = allStandardErrors + rankedErrors

        val canBeSaved = structuralErrors.isEmpty()
        val standardReady = allStandardErrors.isEmpty()
        val rankedReady = allRankedErrors.isEmpty()

        val status = when {
            rankedReady -> PaquetStatus.RANKED_READY
            standardReady -> PaquetStatus.STANDARD_READY
            else -> PaquetStatus.INCOMPLETE
        }

        return PaquetValidationResult(
            totalCards = totalCards,
            canBeSaved = canBeSaved,
            standardReady = standardReady,
            rankedReady = rankedReady,
            status = status,
            standardErrors = allStandardErrors,
            rankedErrors = allRankedErrors
        )
    }
}
