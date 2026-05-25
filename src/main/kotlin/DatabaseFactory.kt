package com.battleon

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction


object DatabaseFactory {
    fun init() {
        Database.connect(
            url = "jdbc:postgresql://localhost:5432/battleon",
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = "Suna1988!"
        )
        transaction {
            SchemaUtils.create(UserAuth, UserProfile, UserCardCollection)
        }
    }
}