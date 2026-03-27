package com.example.attendancewidgetlaudea.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
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
                Text("Exam Seat Finder", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                    if (seat.examName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(seat.examName, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, maxLines = 2)
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
                            "application/vnd.ms-excel"
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Text("Import another file")
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Reading Excel file...", fontSize = 14.sp,
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
                    Text("Find My CA Seat", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Open the seating arrangement Excel file from your email to find your seat",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel"
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Import Excel File", fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Tip: You can also share the Excel file directly from Gmail to this app",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
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
    }
}
