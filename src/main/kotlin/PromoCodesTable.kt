package com.battleon

import org.jetbrains.exposed.sql.Table

object PromoCodes : Table("promo_codes") {
    val id = integer("id").autoIncrement()

    val code = varchar("code", 40).uniqueIndex()

    // Exemples :
    // GEMS, DUST, AVATAR, TITLE
    val rewardType = varchar("reward_type", 30)

    // Pour GEMS / DUST : peut contenir un texte libre ou rester null
    // Pour AVATAR / TITLE : id de la récompense
    val rewardValue = varchar("reward_value", 100).nullable()

    // Pour GEMS / DUST : quantité
    val rewardAmount = integer("reward_amount").nullable()

    val isUsed = bool("is_used").default(false)
    val usedByUserAuthId = integer("used_by_user_auth_id").nullable()
    val usedAtMillis = long("used_at_millis").nullable()

    val expiresAtMillis = long("expires_at_millis").nullable()
    val createdAtMillis = long("created_at_millis")

    override val primaryKey = PrimaryKey(id)
}