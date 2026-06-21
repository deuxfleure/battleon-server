package com.battleon

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import org.jetbrains.exposed.sql.update

object UserService {

    fun createUser(user: User) {
        val hashedPassword = BCrypt.hashpw(user.password, BCrypt.gensalt())

        transaction {
            val authId = UserAuth.insert {
                it[username] = user.username
                it[passwordHash] = hashedPassword
            } get UserAuth.id

            UserProfile.insert {
                it[userAuthId] = authId
                it[displayName] = user.displayName
                it[language] = user.language
                it[avatar] = null
                it[title] = null
                it[soundEnabled] = true
                it[musicEnabled] = true
                it[elo] = 1000
                it[wins] = 0
                it[losses] = 0
                it[gems] = 0
                it[dust] = 0
                it[email] = null
                it[hasBattlePass] = false
            }

            ProfileCosmeticCatalog.defaultUnlocked.forEach { cosmetic ->
                UserProfileCosmetics.insert {
                    it[userAuthId] = authId
                    it[cosmeticId] = cosmetic.id
                    it[cosmeticType] = cosmetic.type.name
                    it[unlockedAtMillis] = System.currentTimeMillis()
                }
            }
        }
    }

    fun initializePlayerCollection(userId: Int) {
        val baseCollection = StarterCollection.cards

        transaction {
            baseCollection.forEach { cardId ->
                UserCardCollection.insert {
                    it[userAuthId] = userId
                    it[UserCardCollection.cardId] = cardId.name
                    it[isOwned] = true
                    it[ownedCopies] = 3
                    it[selectedSkinId] = null
                }
            }
        }
    }

    fun userExists(username: String): Boolean {
        return transaction {
            UserAuth.selectAll()
                .where { UserAuth.username eq username }
                .count() > 0
        }
    }

    fun validateUser(username: String, password: String): AuthUser? {
        val authUser = getAuthUserByUsername(username) ?: return null

        return if (BCrypt.checkpw(password, authUser.passwordHash)) {
            authUser
        } else {
            null
        }
    }

    fun getAuthUserByUsername(username: String): AuthUser? {
        return transaction {
            UserAuth.selectAll()
                .where { UserAuth.username eq username }
                .singleOrNull()
                ?.let {
                    AuthUser(
                        id = it[UserAuth.id],
                        username = it[UserAuth.username],
                        passwordHash = it[UserAuth.passwordHash]
                    )
                }
        }
    }

    fun getMe(userId: Int): MeResponse? {
        return transaction {
            val authRow = UserAuth.selectAll()
                .where { UserAuth.id eq userId }
                .singleOrNull()

            val profileRow = UserProfile.selectAll()
                .where { UserProfile.userAuthId eq userId }
                .singleOrNull()

            if (authRow != null && profileRow != null) {
                MeResponse(
                    id = authRow[UserAuth.id],
                    username = authRow[UserAuth.username],
                    displayName = profileRow[UserProfile.displayName],
                    language = profileRow[UserProfile.language],
                    avatar = profileRow[UserProfile.avatar],
                    title = profileRow[UserProfile.title],
                    chosenFaction = profileRow[UserProfile.chosenFaction],
                    tutorialEnabled = profileRow[UserProfile.tutorialEnabled],
                    shopPopupEnabled = profileRow[UserProfile.shopPopupEnabled],
                    soundEnabled = profileRow[UserProfile.soundEnabled],
                    musicEnabled = profileRow[UserProfile.musicEnabled],
                    gems = profileRow[UserProfile.gems],
                    dust = profileRow[UserProfile.dust],
                    wins = profileRow[UserProfile.wins],
                    losses = profileRow[UserProfile.losses],
                    email = profileRow[UserProfile.email],
                    hasBattlePass = profileRow[UserProfile.hasBattlePass],
                )
            } else {
                null
            }
        }
    }

    //==============================================
    //======== battle Pass cost ====================
    //==============================================
    private const val BATTLE_PASS_COST = 9999

    fun buyBattlePass(userId: Int): Boolean {
        return transaction {
            val profile = UserProfile
                .selectAll()
                .where { UserProfile.userAuthId eq userId }
                .singleOrNull()
                ?: return@transaction false

            val alreadyHasBattlePass = profile[UserProfile.hasBattlePass]
            val currentGems = profile[UserProfile.gems]

            if (alreadyHasBattlePass) return@transaction false
            if (currentGems < BATTLE_PASS_COST) return@transaction false

            UserProfile.update({ UserProfile.userAuthId eq userId }) {
                it[hasBattlePass] = true
                it[gems] = currentGems - BATTLE_PASS_COST
            } > 0
        }
    }

    fun getPlayerCollection(userId: Int): List<PlayerCollectionCard> {
        return transaction {
            UserCardCollection
                .selectAll()
                .where { UserCardCollection.userAuthId eq userId }
                .map { row ->
                    val cardIdString = row[UserCardCollection.cardId]

                    val cardEnum = try {
                        CardId.valueOf(cardIdString)
                    } catch (_: IllegalArgumentException) {
                        null
                    }

                    val canBeTried = if (cardEnum != null) {
                        !CardCatalog.isExcludedFromShop(cardEnum)
                    } else {
                        false
                    }

                    val card = cardEnum?.let {
                        CardCatalog.getCard(it)
                    }

                    val ownedCopies = row[UserCardCollection.ownedCopies]

                    PlayerCollectionCard(
                        cardId = cardIdString,
                        isOwned = ownedCopies > 0,
                        selectedSkinId = row[UserCardCollection.selectedSkinId],
                        canBeTried = canBeTried,
                        ownedCopies = ownedCopies,
                        maxCopies = if (canBeTried) 3 else 1,
                        cost = card?.cost ?: 0,
                        power = card?.power ?: 0
                    )
                }
        }
    }

    fun getUnlockedProfileCosmetics(userId: Int): ProfileCosmeticsResponse {
        return transaction {
            val cosmetics = UserProfileCosmetics
                .selectAll()
                .where { UserProfileCosmetics.userAuthId eq userId }
                .mapNotNull { row ->
                    val cosmeticId = row[UserProfileCosmetics.cosmeticId]
                    val definition = ProfileCosmeticCatalog.findById(cosmeticId)

                    definition?.let {
                        ProfileCosmeticResponse(
                            id = it.id,
                            type = it.type.name,
                            displayName = it.displayName
                        )
                    }
                }

            ProfileCosmeticsResponse(cosmetics = cosmetics)
        }
    }

    fun getOwnedCardIds(userId: Int): List<String> {
        return transaction {
            UserCardCollection
                .selectAll()
                .where {
                    (UserCardCollection.userAuthId eq userId) and
                            (UserCardCollection.isOwned eq true)
                }
                .map { it[UserCardCollection.cardId] }
        }
    }

    fun updateProfile(
        userId: Int,
        request: UpdateProfileRequest
    ): Boolean {
        val allowedFactions = listOf(
            "FACTION_HUMAN",
            "FACTION_BEAST",
            "FACTION_DEMON"
        )

        val sanitizedFaction = request.chosenFaction
            ?.uppercase()
            ?.takeIf { it in allowedFactions }


        val sanitizedLanguage = request.language
            ?.uppercase()

        return transaction {
            UserProfile.update({ UserProfile.userAuthId eq userId }) {
                if (request.avatar != null) {
                    it[avatar] = request.avatar
                }

                if (request.title != null) {
                    it[title] = request.title
                }

                if (sanitizedLanguage != null) {
                    it[language] = sanitizedLanguage
                }

                if (sanitizedFaction != null) {
                    it[chosenFaction] = sanitizedFaction
                }

                if (request.soundEnabled != null) {
                    it[soundEnabled] = request.soundEnabled
                }

                if (request.musicEnabled != null) {
                    it[musicEnabled] = request.musicEnabled
                }

                if (request.tutorialEnabled != null) {
                    it[tutorialEnabled] = request.tutorialEnabled
                }

                if (request.shopPopupEnabled != null) {
                    it[shopPopupEnabled] = request.shopPopupEnabled
                }
            } > 0
        }
    }

    fun addWin(userId: Int): Boolean {
        return transaction {
            UserProfile.update({ UserProfile.userAuthId eq userId }) {
                with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                    it.update(wins, wins + 1)
                }
            } > 0
        }
    }

    fun addLoss(userId: Int): Boolean {
        return transaction {
            UserProfile.update({ UserProfile.userAuthId eq userId }) {
                with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                    it.update(losses, losses + 1)
                }
            } > 0
        }
    }
}