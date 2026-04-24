package com.justpass.app.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.justpass.app.data.model.ChessChallenge
import com.justpass.app.data.model.OnlinePlayer

/**
 * Minimal contract covering presence + active-challenge operations shared
 * by the Firestore (V1) and Cloudflare WebSocket (V2) backends. Profiles,
 * friends, leaderboard, game-history, and Lichess result polling are NOT
 * part of this interface — those stay on [ChessRepository] in both paths.
 *
 * Signatures MUST match [ChessRepository]'s public methods exactly so a
 * concrete impl can be swapped in behind a Remote Config flag without
 * touching [com.justpass.app.ui.viewmodel.ChessViewModel]. We intentionally
 * keep the Firestore [ListenerRegistration] return type — V2 returns an
 * anonymous impl whose `remove()` tears down the WS subscription.
 */
interface ChessLobby {

    // ─── Presence ───────────────────────────────────────────────────────────

    suspend fun goOnline(playerId: String, displayName: String)

    suspend fun goOffline(playerId: String)

    /** V1 parity — V2 implementations may no-op (server-side presence). */
    suspend fun heartbeat(playerId: String)

    fun listenOnlinePlayers(
        myId: String,
        friendIds: Set<String>,
        onUpdate: (List<OnlinePlayer>) -> Unit
    ): ListenerRegistration

    // ─── Challenges ─────────────────────────────────────────────────────────

    suspend fun sendChallenge(
        fromId: String,
        fromName: String,
        toId: String,
        toName: String,
        timeControl: String = "rapid_10"
    ): String?

    suspend fun checkExistingChallenge(fromId: String, toId: String): ChessChallenge?

    fun listenIncomingChallenges(
        myId: String,
        onChallenge: (ChessChallenge) -> Unit,
        onChallengeRemoved: (challengeId: String) -> Unit = {}
    ): ListenerRegistration

    fun listenChallengeStatus(
        challengeId: String,
        onUpdate: (ChessChallenge) -> Unit
    ): ListenerRegistration

    /** Returns Pair(challengerUrl, opponentUrl). */
    suspend fun acceptChallenge(
        challengeId: String,
        timeControl: String = "rapid_10"
    ): Pair<String, String>?

    suspend fun declineChallenge(challengeId: String)

    /** V2 implementations may no-op (the server expires stale challenges). */
    suspend fun cleanupExpiredChallenges()
}
