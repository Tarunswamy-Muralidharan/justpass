package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.AiChatMessage
import com.example.attendancewidgetlaudea.data.model.NavAction
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.RoseFourLoader
import com.example.attendancewidgetlaudea.ui.viewmodel.LiteRtState
import com.example.attendancewidgetlaudea.ui.viewmodel.LiteRtViewModel

private val LiteRtBlue = Color(0xFF4285F4)  // Google blue
private val LiteRtGreen = Color(0xFF34A853)
private val LiteRtGlow = Color(0xFF8AB4F8)

@Composable
fun LiteRtScreen(
    onBack: () -> Unit,
    onNavigate: (NavAction) -> Unit = {},
    viewModel: LiteRtViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll
    LaunchedEffect(uiState.messages.size, uiState.isGenerating, uiState.streamingText) {
        val target = uiState.messages.size - 1 + if (uiState.isGenerating) 1 else 0
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // ── Header ──
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(20.dp))
                }
                Icon(Icons.Default.School, "Advisor", modifier = Modifier.size(22.dp), tint = LiteRtBlue)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chat Advisor", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.state == LiteRtState.READY) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(LiteRtGreen))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            when (uiState.state) {
                                LiteRtState.READY -> if (uiState.useGpu) "GPU · On-device" else "CPU · On-device"
                                LiteRtState.DOWNLOADING -> "Downloading ${uiState.downloadProgress}%"
                                LiteRtState.LOADING -> "Loading..."
                                LiteRtState.ERROR -> "Error"
                                LiteRtState.NOT_DOWNLOADED -> "Setup required"
                            },
                            fontSize = 11.sp,
                            color = if (uiState.state == LiteRtState.READY) LiteRtGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (uiState.state == LiteRtState.READY) {
                    GlassListCard(
                        modifier = Modifier.clickable {
                            if (uiState.isHighEndDevice) viewModel.toggleGpu(!uiState.useGpu)
                        },
                        shape = RoundedCornerShape(8.dp),
                        tintColor = if (uiState.useGpu) LiteRtGreen.copy(alpha = 0.10f)
                            else LiteRtBlue.copy(alpha = 0.08f)
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, null, Modifier.size(12.dp),
                                tint = if (uiState.useGpu) LiteRtGreen else LiteRtGlow)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                if (uiState.useGpu) "GPU" else "CPU",
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = if (uiState.useGpu) LiteRtGreen else LiteRtGlow,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }

        // ── GPU suggestion for high-end devices ──
        if (uiState.showGpuSuggestion) {
            GlassListCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                tintColor = Color(0xFF00E676).copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speed, null, Modifier.size(18.dp), tint = LiteRtGreen)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GPU available", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        Text("Switch to GPU for faster responses", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    TextButton(onClick = {
                        viewModel.toggleGpu(true)
                        viewModel.dismissGpuSuggestion()
                    }) {
                        Text("Switch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LiteRtGreen)
                    }
                    IconButton(onClick = { viewModel.dismissGpuSuggestion() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // ── Content ──
        when (uiState.state) {
            LiteRtState.NOT_DOWNLOADED -> {
                val model = uiState.models.firstOrNull()
                val isDownloaded = model != null && java.io.File(viewModel.modelDir, model.fileName).exists()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassListCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.School, "Advisor", modifier = Modifier.size(44.dp), tint = LiteRtBlue)
                            Spacer(Modifier.height(14.dp))
                            Text("Chat Advisor", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(6.dp))
                            Text("On-device AI advisor for attendance,\nmarks, bunking & academics.",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center, lineHeight = 18.sp)
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.downloadAndLoad() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDownloaded) LiteRtGreen else LiteRtBlue),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Icon(if (isDownloaded) Icons.AutoMirrored.Filled.Send else Icons.Default.Download,
                                    null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isDownloaded) "Start Chat"
                                    else "Download (~${model?.sizeMb ?: "?"} MB)",
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("One-time download · Works offline after", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            LiteRtState.DOWNLOADING -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassListCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Downloading Model", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                            if (uiState.errorMessage != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(uiState.errorMessage!!, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { uiState.downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = LiteRtBlue,
                                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                            )
                            Spacer(Modifier.height(10.dp))
                            // Size progress: "245 MB / 2464 MB"
                            if (uiState.totalMb > 0) {
                                Text(
                                    "${uiState.downloadedMb} MB / ${uiState.totalMb} MB",
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LiteRtBlue
                                )
                            } else {
                                Text("${uiState.downloadProgress}%", fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold, color = LiteRtBlue)
                            }
                            Spacer(Modifier.height(4.dp))
                            // Speed + ETA
                            if (uiState.downloadSpeedKbps > 0) {
                                val speedText = if (uiState.downloadSpeedKbps >= 1024)
                                    "${"%.1f".format(uiState.downloadSpeedKbps / 1024f)} MB/s"
                                else "${uiState.downloadSpeedKbps} KB/s"
                                val etaText = when {
                                    uiState.downloadEtaSeconds >= 3600 -> "${uiState.downloadEtaSeconds / 3600}h ${(uiState.downloadEtaSeconds % 3600) / 60}m left"
                                    uiState.downloadEtaSeconds >= 60 -> "${uiState.downloadEtaSeconds / 60}m ${uiState.downloadEtaSeconds % 60}s left"
                                    uiState.downloadEtaSeconds > 0 -> "${uiState.downloadEtaSeconds}s left"
                                    else -> ""
                                }
                                Text(
                                    "$speedText  •  $etaText",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.cancelDownload() }) {
                                Text("Cancel", fontSize = 13.sp, color = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            }
            LiteRtState.LOADING -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RoseFourLoader(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Initializing Chat Advisor...", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("This may take up to 10 seconds", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
            LiteRtState.ERROR -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        tintColor = Color(0xFFFF5252).copy(alpha = 0.06f)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Something went wrong", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFF5252))
                            Spacer(Modifier.height(4.dp))
                            Text(uiState.errorMessage ?: "Unknown error", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { viewModel.downloadAndLoad() },
                                colors = ButtonDefaults.buttonColors(containerColor = LiteRtBlue),
                                shape = RoundedCornerShape(12.dp)) { Text("Retry") }
                        }
                    }
                }
            }
            LiteRtState.READY -> {
                // ── Chat feed ──
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.messages, key = { it.timestamp }) { message ->
                        if (message.role == "user") {
                            LiteRtUserCard(message.content)
                        } else {
                            LiteRtResponseCard(
                                content = message.content,
                                navAction = message.navAction,
                                onNavigate = onNavigate
                            )
                        }
                    }
                    // Streaming response
                    if (uiState.isGenerating && uiState.streamingText.isNotBlank()) {
                        item { LiteRtResponseCard(uiState.streamingText, isStreaming = true) }
                    } else if (uiState.isGenerating) {
                        item { LiteRtThinkingCard() }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Input bar ──
                GlassListCard(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 90.dp),
                    shape = RoundedCornerShape(20.dp),
                    tintColor = if (inputText.isNotBlank()) LiteRtBlue.copy(alpha = 0.04f) else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ask me anything...", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (inputText.isNotBlank() && !uiState.isGenerating) {
                                    viewModel.sendMessage(inputText.trim()); inputText = ""
                                }
                            }),
                            maxLines = 3,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                        val canSend = inputText.isNotBlank() && !uiState.isGenerating
                        IconButton(
                            onClick = { if (canSend) { viewModel.sendMessage(inputText.trim()); inputText = "" } },
                            enabled = canSend,
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                .then(if (canSend) Modifier.background(LiteRtBlue) else Modifier)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", Modifier.size(18.dp),
                                tint = if (canSend) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiteRtUserCard(content: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.78f).dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF3367D6), LiteRtBlue)))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(content, fontSize = 14.sp, lineHeight = 20.sp, color = Color.White)
        }
    }
}

@Composable
private fun LiteRtResponseCard(
    content: String,
    isStreaming: Boolean = false,
    navAction: NavAction? = null,
    onNavigate: (NavAction) -> Unit = {}
) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tintColor = LiteRtBlue.copy(alpha = 0.03f)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 40.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(LiteRtBlue, LiteRtGlow)))
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(content, fontSize = 14.sp, lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                if (isStreaming) {
                    val transition = rememberInfiniteTransition(label = "cursor")
                    val alpha by transition.animateFloat(
                        initialValue = 0f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                        label = "blink"
                    )
                    Text("▌", fontSize = 14.sp, color = LiteRtBlue.copy(alpha = alpha))
                }
                // ── Nav action chip ──
                if (navAction != null && !isStreaming) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(LiteRtBlue.copy(alpha = 0.10f))
                            .clickable { onNavigate(navAction) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(navAction.icon, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("Open ${navAction.label}", fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = LiteRtBlue)
                            Spacer(Modifier.width(4.dp))
                            Text("→", fontSize = 13.sp, color = LiteRtBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiteRtThinkingCard() {
    val transition = rememberInfiniteTransition(label = "dots")
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        tintColor = LiteRtBlue.copy(alpha = 0.03f)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(LiteRtBlue, LiteRtGlow))))
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { i ->
                    val dotAlpha by transition.animateFloat(
                        initialValue = 0.15f, targetValue = 0.8f,
                        animationSpec = infiniteRepeatable(tween(700, delayMillis = i * 180), RepeatMode.Reverse),
                        label = "dot$i")
                    Box(Modifier.size(5.dp).clip(CircleShape).graphicsLayer { alpha = dotAlpha }
                        .background(LiteRtBlue))
                }
                Spacer(Modifier.width(6.dp))
                Text("Thinking", fontSize = 12.sp, fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

