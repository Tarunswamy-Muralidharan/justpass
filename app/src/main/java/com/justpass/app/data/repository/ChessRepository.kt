package com.justpass.app.data.repository

import android.util.Log
import com.justpass.app.data.model.CHESS_NAMES
import com.justpass.app.data.model.ChessChallenge
import com.justpass.app.data.model.ChessProfile
import com.justpass.app.data.model.FriendRequest
import com.justpass.app.data.model.OnlinePlayer
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
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
        private const val ONLINE_TIMEOUT_MS = 150_000L // 150 seconds stale (heartbeat is 90s) — ~1.6× buffer lets one missed tick slide without falsely hiding the user
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
        if (result !in listOf("win", "loss", "draw")) return
        try {
            // FieldValue.increment is atomic on the server, so two concurrent
            // results landing on the same profile (e.g. both players in two
            // games at once) can't drop an update the way the old read-modify-
            // write transaction could.
            val statField = when (result) { "win" -> "wins"; "loss" -> "losses"; else -> "draws" }
            profileCollection.document(playerId).update(mapOf(
                statField to FieldValue.increment(1),
                "gamesPlayed" to FieldValue.increment(1),
            )).await()
        } catch (e: Exception) {
            Log.e(TAG, "Record result error: ${e.message}")
        }
    }

    // ─── Leaderboard ────────────────────────────────────────────────────────

    /** Get full friend profiles (including offline) */
    suspend fun getFriendProfiles(friendIds: Set<String>): List<ChessProfile> {
        if (friendIds.isEmpty()) return emptyList()
        return try {
            // Firestore `whereIn` max 30 items
            friendIds.chunked(30).flatMap { chunk ->
                profileCollection.whereIn("__name__", chunk.toList())
                    .get().await().documents.mapNotNull { doc ->
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
                    }
            }.sortedByDescending { it.rating }
        } catch (e: Exception) {
            Log.e(TAG, "Get friends error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getLeaderboard(limit: Int = 20): List<ChessProfile> {
        return try {
            // Fetch all profiles and filter/sort client-side to avoid Firestore composite index issues
            val docs = profileCollection
                .get().await()
            docs.documents.mapNotNull { doc ->
                val gamesPlayed = doc.getLong("gamesPlayed")?.toInt() ?: 0
                if (gamesPlayed == 0) return@mapNotNull null
                ChessProfile(
                    id = doc.id,
                    displayName = doc.getString("displayName") ?: "",
                    nickname = doc.getString("nickname") ?: "",
                    nameMode = doc.getString("nameMode") ?: "random",
                    wins = doc.getLong("wins")?.toInt() ?: 0,
                    losses = doc.getLong("losses")?.toInt() ?: 0,
                    draws = doc.getLong("draws")?.toInt() ?: 0,
                    gamesPlayed = gamesPlayed,
                    lastOnline = doc.getLong("lastOnline") ?: 0L
                )
            }.sortedByDescending { it.rating }.take(limit)
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

    suspend fun sendChallenge(fromId: String, fromName: String, toId: String, toName: String, timeControl: String = "rapid_10"): String? {
        return try {
            val doc = challengeCollection.add(hashMapOf(
                "fromId" to fromId, "fromName" to fromName,
                "toId" to toId, "toName" to toName,
                "status" to "pending", "gameUrl" to "", "opponentUrl" to "",
                "lichessGameId" to "", "timeControl" to timeControl,
                // Local clock kept for backward-compat with users on builds that
                // don't read serverTs. New clients use serverTs as the countdown
                // anchor — same value on both sides, no clock-skew/latency gap.
                "timestamp" to System.currentTimeMillis(),
                "serverTs" to FieldValue.serverTimestamp()
            )).await()
            doc.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send challenge: ${e.message}")
            null
        }
    }

    /** Decode a Firestore Timestamp field into ms since epoch. Returns 0 if missing. */
    private fun DocumentSnapshot.serverTsMillis(field: String): Long =
        getTimestamp(field)?.toDate()?.time ?: 0L

    /**
     * Check if there's an existing pending challenge from fromId to toId.
     * Used for mutual challenge prevention — first challenge wins.
     */
    suspend fun checkExistingChallenge(fromId: String, toId: String): ChessChallenge? {
        return try {
            val docs = challengeCollection
                .whereEqualTo("fromId", fromId)
                .whereEqualTo("toId", toId)
                .whereEqualTo("status", "pending")
                .get().await()
            val now = System.currentTimeMillis()
            // Only consider challenges less than 20 seconds old — stale ones are ignored and cleaned up
            val doc = docs.documents.firstOrNull { d ->
                val ts = d.getLong("timestamp") ?: 0L
                now - ts < 20_000L
            }
            if (doc == null) {
                // Clean up any stale pending challenges we found
                docs.documents.forEach { d ->
                    val ts = d.getLong("timestamp") ?: 0L
                    if (now - ts >= 20_000L) {
                        try { d.reference.delete() } catch (_: Exception) {}
                    }
                }
                return null
            }
            ChessChallenge(
                id = doc.id,
                fromId = doc.getString("fromId") ?: "",
                fromName = doc.getString("fromName") ?: "",
                toId = doc.getString("toId") ?: "",
                toName = doc.getString("toName") ?: "",
                status = "pending",
                timestamp = doc.getLong("timestamp") ?: 0L,
                serverTimestamp = doc.serverTsMillis("serverTs"),
                timeControl = doc.get("timeControl")?.toString() ?: "rapid_10"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Check existing challenge error: ${e.message}")
            null
        }
    }

    fun listenIncomingChallenges(
        myId: String,
        onChallenge: (ChessChallenge) -> Unit,
        onChallengeRemoved: (challengeId: String) -> Unit = {}
    ): ListenerRegistration {
        return challengeCollection
            .whereEqualTo("toId", myId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.documentChanges?.forEach { change ->
                    when (change.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                            val doc = change.document
                            onChallenge(ChessChallenge(
                                id = doc.id,
                                fromId = doc.getString("fromId") ?: "",
                                fromName = doc.getString("fromName") ?: "",
                                toId = doc.getString("toId") ?: "",
                                toName = doc.getString("toName") ?: "",
                                status = "pending",
                                gameUrl = doc.getString("gameUrl") ?: "",
                                opponentUrl = doc.getString("opponentUrl") ?: "",
                                lichessGameId = doc.getString("lichessGameId") ?: "",
                                fromColor = doc.getString("fromColor") ?: "white",
                                resultChecked = doc.getBoolean("resultChecked") ?: false,
                                timeControl = doc.get("timeControl")?.toString() ?: "rapid_10",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                serverTimestamp = doc.serverTsMillis("serverTs")
                            ))
                        }
                        // Sender cancelled / modified the challenge elsewhere — drop
                        // it so the receiver's UI doesn't stay stuck on the prompt.
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                            onChallengeRemoved(change.document.id)
                        }
                    }
                }
            }
    }

    /**
     * Atomically mark a game as finished due to the opponent abandoning.
     * Credits winnerId with a win and loserId with a loss. Returns true
     * iff this call was the one to claim the challenge (prevents both
     * devices double-counting).
     */
    suspend fun recordAbandonmentResult(challengeId: String, winnerId: String, loserId: String): Boolean {
        return try {
            val docRef = challengeCollection.document(challengeId)
            val claimed = FirebaseFirestore.getInstance().runTransaction { txn ->
                val snap = txn.get(docRef)
                if (snap.getBoolean("resultChecked") == true) return@runTransaction false
                txn.update(docRef, mapOf(
                    "resultChecked" to true,
                    "abandoned" to true
                ))
                true
            }.await()
            if (claimed) {
                recordGameResult(winnerId, "win")
                recordGameResult(loserId, "loss")
                Log.d(TAG, "Recorded abandonment: $winnerId wins, $loserId loses")
            }
            claimed
        } catch (e: Exception) {
            Log.w(TAG, "recordAbandonmentResult failed: ${e.message}")
            false
        }
    }

    /**
     * Listen for the other player writing a leftBy field to the accepted challenge doc.
     * Callback fires whenever leftBy becomes non-null.
     */
    fun listenGameLeft(challengeId: String, onLeft: (leaverId: String, leaverName: String) -> Unit): ListenerRegistration {
        return challengeCollection.document(challengeId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener
                val leaverId = doc.getString("leftBy") ?: return@addSnapshotListener
                val leaverName = doc.getString("leftByName") ?: "Opponent"
                onLeft(leaverId, leaverName)
            }
    }

    /**
     * Atomic first-leaver-wins: if both devices disconnect simultaneously, the
     * first write lands and the second sees leftBy already set and bails. Prevents
     * last-write-wins from flipping who gets credited as loser.
     */
    suspend fun markGameLeft(challengeId: String, leaverId: String, leaverName: String): Boolean {
        return try {
            val docRef = challengeCollection.document(challengeId)
            val claimed = db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val existing = snap.getString("leftBy")
                if (!existing.isNullOrEmpty()) return@runTransaction false
                tx.update(docRef, mapOf(
                    "leftBy" to leaverId,
                    "leftByName" to leaverName,
                    "leftAt" to System.currentTimeMillis()
                ))
                true
            }.await()
            if (claimed == true) {
                Log.d(TAG, "Marked game $challengeId as left by $leaverName")
            } else {
                Log.d(TAG, "Game $challengeId already marked left — not overwriting")
            }
            claimed == true
        } catch (e: Exception) {
            Log.w(TAG, "markGameLeft failed: ${e.message}")
            false
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
                    fromColor = doc.getString("fromColor") ?: "white",
                    resultChecked = doc.getBoolean("resultChecked") ?: false,
                    timeControl = doc.get("timeControl")?.toString() ?: "rapid_10",
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    serverTimestamp = doc.serverTsMillis("serverTs")
                ))
            }
    }

    /** Returns Pair(challengerUrl, opponentUrl) — challenger = fromId, opponent = toId (acceptor) */
    suspend fun acceptChallenge(challengeId: String, timeControl: String = "rapid_10"): Pair<String, String>? {
        return try {
            val docRef = challengeCollection.document(challengeId)

            // Atomic claim: flip pending → accepting inside a transaction. If another
            // accepter already claimed it (double-tap, two notification cards), the
            // read sees status != "pending" and we bail out before calling Lichess.
            val claimed = db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.getString("status") != "pending") return@runTransaction false
                tx.update(docRef, "status", "accepting")
                true
            }.await()

            if (claimed != true) {
                Log.d(TAG, "Challenge $challengeId already claimed — skipping Lichess game creation")
                return null
            }

            val tc = try {
                com.justpass.app.data.model.TimeControl.valueOf(timeControl.uppercase())
            } catch (_: Exception) {
                com.justpass.app.data.model.TimeControl.RAPID_10
            }
            val result = createLichessGame(tc.paramString)
            if (result != null) {
                // result = (whiteUrl, blackUrl, gameId)
                // Challenger (fromId) gets white, Acceptor (toId) gets black
                docRef.update(mapOf(
                    "status" to "accepted",
                    "gameUrl" to result.first,       // white URL → challenger
                    "opponentUrl" to result.second,   // black URL → acceptor
                    "lichessGameId" to result.third,
                    "fromColor" to "white",
                    "resultChecked" to false
                )).await()
                Pair(result.first, result.second)
            } else {
                // Lichess game creation failed — revert so the challenge can be retried
                try { docRef.update("status", "pending").await() } catch (_: Exception) {}
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept: ${e.message}")
            null
        }
    }

    suspend fun declineChallenge(challengeId: String) {
        try { challengeCollection.document(challengeId).update("status", "declined").await() } catch (_: Exception) {}
    }

    /** Returns (whiteUrl, blackUrl, gameId) */
    private suspend fun createLichessGame(clockParams: String = "clock.limit=600&clock.increment=0&rated=false"): Triple<String, String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://lichess.org/api/challenge/open")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.outputStream.write(clockParams.toByteArray())

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    Log.d(TAG, "Lichess response: $response")
                    val whiteUrl = Regex("\"urlWhite\"\\s*:\\s*\"(https://lichess\\.org/[^\"]+)\"")
                        .find(response)?.groupValues?.get(1)
                    val blackUrl = Regex("\"urlBlack\"\\s*:\\s*\"(https://lichess\\.org/[^\"]+)\"")
                        .find(response)?.groupValues?.get(1)
                    val fallbackUrl = Regex("\"url\"\\s*:\\s*\"(https://lichess\\.org/[^\"]+)\"")
                        .find(response)?.groupValues?.get(1)
                    val gameId = Regex("\"id\"\\s*:\\s*\"([A-Za-z0-9]+)\"")
                        .find(response)?.groupValues?.get(1) ?: ""

                    val white = whiteUrl ?: fallbackUrl ?: return@withContext null
                    val black = blackUrl ?: fallbackUrl ?: return@withContext null
                    Log.d(TAG, "Lichess game: $gameId, white=$white, black=$black")
                    Triple(white, black, gameId)
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Lichess API failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Check game result from Lichess API.
     * Returns "white", "black", "draw", "ongoing", or "aborted".
     */
    suspend fun checkLichessGameResult(gameId: String): String? {
        if (gameId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://lichess.org/api/game/$gameId")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val status = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"")
                        .find(response)?.groupValues?.get(1) ?: ""
                    val winner = Regex("\"winner\"\\s*:\\s*\"([^\"]+)\"")
                        .find(response)?.groupValues?.get(1)

                    Log.d(TAG, "Game $gameId: status=$status, winner=$winner")

                    when {
                        status == "started" || status == "created" -> "ongoing"
                        status == "aborted" -> "aborted"
                        status == "draw" || status == "stalemate" -> "draw"
                        winner == "white" -> "white"
                        winner == "black" -> "black"
                        // resign, timeout, outoftime, mate — all have a winner
                        status in listOf("resign", "timeout", "outoftime", "mate", "cheat") -> winner
                        // noStart, unknownFinish — treat as aborted
                        else -> "aborted"
                    }
                } else {
                    Log.e(TAG, "Lichess game check failed: ${conn.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Check game result failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Process game result for both players in a challenge.
     * fromColor tells us which color the challenger (fromId) played.
     * Uses atomic claim to prevent double-counting when both players process simultaneously.
     */
    suspend fun processGameResult(challenge: ChessChallenge) {
        if (challenge.lichessGameId.isBlank() || challenge.resultChecked) return

        // Atomically claim this challenge — only one device processes it
        val claimed = try {
            val docRef = challengeCollection.document(challenge.id)
            FirebaseFirestore.getInstance().runTransaction { txn ->
                val snap = txn.get(docRef)
                if (snap.getBoolean("resultChecked") == true) return@runTransaction false
                txn.update(docRef, "resultChecked", true)
                true
            }.await()
        } catch (_: Exception) { false }

        if (!claimed) {
            Log.d(TAG, "Challenge ${challenge.id} already processed by another device")
            return
        }

        val result = checkLichessGameResult(challenge.lichessGameId) ?: return
        if (result == "ongoing") {
            // Unclaim — game not finished yet
            try { challengeCollection.document(challenge.id).update("resultChecked", false).await() } catch (_: Exception) {}
            return
        }

        val fromColor = challenge.fromColor.ifBlank { "white" }
        val toColor = if (fromColor == "white") "black" else "white"

        when (result) {
            "draw" -> {
                recordGameResult(challenge.fromId, "draw")
                recordGameResult(challenge.toId, "draw")
            }
            "aborted" -> { /* No rating change */ }
            fromColor -> {
                // Challenger won
                recordGameResult(challenge.fromId, "win")
                recordGameResult(challenge.toId, "loss")
            }
            toColor -> {
                // Acceptor won
                recordGameResult(challenge.fromId, "loss")
                recordGameResult(challenge.toId, "win")
            }
        }

        Log.d(TAG, "Processed result for ${challenge.lichessGameId}: $result (from=$fromColor)")
    }

    /** Get recent accepted challenges for a player (to check results) */
    suspend fun getRecentGames(playerId: String, limit: Int = 10): List<ChessChallenge> {
        return try {
            // Only fromId query uses composite index (fromId+status+timestamp)
            // toId query skips orderBy to avoid needing a second composite index
            val asSender = challengeCollection
                .whereEqualTo("fromId", playerId)
                .whereEqualTo("status", "accepted")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get().await()

            val asReceiver = challengeCollection
                .whereEqualTo("toId", playerId)
                .whereEqualTo("status", "accepted")
                .limit(limit.toLong())
                .get().await()

            (asSender.documents + asReceiver.documents).mapNotNull { doc ->
                ChessChallenge(
                    id = doc.id,
                    fromId = doc.getString("fromId") ?: "",
                    fromName = doc.getString("fromName") ?: "",
                    toId = doc.getString("toId") ?: "",
                    toName = doc.getString("toName") ?: "",
                    status = "accepted",
                    lichessGameId = doc.getString("lichessGameId") ?: "",
                    fromColor = doc.getString("fromColor") ?: "white",
                    resultChecked = doc.getBoolean("resultChecked") ?: false,
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Get recent games error: ${e.message}")
            emptyList()
        }
    }

    suspend fun cleanupExpiredChallenges() {
        try {
            val cutoff = System.currentTimeMillis() - 20_000L
            val expired = challengeCollection
                .whereEqualTo("status", "pending")
                .whereLessThan("timestamp", cutoff)
                .get().await()
            expired.documents.forEach { it.reference.delete() }
        } catch (_: Exception) {}
    }
}
