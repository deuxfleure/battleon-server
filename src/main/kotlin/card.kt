package com.battleon

import kotlinx.serialization.Serializable

@Serializable
enum class CardId {
    // =====================================================
    // cartes de base
    // =====================================================
    COLLECTOR,
    WARRIOR,
    CURSED,
    HEALER,

    // =====================================================
    // cartes liée de base
    // =====================================================
    SQUELETTE_HANTE,
    SQUELETTE_MALEDICTION,
    SQUELETTE_FAIBLESSE,
    ARAIGNEESOLDAT,
    NOURRICEARAIGNEE,
    OEUFDARAIGNEE,

    // =====================================================
    // cartes starter
    // =====================================================
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

    // =====================================================
    // Mission Solo et cartes de booster de base (Saison 0)
    // =====================================================
    EMPOISONNEUSE,
    ONIFLEAUDEGIVRE,
    ARAIGNEEJUVENILE,
    ECUMEDESMERS,
    ENCHAINE,
    ENRAGE,
    ESPIEGLE,
    EVEQUE,
    EXPERIENCEDELABORATOIRE,
    OEIL,
    FORCETRANQUILLE,
    REINEARAIGNEE,
    SOURNOISE,
    TIQUE,
    BATARD,
    CANONNIER,
    CONTAGIEUX,
    COSTAUD,
    DEVOREURDESPOIRS,
    DISCIPLEDELAFLAMME,
    FIDELE,
    GARDEVAUDOU,
    GARDIENSACRE,
    JONGLEURDEJANTE,
    MALIN,
    MANIPULATEUR,
    MUSICIEN,
    PALADIN,
    PERFIDE,
    PORTEPAROLE,
    ROI,
    SAUVAGE,
    SULTAN,
    TISSEURDEMENSONGES,
    TRAPPEUR,
    REPRESAILLES,

    // =====================================================
    // Mission Solo et cartes de booster saison 1
    // =====================================================
    ENIGME,
    LAMEERRANTE,
    VISIONPERCANTE,
    LYCANTHROPE,
    MOUCHERON,
    SEIGNEURVAMPIRE,

    // =====================================================
    // Mission Solo et cartes de booster saison 2
    // =====================================================
    // À activer lors de la sortie de la Saison 2
    // INVENTEUR,
    // REPONSE,
    // GLADIATEURDAMNE,
    // MAITREDESPOTIONS,
    // MINEURDEFOND,
    // SABOTEUR,

    // =====================================================
    // Cartes de booster saison 6
    // =====================================================
    // INSTINCT,

    // =====================================================
    // cartes liée saison
    // =====================================================
    // S0
    GOLEMDOCRE,
    BOULETDECANON,
    // S1
    LOUPGAROU,

    // =====================================================
    // MERCENAIRES SAISON 1
    // =====================================================
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
enum class TacticalEntryType {
    AMBUSH,
    PREPARATION
}

@Serializable
data class TacticalCard(
    val card: Card,
    val entryType: TacticalEntryType
)

@Serializable
data class Card(
    val id: CardId,
    val power: Int,
    val cost: Int,
    val faction: CardFaction,
    val purchaseLimit: Int? = null,
    val hasAmbush: Boolean = false,
    val hasPreparation: Boolean = false,
    val isSpider: Boolean = false,
    val isUnique: Boolean = false
)