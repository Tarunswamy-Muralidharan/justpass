package com.example.attendancewidgetlaudea.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header — real liquid glass
        LiquidGlassCard(cardState = cardState, modifier = Modifier.fillMaxWidth()) {
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

        Spacer(modifier = Modifier.height(32.dp))

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

        Spacer(modifier = Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("for features or colabs: Tarunswamy M", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(painter = painterResource(id = R.drawable.ic_discord), contentDescription = "Discord",
                modifier = Modifier.size(20.dp).clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discordapp.com/users/zeni15u")))
                }, tint = Color(0xFF5865F2))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
