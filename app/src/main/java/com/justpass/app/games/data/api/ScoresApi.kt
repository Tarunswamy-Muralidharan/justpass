package com.justpass.app.games.data.api

import com.google.gson.Gson
import com.justpass.app.BuildConfig
import com.justpass.app.games.data.model.Game
import com.justpass.app.games.data.model.OverallRow
import com.justpass.app.games.data.model.RankInfo
import com.justpass.app.games.data.model.ScoreRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Supabase REST client for scoreboard reads + writes. Configure
 * [SUPABASE_URL] + [SUPABASE_ANON_KEY] before building. All endpoints
 * use PostgREST conventions — no custom Edge Functions needed.
 *
 * Schema expected:
 * ```
 * CREATE TABLE scores (
 *   player_id TEXT NOT NULL,
 *   game_id TEXT NOT NULL,
 *   best_score REAL NOT NULL,
 *   attempts INT DEFAULT 1,
 *   display_name TEXT,
 *   updated_at BIGINT,
 *   PRIMARY KEY (player_id, game_id)
 * );
 * CREATE INDEX idx_game_top ON scores(game_id, best_score);
 * ```
 *
 * Plus an RPC `submit_score(p_player_id, p_game_id, p_score, p_name)`
 * that clamps to plausible bounds + UPSERTs only when better, and
 * `get_rank(p_player_id, p_game_id)` returning the caller's rank.
 */
class ScoresApi(
    private val baseUrl: String = SUPABASE_URL,
    private val anonKey: String = SUPABASE_ANON_KEY
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    /**
     * Submit a new attempt. Server clamps + keeps only the better score.
     * [classId] (e.g. "2022-CSE-A") tags the submission for per-class
     * leaderboards. Null = not yet tagged (only counts toward Overall).
     */
    suspend fun submit(
        playerId: String,
        game: Game,
        score: Double,
        displayName: String?,
        classId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || anonKey.isBlank()) return@withContext false
        try {
            val body = gson.toJson(
                mapOf(
                    "p_player_id" to playerId,
                    "p_game_id" to game.id,
                    "p_score" to score,
                    "p_name" to (displayName ?: ""),
                    "p_class" to (classId ?: "")
                )
            ).toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/submit_score_v2")
                .post(body)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .build()
            client.newCall(req).execute().use { resp -> resp.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Top N players for a game, sorted per [Game.lowerIsBetter]. If
     * [classId] is provided, scopes to scores tagged with that class.
     */
    suspend fun leaderboard(
        game: Game,
        limit: Int = 100,
        classId: String? = null
    ): List<ScoreRow> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || anonKey.isBlank()) return@withContext emptyList()
        try {
            val sortDir = if (game.lowerIsBetter) "asc" else "desc"
            val classFilter = if (classId != null) "&class_id=eq.$classId" else ""
            val req = Request.Builder()
                .url(
                    "$baseUrl/rest/v1/scores?game_id=eq.${game.id}$classFilter" +
                        "&select=player_id,game_id,best_score,display_name,attempts,updated_at,class_id" +
                        "&order=best_score.$sortDir&limit=$limit"
                )
                .get()
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList<ScoreRow>()
                val body = resp.body?.string() ?: return@withContext emptyList<ScoreRow>()
                @Suppress("UNCHECKED_CAST")
                val arr = gson.fromJson(body, List::class.java) as List<Map<String, Any?>>
                arr.map { row ->
                    ScoreRow(
                        playerId = row["player_id"] as? String ?: "",
                        gameId = row["game_id"] as? String ?: "",
                        bestScore = (row["best_score"] as? Number)?.toDouble() ?: 0.0,
                        displayName = row["display_name"] as? String,
                        attempts = (row["attempts"] as? Number)?.toInt() ?: 1,
                        updatedAt = (row["updated_at"] as? Number)?.toLong() ?: 0L,
                        classId = row["class_id"] as? String
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun rank(playerId: String, game: Game): RankInfo? = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || anonKey.isBlank()) return@withContext null
        try {
            val body = gson.toJson(
                mapOf("p_player_id" to playerId, "p_game_id" to game.id)
            ).toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/get_rank")
                .post(body)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val raw = resp.body?.string() ?: return@withContext null
                // RPCs RETURNS TABLE come back as a JSON array of rows
                @Suppress("UNCHECKED_CAST")
                val arr = gson.fromJson(raw, List::class.java) as? List<Map<String, Any?>>
                    ?: return@withContext null
                val map = arr.firstOrNull() ?: return@withContext null
                RankInfo(
                    rank = (map["rank"] as? Number)?.toInt() ?: 0,
                    totalPlayers = (map["total"] as? Number)?.toInt() ?: 0,
                    bestScore = (map["best_score"] as? Number)?.toDouble()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cross-game ranking. Fetches top [perGame] of each game in parallel,
     * awards points by per-game rank (1st = perGame, last = 1), sums per
     * player. Skips games with no scores.
     */
    suspend fun overall(
        perGame: Int = 50,
        limit: Int = 100,
        classId: String? = null
    ): List<OverallRow> = coroutineScope {
        if (baseUrl.isBlank() || anonKey.isBlank()) return@coroutineScope emptyList()
        val games = Game.entries
        val results = games.map { g ->
            async(Dispatchers.IO) { leaderboard(g, perGame, classId) }
        }.awaitAll()
        data class Agg(var name: String?, var pts: Int, var games: Int)
        val agg = HashMap<String, Agg>()
        results.forEachIndexed { _, rows ->
            rows.forEachIndexed { rank, row ->
                val pts = (rows.size - rank).coerceAtLeast(1)
                val a = agg.getOrPut(row.playerId) { Agg(row.displayName, 0, 0) }
                if (a.name.isNullOrBlank() && !row.displayName.isNullOrBlank()) a.name = row.displayName
                a.pts += pts
                a.games += 1
            }
        }
        agg.entries
            .map { (id, a) -> OverallRow(id, a.name, a.pts, a.games) }
            .sortedWith(compareByDescending<OverallRow> { it.totalPoints }
                .thenByDescending { it.gamesPlayed })
            .take(limit)
    }

    companion object {
        // Pulled from local.properties via build.gradle.kts buildConfigField.
        // Add SUPABASE_URL=... and SUPABASE_ANON_KEY=... to local.properties
        // (NOT committed) and rebuild. Until then API calls short-circuit
        // so local play still works offline.
        val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
        val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY
    }
}
