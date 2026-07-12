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
        )
    )

    fun findById(id: String): SoloRuneDefinition? {
        return runes.firstOrNull { it.id == id }
    }
}