package com.battleon.solo

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object SoloProgressService {

    fun getProgress(
        userId: Int,
        missionId: String
    ): ResultRow? =
        transaction {
            UserSoloMissionProgress
                .selectAll()
                .where {
                    (UserSoloMissionProgress.userId eq userId) and
                            (UserSoloMissionProgress.missionId eq missionId)
                }
                .singleOrNull()
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
}