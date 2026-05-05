package com.justpass.app.ui.components

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

object AdConfig {
    private const val KEY_ADS_ENABLED = "ads_enabled"
    private const val PREFS = "ad_config_cache"

    // OFF by default. Flip the `ads_enabled` parameter in Firebase Remote Config
    // (Firebase Console → Engage → Remote Config) to true when you're ready to
    // monetise. Existing installs pick the new value up on their next launch
    // after the 1h fetch cache expires.
    private val _adsEnabled = mutableStateOf(false)
    val adsEnabled: Boolean get() = _adsEnabled.value

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

        // Eliminate the first-frame race: read the LAST-KNOWN value from a
        // sync SharedPreferences cache and seed the UI state with it BEFORE
        // Firebase Remote Config's async fetch runs. Result: every launch
        // after the first-ever shows ads instantly with no flicker. The
        // first-ever launch (no cache yet) still shows nothing for ~500ms
        // until fetchAndActivate completes — unavoidable without baking the
        // value into the APK.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _adsEnabled.value = prefs.getBoolean(KEY_ADS_ENABLED, false)

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600 // 1 hour cache
            }
        )
        remoteConfig.setDefaultsAsync(mapOf(KEY_ADS_ENABLED to false))
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            val fresh = remoteConfig.getBoolean(KEY_ADS_ENABLED)
            _adsEnabled.value = fresh
            // Persist for next launch's pre-render seed.
            prefs.edit().putBoolean(KEY_ADS_ENABLED, fresh).apply()
        }
    }
}
