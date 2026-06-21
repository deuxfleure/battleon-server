package com.battleon

import org.jetbrains.exposed.sql.Table

object UserCardCollection : Table("user_card_collection") {
    val id = integer("id").autoIncrement()
    val userAuthId = integer("user_auth_id")
    val cardId = varchar("card_id", 50)
    val isOwned = bool("is_owned").default(false)
    val ownedCopies = integer("owned_copies").default(0)
    val selectedSkinId = varchar("selected_skin_id", 50).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(true, userAuthId, cardId)
    }
}