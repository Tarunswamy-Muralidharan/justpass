package com.justpass.app.ui.components

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

object AdConfig {
    private const val KEY_ADS_ENABLED = "ads_enabled"
    private const val KEY_ADS_USE_TEST = "ads_use_test_ids"
    private const val PREFS = "ad_config_cache"

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

    fun init(context: Context) {
        // Register test devices (MobileAds.initialize called in MainActivity)
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(
                    "D872E5F72EB689824EFAEAEE0181E4AE",  // Moto G54 (ZD222GJ6WD)
                    "7A84F9727359E7E95B313BCCA0FC5DA8"   // Edge 60 Fusion (ZN4224BRCG)
                ))
                .build()
        )

        // Eliminate the first-frame race. Two layers:
        //  - SharedPreferences cache (sync, instant): every launch after the
        //    first-ever reads the last activated value before any frame draws.
        //  - Bundled XML defaults: first-ever launch (no cache) falls back to
        //    `ads_enabled=true` from res/xml/remote_config_defaults.xml so ads
        //    appear immediately even on a fresh install. Server flip true→false
        //    causes a one-launch flicker, then prefs cache catches up.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _adsEnabled.value = prefs.getBoolean(KEY_ADS_ENABLED, false)
        _useTestIds.value = prefs.getBoolean(KEY_ADS_USE_TEST, false)

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
            _adsEnabled.value = fresh
            _useTestIds.value = freshTest
            prefs.edit()
                .putBoolean(KEY_ADS_ENABLED, fresh)
                .putBoolean(KEY_ADS_USE_TEST, freshTest)
                .apply()
        }
    }
}
