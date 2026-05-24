package com.justpass.app.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Animated rainbow "sticker / shader" ripple ring backed by an animated
 * WebP asset (`assets/ring.webp`) — 400×400, 120 frames @ 30 fps, 4-second
 * loop. Frames were pre-rendered offline from the original SVG-filter HTML
 * via headless Chrome, so we get the exact gooey-sticker look without
 * paying the SVG filter pipeline cost at runtime.
 *
 * - API 28+ (Android 9+): plays animated. ImageDecoder + AnimatedImageDrawable.
 * - API 26-27 (Android 8/8.1): shows first frame only (static). Negligible
 *   user share by 2026; acceptable graceful degradation.
 *
 * Stack as a sibling under your Compose foreground (Box → ShaderRing on the
 * bottom, profile pic on top). The WebP has transparency so it composes
 * cleanly over any background.
 */
@Composable
fun ShaderRing(
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawable = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeAnimated(context)
        } else {
            // Decoding animated WebP as static via BitmapFactory yields
            // first-frame Bitmap; wrap in a BitmapDrawable. API 26-27 only.
            val bytes = context.assets.open("ring.webp").use { it.readBytes() }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            android.graphics.drawable.BitmapDrawable(context.resources, bmp)
        }
    }

    AndroidView(
        modifier = modifier.size(size),
        factory = { ctx ->
            ImageView(ctx).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
    )

    if (drawable is AnimatedImageDrawable) {
        // Tie animation lifecycle to composition. Starts on enter, stops on
        // dispose to release decode threads.
        DisposableEffect(drawable) {
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            drawable.start()
            onDispose { drawable.stop() }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeAnimated(context: android.content.Context): Drawable {
    val source = ImageDecoder.createSource(context.assets, "ring.webp")
    return ImageDecoder.decodeDrawable(source) { _, _, _ ->
        // ImageDecoder defaults are fine; no resize/post-processor needed.
    }
}
