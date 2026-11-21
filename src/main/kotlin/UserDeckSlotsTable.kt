
package com.battleon

import org.jetbrains.exposed.sql.Table

enum class DeckUnlockSource {
    BASE,
    BATTLE_PASS,
    PURCHASE,
    BOOSTER
}

object UserDeckSlots : Table("user_deck_slots") {

    val id = integer("id").autoIncrement()
    val userAuthId = integer("user_auth_id")

    val slotNumber = integer("slot_number")

    val unlockSource = enumerationByName<DeckUnlockSource>(
        "unlock_source",
        20
    )

    // Déblocage permanent.
    // Le slot Battle Pass est disponible selon l'état du pass.
    val isUnlocked = bool("is_unlocked").default(false)

    val deckName = varchar("deck_name", 60).nullable()

    // Composition enregistrée, sérialisée en JSON.
    val deckCards = text("deck_cards").default("[]")

    // Validité des decks.
    val standardReady = bool("standard_ready").default(false)
    val rankedReady = bool("ranked_ready").default(false)

    // Version des bannissements utilisée pour la validation.
    val validatedBanVersion = integer("validated_ban_version")
        .default(0)

    val updatedAtMillis = long("updated_at_millis")
        .default(0L)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userAuthId, slotNumber)
    }
}
