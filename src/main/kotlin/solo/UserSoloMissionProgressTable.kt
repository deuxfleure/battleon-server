package com.battleon.solo

import com.battleon.UserAuth
import org.jetbrains.exposed.sql.Table

object UserSoloMissionProgress : Table("user_solo_mission_progress") {
    val userId = integer("user_id").references(UserAuth.id)
    val missionId = varchar("mission_id", 50)

    val campaignCompleted = bool("campaign_completed").default(false)
    val campaignCompletedAt = long("campaign_completed_at").nullable()
    val campaignRewardClaimed = bool("campaign_reward_claimed").default(false)

    val hardCompleted = bool("hard_completed").default(false)
    val hardCompletedAt = long("hard_completed_at").nullable()
    val hardRewardClaimed = bool("hard_reward_claimed").default(false)

    override val primaryKey = PrimaryKey(userId, missionId)
}