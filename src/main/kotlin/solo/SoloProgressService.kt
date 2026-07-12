package com.battleon.solo

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import com.battleon.UserProfile
import com.battleon.solo.SoloMissionReward
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.insertIgnore


object SoloProgressService {

    fun getAllProgress(
        userId: Int
    ): List<SoloMissionProgressResponse> =
        transaction {
            UserSoloMissionProgress
                .selectAll()
                .where {
                    UserSoloMissionProgress.userId eq userId
                }
                .map { row ->
                    SoloMissionProgressResponse(
                        missionId = row[UserSoloMissionProgress.missionId],
                        campaignCompleted = row[UserSoloMissionProgress.campaignCompleted],
                        campaignRewardClaimed = row[UserSoloMissionProgress.campaignRewardClaimed],
                        hardCompleted = row[UserSoloMissionProgress.hardCompleted],
                        hardRewardClaimed = row[UserSoloMissionProgress.hardRewardClaimed]
                    )
                }
        }

    fun getUnlockedRuneIds(
        userId: Int
    ): List<String> =
        transaction {
            UserRunes
                .selectAll()
                .where {
                    UserRunes.userId eq userId
                }
                .map { row ->
                    row[UserRunes.runeId]
                }
                .sorted()
        }

    fun getRuneLoadout(
        userId: Int
    ): SoloRuneLoadoutResponse =
        transaction {
            val row = UserSoloRuneLoadout
                .selectAll()
                .where {
                    UserSoloRuneLoadout.userId eq userId
                }
                .singleOrNull()

            if (row == null) {
                SoloRuneLoadoutResponse()
            } else {
                SoloRuneLoadoutResponse(
                    majorRuneId = row[UserSoloRuneLoadout.majorRuneId],
                    minorLeftRuneId = row[UserSoloRuneLoadout.minorLeftRuneId],
                    minorRightRuneId = row[UserSoloRuneLoadout.minorRightRuneId]
                )
            }
        }

    fun updateRuneLoadout(
        userId: Int,
        request: SoloRuneLoadoutUpdateRequest
    ): SoloRuneLoadoutUpdateResult =
        transaction {

            val requestedRuneIds = listOfNotNull(
                request.majorRuneId,
                request.minorLeftRuneId,
                request.minorRightRuneId
            )

            // Une même rune ne peut pas occuper plusieurs slots.
            if (requestedRuneIds.distinct().size != requestedRuneIds.size) {
                return@transaction SoloRuneLoadoutUpdateResult.Invalid(
                    reason = "duplicate_rune"
                )
            }

            // Vérifie que toutes les runes demandées existent dans le catalogue serveur.
            val requestedRunes = requestedRuneIds.map { runeId ->
                SoloRuneCatalog.findById(runeId)
                    ?: return@transaction SoloRuneLoadoutUpdateResult.Invalid(
                        reason = "unknown_rune"
                    )
            }

            // Vérifie que le joueur possède réellement toutes les runes demandées.
            val ownedRuneIds = UserRunes
                .selectAll()
                .where {
                    UserRunes.userId eq userId
                }
                .map { row ->
                    row[UserRunes.runeId]
                }
                .toSet()

            if (!ownedRuneIds.containsAll(requestedRuneIds)) {
                return@transaction SoloRuneLoadoutUpdateResult.Invalid(
                    reason = "rune_not_owned"
                )
            }

            // Vérifie que le slot majeur contient uniquement une rune majeure.
            request.majorRuneId?.let { runeId ->
                val rune = requestedRunes.first { it.id == runeId }

                if (rune.type != SoloRuneType.MAJOR) {
                    return@transaction SoloRuneLoadoutUpdateResult.Invalid(
                        reason = "invalid_major_slot"
                    )
                }
            }

            // Vérifie que le slot mineur gauche contient uniquement une rune mineure.
            request.minorLeftRuneId?.let { runeId ->
                val rune = requestedRunes.first { it.id == runeId }

                if (rune.type != SoloRuneType.MINOR) {
                    return@transaction SoloRuneLoadoutUpdateResult.Invalid(
                        reason = "invalid_minor_left_slot"
                    )
                }
            }

            // Vérifie que le slot mineur droit contient uniquement une rune mineure.
            request.minorRightRuneId?.let { runeId ->
                val rune = requestedRunes.first { it.id == runeId }

                if (rune.type != SoloRuneType.MINOR) {
                    return@transaction SoloRuneLoadoutUpdateResult.Invalid(
                        reason = "invalid_minor_right_slot"
                    )
                }
            }

            val existingRow = UserSoloRuneLoadout
                .selectAll()
                .where {
                    UserSoloRuneLoadout.userId eq userId
                }
                .singleOrNull()

            if (existingRow == null) {
                UserSoloRuneLoadout.insert {
                    it[UserSoloRuneLoadout.userId] = userId
                    it[majorRuneId] = request.majorRuneId
                    it[minorLeftRuneId] = request.minorLeftRuneId
                    it[minorRightRuneId] = request.minorRightRuneId
                }
            } else {
                UserSoloRuneLoadout.update({
                    UserSoloRuneLoadout.userId eq userId
                }) {
                    it[majorRuneId] = request.majorRuneId
                    it[minorLeftRuneId] = request.minorLeftRuneId
                    it[minorRightRuneId] = request.minorRightRuneId
                }
            }

            SoloRuneLoadoutUpdateResult.Success(
                loadout = SoloRuneLoadoutResponse(
                    majorRuneId = request.majorRuneId,
                    minorLeftRuneId = request.minorLeftRuneId,
                    minorRightRuneId = request.minorRightRuneId
                )
            )
        }

    fun ensureMissionExists(
        userId: Int,
        missionId: String
    ) {
        transaction {
            val exists = UserSoloMissionProgress
                .selectAll()
                .where {
                    (UserSoloMissionProgress.userId eq userId) and
                            (UserSoloMissionProgress.missionId eq missionId)
                }
                .any()

            if (!exists) {
                UserSoloMissionProgress.insert {
                    it[UserSoloMissionProgress.userId] = userId
                    it[UserSoloMissionProgress.missionId] = missionId
                }
            }
        }
    }

    fun completeCampaignAndClaimReward(
        userId: Int,
        missionId: String,
        reward: SoloMissionReward
    ): Boolean {
        return transaction {
            val now = System.currentTimeMillis()

            ensureMissionExists(
                userId = userId,
                missionId = missionId
            )

            val progress = UserSoloMissionProgress
                .selectAll()
                .where {
                    (UserSoloMissionProgress.userId eq userId) and
                            (UserSoloMissionProgress.missionId eq missionId)
                }
                .singleOrNull()
                ?: return@transaction false

            val campaignAlreadyCompleted =
                progress[UserSoloMissionProgress.campaignCompleted]

            val rewardAlreadyClaimed =
                progress[UserSoloMissionProgress.campaignRewardClaimed]

            if (!rewardAlreadyClaimed) {
                val profile = UserProfile
                    .selectAll()
                    .where {
                        UserProfile.userAuthId eq userId
                    }
                    .singleOrNull()
                    ?: return@transaction false

                UserProfile.update({
                    UserProfile.userAuthId eq userId
                }) {
                    it[gems] = profile[UserProfile.gems] + reward.gems
                    it[dust] = profile[UserProfile.dust] + reward.dust
                }

                reward.runeIds
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { runeId ->
                        UserRunes.insertIgnore {
                            it[UserRunes.userId] = userId
                            it[UserRunes.runeId] = runeId
                        }
                    }
            }

            UserSoloMissionProgress.update({
                (UserSoloMissionProgress.userId eq userId) and
                        (UserSoloMissionProgress.missionId eq missionId)
            }) {
                it[campaignCompleted] = true

                if (!campaignAlreadyCompleted) {
                    it[campaignCompletedAt] = now
                }

                if (!rewardAlreadyClaimed) {
                    it[campaignRewardClaimed] = true
                }
            } > 0
        }
    }

}