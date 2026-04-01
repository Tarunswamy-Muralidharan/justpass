package com.example.attendancewidgetlaudea.data.repository

import android.util.Log
import com.example.attendancewidgetlaudea.data.model.CHESS_NAMES
import com.example.attendancewidgetlaudea.data.model.ChessChallenge
import com.example.attendancewidgetlaudea.data.model.ChessProfile
import com.example.attendancewidgetlaudea.data.model.FriendRequest
import com.example.attendancewidgetlaudea.data.model.OnlinePlayer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class ChessRepository {

    private val db = FirebaseFirestore.getInstance()
    private val onlineCollection = db.collection("chess_online")
    private val challengeCollection = db.collection("chess_challenges")
    private val profileCollection = db.collection("chess_profiles")
    private val friendCollection = db.collection("chess_friends")

    companion object {
        private const val TAG = "ChessRepo"
        private const val ONLINE_TIMEOUT_MS = 90_000L // 90 seconds stale threshold
    }

    // ─── Identity ───────────────────────────────────────────────────────────

    fun getPlayerId(rollNumber: String): String {
        return "p_${abs(rollNumber.hashCode()).toString(16)}"
    }

    fun generateRandomName(rollNumber: String): String {
        val hash = abs(rollNumber.hashCode())
        val name = CHESS_NAMES[hash % CHESS_NAMES.size]
        val suffix = (hash / CHESS_NAMES.size) % 100
        return "$name#$suffix"
    }

    // ─── Profile ────────────────────────────────────────────────────────────

    suspend fun getOrCreateProfile(playerId: String, realName: String, rollNumber: String): ChessProfile? {
        return try {
            val doc = profileCollection.document(playerId).get().await()
            if (doc.exists()) {
                ChessProfile(
                    id = doc.id,
                    displayName = realName,
                    nickname = doc.getString("nickname") ?: generateRandomName(rollNumber),
                    nameMode = doc.getString("nameMode") ?: "random",
                    wins = doc.getLong("wins")?.toInt() ?: 0,
                    losses = doc.getLong("losses")?.toInt() ?: 0,
                    draws = doc.getLong("draws")?.toInt() ?: 0,
                    gamesPlayed = doc.getLong("gamesPlayed")?.toInt() ?: 0,
                    lastOnline = doc.getLong("lastOnline") ?: 0L
                )
            } else {
                val randomName = generateRandomName(rollNumber)
                val profile = hashMapOf(
                    "displayName" to realName,
                    "nickname" to randomName,
                    "nameMode" to "random",
                    "wins" to 0,
                    "losses" to 0,
                    "draws" to 0,
                    "gamesPlayed" to 0,
                    "lastOnline" to System.currentTimeMillis()
                )
                profileCollection.document(playerId).set(profile).await()
                ChessProfile(id = playerId, displayName = realName, nickname = randomName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile error: ${e.message}")
            null
        }
    }

    suspend fun updateProfile(playerId: String, nickname: String, nameMode: String) {
        try {
            profileCollection.document(playerId).update(
                mapOf("nickname" to nickname, "nameMode" to nameMode)
            ).await()
        } catch (e: Exception) {
            Log.e(TAG, "Update profile error: ${e.message}")
        }
    }

    suspend fun recordGameResult(playerId: String, result: String) {
        try {
            val doc = profileCollection.document(playerId).get().await()
            val wins = doc.getLong("wins")?.toInt() ?: 0
            val losses = doc.getLong("losses")?.toInt() ?: 0
            val draws = doc.getLong("draws")?.toInt() ?: 0
            val games = doc.getLong("gamesPlayed")?.toInt() ?: 0
            val updates = when (result) {
                "win" -> mapOf("wins" to wins + 1, "gamesPlayed" to games + 1)
                "loss" -> mapOf("losses" to losses + 1, "gamesPlayed" to games + 1)
                "draw" -> mapOf("draws" to draws + 1, "gamesPlayed" to games + 1)
                else -> return
            }
            profileCollection.document(playerId).update(updates).await()
        } catch (e: Exception) {
            Log.e(TAG, "Record result error: ${e.message}")
        }
    }

    // ─── Leaderboard ────────────────────────────────────────────────────────

    suspend fun getLeaderboard(limit: Int = 20): List<ChessProfile> {
        return try {
            val docs = profileCollection
                .whereGreaterThan("gamesPlayed", 0)
                .orderBy("wins", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get().await()
            docs.documents.mapNotNull { doc ->
                ChessProfile(
                    id = doc.id,
                    displayName = doc.getString("displayName") ?: "",
                    nickname = doc.getString("nickname") ?: "",
                    nameMode = doc.getString("nameMode") ?: "random",
                    wins = doc.getLong("wins")?.toInt() ?: 0,
                    losses = doc.getLong("losses")?.toInt() ?: 0,
                    draws = doc.getLong("draws")?.toInt() ?: 0,
                    gamesPlayed = doc.getLong("gamesPlayed")?.toInt() ?: 0,
                    lastOnline = doc.getLong("lastOnline") ?: 0L
                )
            }.sortedByDescending { it.rating }
        } catch (e: Exception) {
            Log.e(TAG, "Leaderboard error: ${e.message}")
            emptyList()
        }
    }

    // ─── Friends ────────────────────────────────────────────────────────────

    suspend fun sendFriendRequest(fromId: String, fromName: String, toId: String, toName: String): Boolean {
        return try {
            // Check if already friends or pending
            val existing = friendCollection
                .whereEqualTo("fromId", fromId)
                .whereEqualTo("toId", toId)
                .get().await()
            if (existing.documents.isNotEmpty()) return false

            val reverse = friendCollection
                .whereEqualTo("fromId", toId)
                .whereEqualTo("toId", fromId)
                .get().await()
            if (reverse.documents.isNotEmpty()) return false

            friendCollection.add(hashMapOf(
                "fromId" to fromId, "fromName" to fromName,
                "toId" to toId, "toName" to toName,
                "status" to "pending"
            )).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Friend request error: ${e.message}")
            false
        }
    }

    suspend fun acceptFriendRequest(requestId: String) {
        try {
            friendCollection.document(requestId).update("status", "accepted").await()
        } catch (_: Exception) {}
    }

    suspend fun declineFriendRequest(requestId: String) {
        try {
            friendCollection.document(requestId).delete().await()
        } catch (_: Exception) {}
    }

    fun listenFriendRequests(myId: String, onUpdate: (List<FriendRequest>) -> Unit): ListenerRegistration {
        return friendCollection
            .whereEqualTo("toId", myId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                val requests = snapshot?.documents?.map { doc ->
                    FriendRequest(
                        id = doc.id,
                        fromId = doc.getString("fromId") ?: "",
                        fromName = doc.getString("fromName") ?: "",
                        toId = doc.getString("toId") ?: "",
                        toName = doc.getString("toName") ?: "",
                        status = doc.getString("status") ?: "pending"
                    )
                } ?: emptyList()
                onUpdate(requests)
            }
    }

    suspend fun getFriendIds(myId: String): Set<String> {
        return try {
            val sent = friendCollection
                .whereEqualTo("fromId", myId)
                .whereEqualTo("status", "accepted")
                .get().await()
                .documents.mapNotNull { it.getString("toId") }

            val received = friendCollection
                .whereEqualTo("toId", myId)
                .whereEqualTo("status", "accepted")
                .get().await()
                .documents.mapNotNull { it.getString("fromId") }

            (sent + received).toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Get friends error: ${e.message}")
            emptySet()
        }
    }

    // ─── Presence ───────────────────────────────────────────────────────────

    suspend fun goOnline(playerId: String, displayName: String) {
        try {
            val player = hashMapOf(
                "displayName" to displayName,
                "timestamp" to System.currentTimeMillis()
            )
            onlineCollection.document(playerId).set(player).await()
            profileCollection.document(playerId).update("lastOnline", System.currentTimeMillis()).await()
            Log.d(TAG, "Online: $displayName ($playerId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to go online: ${e.message}")
        }
    }

    suspend fun goOffline(playerId: String) {
        try {
            onlineCollection.document(playerId).delete().await()
            profileCollection.document(playerId).update("lastOnline", System.currentTimeMillis()).await()
            Log.d(TAG, "Offline: $playerId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to go offline: ${e.message}")
        }
    }

    suspend fun heartbeat(playerId: String) {
        try {
            onlineCollection.document(playerId).update("timestamp", System.currentTimeMillis()).await()
        } catch (_: Exception) {}
    }

    fun listenOnlinePlayers(
        myId: String,
        friendIds: Set<String>,
        onUpdate: (List<OnlinePlayer>) -> Unit
    ): ListenerRegistration {
        return onlineCollection.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val now = System.currentTimeMillis()
            val players = snapshot?.documents?.mapNotNull { doc ->
                val name = doc.getString("displayName") ?: return@mapNotNull null
                val ts = doc.getLong("timestamp") ?: 0L
                if (doc.id != myId && (now - ts) < ONLINE_TIMEOUT_MS) {
                    OnlinePlayer(
                        id = doc.id, displayName = name, timestamp = ts,
                        isFriend = doc.id in friendIds
                    )
                } else null
            }?.sortedWith(compareByDescending<OnlinePlayer> { it.isFriend }.thenByDescending { it.timestamp })
                ?: emptyList()
            onUpdate(players)
        }
    }

    // ─── Challenges ─────────────────────────────────────────────────────────

    suspend fun sendChallenge(fromId: String, fromName: String, toId: String, toName: String): String? {
        return try {
            val doc = challengeCollection.add(hashMapOf(
                "fromId" to fromId, "fromName" to fromName,
                "toId" to toId, "toName" to toName,
                "status" to "pending", "gameUrl" to "", "opponentUrl" to "",
                "lichessGameId" to "", "timestamp" to System.currentTimeMillis()
            )).await()
            doc.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send challenge: ${e.message}")
            null
        }
    }

    fun listenIncomingChallenges(myId: String, onChallenge: (ChessChallenge) -> Unit): ListenerRegistration {
        return challengeCollection
            .whereEqualTo("toId", myId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        onChallenge(ChessChallenge(
                            id = doc.id,
                            fromId = doc.getString("fromId") ?: "",
                            fromName = doc.getString("fromName") ?: "",
                            toId = doc.getString("toId") ?: "",
                            toName = doc.getString("toName") ?: "",
                            status = "pending",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        ))
                    }
                }
            }
    }

    fun listenChallengeStatus(challengeId: String, onUpdate: (ChessChallenge) -> Unit): ListenerRegistration {
        return challengeCollection.document(challengeId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener
                onUpdate(ChessChallenge(
                    id = doc.id,
                    fromId = doc.getString("fromId") ?: "",
                    fromName = doc.getString("fromName") ?: "",
                    toId = doc.getString("toId") ?: "",
                    toName = doc.getString("toName") ?: "",
                    status = doc.getString("status") ?: "pending",
                    gameUrl = doc.getString("gameUrl") ?: "",
                    opponentUrl = doc.getString("opponentUrl") ?: "",
                    lichessGameId = doc.getString("lichessGameId") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L
                ))
            }
    }

    suspend fun acceptChallenge(challengeId: String): Pair<String, String>? {
        return try {
            val result = createLichessGame()
            if (result != null) {
                challengeCollection.document(challengeId).update(mapOf(
                    "status" to "accepted",
                    "gameUrl" to result.first,
                    "opponentUrl" to result.second,
                    "lichessGameId" to result.third
                )).await()
            }
            result?.let { Pair(it.first, it.second) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept: ${e.message}")
            null
        }
    }

    suspend fun declineChallenge(challengeId: String) {
        try { challengeCollection.document(challengeId).update("status", "declined").await() } catch (_: Exception) {}
    }

    /** Returns (challengerUrl, opponentUrl, gameId) */
    private suspend fun createLichessGame(): Triple<String, String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://lichess.org/api/challenge/open")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.outputStream.write("clock.limit=600&clock.increment=0&rated=false".toByteArray())

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val challengeUrl = Regex("\"url\"\\s*:\\s*\"(https://lichess\\.org/[^\"]+)\"")
                        .find(response)?.groupValues?.get(1)
                    val opponentUrl = Regex("\"urlWhite\"\\s*:\\s*\"(https://lichess\\.org/[^\"]+)\"")
                        .find(response)?.groupValues?.get(1)
                        ?: Regex("\"urlBlack\"\\s*:\\s*\"(https://lichess\\.org/[^\"]+)\"")
                            .find(response)?.groupValues?.get(1)
                    // Extract game ID from the challenge object
                    val gameId = Regex("\"id\"\\s*:\\s*\"([A-Za-z0-9]+)\"")
                        .find(response)?.groupValues?.get(1) ?: ""

                    if (challengeUrl != null) {
                        Triple(challengeUrl, opponentUrl ?: challengeUrl, gameId)
                    } else null
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Lichess API failed: ${e.message}")
                null
            }
        }
    }

    suspend fun cleanupExpiredChallenges() {
        try {
            val cutoff = System.currentTimeMillis() - 120_000L
            val expired = challengeCollection
                .whereEqualTo("status", "pending")
                .whereLessThan("timestamp", cutoff)
                .get().await()
            expired.documents.forEach { it.reference.delete() }
        } catch (_: Exception) {}
    }
}
