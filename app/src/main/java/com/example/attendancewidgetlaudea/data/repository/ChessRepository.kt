package com.example.attendancewidgetlaudea.data.repository

import android.util.Log
import com.example.attendancewidgetlaudea.data.model.CHESS_NAMES
import com.example.attendancewidgetlaudea.data.model.ChessChallenge
import com.example.attendancewidgetlaudea.data.model.OnlinePlayer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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

    companion object {
        private const val TAG = "ChessRepo"
        private const val ONLINE_TIMEOUT_MS = 60_000L // 60 seconds stale threshold
    }

    /** Generate a stable anonymous name from roll number */
    fun getAnonymousName(rollNumber: String): String {
        val hash = abs(rollNumber.hashCode())
        val name = CHESS_NAMES[hash % CHESS_NAMES.size]
        val suffix = (hash / CHESS_NAMES.size) % 100
        return "$name#$suffix"
    }

    /** Generate a stable player ID from roll number (not reversible) */
    fun getPlayerId(rollNumber: String): String {
        return "p_${abs(rollNumber.hashCode()).toString(16)}"
    }

    /** Go online — write presence to Firestore */
    suspend fun goOnline(playerId: String, displayName: String) {
        try {
            val player = hashMapOf(
                "displayName" to displayName,
                "timestamp" to System.currentTimeMillis()
            )
            onlineCollection.document(playerId).set(player).await()
            Log.d(TAG, "Online: $displayName ($playerId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to go online: ${e.message}")
        }
    }

    /** Go offline — remove presence */
    suspend fun goOffline(playerId: String) {
        try {
            onlineCollection.document(playerId).delete().await()
            Log.d(TAG, "Offline: $playerId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to go offline: ${e.message}")
        }
    }

    /** Update heartbeat timestamp */
    suspend fun heartbeat(playerId: String) {
        try {
            onlineCollection.document(playerId).update("timestamp", System.currentTimeMillis()).await()
        } catch (_: Exception) {}
    }

    /** Listen to online players (real-time) */
    fun listenOnlinePlayers(
        myId: String,
        onUpdate: (List<OnlinePlayer>) -> Unit
    ): ListenerRegistration {
        return onlineCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Listen error: ${error.message}")
                return@addSnapshotListener
            }
            val now = System.currentTimeMillis()
            val players = snapshot?.documents?.mapNotNull { doc ->
                val name = doc.getString("displayName") ?: return@mapNotNull null
                val ts = doc.getLong("timestamp") ?: 0L
                // Filter out stale players and self
                if (doc.id != myId && (now - ts) < ONLINE_TIMEOUT_MS) {
                    OnlinePlayer(id = doc.id, displayName = name, timestamp = ts)
                } else null
            }?.sortedByDescending { it.timestamp } ?: emptyList()
            onUpdate(players)
        }
    }

    /** Send a chess challenge */
    suspend fun sendChallenge(
        fromId: String, fromName: String,
        toId: String, toName: String
    ): String? {
        return try {
            val challenge = hashMapOf(
                "fromId" to fromId,
                "fromName" to fromName,
                "toId" to toId,
                "toName" to toName,
                "status" to "pending",
                "gameUrl" to "",
                "opponentUrl" to "",
                "timestamp" to System.currentTimeMillis()
            )
            val doc = challengeCollection.add(challenge).await()
            Log.d(TAG, "Challenge sent: ${doc.id}")
            doc.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send challenge: ${e.message}")
            null
        }
    }

    /** Listen for incoming challenges */
    fun listenIncomingChallenges(
        myId: String,
        onChallenge: (ChessChallenge) -> Unit
    ): ListenerRegistration {
        return challengeCollection
            .whereEqualTo("toId", myId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val challenge = ChessChallenge(
                            id = doc.id,
                            fromId = doc.getString("fromId") ?: "",
                            fromName = doc.getString("fromName") ?: "",
                            toId = doc.getString("toId") ?: "",
                            toName = doc.getString("toName") ?: "",
                            status = doc.getString("status") ?: "pending",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                        onChallenge(challenge)
                    }
                }
            }
    }

    /** Listen for challenge status updates (for the sender) */
    fun listenChallengeStatus(
        challengeId: String,
        onUpdate: (ChessChallenge) -> Unit
    ): ListenerRegistration {
        return challengeCollection.document(challengeId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener
                val challenge = ChessChallenge(
                    id = doc.id,
                    fromId = doc.getString("fromId") ?: "",
                    fromName = doc.getString("fromName") ?: "",
                    toId = doc.getString("toId") ?: "",
                    toName = doc.getString("toName") ?: "",
                    status = doc.getString("status") ?: "pending",
                    gameUrl = doc.getString("gameUrl") ?: "",
                    opponentUrl = doc.getString("opponentUrl") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
                onUpdate(challenge)
            }
    }

    /** Accept a challenge — create Lichess game and update Firestore */
    suspend fun acceptChallenge(challengeId: String): Pair<String, String>? {
        return try {
            // Create Lichess open challenge
            val urls = createLichessGame()
            if (urls != null) {
                challengeCollection.document(challengeId).update(
                    mapOf(
                        "status" to "accepted",
                        "gameUrl" to urls.first,
                        "opponentUrl" to urls.second
                    )
                ).await()
                Log.d(TAG, "Challenge accepted: $challengeId")
            }
            urls
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept: ${e.message}")
            null
        }
    }

    /** Decline a challenge */
    suspend fun declineChallenge(challengeId: String) {
        try {
            challengeCollection.document(challengeId).update("status", "declined").await()
        } catch (_: Exception) {}
    }

    /** Create a Lichess open challenge — returns (challengerUrl, opponentUrl) */
    private suspend fun createLichessGame(): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://lichess.org/api/challenge/open")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true

                // 10+0 rapid game, random color
                val body = "clock.limit=600&clock.increment=0&rated=false"
                conn.outputStream.write(body.toByteArray())

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    // Parse JSON response for URLs
                    val challengeUrl = Regex("\"url\"\\s*:\\s*\"(https://lichess\\.org/[^\"]+)\"")
                        .find(response)?.groupValues?.get(1)
                    val opponentUrl = Regex("\"urlWhite\"\\s*:\\s*\"(https://lichess\\.org/[^\"]+)\"")
                        .find(response)?.groupValues?.get(1)
                        ?: Regex("\"urlBlack\"\\s*:\\s*\"(https://lichess\\.org/[^\"]+)\"")
                            .find(response)?.groupValues?.get(1)

                    if (challengeUrl != null) {
                        // If we can't parse separate URLs, both use the same challenge URL
                        val opponent = opponentUrl ?: challengeUrl
                        Log.d(TAG, "Lichess game created: $challengeUrl")
                        Pair(challengeUrl, opponent)
                    } else {
                        Log.e(TAG, "Failed to parse Lichess response: $response")
                        null
                    }
                } else {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                    Log.e(TAG, "Lichess API error $responseCode: $error")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lichess API failed: ${e.message}")
                null
            }
        }
    }

    /** Clean up expired challenges */
    suspend fun cleanupExpiredChallenges() {
        try {
            val cutoff = System.currentTimeMillis() - 120_000L // 2 min old
            val expired = challengeCollection
                .whereEqualTo("status", "pending")
                .whereLessThan("timestamp", cutoff)
                .get().await()
            expired.documents.forEach { it.reference.delete() }
        } catch (_: Exception) {}
    }
}
