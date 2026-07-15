package com.battleon.solo

enum class SoloRuneType {
    MINOR,
    MAJOR
}

data class SoloRuneDefinition(
    val id: String,
    val type: SoloRuneType
)

object SoloRuneCatalog {

    private val runes = listOf(
        SoloRuneDefinition(
            id = "rune_minor_1",
            type = SoloRuneType.MINOR
        ),
        SoloRuneDefinition(
            id = "rune_minor_2",
            type = SoloRuneType.MINOR
        ),
        SoloRuneDefinition(
            id = "rune_minor_3",
            type = SoloRuneType.MINOR
        ),
        SoloRuneDefinition(
            id = "rune_minor_4",
            type = SoloRuneType.MINOR
        ),
        SoloRuneDefinition(
            id = "rune_minor_5",
            type = SoloRuneType.MINOR
        ),
        SoloRuneDefinition(
            id = "rune_minor_6",
            type = SoloRuneType.MINOR
        ),
        SoloRuneDefinition(
            id = "rune_minor_7",
            type = SoloRuneType.MINOR
        ),
        SoloRuneDefinition(
            id = "rune_minor_8",
            type = SoloRuneType.MINOR
        ),
        SoloRuneDefinition(
            id = "rune_minor_9",
            type = SoloRuneType.MINOR
        ),
        SoloRuneDefinition(
            id = "rune_minor_10",
            type = SoloRuneType.MINOR
        ),

        SoloRuneDefinition(
            id = "rune_major_1",
            type = SoloRuneType.MAJOR
        ),
        SoloRuneDefinition(
            id = "rune_major_2",
            type = SoloRuneType.MAJOR
        ),
        SoloRuneDefinition(
            id = "rune_major_3",
            type = SoloRuneType.MAJOR
        ),
        SoloRuneDefinition(
            id = "rune_major_4",
            type = SoloRuneType.MAJOR
        ),
        SoloRuneDefinition(
            id = "rune_major_5",
            type = SoloRuneType.MAJOR
        )
    )

    fun findById(id: String): SoloRuneDefinition? {
        return runes.firstOrNull { it.id == id }
    }
}