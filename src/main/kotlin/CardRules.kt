package com.battleon

data class EffectResolution(
    val playerHp: Int,
    val opponentHp: Int,
    val playerGold: Int,
    val opponentGold: Int,
    val playerDeck: List<Card>,
    val opponentDeck: List<Card>,
    val playerDiscard: List<Card>,
    val opponentDiscard: List<Card>
)

fun applyCardEffect(
    effectOwnerIsPlayer: Boolean,
    cardId: CardId,
    playerHp: Int,
    opponentHp: Int,
    playerGold: Int,
    opponentGold: Int,
    playerDeck: List<Card>,
    opponentDeck: List<Card>,
    playerDiscard: List<Card>,
    opponentDiscard: List<Card>
): EffectResolution {
    var newPlayerHp = playerHp
    var newOpponentHp = opponentHp
    var newPlayerGold = playerGold
    var newOpponentGold = opponentGold
    var newPlayerDeck = playerDeck
    var newOpponentDeck = opponentDeck
    var newPlayerDiscard = playerDiscard
    var newOpponentDiscard = opponentDiscard

    when (cardId) {
        //----------- Deck de base --------------------------------
        CardId.COLLECTOR -> {
            if (effectOwnerIsPlayer) {
                newPlayerGold += 1
            } else {
                newOpponentGold += 1
            }
        }

        CardId.HEALER -> {
            if (effectOwnerIsPlayer) {
                newPlayerHp += 1
            } else {
                newOpponentHp += 1
            }
        }

        CardId.CURSED -> {
            // défini dans EffectPhaseCardResolver
            // HANTE 1 : défausse la première carte de son propre deck
            // avec reshuffle +1 or si nécessaire
        }

        CardId.WARRIOR -> {
            // pas d'effet
        }
        //----------- Carte de base --------------------------------
        //----------- Humain --------------------------------
        CardId.AMBASSADRICE -> {
            // défini dans CardEffectManager + GameManager
            // +2 force par Ambassadrice dans la défausse
            // protection : perte maximale de 1 PV net sur le tour
        }
        CardId.BARBAREVIKING -> {
            // défini dans CardEffectManager
            // BRUTE + bonus de lien humain depuis la défausse
        }
        CardId.DEVINDELUMIERE -> {
            // défini dans GameManager + CardEffectManager
            // Effet géré en pendingChoice :
            // - Scruter 2
            // - sacrifice post-combat : +1 PV
        }
        CardId.DUC -> {
            if (effectOwnerIsPlayer) {
                newPlayerGold += 2
            } else {
                newOpponentGold += 2
            }
        }
        CardId.ENVOUTEUSE -> {
            // défini dans CardEffectManager
        }
        CardId.MAGE -> {
            // défini dans CardEffectManager
        }
        CardId.REVENDEUR -> {
            // défini dans GameManager + CardEffectManager
            // Effet géré hors CardRules :
            // - gagne 1 Or
            // - Scruter 1
        }
        CardId.SENTINELLE -> {
            // défini dans GameManager + CardEffectManager
            // Effets gérés hors CardRules :
            // - à l'achat : destruction optionnelle d'une carte dans la défausse
            // - pendant la phase d'effet : peut bloquer l'effet adverse
        }
        CardId.TACTICIEN -> {
            // défini dans GameManager + CardEffectManager
            // Peut remplacer la carte révélée adverse par une nouvelle révélation
        }
        CardId.THERMOGUERRIER -> {
            // défini dans CardEffectManager
        }

        //----------- Bête --------------------------------
        CardId.AGILE -> {
            // défini dans CardEffectManager
        }
        CardId.ARAIGNEEGEANTE -> {
            // défini dans PostCombatEffectResolver + CardEffectManager
            // Quand elle gagne un combat :
            // peut remplacer une carte de sa défausse par un Œuf d'Araignée
        }
        CardId.BULLDOZER -> {
            // défini dans CardEffectManager
        }
        CardId.CHAMANE -> {
            // défini dans EffectPhaseCardResolver + CardEffectManager
            // SUBTERFUGE :
            // - regarde la prochaine carte adverse
            // - regarde sa propre prochaine carte
            // - soit défausse la carte adverse
            // - soit détruit sa propre carte si la règle des 5 cartes le permet
        }
        CardId.DURACUIRE -> {
            // défini dans CardEffectManager
        }
        CardId.EPINENOIRE -> {
            // défini dans CardEffectManager
        }
        CardId.GRACIEUSEROBUSTE -> {
            // pas d'effet
        }
        CardId.ROIBAMBOU -> {
            if (effectOwnerIsPlayer) {
                newPlayerGold += 1
            } else {
                newOpponentGold += 1
            }
        }
        CardId.SANGUINAIRE -> {
            // aucun effet implémenté actuellement
            // placeholder mécanique en attente
        }
        CardId.VIOLENT -> {
            // défini dans CardEffectManager
        }

        //----------- Demon --------------------------------
        CardId.DANSEUSEMACABRE -> {
            // défini dans CardEffectManager
        }
        CardId.DEVINDESTENEBRES -> {
            // défini dans GameManager + CardEffectManager
            // Effet géré en pendingChoice :
            // - Scruter sombre 2
            // - sacrifice post-combat : inflige 1 dégât
        }
        CardId.GARDIENDESENFERS -> {
            // défini dans PostCombatEffectResolver
            // TROUBLE : quand il gagne, l'adversaire défausse X cartes
            // X = différence entre les forces des deux combattants
        }
        CardId.MAITREDESCARNAGES -> {
            // défini dans GameManager + CardEffectManager
            // Effet géré en pendingChoice :
            // - choisit un nom de carte
            // - si la prochaine carte révélée porte ce nom : +5 force
        }
        CardId.MAUVAISGENIE -> {
            // défini dans CardEffectManager
        }
        CardId.NECROMANCIEN -> {
            // défini dans CardEffectManager
            // quand il gagne un combat, ajoute un Squelette aléatoire
            // dans la défausse adverse
        }
        CardId.PORTEURDEGIDEDECHU -> {
            // défini dans CardEffectManager
            // perd : gagne 1 PV
            // gagne : Bretteur 1 (défausse la première carte du deck adverse)
        }
        CardId.PYROMANCIEN -> {
            if (effectOwnerIsPlayer) {
                newOpponentHp = maxOf(0, newOpponentHp - 1)
            } else {
                newPlayerHp = maxOf(0, newPlayerHp - 1)
            }
        }
        CardId.ROIDEMON -> {
            // défini dans CardEffectManager
            // Frénésie démon :
            // chaque Roi Démon dans la défausse donne +1 force aux cartes Démon
        }
        CardId.SANGPACTE -> {
            // défini dans CardEffectManager
        }
        //----------- squelettes et araignées de base -----------------------

        CardId.SQUELETTE_HANTE -> {
            // défini dans EffectPhaseCardResolver
            // HANTE 1 : défausse la première carte de son propre deck
            // avec reshuffle +1 or si nécessaire
        }

        CardId.SQUELETTE_MALEDICTION -> {
            // aucun effet implémenté actuellement
            // placeholder mécanique en attente
        }

        CardId.SQUELETTE_FAIBLESSE -> {
            // défini dans CardEffectManager
        }
        CardId.ARAIGNEESOLDAT -> {
            // défini dans EffectPhaseCardResolver + CardEffectManager
            // Choix :
            // - BRÛLURE
            // - ou COMBATIF : prochaine carte +1 force
        }
        CardId.NOURRICEARAIGNEE -> {
            // défini dans EffectPhaseCardResolver + CardEffectManager
            // Choix :
            // - gagne 1 PV
            // - ou paie 2 Or pour ajouter un Œuf d'Araignée dans sa défausse
        }
        CardId.OEUFDARAIGNEE -> {
            // défini dans PostCombatEffectResolver + CardEffectManager
            // Si cette carte perd le combat :
            // l'adversaire reçoit un jeton POISON
            //
            // Après le combat :
            // peut être détruite pour ajouter une carte Araignée disponible
            // dans sa défausse
        }

        //----------- Carte v 1.1 --------------------------------
        //----------- Carte v 1.2 --------------------------------

        else -> {
            println("No effect implemented for $cardId")
        }
    }

    return EffectResolution(
        playerHp = newPlayerHp,
        opponentHp = newOpponentHp,
        playerGold = newPlayerGold,
        opponentGold = newOpponentGold,
        playerDeck = newPlayerDeck,
        opponentDeck = newOpponentDeck,
        playerDiscard = newPlayerDiscard,
        opponentDiscard = newOpponentDiscard
    )
}