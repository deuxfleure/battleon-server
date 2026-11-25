
package com.battleon

object StandardPvpBans {

    val BANNED_CARDS: Set<CardId> = setOf(
        // =====================================================
        // MERCENAIRES SAISON 1
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

        // =====================================================
        // cartes liée de base
        // =====================================================
        CardId.SQUELETTE_HANTE,
        CardId.SQUELETTE_MALEDICTION,
        CardId.SQUELETTE_FAIBLESSE,
        CardId.ARAIGNEESOLDAT,
        CardId.NOURRICEARAIGNEE,
        CardId.OEUFDARAIGNEE,

        // =====================================================
        // cartes liée saison
        // =====================================================
        // S0
        CardId.GOLEMDOCRE,
        CardId.BOULETDECANON,
        // S1
        CardId.LOUPGAROU,
    )
}


