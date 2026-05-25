package com.battleon

import org.jetbrains.exposed.sql.Table

object UserProfile : Table("user_profile") {
    val id = integer("id").autoIncrement()
    val userAuthId = integer("user_auth_id").uniqueIndex()
    val displayName = varchar("display_name", 30)
    val avatar = varchar("avatar", 50).nullable()
    val title = varchar("title", 50).nullable()
    val chosenFaction = varchar("chosen_faction", 30).default("FACTION_HUMAN")
    val tutorialEnabled = bool("tutorial_enabled").default(true)
    val shopPopupEnabled = bool("shop_popup_enabled").default(true)
    val soundEnabled = bool("sound_enabled").default(true)
    val musicEnabled = bool("music_enabled").default(true)
    val elo = integer("elo").default(1000)
    val wins = integer("wins").default(0)
    val losses = integer("losses").default(0)
    val gems = integer("gems").default(0)
    val dust = integer("dust").default(0)

    val email = varchar("email", 100).nullable()

    val language = varchar("language", 10).default("FR")

    val hasBattlePass = bool("has_battle_pass").default(false)

    override val primaryKey = PrimaryKey(id)
}