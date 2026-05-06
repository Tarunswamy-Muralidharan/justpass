package com.justpass.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.justpass.app.ui.viewmodel.BugReportViewModel
import com.justpass.app.ui.viewmodel.BugSubmitStep

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report a Bug", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.submitStep == BugSubmitStep.SUBMITTED) {
                SubmittedCard(onDone = onBack)
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
