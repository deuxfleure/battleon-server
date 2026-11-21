
package com.battleon

import org.jetbrains.exposed.sql.Table

enum class BoosterType {
    PERMANENT,
    SEASONAL
}

object UserBoosterProgress : Table("user_booster_progress") {

    val id = integer("id").autoIncrement()
    val userAuthId = integer("user_auth_id")

    val boosterType = enumerationByName<BoosterType>(
        "booster_type",
        20
    )

    // Nombre de boosters consécutifs sans rare.
    val boostersWithoutRare = integer("boosters_without_rare")
        .default(0)

    // Nombre de boosters consécutifs sans nouvelle commune.
    val boostersWithoutNewCommon = integer("boosters_without_new_common")
        .default(0)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userAuthId, boosterType)
    }
}
