
package com.battleon

object StarterPaquet {

    const val NAME = "Deck Tutoriel"

    val CARDS: List<PaquetCardEntry> = listOf(

        // BETES
        PaquetCardEntry(CardId.CHAMANE, 2),
        PaquetCardEntry(CardId.EPINENOIRE, 2),
        PaquetCardEntry(CardId.ROIBAMBOU, 2),
        PaquetCardEntry(CardId.GRACIEUSEROBUSTE, 2),

        // DEMONS
        PaquetCardEntry(CardId.DEVINDESTENEBRES, 2),
        PaquetCardEntry(CardId.MAUVAISGENIE, 2),
        PaquetCardEntry(CardId.PORTEURDEGIDEDECHU, 2),
        PaquetCardEntry(CardId.PYROMANCIEN, 2),

        // HUMAINS
        PaquetCardEntry(CardId.DEVINDELUMIERE, 2),
        PaquetCardEntry(CardId.ENVOUTEUSE, 2),
        PaquetCardEntry(CardId.REVENDEUR, 2),
        PaquetCardEntry(CardId.THERMOGUERRIER, 2)
    )
}
