package com.justpass.app.data.remote

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * OkHttp WebSocket wrapper for the Cloudflare Durable Object chess lobby.
 *
 * - Auto-reconnects with exponential backoff (1s → 30s cap) on unexpected
 *   drops; stops reconnecting only after [disconnect].
 * - Queues outbound frames while the socket is down (dropping the oldest
 *   once the queue exceeds 100, which signals something is very wrong).
 * - Marshals every listener callback to the main thread so upstream code
 *   doesn't have to reason about OkHttp's dispatcher.
 * - Re-emits a [ConnectionState.RECONNECTED] edge after every successful
 *   reconnect so the caller can re-send JOIN without conflating the first
 *   connect with later ones.
 */
class LobbyWebSocket(
    private val wsUrl: String,
    private val tokenProvider: suspend () -> String?
) {

    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, RECONNECTED }

    companion object {
        private const val TAG = "LobbyWS"
        private const val MAX_QUEUE = 100
        private const val BACKOFF_START_MS = 1_000L
        private const val BACKOFF_CAP_MS = 30_000L
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        // pings are cheap insurance against idle NAT timeouts
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Guarded by `this`.
    private var webSocket: WebSocket? = null
    private val sendQueue: ArrayDeque<String> = ArrayDeque()
    private val messageListeners: MutableList<(String) -> Unit> = mutableListOf()
    private val stateListeners: MutableList<(ConnectionState) -> Unit> = mutableListOf()
    private var hasConnectedOnce = false

    private val wantConnected = AtomicBoolean(false)
    private val reconnectAttempt = AtomicInteger(0)

    // ─── Public API ─────────────────────────────────────────────────────────

    fun connect() {
        if (!wantConnected.compareAndSet(false, true)) return
        emitState(ConnectionState.CONNECTING)
        scope.launch { openSocket() }
    }

    fun disconnect() {
        if (!wantConnected.compareAndSet(true, false)) return
        val ws: WebSocket?
        synchronized(this) {
            ws = webSocket
            webSocket = null
            sendQueue.clear()
            // NOTE: keep the listener lists intact. ChessRepositoryV2 is a
            // singleton that registers listeners ONCE in its `init` block, and
            // a goOffline → goOnline cycle (very common — chess screen mount/
            // unmount or app foreground/background) calls disconnect() then
            // connect() on the same socket. Clearing listeners here meant the
            // user looked online optimistically but the repo never received a
            // single PRESENCE_DIFF after the second connect.
        }
        try { ws?.close(1000, "client disconnect") } catch (_: Exception) {}
        emitState(ConnectionState.DISCONNECTED)
    }

    fun send(json: String) {
        val ws: WebSocket?
        synchronized(this) {
            ws = webSocket
            if (ws == null) {
                if (sendQueue.size >= MAX_QUEUE) {
                    Log.w(TAG, "send queue full ($MAX_QUEUE) — dropping oldest")
                    sendQueue.pollFirst()
                }
                sendQueue.addLast(json)
                return
            }
        }
        ws?.send(json)
    }

    fun addMessageListener(listener: (String) -> Unit): () -> Unit {
        synchronized(this) { messageListeners.add(listener) }
        return { synchronized(this) { messageListeners.remove(listener) } }
    }

    fun addStateListener(listener: (ConnectionState) -> Unit): () -> Unit {
        synchronized(this) { stateListeners.add(listener) }
        return { synchronized(this) { stateListeners.remove(listener) } }
    }

    // ─── Internals ──────────────────────────────────────────────────────────

    private suspend fun openSocket() {
        val token = try { tokenProvider() } catch (e: Exception) {
            Log.w(TAG, "tokenProvider threw: ${e.message}")
            null
        }
        val builder = Request.Builder().url(wsUrl)
        if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
        val req = builder.build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val queued: List<String>
                synchronized(this@LobbyWebSocket) {
                    this@LobbyWebSocket.webSocket = webSocket
                    reconnectAttempt.set(0)
                    queued = sendQueue.toList()
                    sendQueue.clear()
                }
                queued.forEach { webSocket.send(it) }
                val wasReconnect = hasConnectedOnce
                hasConnectedOnce = true
                emitState(if (wasReconnect) ConnectionState.RECONNECTED else ConnectionState.CONNECTED)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                dispatchMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                dispatchMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                try { webSocket.close(code, reason) } catch (_: Exception) {}
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                synchronized(this@LobbyWebSocket) {
                    if (this@LobbyWebSocket.webSocket === webSocket) {
                        this@LobbyWebSocket.webSocket = null
                    }
                }
                emitState(ConnectionState.DISCONNECTED)
                scheduleReconnectIfWanted()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ws failure: ${t.message}")
                synchronized(this@LobbyWebSocket) {
                    if (this@LobbyWebSocket.webSocket === webSocket) {
                        this@LobbyWebSocket.webSocket = null
                    }
                }
                emitState(ConnectionState.DISCONNECTED)
                scheduleReconnectIfWanted()
            }
        }

        try {
            client.newWebSocket(req, listener)
        } catch (e: Exception) {
            Log.w(TAG, "newWebSocket failed: ${e.message}")
            emitState(ConnectionState.DISCONNECTED)
            scheduleReconnectIfWanted()
        }
    }

    private fun scheduleReconnectIfWanted() {
        if (!wantConnected.get()) return
        val attempt = reconnectAttempt.incrementAndGet()
        val delay = min(BACKOFF_CAP_MS, BACKOFF_START_MS * (1L shl (attempt - 1).coerceAtMost(5)))
        Log.d(TAG, "reconnect attempt $attempt in ${delay}ms")
        mainHandler.postDelayed({
            if (wantConnected.get()) {
                emitState(ConnectionState.CONNECTING)
                scope.launch { openSocket() }
            }
        }, delay)
    }

    private fun dispatchMessage(text: String) {
        val snapshot: List<(String) -> Unit>
        synchronized(this) { snapshot = messageListeners.toList() }
        mainHandler.post { snapshot.forEach { it(text) } }
    }

    private fun emitState(state: ConnectionState) {
        val snapshot: List<(ConnectionState) -> Unit>
        synchronized(this) { snapshot = stateListeners.toList() }
        mainHandler.post { snapshot.forEach { it(state) } }
    }
}
