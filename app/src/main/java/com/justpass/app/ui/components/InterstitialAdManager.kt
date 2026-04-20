package com.justpass.app.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import com.justpass.app.data.analytics.Analytics
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAdManager {

    // AdMob approval pending — kill switch in AdConfig.adsEnabled (Firebase Remote Config)
    // keeps this unit dormant until Remote Config flips ads_enabled=true, which should only
    // be done after AdMob completes app review (24–48h).
    private const val AD_UNIT_ID = "ca-app-pub-4936276228225156/3208220090"
    private const val TAG = "InterstitialAd"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun preload(context: Context) {
        if (!AdConfig.adsEnabled) return
        if (interstitialAd != null || isLoading) return
        isLoading = true

        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    Log.d(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    Log.d(TAG, "Interstitial failed to load: ${error.message}")
                }
            }
        )
    }

    fun show(activity: Activity, onDismissed: () -> Unit = {}) {
        if (!AdConfig.adsEnabled) { onDismissed(); return }
        val ad = interstitialAd
        if (ad == null) {
            onDismissed()
            preload(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                onDismissed()
                preload(activity) // preload next one
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                interstitialAd = null
                onDismissed()
                preload(activity)
            }
        }

        Analytics.logAdImpression("Bunkometer", "interstitial")
        ad.show(activity)
    }
}
