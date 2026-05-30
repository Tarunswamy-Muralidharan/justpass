package com.justpass.app.ui.screens.qpapers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justpass.app.data.model.PaperCategory
import com.justpass.app.data.model.QPaperContributor
import com.justpass.app.ui.components.GlassCardShape
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.QPaperViewModel
import io.github.fletchmckee.liquid.LiquidState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Contributor-facing list of everything the user has submitted, with the
 * admin's outcome: still pending, now live, or declined (with the reason
 * the admin gave). The reason is mirrored onto the contributor doc — the
 * only QPapers doc the uploader is allowed to read.
 */
@Composable
fun QPaperMyContributionsScreen(
    cardState: LiquidState,
    viewModel: QPaperViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.myContribState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadMyContributions() }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(
            title = "My Contributions",
            subtitle = if (state.items.isNotEmpty()) "${state.items.size} submitted" else null,
            onBack = onBack,
        )

        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                RoseFourLoader(modifier = Modifier.size(44.dp))
            }
            state.items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Nothing here yet.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Papers you contribute will show up here with their review status.",
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
                items(state.items, key = { it.paperId }) { item ->
                    MyContributionCard(item)
                }
            }
        }
    }
}

@Composable
private fun MyContributionCard(item: QPaperContributor) {
    val catLabel = PaperCategory.fromKey(item.category)?.label ?: item.category.uppercase()
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.subjectCode,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "$catLabel · ${item.examYear} · ${item.regulation}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Submitted ${formatDate(item.submittedAt)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                StatusBadge(item)
            }
            // Decline reason, if any.
            if (item.isDeclined && !item.declineReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.10f))
                        .padding(10.dp),
                ) {
                    Text(
                        item.declineReason,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(item: QPaperContributor) {
    val (label, color) = when {
        item.isApproved -> "Live" to Color(0xFF00E676)
        item.isDeclined -> "Declined" to Color(0xFFFF5252)
        else -> "Pending" to Color(0xFFFFB74D)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun formatDate(ts: Long): String {
    if (ts <= 0) return "—"
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts))
}
