package com.battleon

enum class ProfileCosmeticType {
    AVATAR,
    TITLE,
    FACTION
}

data class ProfileCosmeticDefinition(
    val id: String,
    val type: ProfileCosmeticType,
    val displayName: String
)

object ProfileCosmeticCatalog {

    val defaultUnlocked = listOf(
        ProfileCosmeticDefinition(
            id = "AVATAR_TRAVELLER",
            type = ProfileCosmeticType.AVATAR,
            displayName = "Voyageur"
        ),
        ProfileCosmeticDefinition(
            id = "TITLE_NOVICE",
            type = ProfileCosmeticType.TITLE,
            displayName = "Novice"
        ),
        ProfileCosmeticDefinition(
            id = "TITLE_ALPHA_TESTER",
            type = ProfileCosmeticType.TITLE,
            displayName = "Alpha testeur"
        ),
        ProfileCosmeticDefinition(
            id = "FACTION_HUMAN",
            type = ProfileCosmeticType.FACTION,
            displayName = "Humain"
        ),
        ProfileCosmeticDefinition(
            id = "FACTION_BEAST",
            type = ProfileCosmeticType.FACTION,
            displayName = "Bête"
        ),
        ProfileCosmeticDefinition(
            id = "FACTION_DEMON",
            type = ProfileCosmeticType.FACTION,
            displayName = "Démon"
        ),
        ProfileCosmeticDefinition(
            id = "title_test_account",
            type = ProfileCosmeticType.TITLE,
            displayName = "Compte de test"
        ),
        ProfileCosmeticDefinition(
            id = "title_the_primordial",
            type = ProfileCosmeticType.TITLE,
            displayName = "Le Primordial"
        ),
    )

    fun findById(id: String): ProfileCosmeticDefinition? {
        return defaultUnlocked.find { it.id == id }
    }

}