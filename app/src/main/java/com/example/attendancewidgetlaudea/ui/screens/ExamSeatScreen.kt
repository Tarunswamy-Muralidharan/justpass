package com.example.attendancewidgetlaudea.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.R
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.RoseFourLoader
import com.example.attendancewidgetlaudea.ui.viewmodel.ExamSeatViewModel
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

                Spacer(modifier = Modifier.height(16.dp))

                // Import another file button
                GlassListCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GlassCardShapeSmall
                ) {
                    TextButton(
                        onClick = {
                            viewModel.clearState()
                            filePickerLauncher.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/pdf"
                            ))
                        },
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Text("Import another Excel or PDF")
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
                arrowFromFraction = Offset(0.30f, 0.75f),
                arrowToFraction = Offset(0.30f, 0.85f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2
            GuideStep(
                stepNumber = 2,
                title = "Select \"Open with JustPass\"",
                description = "Seat finder for Excel, timetable parser for PDF — instant results",
                imageRes = R.drawable.guide_exam_open_with,
                arrowFromFraction = Offset(0.40f, 0.33f),
                arrowToFraction = Offset(0.40f, 0.43f)
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
    arrowFromFraction: Offset,
    arrowToFraction: Offset
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

            // Screenshot with arrow overlay
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp))
            ) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = "Step $stepNumber",
                    modifier = Modifier.fillMaxWidth()
                        .drawWithContent {
                            drawContent()

                            val fromX = size.width * arrowFromFraction.x
                            val fromY = size.height * arrowFromFraction.y
                            val toX = size.width * arrowToFraction.x
                            val toY = size.height * arrowToFraction.y

                            // Draw arrow line
                            drawLine(
                                color = Color(0xFFFF5252),
                                start = Offset(fromX, fromY),
                                end = Offset(toX, toY),
                                strokeWidth = 6f
                            )

                            // Draw arrowhead
                            val arrowSize = 24f
                            val dx = toX - fromX
                            val dy = toY - fromY
                            val len = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (len > 0) {
                                val ux = dx / len
                                val uy = dy / len
                                val path = Path().apply {
                                    moveTo(toX, toY)
                                    lineTo(toX - arrowSize * ux + arrowSize * 0.5f * uy,
                                        toY - arrowSize * uy - arrowSize * 0.5f * ux)
                                    lineTo(toX - arrowSize * ux - arrowSize * 0.5f * uy,
                                        toY - arrowSize * uy + arrowSize * 0.5f * ux)
                                    close()
                                }
                                drawPath(path, Color(0xFFFF5252))
                            }

                            // Draw a highlight circle at target
                            drawCircle(
                                color = Color(0xFFFF5252).copy(alpha = 0.25f),
                                radius = 50f,
                                center = Offset(toX, toY)
                            )
                            drawCircle(
                                color = Color(0xFFFF5252),
                                radius = 50f,
                                center = Offset(toX, toY),
                                style = Stroke(width = 4f)
                            )
                        },
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}
