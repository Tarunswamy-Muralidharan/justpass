package com.justpass.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.R
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.ExamSeatViewModel
import io.github.fletchmckee.liquid.LiquidState

@Composable
fun ExamSeatScreen(
    cardState: LiquidState,
    onBack: () -> Unit,
    sharedFileUri: Uri? = null,
    viewModel: ExamSeatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Handle file shared from outside the app
    LaunchedEffect(sharedFileUri) {
        if (sharedFileUri != null) {
            viewModel.processFileUri(sharedFileUri)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processFileUri(uri)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)
    ) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Exams", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.examSeat != null) {
                // Show seat info
                val seat = uiState.examSeat!!
                GlassListCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GlassCardShapeSmall,
                    tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Your Exam Seat", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (seat.date.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(seat.date, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center)
                        }
                        if (seat.examName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(seat.examName, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center, maxLines = 2)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(seat.hall, fontSize = 36.sp, fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary)
                                Text("Hall", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(seat.seatNumber, fontSize = 36.sp, fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E676))
                                Text("Seat", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Import timestamp
                if (uiState.importTimestamp != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Imported: ${uiState.importTimestamp}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Open Excel file externally
                    val context = LocalContext.current
                    GlassListCard(
                        modifier = Modifier.weight(1f),
                        shape = GlassCardShapeSmall
                    ) {
                        TextButton(
                            onClick = {
                                uiState.fileUri?.let { uri ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Open Excel file"))
                                    } catch (_: Exception) {
                                        // No app to open Excel — show in-app fallback
                                        viewModel.toggleRawData()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        ) {
                            Icon(
                                Icons.Default.TableChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View File", maxLines = 1)
                        }
                    }

                    // Import another file button
                    GlassListCard(
                        modifier = Modifier.weight(1f),
                        shape = GlassCardShapeSmall
                    ) {
                        TextButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "application/pdf"
                                ))
                            },
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import New", maxLines = 1)
                        }
                    }
                }

                // Raw data table
                if (uiState.showRawData && uiState.allRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    GlassListCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassCardShapeSmall
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        ) {
                            Text(
                                "Excel Data (first 100 rows)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Horizontally scrollable table
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Column {
                                    uiState.allRows.forEachIndexed { index, row ->
                                        val isHeader = index == 0
                                        Row {
                                            row.forEach { cell ->
                                                Text(
                                                    text = cell.ifBlank { "-" },
                                                    fontSize = if (isHeader) 11.sp else 10.sp,
                                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isHeader)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                                    modifier = Modifier
                                                        .widthIn(min = 80.dp)
                                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (isHeader) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (uiState.isSearching) {
                // Loading
                GlassListCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GlassCardShapeSmall
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RoseFourLoader(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Reading file...", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Import card
                GlassListCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GlassCardShapeSmall
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Import Exam File", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Share the seating Excel or timetable PDF from your email",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "application/pdf"
                                ))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Import File", fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                        }
                    }
                }
            }

            // Error message
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                GlassListCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GlassCardShapeSmall,
                    tintColor = Color(0xFFFF5252).copy(alpha = 0.08f)
                ) {
                    Text(
                        uiState.errorMessage!!,
                        fontSize = 13.sp,
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // --- How to use guide ---
            Spacer(modifier = Modifier.height(24.dp))

            Text("How to use", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp))
            Text("Share the Excel or PDF from your exam email directly to this app",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))

            Spacer(modifier = Modifier.height(12.dp))

            // Step 1
            GuideStep(
                stepNumber = 1,
                title = "Open the exam email & tap the attachment",
                description = "Excel file for seating — from PSGiTECH Examcell",
                imageRes = R.drawable.guide_exam_email,
                highlightFraction = Offset(0.30f, 0.85f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2
            GuideStep(
                stepNumber = 2,
                title = "Select \"Open with JustPass\"",
                description = "Seat finder for Excel, timetable parser for PDF — instant results",
                imageRes = R.drawable.guide_exam_open_with,
                highlightFraction = Offset(0.40f, 0.43f)
            )

            Spacer(modifier = Modifier.height(160.dp)) // bottom padding for nav bar
        }
    }
}

@Composable
private fun GuideStep(
    stepNumber: Int,
    title: String,
    description: String,
    imageRes: Int,
    highlightFraction: Offset
) {
    val accentColor = MaterialTheme.colorScheme.primary

    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassCardShapeSmall
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Step header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp)
                        .background(accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stepNumber.toString(), fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(description, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Screenshot with single circle highlight (no arrows)
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp))
            ) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = "Step $stepNumber",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}
