package com.justpass.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.ui.components.DistributionHistogram
import com.justpass.app.ui.components.GlassCardShape
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.PercentileGauge
import com.justpass.app.ui.components.SubjectBar
import com.justpass.app.ui.viewmodel.ClassRanksViewModel
import io.github.fletchmckee.liquid.LiquidState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassCompareScreen(
    cardState: LiquidState,
    onBack: () -> Unit,
    viewModel: ClassRanksViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Class Comparison") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        when (val s = state) {
            is ClassRanksViewModel.State.Loading -> LoadingContent()
            is ClassRanksViewModel.State.Missing -> MessageContent(s.reason)
            is ClassRanksViewModel.State.Error -> MessageContent(s.message)
            is ClassRanksViewModel.State.Ready -> {
                if (s.stats.studentCount < MIN_SHOW) {
                    val remaining = MIN_SHOW - s.stats.studentCount
                    MessageContent(
                        "Need $remaining more classmates from ${s.classKey.replace('_', ' ')} to start comparing."
                    )
                } else {
                    ReadyContent(
                        classKey = s.classKey,
                        viewModel = viewModel,
                        showDeleteConfirm = showDeleteConfirm,
                        onShowDelete = { showDeleteConfirm = it },
                    )
                }
            }
        }
    }
}

private const val MIN_SHOW = 15

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessageContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadyContent(
    classKey: String,
    viewModel: ClassRanksViewModel,
    showDeleteConfirm: Boolean,
    onShowDelete: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val ready = state as? ClassRanksViewModel.State.Ready ?: return
    val stats = ready.stats
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Header line — class + student count
        Text(
            text = classKey.replace('_', ' '),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${stats.studentCount} students sharing anonymously",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Overall card — percentile gauge + rank
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Where you stand overall",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PercentileGauge(percentile = stats.yourPercentile ?: 0)
                Text(
                    text = if (stats.yourRank != null && stats.yourPercentile != null)
                        "Rank ${stats.yourRank} / ${stats.studentCount}  ·  ${stats.yourPercentile}th percentile"
                    else "Your overall is being computed.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Class min ${stats.overall.min.toInt()}  ·  avg ${stats.overall.avg.toInt()}  ·  max ${stats.overall.max.toInt()}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                DistributionHistogram(
                    histogram = stats.overallHistogram,
                    yourPercentile = stats.yourPercentile,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Per subject",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Per-subject bars
        GlassListCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (stats.subjects.isEmpty()) {
                    Text(
                        text = "No subject data yet — open CA Marks and refresh.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    stats.subjects.entries.forEachIndexed { idx, (code, sub) ->
                        SubjectBar(
                            label = code,
                            yourMark = sub.yourMark,
                            avg = sub.avg,
                            min = sub.min,
                            max = sub.max,
                            histogram = sub.histogram,
                        )
                        if (idx < stats.subjects.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(
            onClick = { onShowDelete(true) },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                "Delete my data",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { onShowDelete(false) },
            title = { Text("Delete my data?") },
            text = {
                Text(
                    "Your anonymized marks will be removed from the class server. " +
                    "Re-opening CA Marks will re-upload them again next sync."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onShowDelete(false)
                    viewModel.deleteMyData { ok ->
                        android.widget.Toast.makeText(
                            context,
                            if (ok) "Deleted" else "Delete failed",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { onShowDelete(false) }) { Text("Cancel") }
            },
            containerColor = Color(0xFF1E2A3A),
        )
    }
}
