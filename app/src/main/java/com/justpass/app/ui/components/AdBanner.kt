package com.justpass.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // Adaptive anchored banner — Google's recommended replacement for the
    // fixed 320x50 BANNER size. Higher fill rate + faster median load because
    // creatives are pre-cached for common adaptive widths. Falls back to the
    // fixed BANNER size on devices where adaptive sizing fails.
    val adSize = remember(configuration.screenWidthDp) {
        val widthDp = configuration.screenWidthDp
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
            ?: AdSize.BANNER
    }

    val widthDp = configuration.screenWidthDp

    // Hoist the AdView so we can wire it into the host Lifecycle.
    // AdMob requires pause()/resume()/destroy() to be called explicitly —
    // without it the banner keeps refreshing in background (battery drain)
    // and leaks its WebView + listener references when the screen leaves
    // composition (12 screens × revisits compounded).
    val adView = remember(adSize, widthDp, screenName) {
        AdBannerPool.acquire(context, adSize, widthDp, screenName) ?: AdView(context).apply {
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

    // Pump host lifecycle events into AdView — pause when the screen is
    // backgrounded, resume on return, destroy when the composable leaves
    // composition for good.
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    // Key on the adView instance so AndroidView reconstructs when the
    // hoisted AdView is replaced (rotation → new widthDp → new adSize).
    // Without this key, AndroidView holds the OLD AdView reference even
    // after remember replaces it, leaving a destroyed view attached.
    key(adView) {
        AndroidView(
            modifier = modifier.fillMaxWidth().height(adSize.height.dp),
            factory = { adView },
        )
    }
}
