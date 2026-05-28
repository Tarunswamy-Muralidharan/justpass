package com.justpass.app.ui.screens.qpapers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justpass.app.data.model.UploadIntent
import com.justpass.app.ui.components.GlassCardShape
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.QPaperViewModel
import io.github.fletchmckee.liquid.LiquidState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PDF_BYTES = 10L * 1024 * 1024 // 10 MB

@Composable
fun QPaperUploadScreen(
    cardState: LiquidState,
    intent: UploadIntent,
    viewModel: QPaperViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uploadState.collectAsStateWithLifecycle()

    LaunchedEffect(intent.subjectCode, intent.category, intent.examYear) {
        viewModel.checkAlreadyContributed(intent)
    }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedSize by remember { mutableStateOf<Long?>(null) }
    var showAnonymityDialog by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pickedUri = uri
        localError = null
        // Find file size if available
        val size = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull()
        pickedSize = size
        if (size != null && size > MAX_PDF_BYTES) {
            localError = "File is ${size / 1_000_000} MB — limit is 10 MB."
            pickedUri = null
            pickedSize = null
            return@rememberLauncherForActivityResult
        }
        showAnonymityDialog = true
    }

    LaunchedEffect(state.uploadedPaper) {
        if (state.uploadedPaper != null) onDone()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(
            title = "Contribute ${intent.category.label}",
            subtitle = "${intent.subjectCode} · ${intent.subjectName} · ${intent.examYear}",
            onBack = onBack,
        )

        when {
            state.isCheckingExisting -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                RoseFourLoader(modifier = Modifier.size(40.dp))
            }

            state.alreadyContributed != null -> AlreadyContributedPanel(onDone = onBack)

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnonymityCard()
                Spacer(modifier = Modifier.height(16.dp))

                val sizeMb = pickedSize?.let { it.toFloat() / 1_000_000f }
                if (pickedUri != null && sizeMb != null) {
                    Text(
                        "Picked: ${"%.1f".format(sizeMb)} MB",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                localError?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                state.errorMessage?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                    enabled = !state.isUploading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (pickedUri == null) "Pick PDF" else "Pick different PDF", fontWeight = FontWeight.SemiBold)
                }

                if (state.isUploading) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoseFourLoader(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Uploading…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showAnonymityDialog && pickedUri != null) {
        AnonymityConfirmDialog(
            onConfirm = {
                showAnonymityDialog = false
                val uri = pickedUri ?: return@AnonymityConfirmDialog
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }.getOrNull()
                    }
                    if (bytes == null || bytes.isEmpty()) {
                        localError = "Couldn't read the file."
                        return@launch
                    }
                    if (bytes.size > MAX_PDF_BYTES) {
                        localError = "File is too large (max 10 MB)."
                        return@launch
                    }
                    viewModel.upload(bytes, intent)
                }
            },
            onDismiss = {
                showAnonymityDialog = false
                pickedUri = null
                pickedSize = null
            },
        )
    }
}

@Composable
private fun AnonymityCard() {
    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassCardShapeSmall,
        tintColor = Color(0xFF00E676).copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF00E676),
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Anonymous to everyone except admin",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Your roll number won't be shown on the paper or attached to it for any viewer.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AnonymityConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        title = {
            Text(
                "Submit anonymously?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                "Your contribution will be reviewed by an admin before it goes live. " +
                    "Your roll number and name are stored internally only — they won't " +
                    "appear on the paper or be visible to anyone viewing it.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Submit", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Cancel", color = Color.White)
            }
        },
    )
}

@Composable
private fun AlreadyContributedPanel(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF00E676),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Already contributed — thanks!",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You've already submitted a paper for this exact slot. " +
                "Other students can still contribute their copy.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onDone, shape = RoundedCornerShape(10.dp)) {
            Text("Back to papers")
        }
    }
}

// ─── Thank-you screen ──────────────────────────────────────────────────

@Composable
fun QPaperThankYouScreen(
    cardState: LiquidState,
    viewModel: QPaperViewModel,
    onPickGap: (UploadIntent) -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.uploadState.collectAsStateWithLifecycle()
    val gaps = state.gapsForFollowUp

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(title = "Thanks!", onBack = onDone)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            GlassListCard(
                modifier = Modifier.fillMaxWidth(),
                shape = GlassCardShape,
                tintColor = Color(0xFF00E676).copy(alpha = 0.10f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Paper submitted!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Goes live after admin verification (usually within a day).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (gaps.isNotEmpty()) {
                Text(
                    "Want to help with more?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Papers you haven't contributed yet for this semester:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (gaps.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(gaps, key = { "${it.subjectCode}_${it.category.key}_${it.examYear}" }) { gap ->
                    GapCard(gap, onClick = { onPickGap(gap) })
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("I'm done — back to papers", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Back to papers", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GapCard(gap: UploadIntent, onClick: () -> Unit) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = GlassCardShapeSmall,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    gap.category.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    gap.subjectCode,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    gap.subjectName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "Help →",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
