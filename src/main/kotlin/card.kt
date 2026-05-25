package com.battleon

import kotlinx.serialization.Serializable

@Serializable
enum class CardId {
    COLLECTOR,
    WARRIOR,
    CURSED,
    HEALER,
    AGILE,
    ARAIGNEEGEANTE,
    BULLDOZER,
    CHAMANE,
    DURACUIRE,
    EPINENOIRE,
    GRACIEUSEROBUSTE,
    ROIBAMBOU,
    SANGUINAIRE,
    VIOLENT,
    DANSEUSEMACABRE,
    DEVINDESTENEBRES,
    GARDIENDESENFERS,
    MAITREDESCARNAGES,
    MAUVAISGENIE,
    NECROMANCIEN,
    PORTEURDEGIDEDECHU,
    PYROMANCIEN,
    ROIDEMON,
    SANGPACTE,
    AMBASSADRICE,
    BARBAREVIKING,
    DEVINDELUMIERE,
    DUC,
    ENVOUTEUSE,
    MAGE,
    REVENDEUR,
    SENTINELLE,
    TACTICIEN,
    THERMOGUERRIER,
    SQUELETTE_HANTE,
    SQUELETTE_MALEDICTION,
    SQUELETTE_FAIBLESSE,
    ARAIGNEESOLDAT,
    NOURRICEARAIGNEE,
    OEUFDARAIGNEE,


    // SEASON Mercenary
    MERCENARY_TNT,
    MERCENARY_ASSASSIN,
    MERCENARY_COLOSSE,
    MERCENARY_DIPLOMATE,
    MERCENARY_FANATIQUE,
    MERCENARY_MAITRE_D_ARMES,
    MERCENARY_PORTEUR_DE_PESTE,
    MERCENARY_PSYCHOPATHE,
    MERCENARY_VOLEUR,
}

@Serializable
data class Card(
    val id: CardId,
    val power: Int,
    val cost: Int,
    val faction: CardFaction,
    val purchaseLimit: Int? = null,
    val hasAmbush: Boolean = false,
    val hasPreparation: Boolean = false,
    val isSpider: Boolean = false
)