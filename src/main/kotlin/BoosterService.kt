
package com.battleon

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

@Serializable
data class BoosterReward(
    val type: String,
    val cardId: String? = null,
    val isRare: Boolean = false,
    val isNew: Boolean = false,
    val dustEarned: Int = 0
)

@Serializable
data class BoosterOpenResult(
    val success: Boolean,
    val error: String? = null,
    val rewards: List<BoosterReward> = emptyList(),
    val gemsRemaining: Int = 0,
    val dustEarned: Int = 0
)

object BoosterService {

    private const val BOOSTER_PRICE = 1000
    private const val RARE_CHANCE = 10
    private const val PAQUET_SLOT_CHANCE = 1
    private const val RARE_PITY = 10
    private const val COMMON_PITY = 3


    private val purchaseJson = Json

    fun buyAndOpenOnce(
        userId: Int,
        type: BoosterType,
        requestId: String
    ): BoosterOpenResult = transaction {

        // Sérialise les achats d'un même joueur.
        val profileExists = UserProfile
            .selectAll()
            .where { UserProfile.userAuthId eq userId }
            .forUpdate()
            .singleOrNull() != null

        if (!profileExists) {
            return@transaction BoosterOpenResult(
                success = false,
                error = "Profil introuvable"
            )
        }

        // Une demande déjà traitée doit rendre exactement
        // son ancien résultat, sans nouveau tirage.
        val previous = UserBoosterPurchases
            .selectAll()
            .where {
                (UserBoosterPurchases.userAuthId eq userId) and
                        (UserBoosterPurchases.requestId eq requestId)
            }
            .singleOrNull()

        if (previous != null) {
            if (previous[UserBoosterPurchases.boosterType] != type.name) {
                return@transaction BoosterOpenResult(
                    success = false,
                    error = "Identifiant d'achat déjà utilisé pour un autre booster"
                )
            }

            val savedResult = previous[UserBoosterPurchases.resultJson]
                ?: error("Achat enregistré sans résultat")

            return@transaction purchaseJson.decodeFromString<BoosterOpenResult>(
                savedResult
            )
        }

        // Réserve l'identifiant avant le tirage.
        // L'insertion est annulée si la transaction échoue.
        UserBoosterPurchases.insert {
            it[UserBoosterPurchases.userAuthId] = userId
            it[UserBoosterPurchases.requestId] = requestId
            it[UserBoosterPurchases.boosterType] = type.name
            it[UserBoosterPurchases.resultJson] = null
        }

        // La transaction de buyAndOpen rejoint celle-ci.
        val result = buyAndOpen(userId, type)

        // Les achats refusés ne sont pas enregistrés.
        if (!result.success) {
            UserBoosterPurchases.deleteWhere {
                (UserBoosterPurchases.userAuthId eq userId) and
                        (UserBoosterPurchases.requestId eq requestId)
            }
            return@transaction result
        }

        // Sauvegarde la réponse exacte avant le commit.
        UserBoosterPurchases.update({
            (UserBoosterPurchases.userAuthId eq userId) and
                    (UserBoosterPurchases.requestId eq requestId)
        }) {
            it[resultJson] = purchaseJson.encodeToString(result)
        }

        result
    }


    fun buyAndOpen(
        userId: Int,
        type: BoosterType
    ): BoosterOpenResult = transaction {
        val profile = UserProfile
            .selectAll()
            .where { UserProfile.userAuthId eq userId }
            .forUpdate()
            .singleOrNull()
            ?: return@transaction BoosterOpenResult(
                success = false,
                error = "Profil introuvable"
            )

        val gems = profile[UserProfile.gems]

        if (gems < BOOSTER_PRICE) {
            return@transaction BoosterOpenResult(
                success = false,
                error = "Gemmes insuffisantes"
            )
        }

        val setIds = when (type) {
            BoosterType.PERMANENT -> SeasonConfig.getPermanentSets()
            BoosterType.SEASONAL -> SeasonConfig.getSeasonalSets()
        }

        if (setIds.isEmpty()) {
            return@transaction BoosterOpenResult(
                success = false,
                error = "Booster indisponible cette saison"
            )
        }

        val sets = setIds.map(CardSetCatalog::getSet)
        val commons = sets.flatMap { it.commonCards }.distinct()
        val rares = sets.flatMap { it.rareCards }.distinct()

        check(commons.isNotEmpty()) {
            "Booster sans cartes communes"
        }
        check(rares.isNotEmpty()) {
            "Booster sans cartes rares"
        }

        val progress = UserBoosterProgress
            .selectAll()
            .where {
                (UserBoosterProgress.userAuthId eq userId) and
                        (UserBoosterProgress.boosterType eq type)
            }
            .forUpdate()
            .singleOrNull()
            ?: error("Progression booster manquante : $userId / $type")

        val slot7 = UserPaquetSlots
            .selectAll()
            .where {
                (UserPaquetSlots.userAuthId eq userId) and
                        (UserPaquetSlots.slotNumber eq 7)
            }
            .singleOrNull()
            ?: error("Emplacement 7 manquant pour $userId")

        val owned = UserCardCollection
            .selectAll()
            .where { UserCardCollection.userAuthId eq userId }
            .associateBy { it[UserCardCollection.cardId] }

        val copies = owned.mapValues { (_, row) ->
            row[UserCardCollection.ownedCopies]
        }.toMutableMap()

        val knownCardIds = owned.keys.toMutableSet()

        val rewards = mutableListOf<BoosterReward>()
        var totalDust = 0
        var foundRare = false
        var foundNewCommon = false

        val rarePityDue =
            progress[UserBoosterProgress.boostersWithoutRare] >= RARE_PITY - 1

        val commonPityDue =
            progress[UserBoosterProgress.boostersWithoutNewCommon] >= COMMON_PITY - 1

        fun grantCard(cardId: CardId, rare: Boolean) {
            val key = cardId.name
            val previousCopies = copies[key] ?: 0
            val previouslyOwned =
                owned[key]?.get(UserCardCollection.isOwned) == true ||
                        previousCopies > 0

            val isNew = !previouslyOwned && previousCopies == 0
            val usableLimit = (
                    CardCatalog.getCard(cardId).purchaseLimit ?: 3
                    ).coerceIn(1, 3)

            val dust = if (previousCopies >= usableLimit) {
                if (rare) 100 else 10
            } else {
                0
            }

            totalDust += dust

            // On conserve jusqu'à 3 exemplaires, même si
            // la limite d'utilisation de la carte est inférieure.
            val newCopies = (previousCopies + 1).coerceAtMost(3)
            copies[key] = newCopies

            if (key !in knownCardIds) {
                UserCardCollection.insert {
                    it[userAuthId] = userId
                    it[UserCardCollection.cardId] = key
                    it[isOwned] = true
                    it[ownedCopies] = newCopies
                    it[selectedSkinId] = null
                }
                knownCardIds.add(key)
            } else {
                UserCardCollection.update({
                    (UserCardCollection.userAuthId eq userId) and
                            (UserCardCollection.cardId eq key)
                }) {
                    it[isOwned] = true
                    it[ownedCopies] = newCopies
                }
            }

            if (rare) foundRare = true
            if (!rare && isNew) foundNewCommon = true

            rewards += BoosterReward(
                type = "CARD",
                cardId = key,
                isRare = rare,
                isNew = isNew,
                dustEarned = dust
            )
            // Débloquer automatiquement les cartes liées
            // uniquement si la carte principale n'est pas transformée en poussière.

            if (dust == 0) {
                for (linkedCardId in LinkedCardCatalog.getLinkedCards(cardId)) {
                    UserCardCollection.upsert(
                        UserCardCollection.userAuthId,
                        UserCardCollection.cardId
                    ) {
                        it[UserCardCollection.userAuthId] = userId
                        it[UserCardCollection.cardId] = linkedCardId.name
                        it[UserCardCollection.isOwned] = true
                    }
                }
            }

        }

        fun drawCommon(guaranteeNew: Boolean = false): CardId {
            if (guaranteeNew) {
                val missing = commons.filter { card ->
                    val key = card.name
                    (copies[key] ?: 0) == 0 &&
                            owned[key]?.get(UserCardCollection.isOwned) != true
                }

                if (missing.isNotEmpty()) {
                    return missing.random()
                }
            }

            return commons.random()
        }

        fun drawRare(): CardId = rares.random()

        // Récompense 1 : chance de débloquer le 7e paquet.
        val unlockPaquet =
            !slot7[UserPaquetSlots.isUnlocked] &&
                    Random.nextInt(100) < PAQUET_SLOT_CHANCE

        if (unlockPaquet) {
            val updated = UserPaquetSlots.update({
                (UserPaquetSlots.userAuthId eq userId) and
                        (UserPaquetSlots.slotNumber eq 7) and
                        (UserPaquetSlots.isUnlocked eq false)
            }) {
                it[isUnlocked] = true
            }

            check(updated == 1) {
                "Impossible de débloquer l'emplacement 7"
            }

            rewards += BoosterReward(type = "PAQUET_SLOT_7")
        } else {
            val rare = Random.nextInt(100) < RARE_CHANCE
            grantCard(
                if (rare) drawRare() else drawCommon(),
                rare
            )
        }

        // Récompense 2 : garantie rare au dixième booster.
        if (rarePityDue && !foundRare) {
            grantCard(drawRare(), true)
        } else {
            grantCard(drawCommon(), false)
        }

        // Récompense 3 : garantie d'une nouvelle commune.
        grantCard(
            drawCommon(
                guaranteeNew = commonPityDue && !foundNewCommon
            ),
            false
        )

        UserBoosterProgress.update({
            (UserBoosterProgress.userAuthId eq userId) and
                    (UserBoosterProgress.boosterType eq type)
        }) {
            it[boostersWithoutRare] =
                if (foundRare) 0
                else progress[UserBoosterProgress.boostersWithoutRare] + 1

            it[boostersWithoutNewCommon] =
                if (foundNewCommon) 0
                else progress[UserBoosterProgress.boostersWithoutNewCommon] + 1
        }

        UserProfile.update({
            UserProfile.userAuthId eq userId
        }) {
            it[UserProfile.gems] = gems - BOOSTER_PRICE
            it[UserProfile.dust] = profile[UserProfile.dust] + totalDust
        }

        BoosterOpenResult(
            success = true,
            rewards = rewards,
            gemsRemaining = gems - BOOSTER_PRICE,
            dustEarned = totalDust
        )
    }
}
