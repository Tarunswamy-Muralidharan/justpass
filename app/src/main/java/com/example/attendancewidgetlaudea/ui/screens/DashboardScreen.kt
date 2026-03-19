package com.example.attendancewidgetlaudea.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.R
import com.example.attendancewidgetlaudea.ui.components.GlassCardShape
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassCard
import com.example.attendancewidgetlaudea.ui.viewmodel.DashboardViewModel
import io.github.fletchmckee.liquid.LiquidState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    cardState: LiquidState,
    viewModel: DashboardViewModel = viewModel(),
    displayName: String = "",
    onLogout: () -> Unit,
    onAbsentDaysClick: () -> Unit = {},
    onSubjectAttendanceClick: () -> Unit = {},
    onExemptionsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Light sweep animation around the header card on refresh
    var refreshGlowKey by remember { mutableIntStateOf(0) }
    val glowAnim = remember { Animatable(0f) }
    LaunchedEffect(refreshGlowKey) {
        if (refreshGlowKey > 0) {
            glowAnim.snapTo(0f)
            glowAnim.animateTo(1f, tween(1000, easing = LinearEasing))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header — liquid glass with light sweep on refresh
        Box(modifier = Modifier.fillMaxWidth()) {
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (displayName.isNotEmpty()) "Welcome, ${displayName.split(" ").firstOrNull() ?: displayName}" else "Attendance",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(uiState.rollNumber, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            refreshGlowKey++
                            viewModel.refreshAttendance()
                        },
                        enabled = !uiState.isRefreshing
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Realistic glass light reflection — diagonal beam sweeps top-left to bottom-right
            val g = glowAnim.value
            if (g in 0.001f..0.999f) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    // The beam travels diagonally across the card
                    // Map progress to a position along the diagonal (with overshoot for smooth entry/exit)
                    val diagonal = size.width + size.height
                    val beamCenter = -size.height * 0.3f + diagonal * 1.3f * g
                    val beamWidth = size.width * 0.45f

                    // Fade in at start, fade out at end
                    val edgeFade = when {
                        g < 0.15f -> g / 0.15f
                        g > 0.8f -> (1f - g) / 0.2f
                        else -> 1f
                    }.coerceIn(0f, 1f)

                    // Main specular highlight — bright diagonal band
                    drawRect(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.3f to Color.Transparent,
                                0.42f to Color.White.copy(alpha = 0.06f * edgeFade),
                                0.48f to Color.White.copy(alpha = 0.25f * edgeFade),
                                0.50f to Color.White.copy(alpha = 0.45f * edgeFade),
                                0.52f to Color.White.copy(alpha = 0.25f * edgeFade),
                                0.58f to Color.White.copy(alpha = 0.06f * edgeFade),
                                0.7f to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            start = Offset(beamCenter - beamWidth, 0f),
                            end = Offset(beamCenter + beamWidth, size.height)
                        )
                    )

                    // Secondary subtle blue tint behind the main beam
                    drawRect(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.35f to Color.Transparent,
                                0.47f to Color(0xFF90CAF9).copy(alpha = 0.10f * edgeFade),
                                0.53f to Color(0xFF90CAF9).copy(alpha = 0.10f * edgeFade),
                                0.65f to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            start = Offset(beamCenter - beamWidth * 1.2f, 0f),
                            end = Offset(beamCenter + beamWidth * 1.2f, size.height)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Attendance % — real liquid glass with color tint
        val attendanceTint = getAttendanceTintColor(uiState.attendanceData.attendanceWithExemption)
        LiquidGlassCard(cardState = cardState,
            modifier = Modifier.fillMaxWidth().clickable { onSubjectAttendanceClick() },
            tintColor = attendanceTint) {
            Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Attendance (with exemption)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                Text("${String.format("%.1f", uiState.attendanceData.attendanceWithExemption)}%",
                    fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, color = MaterialTheme.colorScheme.onSurface)
                if (uiState.attendanceData.attendanceWithExemption != uiState.attendanceData.attendancePercentage) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Without exemption: ${String.format("%.1f", uiState.attendanceData.attendancePercentage)}%",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                // Warning: hours needed for 75%
                if (uiState.attendanceData.attendanceWithExemption < 75.0 && uiState.attendanceData.enteredTillDate > 0) {
                    val present = uiState.attendanceData.presentWithExemptionCount
                    val total = uiState.attendanceData.enteredTillDate
                    // Need: (present + x) / (total + x) >= 0.75 → x >= (0.75*total - present) / 0.25
                    val hoursNeeded = kotlin.math.ceil((0.75 * total - present) / 0.25).toInt()
                    if (hoursNeeded > 0) {
                        // Approx days = hours / avg hours per day (~6 classes/day)
                        val approxDays = kotlin.math.ceil(hoursNeeded / 6.0).toInt()
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Attend $hoursNeeded more hours (~$approxDays days) to reach 75%",
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = Color(0xFFFF8A80))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap for subject-wise details →",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats — lightweight for performance
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Present", uiState.attendanceData.presentWithExemptionCount.toString(), Modifier.weight(1f))
            GlassListCard(modifier = Modifier.weight(1f).clickable { onAbsentDaysClick() }, shape = GlassCardShapeSmall) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Absent", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(uiState.attendanceData.absentCount.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Tap for details", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            if (uiState.attendanceData.exemptionCount > 0) {
                GlassListCard(
                    modifier = Modifier.weight(1f).clickable { onExemptionsClick() },
                    shape = GlassCardShapeSmall
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Exemption", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            uiState.attendanceData.exemptionCount.toString(),
                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text("Tap for details", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total Classes", uiState.attendanceData.enteredTillDate.toString(), Modifier.weight(1f))
            StatCard("Not Entered", uiState.attendanceData.notEnteredTillDate.toString(), Modifier.weight(1f))
        }

        if (uiState.isRefreshing) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        }

        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            GlassListCard(modifier = Modifier.fillMaxWidth(), tintColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Credit + contact
        val context = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("for features or colabs: Tarunswamy M", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(painter = painterResource(id = R.drawable.ic_discord), contentDescription = "Discord",
                modifier = Modifier.size(20.dp).clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discordapp.com/users/zeni15u")))
                }, tint = Color(0xFF5865F2))
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.attendanceData.lastUpdated > 0) {
            Text("Last updated: ${formatTimestamp(uiState.attendanceData.lastUpdated)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    GlassListCard(modifier = modifier, shape = GlassCardShapeSmall) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun getAttendanceTintColor(percentage: Double): Color {
    val isDark = isSystemInDarkTheme()
    return when {
        percentage >= 75 -> if (isDark) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF00C853).copy(alpha = 0.12f)
        percentage >= 65 -> if (isDark) Color(0xFFFFEA00).copy(alpha = 0.15f) else Color(0xFFFFD600).copy(alpha = 0.12f)
        else -> if (isDark) Color(0xFFFF5252).copy(alpha = 0.18f) else Color(0xFFFF1744).copy(alpha = 0.14f)
    }
}

private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(timestamp))
