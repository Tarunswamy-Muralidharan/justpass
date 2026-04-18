package com.example.attendancewidgetlaudea.ui.components

import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

object AdConfig {
    private const val KEY_ADS_ENABLED = "ads_enabled"

    private val _adsEnabled = mutableStateOf(true) // ON by default, Remote Config can kill
    val adsEnabled: Boolean get() = _adsEnabled.value

    fun init(context: android.content.Context) {
        // Register test devices (MobileAds.initialize called in MainActivity)
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(
                    "D872E5F72EB689824EFAEAEE0181E4AE",  // Moto G54 (ZD222GJ6WD)
                    "7A84F9727359E7E95B313BCCA0FC5DA8"   // Edge 60 Fusion (ZN4224BRCG)
                ))
                .build()
        )

        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600 // 1 hour cache
            }
        )
        remoteConfig.setDefaultsAsync(mapOf(KEY_ADS_ENABLED to true))
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            _adsEnabled.value = remoteConfig.getBoolean(KEY_ADS_ENABLED)
        }
    }
}
