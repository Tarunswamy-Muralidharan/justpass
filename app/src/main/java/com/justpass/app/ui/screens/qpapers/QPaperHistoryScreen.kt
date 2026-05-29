package com.justpass.app.ui.screens.qpapers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justpass.app.data.model.QPaper
import com.justpass.app.ui.components.GlassCardShape
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.QPaperViewModel
import io.github.fletchmckee.liquid.LiquidState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QPaperHistoryScreen(
    cardState: LiquidState,
    viewModel: QPaperViewModel,
    onOpenViewer: (QPaper) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.historyState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    val approvedCount = remember(state.processed) { state.processed.count { it.isApproved } }
    val rejectedCount = remember(state.processed) { state.processed.size - approvedCount }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(
            title = "History",
            subtitle = "$approvedCount approved · $rejectedCount rejected",
            onBack = onBack,
        )

        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                RoseFourLoader(modifier = Modifier.size(48.dp))
            }
            state.processed.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Nothing here yet.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Once you approve or reject papers they'll show up here.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.processed, key = { it.id }) { paper ->
                    HistoryPaperCard(
                        paper = paper,
                        onClick = { onOpenViewer(paper) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryPaperCard(
    paper: QPaper,
    onClick: () -> Unit,
) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassCardShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${paper.department} · ${paper.subjectCode}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    paper.subjectName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${paper.categoryEnum.label} · ${paper.examYear} · ${paper.regulation} · Sem ${paper.semester}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formatProcessedAt(paper.approvedAt),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            StatusBadge(approved = paper.isApproved)
        }
    }
}

@Composable
private fun StatusBadge(approved: Boolean) {
    val (bg, fg, label) = if (approved) {
        Triple(Color(0xFF00E676).copy(alpha = 0.18f), Color(0xFF00E676), "Approved")
    } else {
        Triple(Color(0xFFFF5252).copy(alpha = 0.18f), Color(0xFFFF5252), "Rejected")
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatProcessedAt(ts: Long?): String {
    if (ts == null || ts <= 0) return "—"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))
}
