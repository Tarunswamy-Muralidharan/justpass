package com.example.attendancewidgetlaudea.ui.components

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

object AdConfig {
    private const val KEY_ADS_ENABLED = "ads_enabled"

    var adsEnabled: Boolean = false
        private set

    fun init() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600 // 1 hour cache
            }
        )
        remoteConfig.setDefaultsAsync(mapOf(KEY_ADS_ENABLED to false))
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            adsEnabled = remoteConfig.getBoolean(KEY_ADS_ENABLED)
        }
    }
}
