
package com.battleon

object RankedBanService {

    fun getBanVersion(): Int {
        return WeeklyRankedBans.VERSION
    }

    fun isStandardPvpBanned(cardId: CardId): Boolean {
        return cardId in StandardPvpBans.BANNED_CARDS
    }

    fun isPermanentlyRankedBanned(cardId: CardId): Boolean {
        return cardId in PermanentRankedBans.BANNED_CARDS
    }

    fun isWeeklyRankedBanned(cardId: CardId): Boolean {
        return cardId in WeeklyRankedBans.BANNED_CARDS
    }

    fun isRankedBanned(cardId: CardId): Boolean {
        return isStandardPvpBanned(cardId) ||
                isPermanentlyRankedBanned(cardId) ||
                isWeeklyRankedBanned(cardId)
    }

    fun getStandardPvpBannedCards(): Set<CardId> {
        return StandardPvpBans.BANNED_CARDS
    }

    fun getRankedBannedCards(): Set<CardId> {
        return StandardPvpBans.BANNED_CARDS +
                PermanentRankedBans.BANNED_CARDS +
                WeeklyRankedBans.BANNED_CARDS
    }
}
