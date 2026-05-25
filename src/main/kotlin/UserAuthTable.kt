package com.battleon

import org.jetbrains.exposed.sql.Table

object UserAuth : Table("user_auth") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 30).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)

    override val primaryKey = PrimaryKey(id)
}