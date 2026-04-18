package com.example.attendancewidgetlaudea.data.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.attendancewidgetlaudea.data.model.AbsentDay
import com.example.attendancewidgetlaudea.data.model.AttendanceData
import com.example.attendancewidgetlaudea.data.model.AttendanceResponse
import com.example.attendancewidgetlaudea.data.model.CourseMarks
import com.example.attendancewidgetlaudea.data.model.CircularDetail
import com.example.attendancewidgetlaudea.data.model.CircularListResponse
import com.example.attendancewidgetlaudea.data.model.SignedUrlResponse
import com.example.attendancewidgetlaudea.data.model.TimetableResponse
import com.example.attendancewidgetlaudea.data.model.TokenResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class InvalidCredentialsException(message: String) : Exception(message)

/**
 * Auto-detected Keycloak configuration from /auth/config endpoints.
 * Falls back to known defaults if the config endpoint is unreachable.
 */
private data class KeycloakConfig(
    val realm: String,
    val authUrl: String,
    val clientId: String
) {
    val tokenUrl: String
        get() = "${authUrl.trimEnd('/')}/realms/$realm/protocol/openid-connect/token"
}

class WebViewAuthenticator(private val context: Context) {

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())


    // Auto-detected Keycloak configs — fetched once per session
    @Volatile private var sisKeycloakConfig: KeycloakConfig? = null
    @Volatile private var meetingsKeycloakConfig: KeycloakConfig? = null

    /**
     * Fetch Keycloak config from the server's /auth/config endpoint.
     * This is the same endpoint the browser uses, so it always has the current
     * realm, auth URL, and client_id — even if the server changes them.
     */
    private fun fetchKeycloakConfig(configUrl: String, fallback: KeycloakConfig): KeycloakConfig {
        return try {
            val url = java.net.URL(configUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            // Response is JSONP: parseKeycloakInfo({...})
            val jsonStr = body.substringAfter("(").substringBeforeLast(")")
            val json = org.json.JSONObject(jsonStr)
            val realm = json.getString("realm")
            val authUrl = json.optString("url", json.optString("auth-server-url", fallback.authUrl))
            val clientId = json.optString("clientId", json.optString("resource", fallback.clientId))
            android.util.Log.d("WebViewAuth", "Auto-detected Keycloak config: realm=$realm, authUrl=$authUrl, clientId=$clientId")
            KeycloakConfig(realm, authUrl, clientId)
        } catch (e: Exception) {
            android.util.Log.w("WebViewAuth", "Could not fetch Keycloak config from $configUrl, using fallback: ${e.message}")
            fallback
        }
    }

    /** Get the SIS Keycloak config, fetching from server if not yet cached. */
    private fun getSisConfig(): KeycloakConfig {
        return sisKeycloakConfig ?: fetchKeycloakConfig(
            "$SIS_BASE_URL/auth/config?callback=parseKeycloakInfo",
            KeycloakConfig("psgitech", "https://accounts.psgitech.ac.in/", "ies_sis")
        ).also { sisKeycloakConfig = it }
    }

    /** Get the Meetings Keycloak config, fetching from server if not yet cached. */
    private fun getMeetingsConfig(): KeycloakConfig {
        return meetingsKeycloakConfig ?: fetchKeycloakConfig(
            "${MEETINGS_BASE_URL}auth/config?callback=parseKeycloakInfo",
            KeycloakConfig("psgitech", "https://accounts.psgitech.ac.in/", "ies_meetings")
        ).also { meetingsKeycloakConfig = it }
    }

    // Cache the auth token for fast refreshes — persisted to SecurePreferences
    var cachedAuthToken: String?
        get() = _cachedAuthToken ?: com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context).accessToken.also { _cachedAuthToken = it }
        set(value) {
            _cachedAuthToken = value
            com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context).accessToken = value
        }
    private var _cachedAuthToken: String? = null

    var cachedMeetingsToken: String?
        get() = _cachedMeetingsToken ?: com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context).meetingsAccessToken.also { _cachedMeetingsToken = it }
        set(value) {
            _cachedMeetingsToken = value
            com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context).meetingsAccessToken = value
        }
    private var _cachedMeetingsToken: String? = null

    companion object {
        private const val SIS_BASE_URL = "https://laudea.psgitech.ac.in/sis"
        private const val LOGIN_URL_PATTERN = "accounts.psgitech.ac.in"
        private const val ATTENDANCE_API_PATTERN = "/sis/attendance/"
        private const val CA_MARKS_API_URL = "https://laudea.psgitech.ac.in/sis/ca/marks/v2/"
        private const val ATTENDANCE_API_BASE = "https://laudea.psgitech.ac.in/sis/attendance/"
        private const val TIMETABLE_API_BASE = "https://laudea.psgitech.ac.in/sis/time/table/"
        private const val MEETINGS_BASE_URL = "https://laudea.psgitech.ac.in/meetings/"
        // Browser-like User-Agent — the SIS server rejects/stalls requests without one
        private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 13; Moto G54) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
    }

    // Shared OkHttp client for authenticated SIS calls — the JVM's HttpURLConnection
    // hangs on the SIS server but OkHttp with HTTP/2 works.
    private val sisHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** Authenticated GET using OkHttp with a browser User-Agent. Returns null if token missing. */
    private suspend fun authenticatedGet(url: String, token: String): okhttp3.Response? = withContext(Dispatchers.IO) {
        try {
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", BROWSER_UA)
                .header("Referer", "https://laudea.psgitech.ac.in/sis/")
                .get()
                .build()
            sisHttpClient.newCall(req).execute()
        } catch (e: Exception) {
            android.util.Log.e("WebViewAuth", "OkHttp GET failed for $url: ${e.message}")
            null
        }
    }

    /**
     * Headless OAuth 2.0 Authorization Code flow — no WebView needed.
     * Replays what the SIS page would do: GET Keycloak login form, POST credentials,
     * catch the redirect with code, exchange code for token.
     * Works when grant_type=password is blocked (ies_sis client config).
     */
    suspend fun loginViaAuthCodeFlow(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val config = getSisConfig()
            val redirectUri = "https://laudea.psgitech.ac.in/sis/"
            val state = java.util.UUID.randomUUID().toString()

            // Cookie jar for Keycloak session cookies across redirects
            val cookieJar = object : okhttp3.CookieJar {
                private val cookies = mutableListOf<okhttp3.Cookie>()
                override fun saveFromResponse(url: okhttp3.HttpUrl, newCookies: List<okhttp3.Cookie>) {
                    cookies.removeAll { c -> newCookies.any { it.name == c.name && it.domain == c.domain } }
                    cookies.addAll(newCookies)
                }
                override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                    return cookies.filter { it.matches(url) }
                }
            }
            val client = okhttp3.OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // Step 1: GET Keycloak auth endpoint — returns login form HTML
            val authUrl = "${config.authUrl.trimEnd('/')}/realms/${config.realm}/protocol/openid-connect/auth" +
                "?client_id=${config.clientId}" +
                "&response_type=code" +
                "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, "UTF-8") +
                "&scope=" + java.net.URLEncoder.encode("openid offline_access", "UTF-8") +
                "&state=$state"
            android.util.Log.d("WebViewAuth", "Auth-code flow: GET $authUrl")

            var resp = client.newCall(okhttp3.Request.Builder().url(authUrl).build()).execute()
            // Follow any 302 redirects from the initial GET (sometimes Keycloak bounces through)
            var hops = 0
            while (resp.isRedirect && hops < 5) {
                val next = resp.header("Location") ?: break
                resp.close()
                val absUrl = if (next.startsWith("http")) next else "${config.authUrl.trimEnd('/')}$next"
                resp = client.newCall(okhttp3.Request.Builder().url(absUrl).build()).execute()
                hops++
            }
            if (resp.code != 200) {
                android.util.Log.e("WebViewAuth", "Auth-code flow: login page GET returned ${resp.code}")
                resp.close()
                return@withContext false
            }
            val html = resp.body?.string() ?: ""
            resp.close()

            // Already authenticated? Check if Location has code= (silent auth)
            // Otherwise, extract the login form action URL
            val actionMatch = Regex("""<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"""").find(html)
                ?: Regex("""<form[^>]*action="([^"]+)"[^>]*id="kc-form-login"""").find(html)
                ?: Regex("""<form[^>]*action="([^"]+)"""").find(html)
            if (actionMatch == null) {
                android.util.Log.e("WebViewAuth", "Auth-code flow: could not find login form (html len=${html.length})")
                return@withContext false
            }
            val actionUrl = actionMatch.groupValues[1].replace("&amp;", "&")
            android.util.Log.d("WebViewAuth", "Auth-code flow: POST credentials to $actionUrl")

            // Step 2: POST credentials
            val formBody = okhttp3.FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("credentialId", "")
                .build()
            val postReq = okhttp3.Request.Builder()
                .url(actionUrl)
                .post(formBody)
                .build()
            resp = client.newCall(postReq).execute()

            // Expect 302 redirect to redirect_uri with ?code=XXX&state=YYY
            if (!resp.isRedirect) {
                val code = resp.code
                val body = resp.body?.string() ?: ""
                resp.close()
                if (body.contains("Invalid username or password", ignoreCase = true) ||
                    body.contains("invalid", ignoreCase = true) && body.contains("credentials", ignoreCase = true)) {
                    throw InvalidCredentialsException("Invalid roll number or password")
                }
                android.util.Log.e("WebViewAuth", "Auth-code flow: credential POST returned $code (expected 302)")
                return@withContext false
            }
            val location = resp.header("Location") ?: ""
            resp.close()
            val codeMatch = Regex("""[?&#]code=([^&]+)""").find(location)
            if (codeMatch == null) {
                android.util.Log.e("WebViewAuth", "Auth-code flow: no code in redirect: $location")
                return@withContext false
            }
            val authCode = codeMatch.groupValues[1]
            android.util.Log.d("WebViewAuth", "Auth-code flow: captured auth code (len=${authCode.length})")

            // Step 3: Exchange code for token
            val tokenBody = okhttp3.FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", config.clientId)
                .add("code", authCode)
                .add("redirect_uri", redirectUri)
                .build()
            resp = client.newCall(okhttp3.Request.Builder().url(config.tokenUrl).post(tokenBody).build()).execute()
            if (resp.code != 200) {
                val err = resp.body?.string() ?: ""
                resp.close()
                android.util.Log.e("WebViewAuth", "Auth-code flow: token exchange returned ${resp.code}: $err")
                return@withContext false
            }
            val json = resp.body?.string() ?: ""
            resp.close()
            val tokenResponse = gson.fromJson(json, TokenResponse::class.java)
            cachedAuthToken = tokenResponse.accessToken
            val prefs = com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context)
            prefs.refreshToken = tokenResponse.refreshToken
            prefs.tokenExpiryTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)
            android.util.Log.d("WebViewAuth", "Auth-code flow: token captured, expires in ${tokenResponse.expiresIn}s")
            true
        } catch (e: InvalidCredentialsException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("WebViewAuth", "Auth-code flow error: ${e.message}", e)
            false
        }
    }

    /**
     * Get a fresh access token via direct Keycloak HTTP POST (no WebView).
     * Uses grant_type=password — ~200ms vs 15s WebView login.
     * Returns true if a new token was obtained.
     */
    suspend fun loginViaKeycloak(username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val config = getSisConfig()
                android.util.Log.d("WebViewAuth", "Direct Keycloak login for: $username (realm=${config.realm}, clientId=${config.clientId})")
                val url = java.net.URL(config.tokenUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val params = "grant_type=password&client_id=${config.clientId}" +
                    "&scope=openid%20offline_access" +
                    "&username=" + java.net.URLEncoder.encode(username, "UTF-8") +
                    "&password=" + java.net.URLEncoder.encode(password, "UTF-8")
                connection.outputStream.use { it.write(params.toByteArray()) }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val tokenResponse = gson.fromJson(json, TokenResponse::class.java)
                    cachedAuthToken = tokenResponse.accessToken
                    // Save refresh token and expiry for silent token renewal
                    val prefs = com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context)
                    prefs.refreshToken = tokenResponse.refreshToken
                    prefs.tokenExpiryTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
                    android.util.Log.d("WebViewAuth", "Direct Keycloak login successful, token expires in ${tokenResponse.expiresIn}s, refresh expires in ${tokenResponse.refreshExpiresIn}s, scope=${tokenResponse.scope}")
                    true
                } else if (responseCode == 401) {
                    // Definitely invalid credentials — don't fall back to WebView
                    android.util.Log.e("WebViewAuth", "Direct Keycloak login: invalid credentials (HTTP $responseCode)")
                    throw InvalidCredentialsException("Invalid roll number or password")
                } else if (responseCode == 400) {
                    // Could be invalid creds OR "direct access grants disabled"
                    val errBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "" } catch (_: Exception) { "" }
                    android.util.Log.e("WebViewAuth", "Direct Keycloak login HTTP 400: $errBody")
                    if (errBody.contains("invalid_grant")) {
                        throw InvalidCredentialsException("Invalid roll number or password")
                    }
                    // unauthorized_client — ies_sis blocks direct grants. Try headless auth code flow.
                    if (errBody.contains("unauthorized_client")) {
                        android.util.Log.d("WebViewAuth", "Direct grant blocked, trying auth code flow")
                        return@withContext loginViaAuthCodeFlow(username, password)
                    }
                    false
                } else {
                    android.util.Log.e("WebViewAuth", "Direct Keycloak login failed: HTTP $responseCode")
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Direct Keycloak login error: ${e.message}")
                false
            }
        }
    }

    /**
     * Silently refresh the access token using the stored refresh token.
     * Much faster than a full password login since no credentials are sent.
     * Returns true if a new access token was obtained.
     */
    suspend fun refreshAccessToken(): Boolean {
        val prefs = com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context)
        val refreshToken = prefs.refreshToken ?: return false

        return withContext(Dispatchers.IO) {
            try {
                val config = getSisConfig()
                android.util.Log.d("WebViewAuth", "Refreshing access token via refresh_token (realm=${config.realm})")
                val url = java.net.URL(config.tokenUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val params = "grant_type=refresh_token&client_id=${config.clientId}" +
                    "&scope=openid%20offline_access" +
                    "&refresh_token=" + java.net.URLEncoder.encode(refreshToken, "UTF-8")
                connection.outputStream.use { it.write(params.toByteArray()) }

                if (connection.responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val tokenResponse = gson.fromJson(json, TokenResponse::class.java)
                    cachedAuthToken = tokenResponse.accessToken
                    prefs.refreshToken = tokenResponse.refreshToken
                    prefs.tokenExpiryTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
                    android.util.Log.d("WebViewAuth", "Token refresh successful, expires in ${tokenResponse.expiresIn}s, refresh expires in ${tokenResponse.refreshExpiresIn}s, scope=${tokenResponse.scope}")
                    true
                } else {
                    android.util.Log.e("WebViewAuth", "Token refresh failed: HTTP ${connection.responseCode}")
                    prefs.refreshToken = null
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Token refresh error: ${e.message}")
                false
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun loginAndFetchAttendance(
        rollNumber: String,
        password: String
    ): Result<AttendanceData> = suspendCoroutine { continuation ->
        mainHandler.post {
            try {
                // STEP 1: Preserve Keycloak session cookies — SIS app's login.js crashes
                // without a cached user session (entitlements error on line 177).
                val cookieManager = CookieManager.getInstance()
                android.util.Log.d("WebViewAuth", "Starting login (preserving existing cookies)")

                // STEP 2: Create fresh WebView with no-cache settings
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                }

                // STEP 3: Enable cookies for this session
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)

                var loginAttempted = false
                var dataFetched = false
                var fetchStarted = false

                // JavaScript interface to receive data
                webView.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onDataReceived(jsonData: String) {
                        if (dataFetched) return
                        dataFetched = true

                        try {
                            val response = gson.fromJson(jsonData, AttendanceResponse::class.java)
                            val attendanceData = AttendanceData.fromResponse(response)
                            // Persist Keycloak session cookies so they survive process death
                            cookieManager.flush()
                            mainHandler.post {
                                webView.destroy()
                            }
                            continuation.resume(Result.success(attendanceData))
                        } catch (e: Exception) {
                            mainHandler.post {
                                webView.destroy()
                            }
                            continuation.resume(Result.failure(Exception("Failed to parse attendance data: ${e.message}")))
                        }
                    }

                    @JavascriptInterface
                    fun onAuthToken(token: String) {
                        cachedAuthToken = token
                        android.util.Log.d("WebViewAuth", "Auth token cached for fast refresh")
                    }

                    @JavascriptInterface
                    fun onRefreshToken(refreshToken: String, expiresIn: Int) {
                        val prefs = com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context)
                        prefs.refreshToken = refreshToken
                        prefs.tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000L)
                        android.util.Log.d("WebViewAuth", "Auth-code refresh token captured (expires in ${expiresIn}s)")
                    }

                    @JavascriptInterface
                    fun onError(error: String) {
                        if (dataFetched) return
                        dataFetched = true
                        mainHandler.post {
                            webView.destroy()
                        }
                        continuation.resume(Result.failure(Exception(error)))
                    }
                }, "Android")

                // Capture JS console logs for debugging
                webView.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        android.util.Log.d("WebViewJS", "${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                        return true
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        url?.let { currentUrl ->
                            android.util.Log.d("WebViewAuth", "Page loaded: $currentUrl")

                            // Install token capture hook on EVERY page load
                            // Captures: XHR headers, XHR token exchange, fetch() token exchange
                            val jsGlobalHook = """
                                (function() {
                                    if (window.__jpTokenHook) return;
                                    window.__jpTokenHook = true;

                                    // Hook XHR
                                    var origSend = XMLHttpRequest.prototype.send;
                                    var origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
                                    var origOpen = XMLHttpRequest.prototype.open;
                                    XMLHttpRequest.prototype.open = function(m, u) { this._jpUrl = u; return origOpen.apply(this, arguments); };
                                    XMLHttpRequest.prototype.setRequestHeader = function(n, v) {
                                        if (n.toLowerCase() === 'authorization' && v && v.length > 20) {
                                            try { Android.onAuthToken(v.replace('Bearer ', '')); } catch(e) {}
                                        }
                                        return origSetHeader.apply(this, arguments);
                                    };
                                    XMLHttpRequest.prototype.send = function() {
                                        var self = this;
                                        if (self._jpUrl && self._jpUrl.indexOf('/openid-connect/token') >= 0) {
                                            self.addEventListener('load', function() {
                                                if (self.status === 200) {
                                                    try {
                                                        var t = JSON.parse(self.responseText);
                                                        if (t.access_token) { console.log('TOKEN-HOOK: XHR captured token'); Android.onAuthToken(t.access_token); }
                                                        if (t.refresh_token) { Android.onRefreshToken(t.refresh_token, t.expires_in || 600); }
                                                    } catch(e) {}
                                                }
                                            });
                                        }
                                        return origSend.apply(this, arguments);
                                    };

                                    // Hook Keycloak adapter — intercept token when it's set
                                    // Override Keycloak constructor to patch instances
                                    try {
                                        if (window.Keycloak) {
                                            var OrigKeycloak = window.Keycloak;
                                            window.Keycloak = function() {
                                                var kc = new (Function.prototype.bind.apply(OrigKeycloak, [null].concat(Array.prototype.slice.call(arguments))))();
                                                var origInit = kc.init;
                                                kc.init = function() {
                                                    var result = origInit.apply(this, arguments);
                                                    var self = this;
                                                    if (result && result.then) {
                                                        result.then(function(authenticated) {
                                                            if (authenticated && self.token) {
                                                                console.log('TOKEN-HOOK: Keycloak init() authenticated');
                                                                try { Android.onAuthToken(self.token); } catch(e) {}
                                                                if (self.refreshToken) try { Android.onRefreshToken(self.refreshToken, self.tokenParsed ? (self.tokenParsed.exp - Math.floor(Date.now()/1000)) : 600); } catch(e) {}
                                                            }
                                                        });
                                                        result.success = function(fn) { return result.then(function(auth) { if (auth) fn(auth); return auth; }); };
                                                    }
                                                    return result;
                                                };
                                                return kc;
                                            };
                                        }
                                    } catch(kcErr) {}

                                    // Also poll: after Keycloak logs 'authenticated', check for token
                                    var origLog = console.log;
                                    console.log = function() {
                                        origLog.apply(console, arguments);
                                        var msg = arguments[0];
                                        if (typeof msg === 'string' && msg.indexOf('successfully authenticated') >= 0) {
                                            // Keycloak just authenticated — scan for token after a tick
                                            setTimeout(function() {
                                                for (var key in window) {
                                                    try {
                                                        var v = window[key];
                                                        if (v && typeof v === 'object' && v.token && typeof v.token === 'string' && v.token.length > 100 && v.authenticated === true) {
                                                            console.log = origLog; // restore
                                                            origLog('TOKEN-HOOK: Found after auth log, window.' + key);
                                                            try { Android.onAuthToken(v.token); } catch(e) {}
                                                            if (v.refreshToken) try { Android.onRefreshToken(v.refreshToken, 600); } catch(e) {}
                                                            return;
                                                        }
                                                    } catch(e) {}
                                                }
                                            }, 100);
                                        }
                                    };

                                    // Hook fetch() — Keycloak JS may use fetch for token exchange
                                    var origFetch = window.fetch;
                                    window.fetch = function(url, opts) {
                                        var urlStr = typeof url === 'string' ? url : (url && url.url ? url.url : '');
                                        if (urlStr.indexOf('/openid-connect/token') >= 0) {
                                            return origFetch.apply(this, arguments).then(function(response) {
                                                var clone = response.clone();
                                                clone.json().then(function(t) {
                                                    if (t.access_token) { console.log('TOKEN-HOOK: fetch() captured token'); Android.onAuthToken(t.access_token); }
                                                    if (t.refresh_token) { Android.onRefreshToken(t.refresh_token, t.expires_in || 600); }
                                                }).catch(function(){});
                                                return response;
                                            });
                                        }
                                        return origFetch.apply(this, arguments);
                                    };
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(jsGlobalHook, null)

                            when {
                                // On Keycloak login page - fill credentials
                                currentUrl.contains(LOGIN_URL_PATTERN) && !loginAttempted -> {
                                    loginAttempted = true
                                    android.util.Log.d("WebViewAuth", "Filling login form")

                                    // Properly escape credentials for JavaScript
                                    val escapedRoll = rollNumber
                                        .replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")
                                    val escapedPass = password
                                        .replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")

                                    val jsLogin = """
                                        (function() {
                                            var usernameField = document.getElementById('username');
                                            var passwordField = document.getElementById('password');
                                            var loginButton = document.getElementById('kc-login');

                                            if (usernameField && passwordField && loginButton) {
                                                usernameField.value = '$escapedRoll';
                                                passwordField.value = '$escapedPass';

                                                // Trigger input events to ensure Angular/React picks up the values
                                                usernameField.dispatchEvent(new Event('input', { bubbles: true }));
                                                passwordField.dispatchEvent(new Event('input', { bubbles: true }));

                                                setTimeout(function() {
                                                    loginButton.click();
                                                }, 300);
                                            } else {
                                                Android.onError('Login form elements not found');
                                            }
                                        })();
                                    """.trimIndent()

                                    view?.evaluateJavascript(jsLogin, null)
                                }

                                // Detect login failure (still on login page after attempt)
                                currentUrl.contains(LOGIN_URL_PATTERN) && loginAttempted && !dataFetched -> {
                                    // Check for error message on page
                                    val jsCheckError = """
                                        (function() {
                                            var errorElement = document.querySelector('.alert-error, .kc-feedback-text, #input-error');
                                            if (errorElement && errorElement.textContent.trim().length > 0) {
                                                Android.onError('Invalid credentials: ' + errorElement.textContent.trim());
                                            } else if ($fetchStarted) {
                                                // We were redirected back to login after fetching - session not valid
                                                Android.onError('Session expired during login. Please try again.');
                                            }
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(jsCheckError, null)
                                }

                                // Attendance API URL loaded - extract JSON
                                currentUrl.contains("/sis/attendance/") && !dataFetched -> {
                                    android.util.Log.d("WebViewAuth", "Attendance page loaded, extracting JSON")

                                    val jsExtract = """
                                        (function() {
                                            try {
                                                var bodyText = document.body.innerText || document.body.textContent;
                                                console.log('Extracted body length: ' + bodyText.length);
                                                console.log('Body preview: ' + bodyText.substring(0, 200));
                                                if (bodyText && bodyText.trim().length > 0) {
                                                    Android.onDataReceived(bodyText.trim());
                                                } else {
                                                    Android.onError('Empty response from attendance API');
                                                }
                                            } catch(e) {
                                                Android.onError('Failed to extract data: ' + e.message);
                                            }
                                        })();
                                    """.trimIndent()

                                    view?.evaluateJavascript(jsExtract, null)
                                }

                                // On any SIS page load — install XHR hook to capture tokens
                                (currentUrl.contains("laudea.psgitech.ac.in/sis") &&
                                !currentUrl.contains(LOGIN_URL_PATTERN) &&
                                !currentUrl.contains("/attendance/") &&
                                !dataFetched) -> {
                                    if (!fetchStarted) {
                                        fetchStarted = true
                                        android.util.Log.d("WebViewAuth", "On SIS page, injecting token poller then fetching attendance")

                                        // Inject a JS poller that checks every 200ms for Angular's Auth/keycloak token
                                        // The keycloak variable is in a closure (not on window), but Angular's
                                        // injector can access it after bootstrap completes
                                        val jsPoller = """
                                            (function() {
                                                if (window.__jpPoller) return;
                                                window.__jpPoller = true;
                                                var attempts = 0;
                                                var poll = setInterval(function() {
                                                    attempts++;
                                                    try {
                                                        var injector = angular.element(document.body).injector();
                                                        if (injector) {
                                                            // The Keycloak instance is used as a constant/value in the app
                                                            // Try getting it from the rootScope which login.js populates
                                                            var rootScope = injector.get('${'$'}rootScope');
                                                            if (rootScope && rootScope.Auth && rootScope.Auth.token) {
                                                                console.log('TOKEN-POLLER: Found via ${'$'}rootScope.Auth');
                                                                Android.onAuthToken(rootScope.Auth.token);
                                                                if (rootScope.Auth.refreshToken) Android.onRefreshToken(rootScope.Auth.refreshToken, 600);
                                                                clearInterval(poll);
                                                                return;
                                                            }
                                                            // Try http default headers — Angular sets Authorization header globally
                                                            try {
                                                                var http = injector.get('${'$'}http');
                                                                if (http && http.defaults && http.defaults.headers && http.defaults.headers.common) {
                                                                    var authHeader = http.defaults.headers.common['Authorization'] || http.defaults.headers.common['authorization'];
                                                                    if (authHeader && authHeader.length > 20) {
                                                                        console.log('TOKEN-POLLER: Found via ${'$'}http.defaults.headers');
                                                                        Android.onAuthToken(authHeader.replace('Bearer ', ''));
                                                                        clearInterval(poll);
                                                                        return;
                                                                    }
                                                                }
                                                            } catch(e2) {}
                                                        }
                                                    } catch(e) {}
                                                    if (attempts >= 50) { clearInterval(poll); console.log('TOKEN-POLLER: gave up after 50 attempts'); }
                                                }, 200);
                                            })();
                                        """.trimIndent()
                                        view?.evaluateJavascript(jsPoller, null)

                                        // Proceed with XHR intercept after 3s regardless
                                        mainHandler.postDelayed({
                                            interceptXhrAndNavigate(view, rollNumber)
                                        }, 3000)
                                    }
                                }
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        return false // Let WebView handle all URLs
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        return null
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        android.util.Log.d("WebViewAuth", "onPageStarted: $url")
                        // Inject token-capture hooks as early as possible.
                        // Hooks both XMLHttpRequest AND fetch (modern Keycloak-js uses fetch).
                        val jsHookCode = """
                            (function() {
                                console.log('EARLY-HOOK: installing on ' + window.location.href);
                                if (window.__jpEarlyHook) { console.log('EARLY-HOOK: already installed'); return; }
                                window.__jpEarlyHook = true;
                                var origOpen = XMLHttpRequest.prototype.open;
                                var origSend = XMLHttpRequest.prototype.send;
                                var origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
                                XMLHttpRequest.prototype.open = function(m, u) { this._jpUrl = u; return origOpen.apply(this, arguments); };
                                XMLHttpRequest.prototype.setRequestHeader = function(n, v) {
                                    if (n.toLowerCase() === 'authorization' && v && v.length > 20) {
                                        try { Android.onAuthToken(v.replace('Bearer ', '')); } catch(e) {}
                                    }
                                    return origSetHeader.apply(this, arguments);
                                };
                                XMLHttpRequest.prototype.send = function() {
                                    var self = this;
                                    if (self._jpUrl) console.log('EARLY-HOOK-XHR: ' + String(self._jpUrl).substring(0, 100));
                                    if (self._jpUrl && String(self._jpUrl).indexOf('/openid-connect/token') >= 0) {
                                        console.log('EARLY-HOOK: intercepting token XHR');
                                        self.addEventListener('load', function() {
                                            console.log('EARLY-HOOK: token XHR status=' + self.status);
                                            if (self.status === 200) {
                                                try {
                                                    var t = JSON.parse(self.responseText);
                                                    if (t.access_token) { console.log('EARLY-HOOK: XHR token captured'); Android.onAuthToken(t.access_token); }
                                                    if (t.refresh_token) { Android.onRefreshToken(t.refresh_token, t.expires_in || 600); }
                                                } catch(e) { console.log('EARLY-HOOK: parse error ' + e.message); }
                                            }
                                        });
                                    }
                                    return origSend.apply(this, arguments);
                                };
                                // Hook fetch() — modern Keycloak-js uses fetch for token exchange
                                if (window.fetch) {
                                    var origFetch = window.fetch;
                                    window.fetch = function(input, init) {
                                        var urlStr = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                                        if (urlStr) console.log('EARLY-HOOK-FETCH: ' + String(urlStr).substring(0, 100));
                                        var promise = origFetch.apply(this, arguments);
                                        if (urlStr.indexOf('/openid-connect/token') >= 0) {
                                            console.log('EARLY-HOOK: intercepting token fetch');
                                            promise.then(function(response) {
                                                try {
                                                    var clone = response.clone();
                                                    clone.json().then(function(t) {
                                                        if (t && t.access_token) { console.log('EARLY-HOOK: fetch token captured'); Android.onAuthToken(t.access_token); }
                                                        if (t && t.refresh_token) { Android.onRefreshToken(t.refresh_token, t.expires_in || 600); }
                                                    }).catch(function() {});
                                                } catch(e) {}
                                            }).catch(function() {});
                                        }
                                        return promise;
                                    };
                                }
                            })();
                        """.trimIndent()
                        // evaluateJavascript works in onPageStarted on modern WebView
                        view?.evaluateJavascript(jsHookCode, null)
                    }
                }

                // Start by loading the SIS page
                webView.loadUrl(SIS_BASE_URL)

                // Timeout after 60 seconds
                mainHandler.postDelayed({
                    if (!dataFetched) {
                        dataFetched = true
                        webView.destroy()
                        continuation.resume(Result.failure(Exception("Timeout: Could not fetch attendance data. Please check your internet connection and try again.")))
                    }
                }, 60000)

            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    private fun interceptXhrAndNavigate(webView: WebView?, rollNumber: String) {
        android.util.Log.d("WebViewAuth", "Intercepting XHR and navigating to attendance for: $rollNumber")

        val jsIntercept = """
            (function() {
                console.log('Setting up XHR intercept...');
                var captured = false;
                var authHeader = null;

                var origOpen = XMLHttpRequest.prototype.open;
                var origSend = XMLHttpRequest.prototype.send;
                var origSetHeader = XMLHttpRequest.prototype.setRequestHeader;

                XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
                    if (name.toLowerCase() === 'authorization' && !authHeader) {
                        authHeader = value;
                        console.log('Captured auth header from XHR');
                        // Save token for fast refresh later
                        try {
                            var tokenValue = value.replace('Bearer ', '');
                            Android.onAuthToken(tokenValue);
                        } catch(e) {}
                        // Try to capture refresh token from Keycloak JS adapter
                        try {
                            var kc = null;
                            // Check common Keycloak variable names
                            var kcNames = ['keycloak', 'Keycloak', 'kc', '_keycloak', 'auth', 'keycloakAuth'];
                            for (var n = 0; n < kcNames.length; n++) {
                                try {
                                    if (window[kcNames[n]] && window[kcNames[n]].refreshToken) {
                                        kc = window[kcNames[n]];
                                        console.log('Found Keycloak at window.' + kcNames[n]);
                                        break;
                                    }
                                } catch(e2) {}
                            }
                            // Search all window properties
                            if (!kc) {
                                var keys = Object.keys(window);
                                for (var i = 0; i < keys.length; i++) {
                                    try {
                                        var obj = window[keys[i]];
                                        if (obj && typeof obj === 'object' && obj.authenticated === true && obj.refreshToken && obj.tokenParsed) {
                                            kc = obj;
                                            console.log('Found Keycloak at window.' + keys[i]);
                                            break;
                                        }
                                    } catch(e2) {}
                                }
                            }
                            // Try Angular injector
                            if (!kc) {
                                try {
                                    var injector = angular.element(document.body).injector();
                                    if (injector) {
                                        var authService = injector.get('Auth') || injector.get('auth') || injector.get('keycloak');
                                        if (authService && authService.refreshToken) {
                                            kc = authService;
                                            console.log('Found Keycloak via Angular injector');
                                        }
                                    }
                                } catch(e2) {
                                    console.log('Angular injector search failed:', e2.message);
                                }
                            }
                            if (kc && kc.refreshToken) {
                                var expiry = 1800;
                                if (kc.refreshTokenParsed && kc.refreshTokenParsed.exp) {
                                    expiry = Math.floor(kc.refreshTokenParsed.exp - Date.now()/1000);
                                }
                                console.log('Captured refresh token, expires in ' + expiry + 's');
                                Android.onRefreshToken(kc.refreshToken, expiry);
                            } else {
                                console.log('Keycloak refresh token not found on window');
                            }
                        } catch(e) {
                            console.log('Could not capture refresh token:', e.message);
                        }
                    }
                    return origSetHeader.apply(this, arguments);
                };

                XMLHttpRequest.prototype.open = function(method, url) {
                    this._url = url;
                    return origOpen.apply(this, arguments);
                };

                XMLHttpRequest.prototype.send = function() {
                    var self = this;
                    // Intercept Keycloak token exchange to capture the refresh token
                    if (self._url && self._url.indexOf('/openid-connect/token') >= 0) {
                        console.log('Intercepted Keycloak token exchange XHR');
                        self.addEventListener('load', function() {
                            if (self.status === 200) {
                                try {
                                    var tokenData = JSON.parse(self.responseText);
                                    if (tokenData.refresh_token) {
                                        console.log('Captured refresh token from auth code flow');
                                        Android.onRefreshToken(tokenData.refresh_token, tokenData.expires_in || 600);
                                    }
                                } catch(e) {
                                    console.log('Failed to parse token response:', e.message);
                                }
                            }
                        });
                    }
                    if (self._url && self._url.indexOf('/attendance/') >= 0) {
                        console.log('Intercepted attendance XHR:', self._url);
                        self.addEventListener('load', function() {
                            if (captured) return;
                            if (self.status === 200 || self.status === 304) {
                                captured = true;
                                console.log('Attendance XHR response, status:', self.status, 'length:', self.responseText.length);
                                try {
                                    Android.onDataReceived(self.responseText);
                                } catch(e) {
                                    console.log('Error passing data to Android:', e.message);
                                }
                            } else {
                                console.log('Attendance XHR failed with status:', self.status);
                                Android.onError('Attendance request failed with status ' + self.status);
                            }
                        });
                    }
                    return origSend.apply(this, arguments);
                };

                // Navigate Angular to the attendance page
                console.log('Navigating to #!/attendanceStudentView ...');
                window.location.hash = '#!/attendanceStudentView';

                // Fallback: if XHR intercept didn't fire in 3 seconds, try direct fetch
                setTimeout(function() {
                    if (captured) return;
                    console.log('XHR intercept timeout, trying direct fetch with captured auth...');

                    var headers = {
                        'Accept': 'application/json, text/plain, */*'
                    };
                    if (authHeader) {
                        headers['Authorization'] = authHeader;
                    }

                    fetch('https://laudea.psgitech.ac.in/sis/attendance/$rollNumber', {
                        method: 'GET',
                        credentials: 'include',
                        headers: headers
                    })
                    .then(function(r) {
                        console.log('Fallback fetch status:', r.status);
                        if (!r.ok) throw new Error('HTTP ' + r.status);
                        return r.text();
                    })
                    .then(function(data) {
                        if (captured) return;
                        captured = true;
                        console.log('Fallback fetch data received, length:', data.length);
                        Android.onDataReceived(data);
                    })
                    .catch(function(err) {
                        console.log('Fallback fetch error:', err.message);
                        // Don't destroy WebView immediately — wait for token capture hooks
                        setTimeout(function() {
                            Android.onError('Could not fetch attendance: ' + err.message);
                        }, 5000);
                    });
                }, 3000);
            })();
        """.trimIndent()

        webView?.evaluateJavascript(jsIntercept, null)
    }

    private fun fetchAttendanceViaJsFetch(webView: WebView?, rollNumber: String) {
        // For refresh: same XHR intercept + Angular navigation approach
        interceptXhrAndNavigate(webView, rollNumber)
    }

    private fun fetchAttendanceData(webView: WebView?, rollNumber: String) {
        webView?.loadUrl("https://laudea.psgitech.ac.in/sis/attendance/$rollNumber")
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchAttendanceOnly(rollNumber: String): Result<AttendanceData> = suspendCoroutine { continuation ->
        mainHandler.post {
            try {
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)

                var dataFetched = false

                webView.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onDataReceived(jsonData: String) {
                        if (dataFetched) return
                        dataFetched = true

                        try {
                            val response = gson.fromJson(jsonData, AttendanceResponse::class.java)
                            val attendanceData = AttendanceData.fromResponse(response)
                            // Persist cookies so Keycloak session survives process death
                            cookieManager.flush()
                            mainHandler.post { webView.destroy() }
                            continuation.resume(Result.success(attendanceData))
                        } catch (e: Exception) {
                            mainHandler.post { webView.destroy() }
                            continuation.resume(Result.failure(Exception("Failed to parse: ${e.message}")))
                        }
                    }

                    @JavascriptInterface
                    fun onAuthToken(token: String) {
                        cachedAuthToken = token
                        android.util.Log.d("WebViewAuth", "Auth token cached from refresh")
                    }

                    @JavascriptInterface
                    fun onRefreshToken(refreshToken: String, expiresIn: Int) {
                        val prefs = com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context)
                        prefs.refreshToken = refreshToken
                        prefs.tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000L)
                        android.util.Log.d("WebViewAuth", "Auth-code refresh token captured from refresh (expires in ${expiresIn}s)")
                    }

                    @JavascriptInterface
                    fun onError(error: String) {
                        if (dataFetched) return
                        dataFetched = true
                        mainHandler.post { webView.destroy() }
                        continuation.resume(Result.failure(Exception(error)))
                    }
                }, "Android")

                // Capture JS console logs for debugging
                webView.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        android.util.Log.d("WebViewJS", "${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                        return true
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        // If redirected to login, session expired
                        if (url?.contains(LOGIN_URL_PATTERN) == true) {
                            if (!dataFetched) {
                                dataFetched = true
                                mainHandler.post { webView.destroy() }
                                continuation.resume(Result.failure(Exception("Session expired, please login again")))
                            }
                            return
                        }

                        // Attendance API URL loaded - extract JSON
                        if (url?.contains("/sis/attendance/") == true && !dataFetched) {
                            val jsExtract = """
                                (function() {
                                    try {
                                        var bodyText = document.body.innerText || document.body.textContent;
                                        Android.onDataReceived(bodyText);
                                    } catch(e) {
                                        Android.onError('Failed to extract data: ' + e.message);
                                    }
                                })();
                            """.trimIndent()

                            view?.evaluateJavascript(jsExtract, null)
                            return
                        }

                        // On SIS page, intercept XHR and navigate to attendance
                        if (url?.contains("laudea.psgitech.ac.in/sis") == true &&
                            !url.contains("/attendance/") && !dataFetched) {
                            android.util.Log.d("WebViewAuth", "On SIS page, intercepting XHR for attendance")
                            mainHandler.postDelayed({
                                interceptXhrAndNavigate(view, rollNumber)
                            }, 1000)
                        }
                    }
                }

                webView.loadUrl(SIS_BASE_URL)

                mainHandler.postDelayed({
                    if (!dataFetched) {
                        dataFetched = true
                        webView.destroy()
                        continuation.resume(Result.failure(Exception("Timeout")))
                    }
                }, 20000)

            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Fast refresh using cached auth token - no WebView needed.
     * Returns null if token is missing or expired (caller should fall back to WebView).
     */
    suspend fun fetchAttendanceDirect(rollNumber: String): Result<AttendanceData>? {
        val token = cachedAuthToken ?: return null
        android.util.Log.d("WebViewAuth", "Fast refresh with cached token")
        val response = authenticatedGet("${ATTENDANCE_API_BASE}$rollNumber", token) ?: return null
        return response.use { resp ->
            val responseCode = resp.code
            android.util.Log.d("WebViewAuth", "Fast refresh response: $responseCode")
            when {
                responseCode == 200 -> {
                    val jsonData = resp.body?.string() ?: ""
                    val r = gson.fromJson(jsonData, AttendanceResponse::class.java)
                    Result.success(AttendanceData.fromResponse(r))
                }
                responseCode == 401 -> { cachedAuthToken = null; null }
                responseCode in 500..599 -> {
                    val errBody = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                    if (errBody.contains("401") || errBody.contains("unauthorized", ignoreCase = true)) {
                        android.util.Log.d("WebViewAuth", "Fast refresh 500 is proxied 401 — token expired")
                        cachedAuthToken = null
                        null
                    } else Result.failure(ServerDownException(responseCode))
                }
                else -> Result.failure(Exception("HTTP $responseCode"))
            }
        }
    }

    /** Thrown when the LAUDEA server returns a 5xx error. */
    class ServerDownException(val statusCode: Int) : Exception("Server returned HTTP $statusCode")

    /**
     * Fetch CA marks using cached auth token (fast direct HTTP).
     */
    suspend fun fetchCAMarksDirect(rollNumber: String): Result<List<CourseMarks>>? {
        val token = cachedAuthToken ?: return null
        android.util.Log.d("WebViewAuth", "Fetching CA marks direct for: $rollNumber")
        val response = authenticatedGet("${CA_MARKS_API_URL}$rollNumber", token)
            ?: return Result.failure(Exception("Network error fetching CA marks"))
        return response.use { resp ->
            val responseCode = resp.code
            android.util.Log.d("WebViewAuth", "CA marks direct response: $responseCode")
            when {
                responseCode == 200 -> {
                    val jsonData = resp.body?.string() ?: ""
                    val listType = object : TypeToken<List<CourseMarks>>() {}.type
                    val list: List<CourseMarks> = gson.fromJson(jsonData, listType)
                    Result.success(list)
                }
                responseCode == 401 -> { cachedAuthToken = null; null }
                responseCode in 500..599 -> {
                    val errBody = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                    if (errBody.contains("401") || errBody.contains("unauthorized", ignoreCase = true)) {
                        cachedAuthToken = null
                        null
                    } else Result.failure(ServerDownException(responseCode))
                }
                else -> Result.failure(Exception("HTTP $responseCode"))
            }
        }
    }

    /**
     * Fetch absent days details using cached auth token.
     */
    suspend fun fetchAbsentDays(rollNumber: String): Result<List<AbsentDay>>? {
        val token = cachedAuthToken ?: return null
        android.util.Log.d("WebViewAuth", "Fetching absent days for: $rollNumber")
        val response = authenticatedGet("https://laudea.psgitech.ac.in/sis/attendance/absent/$rollNumber", token)
            ?: return Result.failure(Exception("Network error fetching absent days"))
        return response.use { resp ->
            val responseCode = resp.code
            android.util.Log.d("WebViewAuth", "Absent days response: $responseCode")
            when {
                responseCode == 200 -> {
                    val jsonData = resp.body?.string() ?: ""
                    val listType = object : TypeToken<List<AbsentDay>>() {}.type
                    val absentDays: List<AbsentDay> = gson.fromJson(jsonData, listType)
                    Result.success(absentDays)
                }
                responseCode == 401 -> { cachedAuthToken = null; null }
                responseCode in 500..599 -> {
                    val errBody = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                    if (errBody.contains("401") || errBody.contains("unauthorized", ignoreCase = true)) {
                        cachedAuthToken = null
                        null
                    } else Result.failure(ServerDownException(responseCode))
                }
                else -> Result.failure(Exception("HTTP $responseCode"))
            }
        }
    }

    /**
     * Fetch present days using cached auth token (fast direct HTTP).
     * Response format is identical to absent days.
     */
    suspend fun fetchPresentDays(rollNumber: String): Result<List<AbsentDay>>? {
        val token = cachedAuthToken ?: return null
        android.util.Log.d("WebViewAuth", "Fetching present days for: $rollNumber")
        val response = authenticatedGet("https://laudea.psgitech.ac.in/sis/attendance/present/$rollNumber", token)
            ?: return Result.failure(Exception("Network error fetching present days"))
        return response.use { resp ->
            val responseCode = resp.code
            android.util.Log.d("WebViewAuth", "Present days response: $responseCode")
            when {
                responseCode == 200 -> {
                    val jsonData = resp.body?.string() ?: ""
                    val listType = object : TypeToken<List<AbsentDay>>() {}.type
                    val presentDays: List<AbsentDay> = gson.fromJson(jsonData, listType)
                    Result.success(presentDays)
                }
                responseCode == 401 -> { cachedAuthToken = null; null }
                responseCode in 500..599 -> {
                    val errBody = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                    if (errBody.contains("401") || errBody.contains("unauthorized", ignoreCase = true)) {
                        cachedAuthToken = null
                        null
                    } else Result.failure(ServerDownException(responseCode))
                }
                else -> Result.failure(Exception("HTTP $responseCode"))
            }
        }
    }

    /**
     * Fetch exemptions using cached auth token (fast direct HTTP).
     */
    suspend fun fetchExemptionsDirect(rollNumber: String): Result<List<com.example.attendancewidgetlaudea.data.model.Exemption>>? {
        val token = cachedAuthToken ?: return null
        android.util.Log.d("WebViewAuth", "Fetching exemptions for: $rollNumber")
        val response = authenticatedGet("https://laudea.psgitech.ac.in/sis/remote/exemptions/$rollNumber", token)
            ?: return Result.failure(Exception("Network error fetching exemptions"))
        return response.use { resp ->
            val responseCode = resp.code
            android.util.Log.d("WebViewAuth", "Exemptions response: $responseCode")
            when {
                responseCode == 200 -> {
                    val jsonData = resp.body?.string() ?: ""
                    val listType = object : TypeToken<List<com.example.attendancewidgetlaudea.data.model.Exemption>>() {}.type
                    val exemptions: List<com.example.attendancewidgetlaudea.data.model.Exemption> = gson.fromJson(jsonData, listType)
                    Result.success(exemptions)
                }
                responseCode == 401 -> { cachedAuthToken = null; null }
                responseCode in 500..599 -> {
                    val errBody = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                    if (errBody.contains("401") || errBody.contains("unauthorized", ignoreCase = true)) {
                        cachedAuthToken = null
                        null
                    } else Result.failure(ServerDownException(responseCode))
                }
                else -> Result.failure(Exception("HTTP $responseCode"))
            }
        }
    }

    /**
     * Fetch timetable using cached auth token (fast direct HTTP).
     */
    suspend fun fetchTimetableDirect(configId: String, rollNumber: String): Result<TimetableResponse>? {
        val token = cachedAuthToken ?: return null
        android.util.Log.d("WebViewAuth", "Fetching timetable for: $rollNumber with config: $configId")
        val response = authenticatedGet("${TIMETABLE_API_BASE}$configId/$rollNumber", token)
            ?: return Result.failure(Exception("Network error fetching timetable"))
        return response.use { resp ->
            val responseCode = resp.code
            android.util.Log.d("WebViewAuth", "Timetable response: $responseCode")
            when {
                responseCode == 200 -> {
                    val jsonData = resp.body?.string() ?: ""
                    Result.success(gson.fromJson(jsonData, TimetableResponse::class.java))
                }
                responseCode == 401 -> { cachedAuthToken = null; null }
                responseCode in 500..599 -> {
                    val errBody = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                    if (errBody.contains("401") || errBody.contains("unauthorized", ignoreCase = true)) {
                        cachedAuthToken = null
                        null
                    } else Result.failure(ServerDownException(responseCode))
                }
                else -> Result.failure(Exception("HTTP $responseCode"))
            }
        }
    }

    /**
     * Fetch exam results/grades using cached auth token.
     * API: GET /sis/remote/all/results?rollNo={rollNumber}
     */
    suspend fun fetchResultDirect(rollNumber: String): kotlin.Result<String>? {
        val token = cachedAuthToken ?: return null
        val endpoint = "https://laudea.psgitech.ac.in/sis/remote/all/results?rollNo=${java.net.URLEncoder.encode(rollNumber, "UTF-8")}"
        android.util.Log.d("WebViewAuth", "Fetching results for: $rollNumber")
        val response = authenticatedGet(endpoint, token)
            ?: return kotlin.Result.failure(Exception("Network error fetching results"))
        return response.use { resp ->
            val responseCode = resp.code
            android.util.Log.d("WebViewAuth", "Results response: $responseCode")
            when {
                responseCode == 200 -> {
                    val data = resp.body?.string() ?: ""
                    android.util.Log.d("WebViewAuth", "Results data length: ${data.length}")
                    kotlin.Result.success(data)
                }
                responseCode == 401 -> { cachedAuthToken = null; null }
                responseCode in 500..599 -> kotlin.Result.failure(ServerDownException(responseCode))
                else -> kotlin.Result.failure(Exception("HTTP $responseCode"))
            }
        }
    }

    /**
     * Fetch course registration data to identify registered vs honours courses.
     * API: GET /sis/remote/get/registrations/{rollNumber}
     */
    suspend fun fetchRegistrationsDirect(rollNumber: String): kotlin.Result<String>? {
        val token = cachedAuthToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val endpoint = "https://laudea.psgitech.ac.in/sis/remote/get/registrations/$rollNumber"
                val url = java.net.URL(endpoint)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val data = connection.inputStream.bufferedReader().use { it.readText() }
                    android.util.Log.d("WebViewAuth", "Registration data: ${data.take(3000)}")
                    kotlin.Result.success(data)
                } else if (responseCode == 401) {
                    cachedAuthToken = null
                    null
                } else if (responseCode in 500..599) {
                    kotlin.Result.failure(ServerDownException(responseCode))
                } else {
                    kotlin.Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Registration fetch error: ${e.message}")
                kotlin.Result.failure(e)
            }
        }
    }

    /**
     * Fetch student profile to get nodeId (timetable config) for this student.
     * API: GET /sis/students/{rollNumber}
     */
    suspend fun fetchStudentNodeId(rollNumber: String): String? {
        val token = cachedAuthToken ?: return null
        android.util.Log.d("WebViewAuth", "Fetching student nodeId for: $rollNumber")
        val response = authenticatedGet("https://laudea.psgitech.ac.in/sis/students/$rollNumber", token) ?: return null
        return response.use { resp ->
            if (resp.code == 200) {
                val json = resp.body?.string() ?: return@use null
                val map = gson.fromJson(json, Map::class.java)
                val nodeId = map["nodeId"]?.toString()
                android.util.Log.d("WebViewAuth", "Student nodeId: $nodeId")
                nodeId
            } else {
                android.util.Log.e("WebViewAuth", "Student profile fetch failed: HTTP ${resp.code}")
                if (resp.code == 401) cachedAuthToken = null
                null
            }
        }
    }

    /**
     * Fetch full student profile data for biodata display.
     * API: GET /sis/students/{rollNumber}
     */
    suspend fun fetchStudentProfile(rollNumber: String): com.example.attendancewidgetlaudea.data.model.StudentBiodata? {
        val token = cachedAuthToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://laudea.psgitech.ac.in/sis/students/$rollNumber")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                if (connection.responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    android.util.Log.d("WebViewAuth", "Student profile JSON keys: ${org.json.JSONObject(json).keys().asSequence().toList()}")
                    com.example.attendancewidgetlaudea.data.model.StudentBiodata.fromJson(org.json.JSONObject(json))
                } else {
                    if (connection.responseCode == 401) cachedAuthToken = null
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Student profile fetch error: ${e.message}")
                null
            }
        }
    }

    fun clearSession() {
        try {
            // Clear all cookies synchronously
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.removeSessionCookies(null)
            cookieManager.flush()

            // Clear WebView storage
            android.webkit.WebStorage.getInstance().deleteAllData()

            android.util.Log.d("WebViewAuth", "Session cleared")
        } catch (e: Exception) {
            android.util.Log.e("WebViewAuth", "Error clearing session: ${e.message}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchCAMarks(rollNumber: String): Result<List<CourseMarks>> = suspendCoroutine { continuation ->
        mainHandler.post {
            try {
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }

                CookieManager.getInstance().setAcceptCookie(true)

                var dataFetched = false

                webView.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onCAMarksReceived(jsonData: String) {
                        if (dataFetched) return
                        dataFetched = true

                        try {
                            val listType = object : TypeToken<List<CourseMarks>>() {}.type
                            val courseMarksList: List<CourseMarks> = gson.fromJson(jsonData, listType)
                            mainHandler.post { webView.destroy() }
                            continuation.resume(Result.success(courseMarksList))
                        } catch (e: Exception) {
                            mainHandler.post { webView.destroy() }
                            continuation.resume(Result.failure(Exception("Failed to parse CA marks: ${e.message}")))
                        }
                    }

                    @JavascriptInterface
                    fun onError(error: String) {
                        if (dataFetched) return
                        dataFetched = true
                        mainHandler.post { webView.destroy() }
                        continuation.resume(Result.failure(Exception(error)))
                    }
                }, "Android")

                webView.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        android.util.Log.d("WebViewJS", "${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                        return true
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        // Install token hook on every page (including Keycloak login)
                        val jsHook = """
                            (function() {
                                if (window.__jpTokenHook) return;
                                window.__jpTokenHook = true;
                                var origSend = XMLHttpRequest.prototype.send;
                                var origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
                                var origOpen = XMLHttpRequest.prototype.open;
                                XMLHttpRequest.prototype.open = function(m, u) { this._jpUrl = u; return origOpen.apply(this, arguments); };
                                XMLHttpRequest.prototype.setRequestHeader = function(n, v) {
                                    if (n.toLowerCase() === 'authorization' && v && v.length > 20) {
                                        try { Android.onAuthToken(v.replace('Bearer ', '')); } catch(e) {}
                                    }
                                    return origSetHeader.apply(this, arguments);
                                };
                                XMLHttpRequest.prototype.send = function() {
                                    var self = this;
                                    if (self._jpUrl && self._jpUrl.indexOf('/openid-connect/token') >= 0) {
                                        self.addEventListener('load', function() {
                                            if (self.status === 200) {
                                                try {
                                                    var t = JSON.parse(self.responseText);
                                                    if (t.access_token) { console.log('CA-TOKEN-HOOK: Captured token'); Android.onAuthToken(t.access_token); }
                                                    if (t.refresh_token) { Android.onRefreshToken(t.refresh_token, t.expires_in || 600); }
                                                } catch(e) {}
                                            }
                                        });
                                    }
                                    return origSend.apply(this, arguments);
                                };
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(jsHook, null)

                        // If redirected to Keycloak login, fill credentials automatically
                        if (url?.contains(LOGIN_URL_PATTERN) == true) {
                            android.util.Log.d("WebViewAuth", "CA marks: redirected to login, filling credentials")
                            val escapedRoll = rollNumber.replace("\\", "\\\\").replace("'", "\\'")
                            val escapedPass = (com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context).password ?: "").replace("\\", "\\\\").replace("'", "\\'")
                            val jsLogin = """
                                (function() {
                                    var u = document.getElementById('username');
                                    var p = document.getElementById('password');
                                    if (u && p) {
                                        u.value = '$escapedRoll';
                                        p.value = '$escapedPass';
                                        var form = document.getElementById('kc-form-login');
                                        if (form) form.submit();
                                    }
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(jsLogin, null)
                            return
                        }

                        // On SIS page, fetch CA marks via JS
                        if (url?.contains("laudea.psgitech.ac.in/sis") == true &&
                            !url.contains("/ca/marks/") && !dataFetched) {
                            android.util.Log.d("WebViewAuth", "On SIS page, fetching CA marks via JS")
                            mainHandler.postDelayed({
                                fetchCAMarksViaJsFetch(view, rollNumber)
                            }, 500)
                        }

                        // CA marks API URL loaded - extract JSON
                        if (url?.contains("/sis/ca/marks/") == true && !dataFetched) {
                            val jsExtract = """
                                (function() {
                                    try {
                                        var bodyText = document.body.innerText || document.body.textContent;
                                        Android.onCAMarksReceived(bodyText);
                                    } catch(e) {
                                        Android.onError('Failed to extract data: ' + e.message);
                                    }
                                })();
                            """.trimIndent()

                            view?.evaluateJavascript(jsExtract, null)
                        }
                    }
                }

                webView.loadUrl(SIS_BASE_URL)

                mainHandler.postDelayed({
                    if (!dataFetched) {
                        dataFetched = true
                        webView.destroy()
                        continuation.resume(Result.failure(Exception("Timeout")))
                    }
                }, 45000)

            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    private fun fetchCAMarksViaJsFetch(webView: WebView?, rollNumber: String) {
        android.util.Log.d("WebViewAuth", "Starting CA marks fetch for roll: $rollNumber")

        val jsFetch = """
            (function() {
                console.log('Starting CA marks fetch...');
                var token = null;

                // Search for token (same logic as attendance)
                try {
                    var windowKeys = Object.keys(window);
                    for (var i = 0; i < windowKeys.length; i++) {
                        var key = windowKeys[i];
                        if (key.toLowerCase().indexOf('keycloak') >= 0 || key.toLowerCase().indexOf('auth') >= 0) {
                            try {
                                var obj = window[key];
                                if (obj && obj.token) {
                                    token = obj.token;
                                    console.log('Found token in window.' + key);
                                    break;
                                }
                            } catch(e) {}
                        }
                    }
                } catch(e) {}

                if (!token) {
                    try {
                        var allObjs = Object.keys(window);
                        for (var i = 0; i < allObjs.length; i++) {
                            try {
                                var obj = window[allObjs[i]];
                                if (obj && typeof obj === 'object' && obj.authenticated === true && obj.token) {
                                    token = obj.token;
                                    console.log('Found keycloak via authenticated flag');
                                    break;
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                if (!token) {
                    try {
                        for (var i = 0; i < localStorage.length; i++) {
                            var key = localStorage.key(i);
                            var val = localStorage.getItem(key);
                            if (val && val.startsWith('eyJ') && val.length > 100) {
                                token = val;
                                console.log('Found JWT in localStorage:', key);
                                break;
                            }
                        }
                    } catch(e) {}
                }

                var headers = {
                    'Accept': 'application/json, text/plain, */*',
                    'X-Requested-With': 'XMLHttpRequest'
                };

                if (token) {
                    headers['Authorization'] = 'Bearer ' + token;
                }

                fetch('${CA_MARKS_API_URL}$rollNumber', {
                    method: 'GET',
                    credentials: 'include',
                    headers: headers
                })
                .then(function(response) {
                    console.log('CA marks fetch response status:', response.status);
                    if (!response.ok) {
                        if (response.status === 401) {
                            throw new Error('Authentication failed. Please login again.');
                        }
                        throw new Error('HTTP ' + response.status);
                    }
                    return response.text();
                })
                .then(function(data) {
                    console.log('CA marks data received, length:', data.length);
                    Android.onCAMarksReceived(data);
                })
                .catch(function(error) {
                    console.log('CA marks fetch error:', error.message);
                    Android.onError(error.message);
                });
            })();
        """.trimIndent()

        webView?.evaluateJavascript(jsFetch, null)
    }

    /**
     * Fetch profile picture as raw bytes from the SIS portal.
     * Tries the remote/downloadUrl endpoint first, then falls back to a direct S3-style path.
     */
    suspend fun fetchProfilePicture(rollNumber: String): ByteArray? {
        val token = cachedAuthToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                // The SIS Angular app fetches the display picture via:
                //   /sis/students/downloadUrl?contentType=image/jpeg&filename={roll}.jpg_{roll}&id=sis-itech/2023/displayPicture/{roll}.jpg_{roll}&originalname={roll}.jpg
                val filename = "${rollNumber}.jpg_${rollNumber}"
                val id = "sis-itech/2023/displayPicture/$filename"
                val encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8")
                val encodedId = java.net.URLEncoder.encode(id, "UTF-8")
                val encodedOriginal = java.net.URLEncoder.encode("${rollNumber}.jpg", "UTF-8")
                val url = java.net.URL(
                    "${SIS_BASE_URL}students/downloadUrl?contentType=image%2Fjpeg&filename=$encodedFilename&id=$encodedId&originalname=$encodedOriginal&size=79087"
                )
                android.util.Log.d("WebViewAuth", "Fetching profile pic URL: $url")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "*/*")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "Profile pic response: $responseCode")

                if (responseCode == 200) {
                    val contentType = connection.contentType ?: ""
                    if (contentType.startsWith("image/")) {
                        // Direct image bytes returned
                        connection.inputStream.use { it.readBytes() }
                    } else {
                        // Likely a JSON with a pre-signed URL, or a redirect URL as text
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        android.util.Log.d("WebViewAuth", "Profile pic body (first 200): ${body.take(200)}")
                        // Try to parse as a URL (could be a plain signed URL or JSON with "url" field)
                        val imageUrl = if (body.startsWith("http")) {
                            body.trim().trim('"')
                        } else {
                            // Try JSON parse for {"url": "..."} or just the raw string
                            try {
                                val map = gson.fromJson(body, Map::class.java)
                                (map["url"] ?: map["downloadUrl"] ?: map["signedUrl"])?.toString()
                            } catch (_: Exception) { null }
                        }
                        if (imageUrl != null) {
                            // Fetch the actual image from the signed URL (no auth needed)
                            val imgConn = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
                            imgConn.connectTimeout = 10000
                            imgConn.readTimeout = 10000
                            if (imgConn.responseCode == 200) {
                                imgConn.inputStream.use { it.readBytes() }
                            } else null
                        } else null
                    }
                } else if (responseCode == 401) {
                    cachedAuthToken = null
                    null
                } else null
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Profile pic fetch error: ${e.message}")
                null
            }
        }
    }

    /**
     * Probe whether a given token works against the SIS API.
     * Used to test cross-client token reuse (ies_meetings token for SIS).
     */
    private suspend fun probeSisToken(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context)
            val rollNumber = prefs.rollNumber ?: return@withContext false
            val url = java.net.URL("${ATTENDANCE_API_BASE}$rollNumber")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val code = connection.responseCode
            android.util.Log.d("WebViewAuth", "SIS probe response: HTTP $code")
            // 200 = works; 500 often means 401 proxied (check body)
            if (code == 200) return@withContext true
            if (code in 500..599) {
                val errBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "" } catch (_: Exception) { "" }
                return@withContext !(errBody.contains("401") || errBody.contains("unauthorized", ignoreCase = true))
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("WebViewAuth", "SIS probe error: ${e.message}")
            false
        }
    }

    /**
     * Get a meetings module access token via Keycloak password grant.
     * Uses client_id=ies_meetings (separate from SIS's ies_sis).
     */
    suspend fun loginViaMeetingsKeycloak(username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val config = getMeetingsConfig()
                android.util.Log.d("WebViewAuth", "Meetings Keycloak login for: $username (realm=${config.realm}, clientId=${config.clientId})")
                val url = java.net.URL(config.tokenUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val params = "grant_type=password&client_id=${config.clientId}" +
                    "&scope=openid" +
                    "&username=" + java.net.URLEncoder.encode(username, "UTF-8") +
                    "&password=" + java.net.URLEncoder.encode(password, "UTF-8")
                connection.outputStream.use { it.write(params.toByteArray()) }

                if (connection.responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val tokenResponse = gson.fromJson(json, TokenResponse::class.java)
                    cachedMeetingsToken = tokenResponse.accessToken
                    android.util.Log.d("WebViewAuth", "Meetings Keycloak login successful")
                    true
                } else {
                    android.util.Log.e("WebViewAuth", "Meetings Keycloak login failed: HTTP ${connection.responseCode}")
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Meetings Keycloak login error: ${e.message}")
                false
            }
        }
    }

    /**
     * Ensure we have a valid meetings token. Gets one if missing.
     */
    suspend fun ensureMeetingsToken(): Boolean {
        if (cachedMeetingsToken != null) return true
        val prefs = com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context)
        val username = prefs.rollNumber ?: return false
        val password = prefs.password ?: return false
        return loginViaMeetingsKeycloak(username, password)
    }

    /**
     * Fetch circulars list via POST /meetings/circulars/user/pagination
     */
    suspend fun fetchCircularsDirect(skip: Int = 0, limit: Int = 30): kotlin.Result<CircularListResponse>? {
        val token = cachedMeetingsToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val endpoint = "${MEETINGS_BASE_URL}circulars/user/pagination"
                android.util.Log.d("WebViewAuth", "Fetching circulars (skip=$skip, limit=$limit)")
                val url = java.net.URL(endpoint)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val body = """{"filter":{},"skip":$skip,"limit":$limit,"search":"","sort":""}"""
                connection.outputStream.use { it.write(body.toByteArray()) }

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "Circulars response: $responseCode")

                if (responseCode == 200) {
                    val data = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = gson.fromJson(data, CircularListResponse::class.java)
                    kotlin.Result.success(response)
                } else if (responseCode == 401) {
                    cachedMeetingsToken = null
                    null
                } else if (responseCode in 500..599) {
                    kotlin.Result.failure(ServerDownException(responseCode))
                } else {
                    kotlin.Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Circulars fetch error: ${e.message}")
                kotlin.Result.failure(e)
            }
        }
    }

    /**
     * Fetch circular detail by ID via GET /meetings/circulars/{id}
     */
    suspend fun fetchCircularDetailDirect(circularId: String): kotlin.Result<CircularDetail>? {
        val token = cachedMeetingsToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val endpoint = "${MEETINGS_BASE_URL}circulars/$circularId"
                android.util.Log.d("WebViewAuth", "Fetching circular detail: $circularId")
                val url = java.net.URL(endpoint)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "Circular detail response: $responseCode")

                if (responseCode == 200) {
                    val data = connection.inputStream.bufferedReader().use { it.readText() }
                    val detail = gson.fromJson(data, CircularDetail::class.java)
                    kotlin.Result.success(detail)
                } else if (responseCode == 401) {
                    cachedMeetingsToken = null
                    null
                } else if (responseCode in 500..599) {
                    kotlin.Result.failure(ServerDownException(responseCode))
                } else {
                    kotlin.Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Circular detail fetch error: ${e.message}")
                kotlin.Result.failure(e)
            }
        }
    }

    /**
     * Get a pre-signed S3 URL for a circular attachment.
     * GET /meetings/s3/download/url?contentType=...&originalname=...&url=...
     */
    suspend fun fetchCircularPdfUrl(attachment: com.example.attendancewidgetlaudea.data.model.CircularAttachment): kotlin.Result<String>? {
        val token = cachedMeetingsToken ?: return null
        val fileUrl = attachment.url ?: return kotlin.Result.failure(Exception("No attachment URL"))
        val originalName = attachment.originalName ?: "circular.pdf"
        val contentType = attachment.contentType ?: "application/pdf"

        return withContext(Dispatchers.IO) {
            try {
                val endpoint = "${MEETINGS_BASE_URL}s3/download/url" +
                    "?contentType=${java.net.URLEncoder.encode(contentType, "UTF-8")}" +
                    "&originalname=${java.net.URLEncoder.encode(originalName, "UTF-8")}" +
                    "&url=${java.net.URLEncoder.encode(fileUrl, "UTF-8")}"
                android.util.Log.d("WebViewAuth", "Fetching PDF signed URL")
                val url = java.net.URL(endpoint)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "PDF URL response: $responseCode")

                if (responseCode == 200) {
                    val data = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = gson.fromJson(data, SignedUrlResponse::class.java)
                    val signedUrl = response.signedUrl
                    if (signedUrl != null) {
                        kotlin.Result.success(signedUrl)
                    } else {
                        kotlin.Result.failure(Exception("No signed URL in response"))
                    }
                } else if (responseCode == 401) {
                    cachedMeetingsToken = null
                    null
                } else if (responseCode in 500..599) {
                    kotlin.Result.failure(ServerDownException(responseCode))
                } else {
                    kotlin.Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "PDF URL fetch error: ${e.message}")
                kotlin.Result.failure(e)
            }
        }
    }

    /**
     * Download PDF bytes from a pre-signed S3 URL (no auth needed).
     */
    suspend fun downloadPdfBytes(signedUrl: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("WebViewAuth", "Downloading PDF from S3")
                val connection = java.net.URL(signedUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                if (connection.responseCode == 200) {
                    connection.inputStream.use { it.readBytes() }
                } else {
                    android.util.Log.e("WebViewAuth", "PDF download failed: HTTP ${connection.responseCode}")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "PDF download error: ${e.message}")
                null
            }
        }
    }
}
