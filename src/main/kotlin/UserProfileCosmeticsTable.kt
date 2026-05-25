package com.battleon

import org.jetbrains.exposed.sql.Table

object UserProfileCosmetics : Table("user_profile_cosmetics") {
    val id = integer("id").autoIncrement()
    val userAuthId = integer("user_auth_id")
    val cosmeticId = varchar("cosmetic_id", 80)
    val cosmeticType = varchar("cosmetic_type", 30)
    val unlockedAtMillis = long("unlocked_at_millis")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userAuthId, cosmeticId)
    }
}