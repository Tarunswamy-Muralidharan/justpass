package com.justpass.app.ui.components

import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.justpass.app.data.analytics.Analytics

/**
 * One-deep banner pool. Holds a single AdView whose creative was loaded
 * during MobileAds init, so the first AdBanner composable on any screen
 * can attach it immediately instead of waiting for a fresh AdRequest to
 * round-trip the AdMob servers.
 *
 * After acquire() hands the AdView off to a screen, a fresh AdView is
 * preloaded in the background so the next navigation is also warm.
 *
 * Why custom: Mobile Ads SDK 23.6.0 has no built-in banner preload
 * (MobileAds.startPreload added BANNER format only in 24.0.0). When the
 * project bumps play-services-ads to 25.x, replace this with the official
 * preload API.
 */
object AdBannerPool {
    private var pooled: AdView? = null
    private var pooledWidthDp: Int = 0

    fun preload(context: Context, adSize: AdSize, widthDp: Int) {
        if (!AdConfig.adsEnabled) return
        if (pooled != null && pooledWidthDp == widthDp) return
        pooled?.destroy()
        val view = AdView(context.applicationContext).apply {
            setAdSize(adSize)
            adUnitId = AdConfig.bannerAdUnitId
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {}
            }
            loadAd(AdRequest.Builder().build())
        }
        pooled = view
        pooledWidthDp = widthDp
    }

    /**
     * Returns the pooled AdView for [widthDp] if available, or null if no
     * matching pre-load exists. Re-attaches a screen-scoped listener so
     * impressions report against the current screen name. After hand-off,
     * a fresh background preload is queued.
     */
    fun acquire(context: Context, adSize: AdSize, widthDp: Int, screenName: String): AdView? {
        if (!AdConfig.adsEnabled) return null
        val cached = pooled?.takeIf { pooledWidthDp == widthDp } ?: run {
            // Width mismatch (e.g. landscape rotation) — start a fresh
            // preload now so the next acquire matches; caller falls back.
            preload(context, adSize, widthDp)
            return null
        }
        (cached.parent as? ViewGroup)?.removeView(cached)
        cached.adListener = object : AdListener() {
            override fun onAdClicked() {
                Analytics.logAdClick(screenName, "banner")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {}
        }
        // The pre-warmed AdView already fired onAdLoaded during preload, so
        // re-attaching a listener here won't see it again. Log the impression
        // directly — the view is about to be attached to a visible composable.
        Analytics.logAdImpression(screenName, "banner")
        pooled = null
        pooledWidthDp = 0
        // Warm the next navigation in background.
        preload(context, adSize, widthDp)
        return cached
    }
}
