
package com.battleon

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
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

            // Sept emplacements de deck.
            for (slot in 1..7) {
                val source = when (slot) {
                    in 1..4 -> DeckUnlockSource.BASE
                    5 -> DeckUnlockSource.BATTLE_PASS
                    6 -> DeckUnlockSource.PURCHASE
                    else -> DeckUnlockSource.BOOSTER
                }

                UserDeckSlots.insertIgnore {
                    it[userAuthId] = userId
                    it[slotNumber] = slot
                    it[unlockSource] = source
                    it[isUnlocked] = slot in 1..4
                    it[deckName] = null
                    it[deckCards] = "[]"
                    it[standardReady] = false
                    it[rankedReady] = false
                    it[validatedBanVersion] = 0
                    it[updatedAtMillis] = 0L
                }
            }
        }
    }

    /**
     * Initialise tous les comptes existants.
     * Les lignes déjà présentes sont conservées.
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
