package com.example.attendancewidgetlaudea.ui.screens

import com.example.attendancewidgetlaudea.ui.components.AdBanner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.Exemption
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.GlassListSurface
import com.example.attendancewidgetlaudea.ui.components.RoseFourLoader

import com.example.attendancewidgetlaudea.ui.viewmodel.ExemptionsViewModel
import io.github.fletchmckee.liquid.LiquidState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ExemptionsScreen(
    cardState: LiquidState,
    viewModel: ExemptionsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = com.example.attendancewidgetlaudea.ui.components.GlassCardShape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface) }
                Text("Exemptions", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.fetchExemptions() }, enabled = !uiState.isLoading) {
                    Icon(Icons.Default.Refresh, "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        AdBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), screenName = "Exemptions")

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.exemptions.isEmpty() -> {
                    RoseFourLoader(modifier = Modifier.size(48.dp).align(Alignment.Center))
                }
                uiState.errorMessage != null && uiState.exemptions.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.errorMessage ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.fetchExemptions() }) { Text("Retry") }
                    }
                }
                uiState.exemptions.isEmpty() -> {
                    Text(
                        "No exemptions found",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    val sorted = remember(uiState.exemptions) {
                        uiState.exemptions.sortedByDescending { it.fromDate }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sorted) { exemption -> ExemptionCard(exemption) }
                    }
                }
            }

            if (uiState.isLoading && uiState.exemptions.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            }
        }
    }
}

@Composable
private fun ExemptionCard(exemption: Exemption) {
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Top row: type + category badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    exemption.exemptionType,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                GlassListSurface(
                    shape = RoundedCornerShape(6.dp),
                    tintColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                ) {
                    Text(
                        exemption.category,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date range
            Text(
                formatDateRange(exemption.fromDate, exemption.toDate),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Sessions or Full Day
            if (!exemption.sessions.isNullOrEmpty()) {
                Text(
                    exemption.sessions.joinToString("\n"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    "Full Day",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Reason
            Text(
                exemption.reason,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status badge
            if (exemption.status == "V") {
                GlassListSurface(
                    shape = RoundedCornerShape(6.dp),
                    tintColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                ) {
                    Text(
                        "Verified",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                GlassListSurface(
                    shape = RoundedCornerShape(6.dp),
                    tintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                ) {
                    Text(
                        exemption.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val displayDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

private fun formatDateRange(fromDate: String, toDate: String): String {
    return try {
        val from = apiDateFormat.parse(fromDate) ?: return "$fromDate - $toDate"
        val to = apiDateFormat.parse(toDate) ?: return "$fromDate - $toDate"
        if (fromDate == toDate) {
            displayDateFormat.format(from)
        } else {
            // Same year — omit year from first date
            val fromYear = SimpleDateFormat("yyyy", Locale.US).format(from)
            val toYear = SimpleDateFormat("yyyy", Locale.US).format(to)
            if (fromYear == toYear) {
                val shortFormat = SimpleDateFormat("MMM d", Locale.US)
                "${shortFormat.format(from)} - ${displayDateFormat.format(to)}"
            } else {
                "${displayDateFormat.format(from)} - ${displayDateFormat.format(to)}"
            }
        }
    } catch (e: Exception) {
        "$fromDate - $toDate"
    }
}
