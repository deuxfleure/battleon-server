package com.battleon

import org.jetbrains.exposed.sql.transactions.transaction

object LeaderboardService {

    private const val CAMPAIGN_MISSION_COUNT = 80
    private const val MISSIONS_PER_CHAPTER = 20
    private const val TOP_PLAYER_LIMIT = 100

    fun getLeaderboard(
        currentUserId: Int
    ): LeaderboardResponse? =
        transaction {

            val query = """
                WITH solo_progress_scored AS (
                    SELECT
                        user_id,
                        mission_id,

                        CASE
                            WHEN hard_completed THEN
                                $CAMPAIGN_MISSION_COUNT
                                +
                                (
                                    (
                                        SUBSTRING(
                                            mission_id
                                            FROM 'c([0-9]+)_m'
                                        )::INTEGER - 1
                                    ) * $MISSIONS_PER_CHAPTER
                                )
                                +
                                SUBSTRING(
                                    mission_id
                                    FROM '_m([0-9]+)$'
                                )::INTEGER

                            WHEN campaign_completed THEN
                                (
                                    (
                                        SUBSTRING(
                                            mission_id
                                            FROM 'c([0-9]+)_m'
                                        )::INTEGER - 1
                                    ) * $MISSIONS_PER_CHAPTER
                                )
                                +
                                SUBSTRING(
                                    mission_id
                                    FROM '_m([0-9]+)$'
                                )::INTEGER

                            ELSE NULL
                        END AS progress_score,

                        CASE
                            WHEN hard_completed THEN 'HARD'
                            WHEN campaign_completed THEN 'CAMPAIGN'
                            ELSE NULL
                        END AS solo_difficulty

                    FROM user_solo_mission_progress

                    WHERE
                        campaign_completed = TRUE
                        OR hard_completed = TRUE
                ),

                best_solo_progress AS (
                    SELECT DISTINCT ON (user_id)
                        user_id,
                        mission_id,
                        solo_difficulty

                    FROM solo_progress_scored

                    WHERE progress_score IS NOT NULL

                    ORDER BY
                        user_id,
                        progress_score DESC
                ),

                ranked_players AS (
                    SELECT
                        profile.user_auth_id,
                        profile.display_name,
                        profile.title,
                        profile.elo,
                        profile.wins,
                        profile.losses,

                        CASE
                            WHEN profile.wins + profile.losses = 0 THEN 0.0
                            ELSE
                                (
                                    profile.wins::DOUBLE PRECISION
                                    /
                                    (profile.wins + profile.losses)
                                ) * 100.0
                        END AS win_rate,

                        best_solo.mission_id AS latest_solo_mission_id,
                        best_solo.solo_difficulty AS latest_solo_difficulty,

                        ROW_NUMBER() OVER (
                            ORDER BY
                                profile.elo DESC,
                                profile.wins DESC,

                                CASE
                                    WHEN profile.wins + profile.losses = 0 THEN 0.0
                                    ELSE
                                        profile.wins::DOUBLE PRECISION
                                        /
                                        (profile.wins + profile.losses)
                                END DESC,

                                profile.user_auth_id ASC
                        ) AS player_rank

                    FROM user_profile AS profile

                    LEFT JOIN best_solo_progress AS best_solo
                        ON best_solo.user_id = profile.user_auth_id
                )

                SELECT
                    user_auth_id,
                    display_name,
                    title,
                    elo,
                    wins,
                    losses,
                    win_rate,
                    latest_solo_mission_id,
                    latest_solo_difficulty,
                    player_rank

                FROM ranked_players

                WHERE
                    player_rank <= $TOP_PLAYER_LIMIT
                    OR user_auth_id = $currentUserId

                ORDER BY player_rank ASC
            """.trimIndent()

            val entries = exec(query) { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            LeaderboardEntryResponse(
                                userId = resultSet.getInt("user_auth_id"),
                                rank = resultSet
                                    .getLong("player_rank")
                                    .toInt(),
                                displayName = resultSet.getString("display_name"),
                                titleId = resultSet.getString("title"),
                                elo = resultSet.getInt("elo"),
                                wins = resultSet.getInt("wins"),
                                losses = resultSet.getInt("losses"),
                                winRate = resultSet.getDouble("win_rate"),
                                latestSoloMissionId =
                                    resultSet.getString("latest_solo_mission_id"),
                                latestSoloDifficulty =
                                    resultSet.getString("latest_solo_difficulty")
                            )
                        )
                    }
                }
            } ?: emptyList()

            val currentPlayer = entries.firstOrNull {
                it.userId == currentUserId
            } ?: return@transaction null

            LeaderboardResponse(
                currentPlayer = currentPlayer,
                topPlayers = entries
                    .filter { it.rank <= TOP_PLAYER_LIMIT }
            )
        }
}