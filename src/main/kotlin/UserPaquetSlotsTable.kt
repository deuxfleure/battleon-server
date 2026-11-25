
package com.battleon

import org.jetbrains.exposed.sql.Table

enum class PaquetUnlockSource {
    BASE,
    PROGRESSION,
    BATTLE_PASS,
    PURCHASE,
    BOOSTER
}

object UserPaquetSlots : Table("user_paquet_slots") {

    val id = integer("id").autoIncrement()
    val userAuthId = integer("user_auth_id")

    val slotNumber = integer("slot_number")

    val unlockSource = enumerationByName<PaquetUnlockSource>(
        "unlock_source",
        20
    )

    // Déblocage permanent.
    // Le slot Battle Pass est disponible selon l'état du pass.
    val isUnlocked = bool("is_unlocked").default(false)

    val paquetName = varchar("paquet_name", 60).nullable()

    // Composition enregistrée, sérialisée en JSON.
    val paquetCards = text("paquet_cards").default("[]")

    // Validité des paquets.
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
