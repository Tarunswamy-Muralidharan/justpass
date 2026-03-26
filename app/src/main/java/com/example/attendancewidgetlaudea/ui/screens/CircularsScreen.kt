package com.example.attendancewidgetlaudea.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.Circular
import com.example.attendancewidgetlaudea.ui.components.GlassCardShape
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.viewmodel.CircularViewModel
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import io.github.fletchmckee.liquid.LiquidState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CircularsScreen(
    cardState: LiquidState,
    viewModel: CircularViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val pdfState by viewModel.pdfState.collectAsState()
    val isDark = isSystemInDarkTheme()

    // Mark the newest circular as seen so the background worker doesn't re-notify
    LaunchedEffect(uiState.circulars) {
        if (uiState.circulars.isNotEmpty()) {
            context.getSharedPreferences("laudea_prefs", Context.MODE_PRIVATE)
                .edit().putString("last_seen_circular_id", uiState.circulars.first().id).apply()
        }
    }

    // If viewing a PDF, show the PDF viewer
    if (pdfState.isLoading || pdfState.pdfPages.isNotEmpty() || pdfState.circularDetail != null) {
        PdfViewerScreen(
            pdfState = pdfState,
            onBack = { viewModel.clearPdfState() }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = GlassCardShape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "Circulars",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.fetchCirculars() },
                    enabled = !uiState.isLoading
                ) {
                    Icon(
                        Icons.Default.Refresh, "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.circulars.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.errorMessage != null && uiState.circulars.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            uiState.errorMessage ?: "Error",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.fetchCirculars() }) {
                            Text("Retry")
                        }
                    }
                }
                uiState.circulars.isEmpty() -> {
                    Text(
                        "No circulars found",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 8.dp, bottom = 130.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.circulars, key = { it.id }) { circular ->
                            CircularCard(
                                circular = circular,
                                isDark = isDark,
                                onClick = {
                                    viewModel.loadCircularPdf(circular.id)
                                }
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.circulars.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CircularCard(
    circular: Circular,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val tagColor = getTagColor(circular.tag, isDark)

    GlassListCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = GlassCardShapeSmall,
        tintColor = tagColor.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        ) {
            // Tag badge
            circular.tag?.let { tag ->
                Text(
                    text = tag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tagColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Title
            Text(
                text = circular.title ?: "Untitled",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Ref + Date row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                circular.ref?.let { ref ->
                    Text(
                        text = ref,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                circular.date?.let { date ->
                    Text(
                        text = formatCircularDate(date),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfViewerScreen(
    pdfState: com.example.attendancewidgetlaudea.ui.viewmodel.PdfViewerState,
    onBack: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset = Offset(
            x = offset.x + panChange.x,
            y = offset.y + panChange.y
        )
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = GlassCardShape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = pdfState.circularDetail?.title ?: "Circular",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (scale != 1f) {
                    TextButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                        Text("Reset", fontSize = 12.sp)
                    }
                }
            }
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                pdfState.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Loading PDF...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                pdfState.errorMessage != null -> {
                    Text(
                        pdfState.errorMessage,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
                pdfState.pdfPages.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(state = transformState)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentPadding = PaddingValues(
                            start = 8.dp, end = 8.dp,
                            top = 8.dp, bottom = 130.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(pdfState.pdfPages) { index, bitmap ->
                            PdfPageCard(bitmap = bitmap, pageNumber = index + 1, totalPages = pdfState.pdfPages.size)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageCard(bitmap: Bitmap, pageNumber: Int, totalPages: Int) {
    Column {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Page $pageNumber",
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassCardShapeSmall),
            contentScale = ContentScale.FillWidth
        )
        if (totalPages > 1) {
            Text(
                "Page $pageNumber of $totalPages",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun getTagColor(tag: String?, isDark: Boolean): Color {
    return when (tag?.uppercase()) {
        "PRINCIPAL OFFICE" -> if (isDark) Color(0xFF8E24AA) else Color(0xFF7B1FA2)
        "ADMINISTRATION" -> if (isDark) Color(0xFF1E88E5) else Color(0xFF1565C0)
        "EXAMINATION" -> if (isDark) Color(0xFFE53935) else Color(0xFFC62828)
        "ACCOUNTS" -> if (isDark) Color(0xFF43A047) else Color(0xFF2E7D32)
        else -> if (isDark) Color(0xFFFF9800) else Color(0xFFE65100)
    }
}

private fun formatCircularDate(isoDate: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(isoDate) ?: return isoDate
        val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        outputFormat.format(date)
    } catch (e: Exception) {
        isoDate.take(10)
    }
}
