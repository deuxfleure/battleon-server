
package com.battleon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction

object PlayerProgressInitializer {

    /**
     * Complète les données manquantes d'un joueur.
     * N'écrase jamais ses données existantes.
     *
     * Peut être appelé plusieurs fois sans effet secondaire.
     */
    fun initializePlayer(userId: Int) {
        transaction {

            // Deux compteurs indépendants pour les boosters.
            BoosterType.entries.forEach { type ->
                UserBoosterProgress.insertIgnore {
                    it[userAuthId] = userId
                    it[boosterType] = type
                    it[boostersWithoutRare] = 0
                    it[boostersWithoutNewCommon] = 0
                }
            }

            // Sept emplacements de paquet.
            for (slot in 1..PaquetRules.SLOT_COUNT) {

                val source = when (slot) {
                    in 1..2 -> PaquetUnlockSource.BASE
                    in 3..4 -> PaquetUnlockSource.PROGRESSION
                    5 -> PaquetUnlockSource.BATTLE_PASS
                    6 -> PaquetUnlockSource.PURCHASE
                    7 -> PaquetUnlockSource.BOOSTER
                    else -> error("Emplacement de paquet inconnu")
                }

                UserPaquetSlots.insertIgnore {
                    it[userAuthId] = userId
                    it[slotNumber] = slot
                    it[unlockSource] = source
                    it[isUnlocked] = slot in 1..2

                    it[paquetName] = if (slot == 1) {
                        StarterPaquet.NAME
                    } else {
                        null
                    }

                    it[paquetCards] = if (slot == 1) {
                        Json.encodeToString(StarterPaquet.CARDS)
                    } else {
                        "[]"
                    }

                    it[standardReady] = false
                    it[rankedReady] = false
                    it[validatedBanVersion] = 0

                    it[updatedAtMillis] = if (slot == 1) {
                        System.currentTimeMillis()
                    } else {
                        0L
                    }
                }
            }

            // Attribution du paquet tutoriel aux anciens comptes
            // dont le premier emplacement n'a jamais été initialisé.
            UserPaquetSlots.update({
                (UserPaquetSlots.userAuthId eq userId) and
                        (UserPaquetSlots.slotNumber eq 1) and
                        (UserPaquetSlots.updatedAtMillis eq 0L) and
                        (UserPaquetSlots.paquetName.isNull()) and
                        (UserPaquetSlots.paquetCards eq "[]")
            }) {
                it[paquetName] = StarterPaquet.NAME
                it[paquetCards] = Json.encodeToString(
                    StarterPaquet.CARDS
                )
                it[standardReady] = false
                it[rankedReady] = false
                it[validatedBanVersion] = 0
                it[updatedAtMillis] = System.currentTimeMillis()
            }
        }
    }

    /**
     * Initialise tous les comptes existants.
     * Les compositions déjà présentes sont conservées.
     */
    fun initializeAllPlayers() {
        val userIds = transaction {
            UserAuth.selectAll().map { it[UserAuth.id] }
        }

        userIds.forEach { userId ->
            initializePlayer(userId)
        }
    }
}
