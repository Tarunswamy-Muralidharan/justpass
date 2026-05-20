package com.justpass.app.games.data.model

/** Single leaderboard row returned by Supabase REST. */
data class ScoreRow(
    val playerId: String,
    val gameId: String,
    val bestScore: Double,
    val displayName: String? = null,
    val attempts: Int = 1,
    val updatedAt: Long = 0,
    /** Class identifier (e.g. "2022-CSE-A"). Null = legacy row or no biodata. */
    val classId: String? = null
)

/** Caller's rank for a particular game. */
data class RankInfo(
    val rank: Int,
    val totalPlayers: Int,
    val bestScore: Double?
)

/** Aggregate cross-game ranking row. */
data class OverallRow(
    val playerId: String,
    val displayName: String?,
    val totalPoints: Int,
    val gamesPlayed: Int
)
