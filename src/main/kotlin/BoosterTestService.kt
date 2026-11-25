
package com.battleon

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

// TODO TEMP_BOOSTER_TEST : Supprimer ce fichier avant la sortie publique.

@Serializable
data class BoosterTestResetRequest(
    val gems: Int = 30000
)

object BoosterTestService {

    private val allowedUserIds = setOf(3, 5)

    fun isAllowed(userId: Int): Boolean {
        return userId in allowedUserIds
    }

    fun reset(userId: Int, gems: Int): Boolean {

        // Sécurité : seuls Maxime et aaa sont autorisés.
        require(isAllowed(userId))
        require(gems in 0..1000000)

        return transaction {

            // Même verrou que celui utilisé pour les achats de boosters.
            val profile = UserProfile
                .selectAll()
                .where { UserProfile.userAuthId eq userId }
                .forUpdate()
                .singleOrNull()
                ?: return@transaction false

            // Complète les éventuelles données manquantes.
            PlayerProgressInitializer.initializePlayer(userId)

            // 1. Vider tous les paquets sauvegardés.
            UserPaquetSlots.update({
                UserPaquetSlots.userAuthId eq userId
            }) {
                it[paquetName] = null
                it[paquetCards] = "[]"
                it[standardReady] = false
                it[rankedReady] = false
                it[validatedBanVersion] = 0
                it[updatedAtMillis] = 0L
            }

            // 2. Reverrouiller le septième emplacement.
            UserPaquetSlots.update({
                (UserPaquetSlots.userAuthId eq userId) and
                        (UserPaquetSlots.slotNumber eq 7)
            }) {
                it[isUnlocked] = false
            }

            // 3. Supprimer toute la collection.
            UserCardCollection.deleteWhere {
                UserCardCollection.userAuthId eq userId
            }

            // 4. Restaurer les cartes de départ.
            UserService.initializePlayerCollection(userId)

            // 5. Réinitialiser les garanties des boosters.
            UserBoosterProgress.update({
                UserBoosterProgress.userAuthId eq userId
            }) {
                it[boostersWithoutRare] = 0
                it[boostersWithoutNewCommon] = 0
            }

            // 6. Réinitialiser les ressources.
            UserProfile.update({
                UserProfile.userAuthId eq userId
            }) {
                it[UserProfile.gems] = gems
                it[UserProfile.dust] = 0
            }

            true
        }
    }
}
