package com.battleon

object CardCatalog {

    private val cardsById: Map<CardId, Card> = listOf(
        Card(CardId.COLLECTOR, 1, 0, CardFaction.MERCENARY, purchaseLimit = null,false,false,false),
        Card(CardId.WARRIOR, 3, 0,CardFaction.BEAST, purchaseLimit = null,false,false,false),
        Card(CardId.CURSED, 2, 0,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.HEALER, 2, 0,CardFaction.HUMAN, purchaseLimit = null,false,false,false),

        Card(CardId.SQUELETTE_HANTE, 1, 0,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.SQUELETTE_MALEDICTION, 1, 0,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.SQUELETTE_FAIBLESSE, 1, 0,CardFaction.DEMON, purchaseLimit = null,false,false,false),

        Card(CardId.ARAIGNEEGEANTE, 3, 5, CardFaction.BEAST, purchaseLimit = null, false, false, true),
        Card(CardId.ARAIGNEESOLDAT, 3, 0, CardFaction.BEAST, purchaseLimit = null, false, false, true),
        Card(CardId.NOURRICEARAIGNEE, 2, 0, CardFaction.BEAST, purchaseLimit = null, false, false, true),
        Card(CardId.OEUFDARAIGNEE, 0, 0, CardFaction.BEAST, purchaseLimit = null, false, false, true),

        Card(CardId.AGILE, 4, 4,CardFaction.BEAST, purchaseLimit = null,false,false,false),
        Card(CardId.ARAIGNEEGEANTE, 3, 5,CardFaction.BEAST, purchaseLimit = null,false,false,true),
        Card(CardId.BULLDOZER, 6, 6,CardFaction.BEAST, purchaseLimit = null,false,false,false),
        Card(CardId.CHAMANE, 3, 3,CardFaction.BEAST, purchaseLimit = null,false,false,false),
        Card(CardId.DURACUIRE, 3, 2,CardFaction.BEAST, purchaseLimit = null,false,false,false),
        Card(CardId.EPINENOIRE, 5, 4,CardFaction.BEAST, purchaseLimit = null,false,false,false),
        Card(CardId.GRACIEUSEROBUSTE, 7, 5,CardFaction.BEAST, purchaseLimit = null,false,false,false),
        Card(CardId.ROIBAMBOU, 5, 5,CardFaction.BEAST, purchaseLimit = null,false,false,false),
        Card(CardId.SANGUINAIRE, 10, 6,CardFaction.BEAST, purchaseLimit = null,false,false,false),
        Card(CardId.VIOLENT, 6, 7,CardFaction.BEAST, purchaseLimit = null,false,false,false),

        Card(CardId.DANSEUSEMACABRE, 3, 3,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.DEVINDESTENEBRES, 3, 2,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.GARDIENDESENFERS, 5, 5,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.MAITREDESCARNAGES, 3, 3,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.MAUVAISGENIE, 1, 2,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.NECROMANCIEN, 4, 6,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.PORTEURDEGIDEDECHU, 4, 4,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.PYROMANCIEN, 3, 5,CardFaction.DEMON, purchaseLimit = null,false,false,false),
        Card(CardId.ROIDEMON, 2, 4,CardFaction.DEMON, purchaseLimit = 2,false,false,false),
        Card(CardId.SANGPACTE, 3, 3,CardFaction.DEMON, purchaseLimit = null,false,false,false),

        Card(CardId.AMBASSADRICE, 1, 2,CardFaction.HUMAN, purchaseLimit = null,false,false,false),
        Card(CardId.BARBAREVIKING, 1, 4,CardFaction.HUMAN, purchaseLimit = null,false,false,false),
        Card(CardId.DEVINDELUMIERE, 3, 2,CardFaction.HUMAN, purchaseLimit = null,false,false,false),
        Card(CardId.DUC, 1, 4,CardFaction.HUMAN, purchaseLimit = null,false,false,false),
        Card(CardId.ENVOUTEUSE, 4, 6,CardFaction.HUMAN, purchaseLimit = null,false,false,false),
        Card(CardId.MAGE, 2, 2,CardFaction.HUMAN, purchaseLimit = null,false,false,false),
        Card(CardId.REVENDEUR, 2, 2,CardFaction.HUMAN, purchaseLimit = null,false,false,false),
        Card(CardId.SENTINELLE, 3, 2,CardFaction.HUMAN, purchaseLimit = null,false,false,false),
        Card(CardId.TACTICIEN, 5, 5,CardFaction.HUMAN, purchaseLimit = null,false,false,false),
        Card(CardId.THERMOGUERRIER, 2, 4,CardFaction.HUMAN, purchaseLimit = null,false,false,false),

        // =====================================================
        // MERCENAIRES SAISON
        // =====================================================
        Card(CardId.MERCENARY_TNT, 5, 3, CardFaction.MERCENARY, purchaseLimit = 1, false, false, false),
        Card(CardId.MERCENARY_ASSASSIN, 5, 3, CardFaction.MERCENARY, purchaseLimit = 1, false, false, false),
        Card(CardId.MERCENARY_COLOSSE, 4, 15, CardFaction.MERCENARY, purchaseLimit = 1, false, false, false),
        Card(CardId.MERCENARY_DIPLOMATE, 3, 4, CardFaction.MERCENARY, purchaseLimit = 1, false, false, false),
        Card(CardId.MERCENARY_FANATIQUE, 6, 2, CardFaction.MERCENARY, purchaseLimit = 1, false, false, false),
        Card(CardId.MERCENARY_MAITRE_D_ARMES, 5, 3, CardFaction.MERCENARY, purchaseLimit = 1, false, false, false),
        Card(CardId.MERCENARY_PORTEUR_DE_PESTE, 5, 5, CardFaction.MERCENARY, purchaseLimit = 1, false, false, false),
        Card(CardId.MERCENARY_PSYCHOPATHE, 6, 4, CardFaction.MERCENARY, purchaseLimit = 1, false, false, false),
        Card(CardId.MERCENARY_VOLEUR, 4, 4, CardFaction.MERCENARY, purchaseLimit = 1, false, false, false),



    ).associateBy { it.id }

    private val excludedFromShop = setOf(
        CardId.COLLECTOR,
        CardId.WARRIOR,
        CardId.CURSED,
        CardId.HEALER,
        CardId.SQUELETTE_HANTE,
        CardId.SQUELETTE_MALEDICTION,
        CardId.SQUELETTE_FAIBLESSE,
        CardId.ARAIGNEESOLDAT,
        CardId.NOURRICEARAIGNEE,
        CardId.OEUFDARAIGNEE,

        // =====================================================
        // MERCENAIRES (UNIQUEMENT MODE SAISON)
        // =====================================================
        CardId.MERCENARY_TNT,
        CardId.MERCENARY_ASSASSIN,
        CardId.MERCENARY_COLOSSE,
        CardId.MERCENARY_DIPLOMATE,
        CardId.MERCENARY_FANATIQUE,
        CardId.MERCENARY_MAITRE_D_ARMES,
        CardId.MERCENARY_PORTEUR_DE_PESTE,
        CardId.MERCENARY_PSYCHOPATHE,
        CardId.MERCENARY_VOLEUR,
    )

    fun isExcludedFromShop(cardId: CardId): Boolean {
        return cardId in excludedFromShop
    }

    fun getCard(cardId: CardId): Card {
        return cardsById[cardId]
            ?: error("No card found in CardCatalog for $cardId")
    }
}