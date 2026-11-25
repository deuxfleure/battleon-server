
package com.battleon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction

object PaquetService {

    private val json = Json

    // =====================================================
    // COLLECTION DU JOUEUR
    // =====================================================

    private fun getOwnedCopies(userId: Int): Map<CardId, Int> {

        val ownedRows = UserCardCollection
            .selectAll()
            .where {
                (UserCardCollection.userAuthId eq userId) and
                        (UserCardCollection.isOwned eq true)
            }
            .associate { row ->
                row[UserCardCollection.cardId] to
                        row[UserCardCollection.ownedCopies]
            }

        return CardCatalog.getAllCards().associate { card ->
            card.id to (ownedRows[card.id.name] ?: 0)
                .coerceIn(0, PaquetRules.DEFAULT_COPY_LIMIT)
        }
    }

    // =====================================================
    // DISPONIBILITE D'UN EMPLACEMENT
    // =====================================================

    private fun isSlotUnlocked(
        row: ResultRow,
        hasBattlePass: Boolean
    ): Boolean {

        return when (row[UserPaquetSlots.unlockSource]) {

            PaquetUnlockSource.BASE -> true

            PaquetUnlockSource.BATTLE_PASS -> hasBattlePass

            PaquetUnlockSource.PROGRESSION,
            PaquetUnlockSource.PURCHASE,
            PaquetUnlockSource.BOOSTER ->
                row[UserPaquetSlots.isUnlocked]
        }
    }

    // =====================================================
    // CONVERSION EN DTO
    // =====================================================

    private fun toSlotDto(
        row: ResultRow,
        hasBattlePass: Boolean,
        ownedCopies: Map<CardId, Int>
    ): PaquetSlotDto {

        val cards = json.decodeFromString<List<PaquetCardEntry>>(
            row[UserPaquetSlots.paquetCards]
        )

        val validation = PaquetValidator.validate(
            paquetCards = cards,
            ownedCopies = ownedCopies
        )

        return PaquetSlotDto(
            slotNumber = row[UserPaquetSlots.slotNumber],
            unlockSource = row[UserPaquetSlots.unlockSource],
            isUnlocked = isSlotUnlocked(row, hasBattlePass),
            paquetName = row[UserPaquetSlots.paquetName],
            paquetCards = cards,
            validation = validation,
            validatedBanVersion = RankedBanService.getBanVersion(),
            updatedAtMillis = row[UserPaquetSlots.updatedAtMillis]
        )
    }

    // =====================================================
    // ACTUALISATION DE LA VALIDITE EN BASE
    // =====================================================

    private fun refreshValidation(
        row: ResultRow,
        validation: PaquetValidationResult
    ) {

        val banVersion = RankedBanService.getBanVersion()

        if (
            row[UserPaquetSlots.standardReady] == validation.standardReady &&
            row[UserPaquetSlots.rankedReady] == validation.rankedReady &&
            row[UserPaquetSlots.validatedBanVersion] == banVersion
        ) {
            return
        }

        val rowId = row[UserPaquetSlots.id]

        UserPaquetSlots.update({
            UserPaquetSlots.id eq rowId
        }) {
            it[standardReady] = validation.standardReady
            it[rankedReady] = validation.rankedReady
            it[validatedBanVersion] = banVersion
        }
    }

    // =====================================================
    // RECUPERATION DES SEPT EMPLACEMENTS
    // =====================================================

    fun getPaquetSlots(userId: Int): List<PaquetSlotDto> {

        return transaction {

            val profile = UserProfile
                .selectAll()
                .where { UserProfile.userAuthId eq userId }
                .forUpdate()
                .singleOrNull()
                ?: error("Profil introuvable")

            val hasBattlePass = profile[UserProfile.hasBattlePass]
            val ownedCopies = getOwnedCopies(userId)

            val rows = UserPaquetSlots
                .selectAll()
                .where { UserPaquetSlots.userAuthId eq userId }
                .forUpdate()
                .toList()
                .sortedBy { it[UserPaquetSlots.slotNumber] }

            rows.map { row ->

                val slot = toSlotDto(
                    row = row,
                    hasBattlePass = hasBattlePass,
                    ownedCopies = ownedCopies
                )

                refreshValidation(row, slot.validation)

                slot
            }
        }
    }

    // =====================================================
    // SAUVEGARDE D'UN PAQUET
    // =====================================================

    fun savePaquet(
        userId: Int,
        slotNumber: Int,
        request: SavePaquetRequest
    ): SavePaquetResponse {

        if (!PaquetRules.isValidSlotNumber(slotNumber)) {
            return SavePaquetResponse(
                success = false,
                error = "INVALID_SLOT_NUMBER"
            )
        }

        val name = request.paquetName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (
            name != null &&
            name.length > PaquetRules.MAX_NAME_LENGTH
        ) {
            return SavePaquetResponse(
                success = false,
                error = "NAME_TOO_LONG"
            )
        }

        return transaction {

            // Verrouillage du profil avant l'emplacement.
            val profile = UserProfile
                .selectAll()
                .where { UserProfile.userAuthId eq userId }
                .forUpdate()
                .singleOrNull()
                ?: return@transaction SavePaquetResponse(
                    success = false,
                    error = "PROFILE_NOT_FOUND"
                )

            val hasBattlePass = profile[UserProfile.hasBattlePass]

            val row = UserPaquetSlots
                .selectAll()
                .where {
                    (UserPaquetSlots.userAuthId eq userId) and
                            (UserPaquetSlots.slotNumber eq slotNumber)
                }
                .forUpdate()
                .singleOrNull()
                ?: return@transaction SavePaquetResponse(
                    success = false,
                    error = "SLOT_NOT_FOUND"
                )

            if (!isSlotUnlocked(row, hasBattlePass)) {
                return@transaction SavePaquetResponse(
                    success = false,
                    error = "SLOT_LOCKED"
                )
            }

            val ownedCopies = getOwnedCopies(userId)

            val validation = PaquetValidator.validate(
                paquetCards = request.paquetCards,
                ownedCopies = ownedCopies
            )

            if (!validation.canBeSaved) {
                return@transaction SavePaquetResponse(
                    success = false,
                    error = "INVALID_PAQUET",
                    validation = validation
                )
            }

            val cards = request.paquetCards
                .sortedBy { it.cardId.name }

            val banVersion = RankedBanService.getBanVersion()
            val now = System.currentTimeMillis()
            val rowId = row[UserPaquetSlots.id]

            val updatedRows = UserPaquetSlots.update({
                UserPaquetSlots.id eq rowId
            }) {
                it[paquetName] = name
                it[paquetCards] = json.encodeToString(cards)
                it[standardReady] = validation.standardReady
                it[rankedReady] = validation.rankedReady
                it[validatedBanVersion] = banVersion
                it[updatedAtMillis] = now
            }

            check(updatedRows == 1) {
                "Impossible de sauvegarder le paquet"
            }

            SavePaquetResponse(
                success = true,
                validation = validation,
                slot = PaquetSlotDto(
                    slotNumber = slotNumber,
                    unlockSource = row[UserPaquetSlots.unlockSource],
                    isUnlocked = true,
                    paquetName = name,
                    paquetCards = cards,
                    validation = validation,
                    validatedBanVersion = banVersion,
                    updatedAtMillis = now
                )
            )
        }
    }
}
