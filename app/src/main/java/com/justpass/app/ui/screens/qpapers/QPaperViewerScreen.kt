package com.justpass.app.ui.screens.qpapers

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.data.model.QPaper
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.QPaperViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF viewer for an approved question paper.
 *
 * Sets WindowManager.LayoutParams.FLAG_SECURE on the host Activity for
 * the lifetime of this composable. This blocks system screenshots,
 * blocks screen recording (window renders black), and blocks adb screencap.
 * Cleared on dispose so other screens behave normally.
 *
 * Bytes are downloaded once into memory and a temp file is created in
 * the cache dir for PdfRenderer (which requires a ParcelFileDescriptor).
 * Temp file is deleted as soon as rendering completes — the PDF is never
 * persisted in any user-accessible location.
 */
@Composable
fun QPaperViewerScreen(
    paper: QPaper,
    viewModel: QPaperViewModel,
    isAdmin: Boolean = false,
    onReuploadElsewhere: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // FLAG_SECURE on the host Activity for the lifetime of this screen.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(paper.id) {
        loading = true
        error = null
        val bytes = viewModel.downloadPaperBytes(paper)
        if (bytes == null || bytes.isEmpty()) {
            error = "Failed to load PDF"
            loading = false
            return@LaunchedEffect
        }
        val rendered = renderPdfBytes(context, bytes)
        pages = rendered
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            pages.forEach { runCatching { it.recycle() } }
        }
    }

    val scale = remember { mutableFloatStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale.floatValue = (scale.floatValue * zoomChange).coerceIn(0.5f, 5f)
        offset.value = Offset(
            x = offset.value.x + panChange.x,
            y = offset.value.y + panChange.y,
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0E1A)).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${paper.subjectCode} · ${paper.categoryEnum.label} · ${paper.examYear}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    paper.subjectName,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (scale.floatValue != 1f) {
                TextButton(onClick = {
                    scale.floatValue = 1f
                    offset.value = Offset.Zero
                }) { Text("Reset", color = Color.White, fontSize = 12.sp) }
            }
            if (isAdmin) {
                TextButton(onClick = onReuploadElsewhere) {
                    Text("Re-upload", color = Color(0xFF7C4DFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RoseFourLoader(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading paper…", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                }
                error != null -> Text(
                    error!!,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
                pages.isNotEmpty() -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformState)
                        .graphicsLayer {
                            scaleX = scale.floatValue
                            scaleY = scale.floatValue
                            translationX = offset.value.x
                            translationY = offset.value.y
                        },
                    contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 160.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(pages) { index, bm ->
                        PdfPageView(bm, index + 1, pages.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageView(bitmap: Bitmap, pageNumber: Int, total: Int) {
    Column {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Page $pageNumber",
            modifier = Modifier.fillMaxWidth().clip(GlassCardShapeSmall),
            contentScale = ContentScale.FillWidth,
        )
        if (total > 1) {
            Text(
                "Page $pageNumber of $total",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 2.dp),
            )
        }
    }
}

private suspend fun renderPdfBytes(context: Context, bytes: ByteArray): List<Bitmap> =
    withContext(Dispatchers.IO) {
        val out = mutableListOf<Bitmap>()
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("qpaper_", ".pdf", context.cacheDir)
            tempFile.writeBytes(bytes)
            val fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = 2
                val bm = Bitmap.createBitmap(
                    page.width * scale,
                    page.height * scale,
                    Bitmap.Config.ARGB_8888,
                )
                bm.eraseColor(android.graphics.Color.WHITE)
                page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                out.add(bm)
            }
            renderer.close()
            fd.close()
        } catch (e: Exception) {
            android.util.Log.e("QPaperViewer", "render failed: ${e.message}", e)
        } finally {
            // Delete the temp file immediately — never leave the PDF in user-accessible
            // storage. Bitmaps stay in memory until the composable is disposed.
            tempFile?.delete()
        }
        out
    }

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
