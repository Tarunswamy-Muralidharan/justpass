package com.justpass.app.ui.components

import android.util.DisplayMetrics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.justpass.app.data.analytics.Analytics
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

// Ad unit ID resolved through AdConfig — flips between real + AdMob's public
// test IDs based on the `ads_use_test_ids` Remote Config flag.

@Composable
fun AdBanner(modifier: Modifier = Modifier, screenName: String = "unknown") {
    if (!AdConfig.adsEnabled) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Adaptive anchored banner — Google's recommended replacement for the
    // fixed 320x50 BANNER size. Higher fill rate + faster median load because
    // creatives are pre-cached for common adaptive widths. Falls back to the
    // fixed BANNER size on devices where adaptive sizing fails.
    val adSize = remember(configuration.screenWidthDp) {
        val widthDp = configuration.screenWidthDp
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
            ?: AdSize.BANNER
    }

    // Reserve the ad height upfront so the layout doesn't pop when the
    // creative finally renders. Without this, the ad slot is 0.dp until
    // load completes — visible jank on slower networks.
    AndroidView(
        modifier = modifier.fillMaxWidth().height(adSize.height.dp),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adSize)
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
