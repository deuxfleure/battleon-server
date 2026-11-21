package com.battleon

enum class CardSetId {
    BASE,
    SET_01
}

data class CardSetDefinition(
    val id: CardSetId,
    val commonCards: List<CardId>,
    val rareCards: List<CardId>
) {
    val allCards: List<CardId>
        get() = commonCards + rareCards
}

object CardSetCatalog {

    private val sets = listOf(

        // =========================================
        // BOOSTER DE BASE
        // =========================================

        CardSetDefinition(
            id = CardSetId.BASE,

            commonCards = listOf(
                // BÊTES
                CardId.ARAIGNEEJUVENILE,
                CardId.ECUMEDESMERS,
                CardId.ENCHAINE,
                CardId.ESPIEGLE,
                CardId.FORCETRANQUILLE,
                CardId.TIQUE,
                CardId.COSTAUD,
                CardId.MALIN,
                CardId.SAUVAGE,
                CardId.REPRESAILLES,

                // DÉMONS
                CardId.ONIFLEAUDEGIVRE,
                CardId.ENRAGE,
                CardId.OEIL,
                CardId.BATARD,
                CardId.CONTAGIEUX,
                CardId.DEVOREURDESPOIRS,
                CardId.GARDEVAUDOU,
                CardId.JONGLEURDEJANTE,
                CardId.MANIPULATEUR,
                CardId.PERFIDE,
                CardId.TISSEURDEMENSONGES,

                // HUMAINS
                CardId.EVEQUE,
                CardId.EXPERIENCEDELABORATOIRE,
                CardId.SOURNOISE,
                CardId.CANONNIER,
                CardId.DISCIPLEDELAFLAMME,
                CardId.FIDELE,
                CardId.MUSICIEN,
                CardId.PORTEPAROLE,
                CardId.ROI,
                CardId.SULTAN,
                CardId.TRAPPEUR
            ),

            rareCards = listOf(
                CardId.REINEARAIGNEE,
                CardId.GARDIENSACRE,
                CardId.EMPOISONNEUSE,
                CardId.PALADIN
            )
        ),

        // =========================================
        // SET 01
        // =========================================

        CardSetDefinition(
            id = CardSetId.SET_01,

            commonCards = listOf(
                CardId.ENIGME,
                CardId.VISIONPERCANTE,
                CardId.LAMEERRANTE,
                CardId.LYCANTHROPE,
                CardId.MOUCHERON
            ),

            rareCards = listOf(
                CardId.SEIGNEURVAMPIRE
            )
        )

    ).associateBy { it.id }

    fun getSet(id: CardSetId): CardSetDefinition {
        return sets.getValue(id)
    }

    fun getAllSets(): List<CardSetDefinition> {
        return sets.values.toList()
    }
}