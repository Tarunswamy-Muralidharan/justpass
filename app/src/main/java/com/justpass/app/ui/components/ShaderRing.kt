package com.justpass.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Animated rainbow "sticker / shader" ripple ring rendered by a small
 * transparent WebView hosting `assets/profile_shader_ring.html`. The
 * SVG `feGaussianBlur + feColorMatrix` chain in the asset produces the
 * gooey-sticker look that Compose can't render natively without an
 * AGSL shader.
 *
 * Notes:
 * - **Software layer** (`LAYER_TYPE_SOFTWARE`) is used instead of
 *   hardware. Slower in throughput but the SVG-filter renderer crashed
 *   the Chromium renderer on the hardware path during testing.
 * - JS is left disabled — the animation is pure CSS (`@property
 *   --ripple-offset` + `@keyframes`), no scripting needed.
 * - Stack as a sibling under your Compose foreground (Box → ShaderRing
 *   on the bottom, profile pic on top). Don't try the inverse —
 *   Compose-over-WebView is the flicker-prone direction.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShaderRing(
    size: Dp,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.size(size),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
                settings.allowFileAccess = true
                settings.domStorageEnabled = false
                // Default (hardware) — software layer killed the SVG
                // filter pipeline entirely (tile memory exhaustion, no
                // content drawn). Hardware crashes were tied to filter
                // size + repeat radial cost; mitigated by shrinking
                // filter region + stdDeviation in the HTML asset.
                loadUrl("file:///android_asset/profile_shader_ring.html")
            }
        }
    )
}
