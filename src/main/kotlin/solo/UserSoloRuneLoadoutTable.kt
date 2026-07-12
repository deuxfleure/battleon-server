package com.battleon.solo

import com.battleon.UserAuth
import org.jetbrains.exposed.sql.Table

object UserSoloRuneLoadout : Table("user_solo_rune_loadout") {

    val userId = integer("user_id")
        .references(UserAuth.id)

    val majorRuneId = varchar("major_rune_id", 50)
        .nullable()

    val minorLeftRuneId = varchar("minor_left_rune_id", 50)
        .nullable()

    val minorRightRuneId = varchar("minor_right_rune_id", 50)
        .nullable()

    override val primaryKey = PrimaryKey(userId)
}