package com.battleon

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.battleon.solo.UserSoloMissionProgress
import com.battleon.solo.UserRunes
import com.battleon.solo.UserSoloRuneLoadout


object DatabaseFactory {
    fun init() {
        Database.connect(
            url = "jdbc:postgresql://localhost:5432/battleon_db",
            driver = "org.postgresql.Driver",
            user = "battleon",
            password = "battleon123$"
        )
        transaction {
            SchemaUtils.create(
                UserAuth,
                UserProfile,
                UserCardCollection,
                UserProfileCosmetics,
                PromoCodes,
                UserSoloMissionProgress,
                UserRunes,
                UserSoloRuneLoadout
            )
        }
    }
}