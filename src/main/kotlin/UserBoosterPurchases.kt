
package com.battleon

import org.jetbrains.exposed.sql.Table

object UserBoosterPurchases : Table("user_booster_purchases") {

    val id = integer("id").autoIncrement()
    val userAuthId = integer("user_auth_id")
    val requestId = varchar("request_id", 36)
    val boosterType = varchar("booster_type", 20)
    val resultJson = text("result_json").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userAuthId, requestId)
    }
}
