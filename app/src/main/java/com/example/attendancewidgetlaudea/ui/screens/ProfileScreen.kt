package com.example.attendancewidgetlaudea.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.attendancewidgetlaudea.R
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassCard
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassSurface
import io.github.fletchmckee.liquid.LiquidState

@Composable
fun ProfileScreen(
    cardState: LiquidState,
    displayName: String = "",
    onLogout: () -> Unit,
    onPrivacyPolicyClick: () -> Unit
) {
    val context = LocalContext.current
    val securePrefs = SecurePreferences.getInstance(context)
    val rollNumber = securePrefs.rollNumber ?: ""
    val attendanceData = remember { AttendanceRepository.getInstance(context).getCachedAttendance() }
    val appVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
    }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") }, text = { Text("Are you sure you want to logout?") },
            confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Logout") } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = com.example.attendancewidgetlaudea.ui.components.GlassCardShape) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Avatar — real liquid glass circle
        LiquidGlassSurface(cardState = cardState, modifier = Modifier.size(80.dp), shape = CircleShape,
            tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
            Icon(Icons.Default.Person, null, modifier = Modifier.padding(16.dp).fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (displayName.isNotEmpty()) Text(displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(rollNumber, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(24.dp))

        // Attendance Overview card
        GlassListCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Attendance Overview", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                if (attendanceData.enteredTillDate > 0) {
                    Text("${String.format("%.1f", attendanceData.attendanceWithExemption)}%",
                        fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (attendanceData.attendanceWithExemption != attendanceData.attendancePercentage) {
                        Text("Without exemption: ${String.format("%.1f", attendanceData.attendancePercentage)}%",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(attendanceData.presentCount.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Present", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(attendanceData.absentCount.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("Absent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (attendanceData.exemptionCount > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(attendanceData.exemptionCount.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                Text("Exemption", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Text("No data yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text("Refresh attendance from the home screen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Menu — real liquid glass
        LiquidGlassCard(cardState = cardState, modifier = Modifier.fillMaxWidth()) {
            Column {
                ListItem(headlineContent = { Text("Privacy Policy") }, leadingContent = { Icon(Icons.Default.Info, null) },
                    modifier = Modifier.clickable { onPrivacyPolicyClick() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                ListItem(headlineContent = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showLogoutDialog = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Info card
        GlassListCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App Info", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Laudea Attendance Widget", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Version $appVersion", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("for features or colabs: Tarunswamy M", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f))
                    Icon(painter = painterResource(id = R.drawable.ic_discord), contentDescription = "Discord",
                        modifier = Modifier.size(22.dp).clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discordapp.com/users/zeni15u")))
                        }, tint = Color(0xFF5865F2))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
