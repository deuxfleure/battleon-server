package com.battleon

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
                it[battlePassCoupons] = 0
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
        PlayerProgressInitializer.initializePlayer(
            getAuthUserByUsername(user.username)!!.id
        )
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

            val pityByType = UserBoosterProgress.selectAll()
                .where { UserBoosterProgress.userAuthId eq userId }
                .associate { row ->
                    row[UserBoosterProgress.boosterType] to
                            row[UserBoosterProgress.boostersWithoutRare]
                }

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
                    battlePassCoupons = profileRow[UserProfile.battlePassCoupons],
                    permanentRarePity = pityByType[BoosterType.PERMANENT] ?: 0,
                    seasonalRarePity = pityByType[BoosterType.SEASONAL] ?: 0,
                    seasonalBoosterAvailable = SeasonConfig.isSeasonalBoosterAvailable(),
                )
            } else {
                null
            }
        }
    }

    //==============================================
    //======== Battle Pass : coupon ================
    //==============================================

    fun activateBattlePassWithCoupon(userId: Int): Boolean {
        return transaction {
            // Verrouille le profil pendant l'activation pour éviter
            // que deux requêtes consomment le même coupon.
            val profile = UserProfile
                .selectAll()
                .where { UserProfile.userAuthId eq userId }
                .forUpdate()
                .singleOrNull()
                ?: return@transaction false

            if (profile[UserProfile.hasBattlePass]) {
                return@transaction false
            }

            val coupons = profile[UserProfile.battlePassCoupons]
            if (coupons < 1) {
                return@transaction false
            }

            // Vérifier que le cinquième emplacement existe AVANT
            // de consommer le coupon.
            val slotExists = UserPaquetSlots
                .selectAll()
                .where {
                    (UserPaquetSlots.userAuthId eq userId) and
                            (UserPaquetSlots.slotNumber eq 5)
                }
                .singleOrNull() != null

            if (!slotExists) {
                return@transaction false
            }

            // Débloquer l'emplacement.
            val updatedSlots = UserPaquetSlots.update({
                (UserPaquetSlots.userAuthId eq userId) and
                        (UserPaquetSlots.slotNumber eq 5)
            }) {
                it[isUnlocked] = true
            }

            // Une anomalie doit annuler toute la transaction.
            check(updatedSlots == 1) {
                "Battle Pass: impossible de débloquer le slot 5"
            }

            // Activer le Battle Pass et consommer un coupon.
            val updatedProfiles = UserProfile.update({
                UserProfile.userAuthId eq userId
            }) {
                it[hasBattlePass] = true
                it[battlePassCoupons] = coupons - 1
            }

            check(updatedProfiles == 1) {
                "Battle Pass: impossible de mettre à jour le profil"
            }

            true
        }
    }

    fun redeemPromoCode(
        userId: Int,
        rawCode: String
    ): RedeemPromoCodeResponse {
        val normalizedCode = rawCode
            .trim()
            .uppercase()
            .replace(" ", "")

        if (normalizedCode.isBlank()) {
            return RedeemPromoCodeResponse(
                success = false,
                message = "Code vide",
                error = "EMPTY_CODE"
            )
        }

        return transaction {
            val now = System.currentTimeMillis()

            val profile = UserProfile
                .selectAll()
                .where { UserProfile.userAuthId eq userId }
                .singleOrNull()
                ?: return@transaction RedeemPromoCodeResponse(
                    success = false,
                    message = "Profil introuvable",
                    error = "PROFILE_NOT_FOUND"
                )

            val codeRow = PromoCodes
                .selectAll()
                .where { PromoCodes.code eq normalizedCode }
                .singleOrNull()
                ?: return@transaction RedeemPromoCodeResponse(
                    success = false,
                    message = "Code invalide",
                    error = "INVALID_CODE"
                )

            if (codeRow[PromoCodes.isUsed]) {
                return@transaction RedeemPromoCodeResponse(
                    success = false,
                    message = "Ce code a déjà été utilisé",
                    error = "CODE_ALREADY_USED"
                )
            }

            val expiresAtMillis = codeRow[PromoCodes.expiresAtMillis]
            if (expiresAtMillis != null && expiresAtMillis < now) {
                return@transaction RedeemPromoCodeResponse(
                    success = false,
                    message = "Ce code a expiré",
                    error = "CODE_EXPIRED"
                )
            }

            val rewardType = codeRow[PromoCodes.rewardType].uppercase()
            val rewardValue = codeRow[PromoCodes.rewardValue]
            val rewardAmount = codeRow[PromoCodes.rewardAmount]

            val rewardText = when (rewardType) {
                "GEMS" -> {
                    val amount = rewardAmount ?: return@transaction RedeemPromoCodeResponse(
                        success = false,
                        message = "Code mal configuré",
                        error = "INVALID_REWARD_CONFIGURATION"
                    )

                    UserProfile.update({ UserProfile.userAuthId eq userId }) {
                        it[gems] = profile[UserProfile.gems] + amount
                    }

                    "$amount gemmes"
                }

                "DUST" -> {
                    val amount = rewardAmount ?: return@transaction RedeemPromoCodeResponse(
                        success = false,
                        message = "Code mal configuré",
                        error = "INVALID_REWARD_CONFIGURATION"
                    )

                    UserProfile.update({ UserProfile.userAuthId eq userId }) {
                        it[dust] = profile[UserProfile.dust] + amount
                    }

                    "$amount poussières"
                }

                "AVATAR" -> {
                    val cosmeticId = rewardValue ?: return@transaction RedeemPromoCodeResponse(
                        success = false,
                        message = "Code mal configuré",
                        error = "INVALID_REWARD_CONFIGURATION"
                    )

                    UserProfileCosmetics.insert {
                        it[UserProfileCosmetics.userAuthId] = userId
                        it[UserProfileCosmetics.cosmeticId] = cosmeticId
                        it[UserProfileCosmetics.cosmeticType] = "AVATAR"
                        it[UserProfileCosmetics.unlockedAtMillis] = now
                    }

                    "Avatar débloqué : $cosmeticId"
                }

                "TITLE" -> {
                    val cosmeticId = rewardValue ?: return@transaction RedeemPromoCodeResponse(
                        success = false,
                        message = "Code mal configuré",
                        error = "INVALID_REWARD_CONFIGURATION"
                    )

                    UserProfileCosmetics.insert {
                        it[UserProfileCosmetics.userAuthId] = userId
                        it[UserProfileCosmetics.cosmeticId] = cosmeticId
                        it[UserProfileCosmetics.cosmeticType] = "TITLE"
                        it[UserProfileCosmetics.unlockedAtMillis] = now
                    }

                    "Titre débloqué : $cosmeticId"
                }

                else -> {
                    return@transaction RedeemPromoCodeResponse(
                        success = false,
                        message = "Type de récompense inconnu",
                        error = "UNKNOWN_REWARD_TYPE"
                    )
                }
            }

            val markedAsUsed = PromoCodes.update({
                (PromoCodes.code eq normalizedCode) and
                        (PromoCodes.isUsed eq false)
            }) {
                it[isUsed] = true
                it[usedByUserAuthId] = userId
                it[usedAtMillis] = now
            } > 0

            if (!markedAsUsed) {
                return@transaction RedeemPromoCodeResponse(
                    success = false,
                    message = "Ce code a déjà été utilisé",
                    error = "CODE_ALREADY_USED"
                )
            }

            RedeemPromoCodeResponse(
                success = true,
                message = "Code validé",
                rewardText = rewardText
            )
        }
    }

    fun getPlayerCollection(userId: Int): List<PlayerCollectionCard> {
        return transaction {
            val ownedRowsByCardId = UserCardCollection
                .selectAll()
                .where { UserCardCollection.userAuthId eq userId }
                .associateBy { row ->
                    row[UserCardCollection.cardId]
                }

            CardCatalog.getAllCards().map { card ->
                val cardIdString = card.id.name
                val ownedRow = ownedRowsByCardId[cardIdString]

                val canBeTried = !CardCatalog.isExcludedFromShop(card.id)

                val maxCopies = if (canBeTried) {
                    card.purchaseLimit ?: 3
                } else {
                    1
                }

                val ownedCopies = (ownedRow
                    ?.get(UserCardCollection.ownedCopies)
                    ?: 0).coerceIn(0, 3)


                PlayerCollectionCard(
                    cardId = cardIdString,
                    isOwned = ownedRow?.get(UserCardCollection.isOwned) ?: false,
                    selectedSkinId = ownedRow
                        ?.get(UserCardCollection.selectedSkinId),
                    canBeTried = canBeTried,
                    ownedCopies = ownedCopies,
                    maxCopies = maxCopies,
                    cost = card.cost,
                    power = card.power,
                    faction = card.faction.name
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