package com.battleon.solo

enum class SoloRuneType {
    MINOR,
    MAJOR
}

enum class AmbushWindow {
    BEFORE_REVEAL,
    BEFORE_EFFECTS,
    BEFORE_COMBAT,
    BEFORE_POST_COMBAT,
    BEFORE_SHOP
}

enum class SoloRuneEffectType {
    CURRENT_CARD_POWER_BONUS,
    GAIN_GOLD_AND_SCRY,
    GAIN_GOLD,
    BLOCK_OPPONENT_CARD_EFFECT,
    HEAL,
    GIVE_BURN_AND_DISEASE,
    OPPONENT_LOSE_GOLD_AND_DARK_SCRY,
    DESTROY_OPPONENT_AMBUSH_OR_PREPARATION
}

data class SoloRuneDefinition(
    val id: String,
    val type: SoloRuneType,
    val unlockMissionId: String,
    val activationWindows: Set<AmbushWindow>,
    val effectType: SoloRuneEffectType,
    val value: Int = 0
)

object SoloRuneCatalog {

    private val allWindows = AmbushWindow.entries.toSet()

    private val runes = listOf(

        // M3 : +2 Force
        SoloRuneDefinition(
            id = "rune_minor_1",
            type = SoloRuneType.MINOR,
            unlockMissionId = "c1_m03",
            activationWindows = setOf(
                AmbushWindow.BEFORE_EFFECTS,
                AmbushWindow.BEFORE_COMBAT
            ),
            effectType = SoloRuneEffectType.CURRENT_CARD_POWER_BONUS,
            value = 2
        ),

        // M7 : +1 Pièce et Scruter 2
        SoloRuneDefinition(
            id = "rune_minor_2",
            type = SoloRuneType.MINOR,
            unlockMissionId = "c1_m07",
            activationWindows = setOf(
                AmbushWindow.BEFORE_REVEAL,
                AmbushWindow.BEFORE_EFFECTS,
                AmbushWindow.BEFORE_SHOP
            ),
            effectType = SoloRuneEffectType.GAIN_GOLD_AND_SCRY,
            value = 2
        ),

        // M18 : bloque l'effet de la carte adverse
        SoloRuneDefinition(
            id = "rune_minor_3",
            type = SoloRuneType.MINOR,
            unlockMissionId = "c1_m18",
            activationWindows = setOf(
                AmbushWindow.BEFORE_EFFECTS
            ),
            effectType = SoloRuneEffectType.BLOCK_OPPONENT_CARD_EFFECT
        ),

        // M24 : Brûlure + Maladie
        SoloRuneDefinition(
            id = "rune_minor_4",
            type = SoloRuneType.MINOR,
            unlockMissionId = "c1_m24",
            activationWindows = setOf(
                AmbushWindow.BEFORE_EFFECTS,
                AmbushWindow.BEFORE_COMBAT
            ),
            effectType = SoloRuneEffectType.GIVE_BURN_AND_DISEASE
        ),

        // M30 : +3 PV
        SoloRuneDefinition(
            id = "rune_minor_5",
            type = SoloRuneType.MINOR,
            unlockMissionId = "c1_m30",
            activationWindows = allWindows,
            effectType = SoloRuneEffectType.HEAL,
            value = 3
        ),

        // M34 : -1 Pièce adverse + Scruter sombre 2
        SoloRuneDefinition(
            id = "rune_minor_6",
            type = SoloRuneType.MINOR,
            unlockMissionId = "c1_m34",
            activationWindows = setOf(
                AmbushWindow.BEFORE_REVEAL,
                AmbushWindow.BEFORE_EFFECTS,
                AmbushWindow.BEFORE_SHOP
            ),
            effectType = SoloRuneEffectType.OPPONENT_LOSE_GOLD_AND_DARK_SCRY,
            value = 2
        ),

        // M15 : +3 Pièces
        SoloRuneDefinition(
            id = "rune_major_1",
            type = SoloRuneType.MAJOR,
            unlockMissionId = "c1_m15",
            activationWindows = setOf(
                AmbushWindow.BEFORE_EFFECTS,
                AmbushWindow.BEFORE_SHOP
            ),
            effectType = SoloRuneEffectType.GAIN_GOLD,
            value = 3
        ),

        // M20 : +5 PV
        SoloRuneDefinition(
            id = "rune_major_2",
            type = SoloRuneType.MAJOR,
            unlockMissionId = "c1_m20",
            activationWindows = allWindows,
            effectType = SoloRuneEffectType.HEAL,
            value = 5
        ),

        // M37 : +5 Force
        SoloRuneDefinition(
            id = "rune_major_3",
            type = SoloRuneType.MAJOR,
            unlockMissionId = "c1_m37",
            activationWindows = setOf(
                AmbushWindow.BEFORE_EFFECTS,
                AmbushWindow.BEFORE_COMBAT
            ),
            effectType = SoloRuneEffectType.CURRENT_CARD_POWER_BONUS,
            value = 5
        ),

        // M40 : détruit une Préparation / Embuscade adverse
        SoloRuneDefinition(
            id = "rune_major_4",
            type = SoloRuneType.MAJOR,
            unlockMissionId = "c1_m40",
            activationWindows = allWindows,
            effectType = SoloRuneEffectType.DESTROY_OPPONENT_AMBUSH_OR_PREPARATION
        )
    )

    fun findById(id: String): SoloRuneDefinition? {
        return runes.firstOrNull { it.id == id }
    }

    fun findAllByIds(ids: List<String>): List<SoloRuneDefinition> {
        return ids.mapNotNull(::findById)
    }
}