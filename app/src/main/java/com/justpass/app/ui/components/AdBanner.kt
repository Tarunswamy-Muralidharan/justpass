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

// Ad unit ID resolved through AdConfig — flips between real + AdMob's public
// test IDs based on the `ads_use_test_ids` Remote Config flag. Lets the dev
// flip to safe test ads instantly without an APK rebuild.

@Composable
fun AdBanner(modifier: Modifier = Modifier, screenName: String = "unknown") {
    if (!AdConfig.adsEnabled) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdConfig.bannerAdUnitId
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
