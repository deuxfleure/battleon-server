package com.battleon.solo

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import com.battleon.UserProfile
import com.battleon.solo.SoloMissionReward
import org.jetbrains.exposed.sql.update

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

            ensureMissionExists(userId, missionId)

            val progress = UserSoloMissionProgress
                .selectAll()
                .where {
                    (UserSoloMissionProgress.userId eq userId) and
                            (UserSoloMissionProgress.missionId eq missionId)
                }
                .singleOrNull()
                ?: return@transaction false

            val rewardAlreadyClaimed = progress[UserSoloMissionProgress.campaignRewardClaimed]

            if (!rewardAlreadyClaimed) {
                val profile = UserProfile
                    .selectAll()
                    .where { UserProfile.userAuthId eq userId }
                    .singleOrNull()
                    ?: return@transaction false

                UserProfile.update({ UserProfile.userAuthId eq userId }) {
                    it[gems] = profile[UserProfile.gems] + reward.gems
                    it[dust] = profile[UserProfile.dust] + reward.dust
                }
            }

            UserSoloMissionProgress.update({
                (UserSoloMissionProgress.userId eq userId) and
                        (UserSoloMissionProgress.missionId eq missionId)
            }) {
                it[campaignCompleted] = true
                it[campaignCompletedAt] = now

                if (!rewardAlreadyClaimed) {
                    it[campaignRewardClaimed] = true
                }
            } > 0
        }
    }

}