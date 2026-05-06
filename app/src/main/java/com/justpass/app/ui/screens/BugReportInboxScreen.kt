package com.justpass.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.data.model.BugReport
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.viewmodel.BugReportViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportInboxScreen(
    onBack: () -> Unit,
    viewModel: BugReportViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(state.isAdmin) {
        if (state.isAdmin) viewModel.startListeningInbox()
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopListeningInbox() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bug Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        if (!state.isAdmin) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Admin only", color = Color(0xFF90A4AE))
            }
            return@Scaffold
        }

        if (state.reports.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No reports yet", color = Color(0xFF90A4AE))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.reports) { r ->
                ReportCard(
                    r = r,
                    onOpenImage = {
                        if (r.imageUrl.isNotBlank()) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.imageUrl)))
                        }
                    },
                    onMarkFixed = { viewModel.setReportStatus(r.id, "fixed", "") },
                    onMarkWontFix = { viewModel.setReportStatus(r.id, "wontfix", "") }
                )
            }
        }
    }
}

@Composable
private fun ReportCard(
    r: BugReport,
    onOpenImage: () -> Unit,
    onMarkFixed: () -> Unit,
    onMarkWontFix: () -> Unit
) {
    val tint = when (r.status) {
        "fixed" -> Color(0xFF00E676).copy(alpha = 0.10f)
        "wontfix", "duplicate" -> Color(0xFF90A4AE).copy(alpha = 0.10f)
        else -> Color(0xFFFF5252).copy(alpha = 0.10f)
    }
    val date = remember(r.createdAt) {
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(r.createdAt))
    }

    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), tintColor = tint) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = Color.White, modifier = Modifier.weight(1f))
                StatusBadge(r.status)
            }
            Text(date, fontSize = 11.sp, color = Color(0xFF90A4AE))
            Text(r.description, fontSize = 13.sp, color = Color(0xFFCFD8DC))

            if (r.imageUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = onOpenImage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open screenshot", fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = Color(0xFF263238))
            Text("Reporter", fontSize = 11.sp, color = Color(0xFF607D8B))
            Text(r.reporterName.ifBlank { "(unknown)" }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${r.reporterRollNumber.ifBlank { "—" }} · ${r.reporterDepartment.ifBlank { "—" }}",
                fontSize = 11.sp, color = Color(0xFFB0BEC5)
            )
            Text(
                "${r.deviceModel} · ${r.osVersion} · v${r.appVersion}",
                fontSize = 10.sp, color = Color(0xFF607D8B)
            )

            if (r.status == "open") {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onMarkFixed,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("Fixed", color = Color.Black, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onMarkWontFix, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Cancel, null, Modifier.size(14.dp), tint = Color(0xFF90A4AE))
                        Spacer(Modifier.width(4.dp))
                        Text("Won't fix", fontSize = 12.sp, color = Color(0xFF90A4AE))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        "fixed" -> "FIXED" to Color(0xFF00E676)
        "wontfix" -> "WONTFIX" to Color(0xFF90A4AE)
        "duplicate" -> "DUP" to Color(0xFF90A4AE)
        else -> "OPEN" to Color(0xFFFF5252)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
