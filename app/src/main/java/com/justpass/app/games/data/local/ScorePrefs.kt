package com.justpass.app.games.data.local

import android.content.Context
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.games.data.model.Game

/**
 * Local cache of the player's best score per game so the home screen
 * can show personal bests immediately, even offline. Backed by a plain
 * SharedPreferences — no encrypted prefs needed since these are public-
 * facing leaderboard scores.
 *
 * Identity fields ([playerId], [displayName], [classId]) are sourced
 * from JustPass's [SecurePreferences] so the same student appears under
 * their roll number + name on the global leaderboard.
 */
class ScorePrefs private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "humanbenchmark_scores", Context.MODE_PRIVATE
    )
    private val securePrefs = SecurePreferences.getInstance(context.applicationContext)

    fun getBest(game: Game): Double? {
        if (!prefs.contains("best_${game.id}")) return null
        return prefs.getFloat("best_${game.id}", 0f).toDouble()
    }

    /**
     * Save [score] only if it's actually better than the current best
     * (per the game's lowerIsBetter rule). Returns true if a new best
     * was recorded.
     */
    fun saveIfBetter(game: Game, score: Double): Boolean {
        val current = getBest(game)
        val isBetter = when {
            current == null -> true
            game.lowerIsBetter -> score < current
            else -> score > current
        }
        if (isBetter) {
            prefs.edit().putFloat("best_${game.id}", score.toFloat()).apply()
        }
        return isBetter
    }

    fun hasSeenInstructions(game: Game): Boolean =
        prefs.getBoolean("instr_seen_${game.id}", false)

    fun markInstructionsSeen(game: Game) {
        prefs.edit().putBoolean("instr_seen_${game.id}", true).apply()
    }

    /**
     * Display name shown on leaderboards. Pulled from JustPass biodata
     * (student name). Falls back to "Player <roll>" or "Anonymous" if
     * biodata isn't yet hydrated.
     */
    val displayName: String?
        get() {
            val name = securePrefs.displayName?.takeIf { it.isNotBlank() }
            if (name != null) return name
            val roll = securePrefs.rollNumber?.takeIf { it.isNotBlank() }
            return if (roll != null) "Player $roll" else null
        }

    /**
     * Leaderboard identity = the student's roll number. Stable across
     * reinstalls + uniquely identifies one human inside PSGiTech. If
     * the user hasn't logged in yet, fall back to a per-install UUID
     * so offline play still works (these scores will not aggregate).
     */
    val playerId: String
        get() {
            val roll = securePrefs.rollNumber?.takeIf { it.isNotBlank() }
            if (roll != null) return roll
            val existing = prefs.getString("anon_player_id", null)
            if (existing != null) return existing
            val fresh = "anon_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            prefs.edit().putString("anon_player_id", fresh).apply()
            return fresh
        }

    /**
     * Class identifier for per-class leaderboard scoping. Format:
     * `${batchYear}-${dept}-${section}` (e.g. "2022-CSE-A"). Returns
     * `null` if biodata not yet populated — submissions go through but
     * with null class so they only count toward overall.
     */
    val classId: String?
        get() {
            val batch = securePrefs.batchYear.takeIf { it > 0 }
            val dept = securePrefs.cachedDepartment?.takeIf { it.isNotBlank() }
            val section = securePrefs.cachedSection?.takeIf { it.isNotBlank() }
            if (batch == null || dept == null || section == null) return null
            return "$batch-$dept-$section"
        }

    companion object {
        @Volatile private var INSTANCE: ScorePrefs? = null
        fun getInstance(context: Context): ScorePrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScorePrefs(context).also { INSTANCE = it }
            }
    }
}
