package com.justpass.app.ui.screens.qpapers

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.data.model.QPaper
import com.justpass.app.data.model.QPaperContributor
import com.justpass.app.ui.components.GlassCardShape
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.QPaperViewModel
import io.github.fletchmckee.liquid.LiquidState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QPaperAdminScreen(
    cardState: LiquidState,
    viewModel: QPaperViewModel,
    onOpenViewer: (QPaper) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.adminState.collectAsState()
    val context = LocalContext.current

    // PDF picker for the replace-and-approve flow. The currently selected
    // paper is the target — the VM binds to adminState.selectedPaper, so
    // the launcher just needs the bytes.
    val replacePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Read everything in memory. Cloudinary preset enforces 10 MB max
        // server-side; we soft-cap here so an oversize file fails fast
        // instead of after a long upload.
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            Toast.makeText(context, "Couldn't read file", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        if (bytes.size > 10 * 1024 * 1024) {
            Toast.makeText(context, "PDF too large (max 10 MB)", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.replaceAndApproveSelected(bytes)
    }

    LaunchedEffect(Unit) {
        viewModel.loadPending()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(
            title = "Approval Queue",
            subtitle = "${state.pending.size} pending",
            onBack = onBack,
        )

        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                RoseFourLoader(modifier = Modifier.size(48.dp))
            }
            state.pending.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✓", fontSize = 48.sp, color = Color(0xFF00E676))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "All caught up.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Nothing pending review.",
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
                items(state.pending, key = { it.id }) { paper ->
                    val isSelected = state.selectedPaper?.id == paper.id
                    PendingPaperCard(
                        paper = paper,
                        contributor = if (isSelected) state.selectedContributor else null,
                        isExpanded = isSelected,
                        isReplacing = isSelected && state.isReplacing,
                        onExpand = {
                            if (isSelected) viewModel.clearSelection()
                            else viewModel.openPaperForReview(paper)
                        },
                        onPreview = { onOpenViewer(paper) },
                        onReplace = {
                            // Ensure this paper is the VM's selected target
                            // (Replace can be tapped from a non-expanded row
                            // via the expanded one only; defensively re-set).
                            if (!isSelected) viewModel.openPaperForReview(paper)
                            replacePicker.launch("application/pdf")
                        },
                        onApprove = viewModel::approveSelected,
                        onReject = viewModel::rejectSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingPaperCard(
    paper: QPaper,
    contributor: QPaperContributor?,
    isExpanded: Boolean,
    isReplacing: Boolean,
    onExpand: () -> Unit,
    onPreview: () -> Unit,
    onReplace: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassCardShape,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
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
                }
                Text(
                    if (isExpanded) "▾" else "▸",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Column(modifier = Modifier.padding(16.dp)) {
                    if (contributor == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoseFourLoader(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Loading contributor…",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            "Contributor (internal — never shown to viewers)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            contributor.displayName.ifBlank { "—" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Roll: ${contributor.rollNumber}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Submitted: ${formatSubmittedAt(contributor.submittedAt)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    if (isReplacing) {
                        // While the new PDF is uploading + Firestore is being
                        // written, hide the action buttons to prevent double-
                        // taps and show progress. clearSelection() on success
                        // will collapse this row entirely.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RoseFourLoader(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Replacing PDF…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // Row 1: read-only actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onPreview,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preview", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = onReplace,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Replace", fontSize = 13.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Row 2: terminal decisions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onReject,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reject", color = Color.White, fontSize = 13.sp)
                            }
                            Button(
                                onClick = onApprove,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSubmittedAt(ts: Long): String {
    if (ts <= 0) return "—"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))
}
