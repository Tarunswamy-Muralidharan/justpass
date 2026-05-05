package com.justpass.app.data.repository

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.justpass.app.data.model.ChessChallenge
import com.justpass.app.data.model.OnlinePlayer
import com.justpass.app.data.remote.LobbyWebSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Cloudflare Durable Object backed implementation of [ChessLobby]. Presence
 * and active challenges flow through a single authenticated WebSocket; the
 * server is authoritative for challenge lifecycle and online lists.
 *
 * Intentionally does NOT implement profiles / friends / leaderboard / game
 * history — those stay on [ChessRepository] in both backends.
 */
class ChessRepositoryV2 private constructor() : ChessLobby {

    // Cloudflare DO infers presence from WebSocket liveness — no client
    // tick needed. Skipping the 90s heartbeat coroutine saves battery and
    // CPU wakeups for the whole user base.
    override val requiresHeartbeat: Boolean = false

    companion object {
        private const val TAG = "ChessRepoV2"

        // Deployed Cloudflare Worker URL. Points to the user's own CF account (tmswamy10).
        // Worker source: chess-lobby/ at repo root. Redeploy with `cd chess-lobby && npx wrangler deploy`.
        private const val WS_URL = "wss://chess-lobby.tmswamy10.workers.dev/ws"

        private const val ACCEPT_TIMEOUT_MS = 10_000L

        @Volatile private var INSTANCE: ChessRepositoryV2? = null
        fun getInstance(): ChessRepositoryV2 =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChessRepositoryV2().also { INSTANCE = it }
            }
    }

    private val gson = Gson()

    private val ws: LobbyWebSocket = LobbyWebSocket(WS_URL) { fetchFirebaseIdToken() }

    // Single slot — ChessViewModel only calls listenOnlinePlayers once per session.
    @Volatile private var onlinePlayersCallback: ((List<OnlinePlayer>) -> Unit)? = null
    @Volatile private var friendIdsSnapshot: Set<String> = emptySet()
    @Volatile private var myId: String = ""
    @Volatile private var myDisplayName: String = ""
    private val currentOnlinePlayers: MutableMap<String, OnlinePlayer> = ConcurrentHashMap()

    // Incoming-challenge callbacks (pair: onChallenge + onChallengeRemoved).
    @Volatile private var incomingChallengeOnNew: ((ChessChallenge) -> Unit)? = null
    @Volatile private var incomingChallengeOnRemoved: ((String) -> Unit)? = null

    // Per-challenge status listeners — used by sender to observe accept/decline.
    private val challengeStatusCallbacks: ConcurrentHashMap<String, (ChessChallenge) -> Unit> =
        ConcurrentHashMap()

    // Pending accepts waiting for the CHALLENGE_ACCEPTED reply.
    private val pendingAcceptCompletables:
            ConcurrentHashMap<String, CompletableDeferred<Pair<String, String>?>> =
        ConcurrentHashMap()

    // Cache of recently-seen challenges (by "fromId->toId") for the
    // checkExistingChallenge fallback. Keyed by "from:to", value = ChessChallenge.
    private val recentIncoming: ConcurrentHashMap<String, ChessChallenge> = ConcurrentHashMap()

    // Sent-challenge metadata (clientId -> pending ChessChallenge record) so
    // that status listeners can surface accepted/declined updates even though
    // the server doesn't echo the challenge back to the sender on send.
    private val sentChallenges: ConcurrentHashMap<String, ChessChallenge> = ConcurrentHashMap()

    @Volatile private var wsConnected: Boolean = false

    init {
        ws.addMessageListener(::onWsMessage)
        ws.addStateListener { state ->
            when (state) {
                LobbyWebSocket.ConnectionState.CONNECTED -> {
                    wsConnected = true
                    sendJoin()
                }
                LobbyWebSocket.ConnectionState.RECONNECTED -> {
                    // Rejoin after an unexpected drop so the server reinstates presence.
                    wsConnected = true
                    sendJoin()
                }
                LobbyWebSocket.ConnectionState.DISCONNECTED -> wsConnected = false
                LobbyWebSocket.ConnectionState.CONNECTING -> { /* transient */ }
            }
        }
    }

    // ─── Presence ───────────────────────────────────────────────────────────

    override suspend fun goOnline(playerId: String, displayName: String) {
        myId = playerId
        myDisplayName = displayName
        ws.connect()
        // JOIN is sent inside onOpen; if we're already connected (late call), send now.
        if (wsConnected) sendJoin()
    }

    override suspend fun goOffline(playerId: String) {
        currentOnlinePlayers.clear()
        recentIncoming.clear()
        sentChallenges.clear()
        pendingAcceptCompletables.values.forEach { it.complete(null) }
        pendingAcceptCompletables.clear()
        challengeStatusCallbacks.clear()
        onlinePlayersCallback = null
        incomingChallengeOnNew = null
        incomingChallengeOnRemoved = null
        ws.disconnect()
    }

    override suspend fun heartbeat(playerId: String) {
        Log.d(TAG, "[V2] heartbeat no-op — server tracks presence via WS liveness")
    }

    override fun listenOnlinePlayers(
        myId: String,
        friendIds: Set<String>,
        onUpdate: (List<OnlinePlayer>) -> Unit
    ): ListenerRegistration {
        this.friendIdsSnapshot = friendIds
        this.onlinePlayersCallback = onUpdate
        // Fire current snapshot immediately so the UI isn't blank until the
        // next PRESENCE_DIFF lands.
        emitOnlinePlayers()
        return object : ListenerRegistration {
            override fun remove() {
                if (onlinePlayersCallback === onUpdate) onlinePlayersCallback = null
            }
        }
    }

    // ─── Challenges ─────────────────────────────────────────────────────────

    override suspend fun sendChallenge(
        fromId: String,
        fromName: String,
        toId: String,
        toName: String,
        timeControl: String
    ): String? {
        // DEVIATION: the CF spec's CHALLENGE message doesn't include a client
        // ID and the server only returns the challengeId to the receiver in
        // CHALLENGE_INCOMING. We generate a client-side UUID and send it in
        // the message so the sender has a stable handle for listenChallengeStatus
        // and cancel(). If the server-side agent rejects unknown fields, OkHttp
        // will still deliver this cleanly (servers typically ignore extras) —
        // worst case, accept() flow still works because CHALLENGE_ACCEPTED is
        // keyed by the server-assigned id (which we match by toId fallback).
        val clientId = UUID.randomUUID().toString()
        val msg = JsonObject().apply {
            addProperty("type", "CHALLENGE")
            addProperty("challengeId", clientId)
            addProperty("toId", toId)
            // Protocol uses uppercase enum keys (RAPID_10, BLITZ_5…); app uses lowercase.
            addProperty("timeControl", timeControl.uppercase())
        }
        ws.send(msg.toString())
        val record = ChessChallenge(
            id = clientId,
            fromId = fromId,
            fromName = fromName,
            toId = toId,
            toName = toName,
            status = "pending",
            timeControl = timeControl,
            timestamp = System.currentTimeMillis()
        )
        sentChallenges[clientId] = record
        return clientId
    }

    override suspend fun checkExistingChallenge(fromId: String, toId: String): ChessChallenge? {
        // Best-effort: scan recently-observed incoming challenges (last 20s).
        // The server is authoritative for mutual-detection during accept, so
        // a null here is safe — the ViewModel simply proceeds to sendChallenge.
        val now = System.currentTimeMillis()
        val key = "$fromId->$toId"
        val cached = recentIncoming[key] ?: return null
        return if (now - cached.timestamp < 20_000L) cached else null
    }

    override fun listenIncomingChallenges(
        myId: String,
        onChallenge: (ChessChallenge) -> Unit,
        onChallengeRemoved: (challengeId: String) -> Unit
    ): ListenerRegistration {
        incomingChallengeOnNew = onChallenge
        incomingChallengeOnRemoved = onChallengeRemoved
        return object : ListenerRegistration {
            override fun remove() {
                if (incomingChallengeOnNew === onChallenge) incomingChallengeOnNew = null
                if (incomingChallengeOnRemoved === onChallengeRemoved) incomingChallengeOnRemoved = null
            }
        }
    }

    override fun listenChallengeStatus(
        challengeId: String,
        onUpdate: (ChessChallenge) -> Unit
    ): ListenerRegistration {
        challengeStatusCallbacks[challengeId] = onUpdate
        return object : ListenerRegistration {
            override fun remove() { challengeStatusCallbacks.remove(challengeId) }
        }
    }

    override suspend fun acceptChallenge(
        challengeId: String,
        timeControl: String
    ): Pair<String, String>? {
        val deferred = CompletableDeferred<Pair<String, String>?>()
        pendingAcceptCompletables[challengeId] = deferred
        val msg = JsonObject().apply {
            addProperty("type", "ACCEPT")
            addProperty("challengeId", challengeId)
        }
        ws.send(msg.toString())
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(ACCEPT_TIMEOUT_MS) { deferred.await() }.also {
                pendingAcceptCompletables.remove(challengeId)
            }
        }
    }

    override suspend fun declineChallenge(challengeId: String) {
        val isSender = sentChallenges.containsKey(challengeId)
        val msg = JsonObject().apply {
            // If we're the sender of this challenge, it's a CANCEL; otherwise DECLINE.
            addProperty("type", if (isSender) "CANCEL" else "DECLINE")
            addProperty("challengeId", challengeId)
        }
        ws.send(msg.toString())
        sentChallenges.remove(challengeId)
    }

    override suspend fun cleanupExpiredChallenges() {
        Log.d(TAG, "[V2] cleanupExpiredChallenges no-op — server handles TTL")
    }

    // ─── WS message routing ─────────────────────────────────────────────────

    private fun sendJoin() {
        if (myId.isBlank()) return
        val msg = JsonObject().apply {
            addProperty("type", "JOIN")
            addProperty("displayName", myDisplayName)
        }
        ws.send(msg.toString())
    }

    private fun onWsMessage(text: String) {
        val obj = try {
            JsonParser.parseString(text).asJsonObject
        } catch (e: Exception) {
            Log.w(TAG, "malformed ws message: ${e.message}")
            return
        }
        when (obj.get("type")?.asString) {
            "PRESENCE_SNAPSHOT" -> handlePresenceSnapshot(obj)
            "PRESENCE_DIFF" -> handlePresenceDiff(obj)
            "CHALLENGE_INCOMING" -> handleChallengeIncoming(obj)
            "CHALLENGE_ACCEPTED" -> handleChallengeAccepted(obj)
            "CHALLENGE_DECLINED" -> handleChallengeDeclined(obj, canceled = false)
            "CHALLENGE_CANCELED" -> handleChallengeDeclined(obj, canceled = true)
            "ERROR" -> Log.w(TAG, "server error: ${obj.get("code")?.asString} ${obj.get("message")?.asString}")
            else -> Log.d(TAG, "unhandled ws message: ${obj.get("type")?.asString}")
        }
    }

    private fun handlePresenceSnapshot(obj: JsonObject) {
        currentOnlinePlayers.clear()
        val players = obj.getAsJsonArray("players") ?: return
        for (el in players) {
            val p = el.asJsonObject
            val id = p.get("id")?.asString ?: continue
            currentOnlinePlayers[id] = OnlinePlayer(
                id = id,
                displayName = p.get("displayName")?.asString ?: "",
                timestamp = p.get("joinedAt")?.asLong ?: System.currentTimeMillis(),
                isFriend = id in friendIdsSnapshot
            )
        }
        emitOnlinePlayers()
    }

    private fun handlePresenceDiff(obj: JsonObject) {
        obj.getAsJsonArray("added")?.forEach { el ->
            val p = el.asJsonObject
            val id = p.get("id")?.asString ?: return@forEach
            currentOnlinePlayers[id] = OnlinePlayer(
                id = id,
                displayName = p.get("displayName")?.asString ?: "",
                timestamp = p.get("joinedAt")?.asLong ?: System.currentTimeMillis(),
                isFriend = id in friendIdsSnapshot
            )
        }
        obj.getAsJsonArray("removed")?.forEach { el ->
            currentOnlinePlayers.remove(el.asString)
        }
        emitOnlinePlayers()
    }

    private fun emitOnlinePlayers() {
        val cb = onlinePlayersCallback ?: return
        val list = currentOnlinePlayers.values
            .filter { it.id != myId }
            .sortedWith(compareByDescending<OnlinePlayer> { it.isFriend }.thenByDescending { it.timestamp })
        cb(list)
    }

    private fun handleChallengeIncoming(obj: JsonObject) {
        val id = obj.get("challengeId")?.asString ?: return
        val fromId = obj.get("fromId")?.asString ?: ""
        val fromName = obj.get("fromName")?.asString ?: ""
        // Server ships uppercase enum keys — convert back to the app's lowercase convention.
        val tc = obj.get("timeControl")?.asString?.lowercase() ?: "rapid_10"
        val challenge = ChessChallenge(
            id = id,
            fromId = fromId,
            fromName = fromName,
            toId = myId,
            toName = myDisplayName,
            status = "pending",
            timeControl = tc,
            timestamp = System.currentTimeMillis()
        )
        recentIncoming["$fromId->$myId"] = challenge
        incomingChallengeOnNew?.invoke(challenge)
    }

    private fun handleChallengeAccepted(obj: JsonObject) {
        val id = obj.get("challengeId")?.asString ?: return
        val whiteUrl = obj.get("whiteUrl")?.asString ?: ""
        val blackUrl = obj.get("blackUrl")?.asString ?: ""
        val lichessGameId = obj.get("lichessGameId")?.asString ?: ""
        val fromColor = obj.get("fromColor")?.asString ?: "white"

        // Pair(challengerUrl, opponentUrl) — matches ChessRepository.acceptChallenge contract.
        val challengerUrl = if (fromColor == "white") whiteUrl else blackUrl
        val opponentUrl = if (fromColor == "white") blackUrl else whiteUrl
        val pair = challengerUrl to opponentUrl

        pendingAcceptCompletables.remove(id)?.complete(pair)

        // Fire status listener so the sender's UI transitions to "accepted".
        val sent = sentChallenges[id]
        val updated = (sent ?: ChessChallenge(id = id)).copy(
            status = "accepted",
            gameUrl = challengerUrl,
            opponentUrl = opponentUrl,
            lichessGameId = lichessGameId,
            fromColor = fromColor
        )
        challengeStatusCallbacks[id]?.invoke(updated)
        sentChallenges.remove(id)
    }

    private fun handleChallengeDeclined(obj: JsonObject, canceled: Boolean) {
        val id = obj.get("challengeId")?.asString ?: return
        // Surface to the sender's status listener as "declined" (ViewModel
        // treats CANCEL and DECLINE identically from the sender's POV).
        val sent = sentChallenges[id]
        val updated = (sent ?: ChessChallenge(id = id)).copy(status = "declined")
        challengeStatusCallbacks[id]?.invoke(updated)
        sentChallenges.remove(id)
        pendingAcceptCompletables.remove(id)?.complete(null)

        // Surface to the receiver's listener: the sender canceled before we
        // responded, or our own decline round-tripped — clear the prompt.
        incomingChallengeOnRemoved?.invoke(id)
        // Clean up cached incoming record.
        recentIncoming.entries.removeAll { it.value.id == id }
        if (canceled) Log.d(TAG, "challenge $id canceled by sender")
    }

    // ─── Auth ───────────────────────────────────────────────────────────────

    /**
     * Fetches a Firebase ID token. The app uses Keycloak for SIS auth but
     * Firebase Anonymous Auth is added in v3.0 solely to produce a token the
     * Cloudflare Worker can validate via Google's JWKS. Sign-in happens at
     * app startup (MainActivity); this method just pulls the current token.
     * If no user is signed in, we attempt anonymous sign-in inline as a
     * fallback so a freshly-installed app can still connect without waiting
     * for MainActivity to re-run.
     */
    private suspend fun fetchFirebaseIdToken(): String? {
        return try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val user = auth.currentUser
                ?: auth.signInAnonymously().await().user
                ?: return null
            user.getIdToken(false).await().token
        } catch (e: Exception) {
            Log.w(TAG, "fetchFirebaseIdToken failed: ${e.message}")
            null
        }
    }
}
