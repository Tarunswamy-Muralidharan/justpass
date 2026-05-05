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

        // Eliminate the first-frame race. Two layers:
        //  - SharedPreferences cache (sync, instant): every launch after the
        //    first-ever reads the last activated value before any frame draws.
        //  - Bundled XML defaults: first-ever launch (no cache) falls back to
        //    `ads_enabled=true` from res/xml/remote_config_defaults.xml so ads
        //    appear immediately even on a fresh install. Server flip true→false
        //    causes a one-launch flicker, then prefs cache catches up.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _adsEnabled.value = prefs.getBoolean(KEY_ADS_ENABLED, true)

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600 // 1 hour cache
            }
        )
        remoteConfig.setDefaultsAsync(com.justpass.app.R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            val fresh = remoteConfig.getBoolean(KEY_ADS_ENABLED)
            _adsEnabled.value = fresh
            // Persist for next launch's pre-render seed.
            prefs.edit().putBoolean(KEY_ADS_ENABLED, fresh).apply()
        }
    }
}
