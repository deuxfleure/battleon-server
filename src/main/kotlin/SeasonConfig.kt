
package com.battleon

/**
 * Configuration des saisons de BattleOn.
 *
 * La saison active est définie ici pour le moment.
 * Plus tard, elle pourra être récupérée depuis la base de données
 * sans modifier les règles de sélection des boosters.
 */
object SeasonConfig {

    const val CURRENT_SEASON = 1

    /**
     * Ensembles disponibles dans le booster permanent.
     *
     * Saison 1 : BASE + SET_01
     * Saison 2 : BASE + SET_01
     * Saison 3 : BASE + SET_01 + SET_02
     * Saison 4 : BASE + SET_01 + SET_02 + SET_03
     */
    fun getPermanentSets(): List<CardSetId> {
        val sets = mutableListOf(CardSetId.BASE)

        val latestSeasonSet = maxOf(1, CURRENT_SEASON - 1)

        for (season in 1..latestSeasonSet) {
            sets.add(getSeasonSet(season))
        }

        return sets
    }

    /**
     * Ensembles disponibles dans le booster saisonnier.
     *
     * Saison 1 : indisponible
     * Saison 2 : SET_01 + SET_02
     * Saison 3 : SET_02 + SET_03
     * Saison 4 : SET_03 + SET_04
     */
    fun getSeasonalSets(): List<CardSetId> {
        if (CURRENT_SEASON < 2) {
            return emptyList()
        }

        return listOf(
            getSeasonSet(CURRENT_SEASON - 1),
            getSeasonSet(CURRENT_SEASON)
        )
    }

    fun isSeasonalBoosterAvailable(): Boolean {
        return CURRENT_SEASON >= 2
    }

    private fun getSeasonSet(season: Int): CardSetId {
        require(season >= 1) {
            "Numéro de saison invalide : $season"
        }

        val setName = "SET_${season.toString().padStart(2, '0')}"

        return CardSetId.entries.firstOrNull { it.name == setName }
            ?: error("Ensemble de cartes non défini : $setName")
    }
}
