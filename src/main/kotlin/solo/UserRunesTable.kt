package com.battleon.solo

import com.battleon.UserAuth
import org.jetbrains.exposed.sql.Table

object UserRunes : Table("user_runes") {

    val userId = integer("user_id")
        .references(UserAuth.id)

    val runeId = varchar("rune_id", 50)

    override val primaryKey = PrimaryKey(
        userId,
        runeId
    )
}