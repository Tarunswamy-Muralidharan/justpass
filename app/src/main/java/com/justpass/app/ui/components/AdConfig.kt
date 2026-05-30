package com.justpass.app.ui.components

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.justpass.app.BuildConfig
import com.justpass.app.data.local.SecurePreferences

object AdConfig {
    private const val KEY_ADS_ENABLED = "ads_enabled"
    private const val KEY_ADS_USE_TEST = "ads_use_test_ids"
    private const val KEY_TEST_DEVICE_IDS = "ads_test_device_ids"
    private const val PREFS = "ad_config_cache"

    // Owner-only demo override. When the signed-in roll matches one of these,
    // ads are forced ON with AdMob's test creatives regardless of the global
    // ads_enabled Remote Config flag. Lets the developer preview the ad slots
    // on their own device while the rest of the userbase stays ad-free.
    // Gated to debug builds so a leaked release APK can't impersonate.
    private val OWNER_DEMO_ROLLS = setOf<String>(
        // "715523244053", // Tarunswamy — disabled: no demo ads on device for now
    )

    // Real (production) AdMob unit IDs.
    const val REAL_BANNER_ID = "ca-app-pub-4936276228225156/4108831863"
    const val REAL_INTERSTITIAL_ID = "ca-app-pub-4936276228225156/3208220090"

    // Google AdMob's public test unit IDs — never serve real ads, never bill,
    // safe to load anywhere. Used when `ads_use_test_ids` Remote Config flag
    // is true (e.g. when staging a release build for testing on Closed track).
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    // OFF by default. Flip the `ads_enabled` parameter in Firebase Remote Config
    // (Firebase Console → Engage → Remote Config) to true when you're ready to
    // monetise. Existing installs pick the new value up on their next launch
    // after the 1h fetch cache expires.
    private val _adsEnabled = mutableStateOf(false)
    val adsEnabled: Boolean get() = _adsEnabled.value

    // false = serve real ads (production).
    // true  = serve AdMob's test ads (no revenue, no policy risk).
    // Flip via Remote Config key `ads_use_test_ids` for an instant kill switch
    // if AdMob flags traffic or you want to demo the app without real ads.
    private val _useTestIds = mutableStateOf(false)
    val useTestIds: Boolean get() = _useTestIds.value

    val bannerAdUnitId: String get() = if (useTestIds) TEST_BANNER_ID else REAL_BANNER_ID
    val interstitialAdUnitId: String get() = if (useTestIds) TEST_INTERSTITIAL_ID else REAL_INTERSTITIAL_ID

    /**
     * Debug-build + matching roll = owner demo device. Forces ads ON with
     * test creatives even when the global ads_enabled RC flag is off.
     * Release builds always return false so a leaked APK can't impersonate.
     */
    private fun isOwnerDemoDevice(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        val roll = SecurePreferences.getInstance(context).rollNumber ?: return false
        return roll in OWNER_DEMO_ROLLS
    }

    /**
     * Call this after login completes so the owner-demo override takes
     * effect on the same session as a fresh install. AdConfig.init() runs
     * during Activity.onCreate before login, so first-install users would
     * otherwise need a relaunch to see demo ads.
     */
    fun refreshOwnerDemo(context: Context) {
        if (isOwnerDemoDevice(context)) {
            _adsEnabled.value = true
            _useTestIds.value = true
        }
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ownerDemo = isOwnerDemoDevice(context)
        _adsEnabled.value = ownerDemo || prefs.getBoolean(KEY_ADS_ENABLED, false)
        _useTestIds.value = ownerDemo || prefs.getBoolean(KEY_ADS_USE_TEST, false)

        // Apply the last-known test-device list before any ad request fires.
        // Format: comma-separated AdMob hashed device IDs (each device prints
        // its own ID to logcat the first time it loads an ad).
        applyTestDeviceIds(prefs.getString(KEY_TEST_DEVICE_IDS, "") ?: "")

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600 // 1 hour cache
            }
        )
        remoteConfig.setDefaultsAsync(com.justpass.app.R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            val fresh = remoteConfig.getBoolean(KEY_ADS_ENABLED)
            val freshTest = remoteConfig.getBoolean(KEY_ADS_USE_TEST)
            val freshDeviceIds = remoteConfig.getString(KEY_TEST_DEVICE_IDS)
            // Owner demo OR-with the RC values so an empty/false RC can't
            // turn off demo ads on the owner device after first fetch.
            _adsEnabled.value = ownerDemo || fresh
            _useTestIds.value = ownerDemo || freshTest
            applyTestDeviceIds(freshDeviceIds)
            prefs.edit()
                .putBoolean(KEY_ADS_ENABLED, fresh)
                .putBoolean(KEY_ADS_USE_TEST, freshTest)
                .putString(KEY_TEST_DEVICE_IDS, freshDeviceIds)
                .apply()
        }
    }

    /**
     * Apply a comma-separated list of AdMob test device IDs. Devices on
     * the list receive AdMob's test creative even when [useTestIds] is
     * false (i.e. while the world is served real ads). Empty input = clear
     * the list.
     */
    private fun applyTestDeviceIds(csv: String) {
        val ids = csv.split(',')
            .mapNotNull { raw -> raw.trim().ifBlank { null } }
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(ids)
                .build()
        )
    }
}
