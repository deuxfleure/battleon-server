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

    private val allCosmetics = listOf(
        ProfileCosmeticDefinition(
            id = "AVATAR_TRAVELLER",
            type = ProfileCosmeticType.AVATAR,
            displayName = "Voyageur"
        ),
        ProfileCosmeticDefinition(
            id = "AVATAR_MALVARIS",
            type = ProfileCosmeticType.AVATAR,
            displayName = "Malvaris"
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
        )
    )

    private val defaultUnlockedIds = setOf(
        "AVATAR_TRAVELLER",
        "TITLE_NOVICE",
        "TITLE_ALPHA_TESTER",
        "FACTION_HUMAN",
        "FACTION_BEAST",
        "FACTION_DEMON",
        "title_test_account",
        "title_the_primordial"
    )

    val defaultUnlocked: List<ProfileCosmeticDefinition> =
        allCosmetics.filter { it.id in defaultUnlockedIds }

    fun findById(id: String): ProfileCosmeticDefinition? {
        return allCosmetics.find { it.id == id }
    }
}