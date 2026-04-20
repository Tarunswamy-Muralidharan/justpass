package com.justpass.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.justpass.app.data.analytics.Analytics
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

// AdMob approval pending — kill switch in AdConfig.adsEnabled (Firebase Remote Config)
// keeps this unit dormant until Remote Config flips ads_enabled=true, which should only
// be done after AdMob completes app review (24–48h).
private const val BANNER_AD_UNIT_ID = "ca-app-pub-4936276228225156/4108831863"

@Composable
fun AdBanner(modifier: Modifier = Modifier, screenName: String = "unknown") {
    if (!AdConfig.adsEnabled) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Analytics.logAdImpression(screenName, "banner")
                    }
                    override fun onAdClicked() {
                        Analytics.logAdClick(screenName, "banner")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {}
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
