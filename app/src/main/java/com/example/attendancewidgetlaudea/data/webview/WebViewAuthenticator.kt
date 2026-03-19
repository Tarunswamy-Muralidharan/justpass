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
import com.example.attendancewidgetlaudea.data.model.TimetableResponse
import com.example.attendancewidgetlaudea.data.model.TokenResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class InvalidCredentialsException(message: String) : Exception(message)

class WebViewAuthenticator(private val context: Context) {

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Cache the auth token for fast refreshes — persisted to SecurePreferences
    var cachedAuthToken: String?
        get() = _cachedAuthToken ?: com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context).accessToken.also { _cachedAuthToken = it }
        set(value) {
            _cachedAuthToken = value
            com.example.attendancewidgetlaudea.data.local.SecurePreferences.getInstance(context).accessToken = value
        }
    private var _cachedAuthToken: String? = null

    companion object {
        private const val SIS_BASE_URL = "https://laudea.psgitech.ac.in/sis/"
        private const val LOGIN_URL_PATTERN = "accounts.psgitech.ac.in"
        private const val ATTENDANCE_API_PATTERN = "/sis/attendance/"
        private const val CA_MARKS_API_URL = "https://laudea.psgitech.ac.in/sis/ca/marks/v2/"
        private const val ATTENDANCE_API_BASE = "https://laudea.psgitech.ac.in/sis/attendance/"
        private const val TIMETABLE_API_BASE = "https://laudea.psgitech.ac.in/sis/time/table/"
        private const val KEYCLOAK_TOKEN_URL = "https://accounts.psgitech.ac.in/realms/itech/protocol/openid-connect/token"
    }

    /**
     * Get a fresh access token via direct Keycloak HTTP POST (no WebView).
     * Uses grant_type=password — ~200ms vs 15s WebView login.
     * Returns true if a new token was obtained.
     */
    suspend fun loginViaKeycloak(username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("WebViewAuth", "Direct Keycloak login for: $username")
                val url = java.net.URL(KEYCLOAK_TOKEN_URL)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val params = "grant_type=password&client_id=ies_sis" +
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
                } else if (responseCode == 401 || responseCode == 400) {
                    // Invalid credentials — don't fall back to WebView
                    android.util.Log.e("WebViewAuth", "Direct Keycloak login: invalid credentials (HTTP $responseCode)")
                    throw InvalidCredentialsException("Invalid roll number or password")
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
                android.util.Log.d("WebViewAuth", "Refreshing access token via refresh_token")
                val url = java.net.URL(KEYCLOAK_TOKEN_URL)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val params = "grant_type=refresh_token&client_id=ies_sis" +
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
                // STEP 1: Set up cookie manager (don't clear — preserve Keycloak session cookies)
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

                                // Successfully on SIS page (after login) - intercept XHR and navigate to attendance
                                (currentUrl.contains("laudea.psgitech.ac.in/sis") &&
                                !currentUrl.contains(LOGIN_URL_PATTERN) &&
                                !currentUrl.contains("/attendance/") &&
                                !dataFetched && !fetchStarted &&
                                loginAttempted) -> {
                                    fetchStarted = true
                                    android.util.Log.d("WebViewAuth", "On authenticated SIS page, intercepting XHR for attendance")

                                    mainHandler.postDelayed({
                                        interceptXhrAndNavigate(view, rollNumber)
                                    }, 1000)
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
                        Android.onError('Could not fetch attendance: ' + err.message);
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

        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("WebViewAuth", "Fast refresh with cached token")
                val url = java.net.URL("${ATTENDANCE_API_BASE}$rollNumber")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "Fast refresh response: $responseCode")

                if (responseCode == 200) {
                    val jsonData = connection.inputStream.bufferedReader().use { it.readText() }
                    val response = gson.fromJson(jsonData, AttendanceResponse::class.java)
                    val attendanceData = AttendanceData.fromResponse(response)
                    Result.success(attendanceData)
                } else if (responseCode == 401) {
                    // Token expired
                    cachedAuthToken = null
                    null
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Fast refresh error: ${e.message}")
                null // Fall back to WebView
            }
        }
    }

    /**
     * Fetch CA marks using cached auth token (fast direct HTTP).
     */
    suspend fun fetchCAMarksDirect(rollNumber: String): Result<List<CourseMarks>>? {
        val token = cachedAuthToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("WebViewAuth", "Fetching CA marks direct for: $rollNumber")
                val url = java.net.URL("${CA_MARKS_API_URL}$rollNumber")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "CA marks direct response: $responseCode")

                if (responseCode == 200) {
                    val jsonData = connection.inputStream.bufferedReader().use { it.readText() }
                    val listType = object : TypeToken<List<CourseMarks>>() {}.type
                    val courseMarksList: List<CourseMarks> = gson.fromJson(jsonData, listType)
                    Result.success(courseMarksList)
                } else if (responseCode == 401) {
                    cachedAuthToken = null
                    null
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "CA marks direct error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch absent days details using cached auth token.
     */
    suspend fun fetchAbsentDays(rollNumber: String): Result<List<AbsentDay>>? {
        val token = cachedAuthToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("WebViewAuth", "Fetching absent days for: $rollNumber")
                val url = java.net.URL("https://laudea.psgitech.ac.in/sis/attendance/absent/$rollNumber")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "Absent days response: $responseCode")

                if (responseCode == 200) {
                    val jsonData = connection.inputStream.bufferedReader().use { it.readText() }
                    val listType = object : TypeToken<List<AbsentDay>>() {}.type
                    val absentDays: List<AbsentDay> = gson.fromJson(jsonData, listType)
                    Result.success(absentDays)
                } else if (responseCode == 401) {
                    cachedAuthToken = null
                    null
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Absent days error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch present days using cached auth token (fast direct HTTP).
     * Response format is identical to absent days.
     */
    suspend fun fetchPresentDays(rollNumber: String): Result<List<AbsentDay>>? {
        val token = cachedAuthToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("WebViewAuth", "Fetching present days for: $rollNumber")
                val url = java.net.URL("https://laudea.psgitech.ac.in/sis/attendance/present/$rollNumber")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "Present days response: $responseCode")

                if (responseCode == 200) {
                    val jsonData = connection.inputStream.bufferedReader().use { it.readText() }
                    val listType = object : TypeToken<List<AbsentDay>>() {}.type
                    val presentDays: List<AbsentDay> = gson.fromJson(jsonData, listType)
                    Result.success(presentDays)
                } else if (responseCode == 401) {
                    cachedAuthToken = null
                    null
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Present days error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch exemptions using cached auth token (fast direct HTTP).
     */
    suspend fun fetchExemptionsDirect(rollNumber: String): Result<List<com.example.attendancewidgetlaudea.data.model.Exemption>>? {
        val token = cachedAuthToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("WebViewAuth", "Fetching exemptions for: $rollNumber")
                val url = java.net.URL("https://laudea.psgitech.ac.in/sis/remote/exemptions/$rollNumber")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "Exemptions response: $responseCode")

                if (responseCode == 200) {
                    val jsonData = connection.inputStream.bufferedReader().use { it.readText() }
                    val listType = object : TypeToken<List<com.example.attendancewidgetlaudea.data.model.Exemption>>() {}.type
                    val exemptions: List<com.example.attendancewidgetlaudea.data.model.Exemption> = gson.fromJson(jsonData, listType)
                    Result.success(exemptions)
                } else if (responseCode == 401) {
                    cachedAuthToken = null
                    null
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Exemptions error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch timetable using cached auth token (fast direct HTTP).
     */
    suspend fun fetchTimetableDirect(configId: String, rollNumber: String): Result<TimetableResponse>? {
        val token = cachedAuthToken ?: return null

        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("WebViewAuth", "Fetching timetable for: $rollNumber with config: $configId")
                val url = java.net.URL("${TIMETABLE_API_BASE}$configId/$rollNumber")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                android.util.Log.d("WebViewAuth", "Timetable response: $responseCode")

                if (responseCode == 200) {
                    val jsonData = connection.inputStream.bufferedReader().use { it.readText() }
                    val timetable = gson.fromJson(jsonData, TimetableResponse::class.java)
                    Result.success(timetable)
                } else if (responseCode == 401) {
                    cachedAuthToken = null
                    null
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WebViewAuth", "Timetable fetch error: ${e.message}")
                Result.failure(e)
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

                        // If redirected to login, session expired
                        if (url?.contains(LOGIN_URL_PATTERN) == true) {
                            if (!dataFetched) {
                                dataFetched = true
                                mainHandler.post { webView.destroy() }
                                continuation.resume(Result.failure(Exception("Session expired, please login again")))
                            }
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
                }, 20000)

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
}
