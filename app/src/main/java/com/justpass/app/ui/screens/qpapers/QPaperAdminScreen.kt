package com.justpass.app.ui.screens.qpapers

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justpass.app.data.model.DeclineKind
import com.justpass.app.data.model.QPaper
import com.justpass.app.data.model.QPaperContributor
import com.justpass.app.ui.components.GlassCardShape
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
    onApproveAndPlace: (QPaper) -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.adminState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Surface download outcomes as a toast, then clear so it fires once.
    LaunchedEffect(state.downloadMessage) {
        state.downloadMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeDownloadMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadPending()
    }

    // Decline-reason dialog target (null = closed).
    var decliningPaper by remember { mutableStateOf<QPaper?>(null) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(
            title = "Approval Queue",
            subtitle = "${state.pending.size} pending",
            onBack = onBack,
            trailing = {
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "History",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
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
                        isDownloading = isSelected && state.isDownloading,
                        onExpand = {
                            if (isSelected) viewModel.clearSelection()
                            else viewModel.openPaperForReview(paper)
                        },
                        onPreview = { onOpenViewer(paper) },
                        onDownload = {
                            if (!isSelected) viewModel.openPaperForReview(paper)
                            viewModel.downloadSelectedToDevice()
                        },
                        onDecline = {
                            if (!isSelected) viewModel.openPaperForReview(paper)
                            decliningPaper = paper
                        },
                        onApproveAndPlace = {
                            if (!isSelected) viewModel.openPaperForReview(paper)
                            onApproveAndPlace(paper)
                        },
                    )
                }
            }
        }
    }

    decliningPaper?.let { paper ->
        DeclineDialog(
            onDismiss = { decliningPaper = null },
            onConfirm = { kind, message ->
                // Make sure the VM has this paper selected before declining.
                if (state.selectedPaper?.id != paper.id) viewModel.openPaperForReview(paper)
                viewModel.declineSelected(kind, message)
                decliningPaper = null
            },
        )
    }
}

@Composable
private fun PendingPaperCard(
    paper: QPaper,
    contributor: QPaperContributor?,
    isExpanded: Boolean,
    isDownloading: Boolean,
    onExpand: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onDecline: () -> Unit,
    onApproveAndPlace: () -> Unit,
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

                    // Row 1: examine — preview in-app or download to edit.
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
                            onClick = onDownload,
                            enabled = !isDownloading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            if (isDownloading) {
                                RoseFourLoader(modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isDownloading) "Saving…" else "Download", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Row 2: decisions.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onDecline,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Decline", color = Color.White, fontSize = 13.sp)
                        }
                        Button(
                            onClick = onApproveAndPlace,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Approve & Place", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Decline-reason picker. Three canned reasons; "Custom message" reveals a
 * free-text field whose contents are sent to the contributor verbatim.
 */
@Composable
private fun DeclineDialog(
    onDismiss: () -> Unit,
    onConfirm: (DeclineKind, String) -> Unit,
) {
    var customMode by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Text(
                if (customMode) "Custom message" else "Decline — why?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (customMode) {
                Column {
                    Text(
                        "This message is shown to the contributor in My Contributions.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type your message…") },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                }
            } else {
                Column {
                    DeclineOptionRow(DeclineKind.ALREADY_RECEIVED.adminLabel) {
                        onConfirm(DeclineKind.ALREADY_RECEIVED, "")
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    DeclineOptionRow(DeclineKind.INVALID.adminLabel) {
                        onConfirm(DeclineKind.INVALID, "")
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    DeclineOptionRow(DeclineKind.CUSTOM.adminLabel) { customMode = true }
                }
            }
        },
        confirmButton = {
            if (customMode) {
                Button(
                    onClick = { onConfirm(DeclineKind.CUSTOM, customText) },
                    enabled = customText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Send & decline", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { if (customMode) customMode = false else onDismiss() },
                shape = RoundedCornerShape(10.dp),
            ) { Text(if (customMode) "Back" else "Cancel", color = Color.White) }
        },
    )
}

@Composable
private fun DeclineOptionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        color = Color.White,
        fontSize = 14.sp,
    )
}

private fun formatSubmittedAt(ts: Long): String {
    if (ts <= 0) return "—"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))
}
