package com.justpass.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.data.model.BugReport
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.viewmodel.BugReportViewModel
import com.justpass.app.ui.viewmodel.BugSubmitStep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportScreen(
    onBack: () -> Unit,
    viewModel: BugReportViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ctx = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> viewModel.setImage(uri) }

    // Decode the picked URI to a Bitmap for preview. Re-decoded whenever
    // the URI changes; null while no image is selected.
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(state.imageUri) {
        preview = state.imageUri?.let { uri ->
            try {
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Live-listen reporter's own reports while on this screen so the
    // "My Reports" tab always shows fresh admin replies + status changes.
    LaunchedEffect(state.reporterPlayerId) {
        if (state.reporterPlayerId.isNotBlank()) viewModel.startListeningMine()
    }
    DisposableEffect(Unit) { onDispose { viewModel.stopListeningMine() } }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Report a Bug", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Submit") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("My Reports")
                                if (state.myReports.isNotEmpty()) {
                                    Spacer(Modifier.width(4.dp))
                                    Badge { Text("${state.myReports.size}") }
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        if (selectedTab == 1) {
            MyReportsTab(
                reports = state.myReports,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.submitStep == BugSubmitStep.SUBMITTED) {
                SubmittedCard(onDone = { selectedTab = 1; viewModel.resetForm() })
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, null, tint = Color(0xFFFF5252))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tell me what's broken or what you'd like to see. I read every report.",
                    fontSize = 12.sp, color = Color(0xFFB0BEC5)
                )
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("What happened? (short)") },
                placeholder = { Text("e.g. Chess crash on rapid 10+0") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Details") },
                placeholder = { Text("Steps to reproduce, what you expected, what happened instead") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                minLines = 4, maxLines = 10
            )

            // Image picker
            if (state.imageUri == null) {
                OutlinedButton(
                    onClick = {
                        pickImage.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        ))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Attach a screenshot (optional)")
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    preview?.let { bm ->
                        Image(
                            bitmap = bm.asImageBitmap(),
                            contentDescription = "Selected screenshot",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setImage(null) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, "Remove image", tint = Color.White)
                    }
                }
            }

            Text(
                "Sent with: device model, app version, your name + roll. Helps me reproduce.",
                fontSize = 11.sp, color = Color(0xFF607D8B)
            )

            state.errorMessage?.let { msg ->
                Text(msg, color = Color(0xFFFF5252), fontSize = 13.sp)
            }

            when (state.submitStep) {
                BugSubmitStep.IDLE, BugSubmitStep.FAILED -> {
                    Button(
                        onClick = viewModel::submit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.title.isNotBlank() && state.description.isNotBlank()
                    ) {
                        Text("Submit")
                    }
                }
                BugSubmitStep.SUBMITTING -> {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.imageUri != null) "Uploading…" else "Submitting…")
                    }
                }
                BugSubmitStep.SUBMITTED -> { /* handled */ }
            }
        }
    }
}

@Composable
private fun MyReportsTab(reports: List<BugReport>, modifier: Modifier = Modifier) {
    if (reports.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BugReport, null, Modifier.size(40.dp), tint = Color(0xFF607D8B))
                Spacer(Modifier.height(8.dp))
                Text("No reports yet", color = Color(0xFF90A4AE), fontSize = 14.sp)
                Text("Submit one and replies will appear here.",
                    color = Color(0xFF607D8B), fontSize = 12.sp)
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(reports) { r -> MyReportCard(r) }
    }
}

@Composable
private fun MyReportCard(r: BugReport) {
    val tint = when (r.status) {
        "fixed" -> Color(0xFF00E676).copy(alpha = 0.10f)
        "wontfix", "duplicate" -> Color(0xFF90A4AE).copy(alpha = 0.10f)
        else -> Color(0xFFFF5252).copy(alpha = 0.10f)
    }
    val statusLabel = when (r.status) {
        "fixed" -> "FIXED" to Color(0xFF00E676)
        "wontfix" -> "WONTFIX" to Color(0xFF90A4AE)
        "duplicate" -> "DUP" to Color(0xFF90A4AE)
        else -> "OPEN" to Color(0xFFFF5252)
    }
    val date = remember(r.createdAt) {
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(r.createdAt))
    }
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), tintColor = tint) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = Color.White, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusLabel.second.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(statusLabel.first, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, color = statusLabel.second)
                }
            }
            Text(date, fontSize = 11.sp, color = Color(0xFF90A4AE))
            Text(r.description, fontSize = 13.sp, color = Color(0xFFCFD8DC))

            if (r.adminReply.isNotBlank()) {
                val replyDate = remember(r.repliedAt) {
                    if (r.repliedAt > 0L)
                        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(r.repliedAt))
                    else ""
                }
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2A3A))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(12.dp),
                                tint = Color(0xFF64B5F6))
                            Spacer(Modifier.width(4.dp))
                            Text("Reply from developer", fontSize = 10.sp,
                                color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
                            if (replyDate.isNotBlank()) {
                                Spacer(Modifier.weight(1f))
                                Text(replyDate, fontSize = 10.sp, color = Color(0xFF607D8B))
                            }
                        }
                        Text(r.adminReply, fontSize = 12.sp, color = Color(0xFFCFD8DC))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmittedCard(onDone: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2A3A))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = Color(0xFF00E676))
            Text("Thanks!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Text(
                "Got your report. I'll look at it soon.",
                fontSize = 13.sp, color = Color(0xFFB0BEC5)
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}
