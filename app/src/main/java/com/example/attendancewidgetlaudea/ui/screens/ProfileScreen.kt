package com.example.attendancewidgetlaudea.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import com.example.attendancewidgetlaudea.R
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.repository.AttendanceRepository
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassCard
import com.example.attendancewidgetlaudea.ui.components.LiquidGlassSurface
import io.github.fletchmckee.liquid.LiquidState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showFaah by remember { mutableStateOf(false) }

    // Fetch profile picture
    LaunchedEffect(rollNumber) {
        if (rollNumber.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val repo = AttendanceRepository.getInstance(context)
                    val bytes = repo.fetchProfilePicture()
                    if (bytes != null && bytes.isNotEmpty()) {
                        profileBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") }, text = { Text("Are you sure you want to logout?") },
            confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Logout") } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } },
            containerColor = Color(0xFF1E2A3A)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 130.dp)
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

        // Avatar — real liquid glass circle with profile picture
        LiquidGlassSurface(cardState = cardState, modifier = Modifier.size(80.dp), shape = CircleShape,
            tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
            if (profileBitmap != null) {
                Image(
                    bitmap = profileBitmap!!.asImageBitmap(),
                    contentDescription = "Profile picture",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.padding(16.dp).fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary)
            }
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

        Spacer(modifier = Modifier.height(12.dp))

        // Easter egg — chomping animation: jaws bite down on the card
        val biteTransition = rememberInfiniteTransition(label = "bite")
        val chomp by biteTransition.animateFloat(
            initialValue = 0f, targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2000
                    0f at 0          // jaws open
                    0f at 500        // pause open
                    1f at 700        // CHOMP! fast close
                    0.6f at 800      // bounce back
                    1f at 900        // settle closed
                    1f at 1100       // hold
                    0f at 1400       // open
                    0f at 2000       // pause
                },
                repeatMode = RepeatMode.Restart
            ), label = "chomp"
        )
        val chompShake by biteTransition.animateFloat(
            initialValue = 0f, targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2000
                    0f at 0
                    0f at 680
                    3f at 720       // shake on impact
                    -2f at 760
                    1f at 800
                    0f at 900
                    0f at 2000
                },
                repeatMode = RepeatMode.Restart
            ), label = "chompShake"
        )
        Box(
            modifier = Modifier.fillMaxWidth()
                .clickable {
                    com.example.attendancewidgetlaudea.data.analytics.Analytics.logEasterEggTriggered("bite_me")
                    try {
                        val mediaPlayer = MediaPlayer.create(context, R.raw.faah)
                        mediaPlayer?.setOnCompletionListener { it.release() }
                        mediaPlayer?.start()
                    } catch (_: Exception) {}
                    showFaah = true
                }
        ) {
            GlassListCard(
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer {
                        scaleY = 1f - chomp * 0.2f
                        scaleX = 1f + chomp * 0.03f
                        translationX = chompShake * 2f
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("BITE ME", fontSize = 16.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = Color(0xFFFF1744))
                }
            }
            // Top jaw — triangle teeth dropping down
            val jawDrop = chomp * 10f
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.TopCenter)) {
                val teethCount = 7
                val teethWidth = size.width / teethCount
                for (i in 0 until teethCount) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(i * teethWidth, 0f)
                        lineTo(i * teethWidth + teethWidth / 2, jawDrop * 3f)
                        lineTo((i + 1) * teethWidth, 0f)
                        close()
                    }
                    drawPath(path, Color.White.copy(alpha = chomp * 0.9f))
                }
            }
            // Bottom jaw — triangle teeth rising up
            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)) {
                val teethCount = 7
                val teethWidth = size.width / teethCount
                for (i in 0 until teethCount) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(i * teethWidth, size.height)
                        lineTo(i * teethWidth + teethWidth / 2, size.height - jawDrop * 3f)
                        lineTo((i + 1) * teethWidth, size.height)
                        close()
                    }
                    drawPath(path, Color.White.copy(alpha = chomp * 0.9f))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Side eye dog meme easter egg
        var showSideEye by remember { mutableStateOf(false) }
        GlassListCard(
            modifier = Modifier.fillMaxWidth().clickable {
                if (!showSideEye) com.example.attendancewidgetlaudea.data.analytics.Analytics.logEasterEggTriggered("side_eye_dog")
                showSideEye = !showSideEye
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tap to view others profile", fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                if (showSideEye) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val dogBitmap = remember {
                        try {
                            val inputStream = context.resources.openRawResource(R.raw.dog_side_eye)
                            android.graphics.BitmapFactory.decodeStream(inputStream)
                        } catch (_: Exception) { null }
                    }
                    if (dogBitmap != null) {
                        Image(
                            bitmap = dogBitmap.asImageBitmap(),
                            contentDescription = "Side eye dog",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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

    // Faah explosion overlay
    if (showFaah) {
        var animProgress by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(Unit) {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 2500) {
                animProgress = ((System.currentTimeMillis() - startTime) / 2500f).coerceIn(0f, 1f)
                delay(16)
            }
            showFaah = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (0.8f * (1f - animProgress)).coerceIn(0f, 0.8f)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showFaah = false }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Expanding circle
            val circleSize = animProgress * 2000f
            Box(
                modifier = Modifier
                    .size(circleSize.dp.coerceAtMost(1000.dp))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF1744).copy(alpha = (1f - animProgress).coerceIn(0f, 0.6f)),
                                Color(0xFFFF9100).copy(alpha = (1f - animProgress).coerceIn(0f, 0.4f)),
                                Color.Transparent
                            )
                        )
                    )
            )

            // FAAAAAH text
            val scale = 1f + animProgress * 3f
            val alpha = (1f - animProgress * 0.8f).coerceIn(0f, 1f)
            Text(
                "FAAAAAH",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = alpha),
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = animProgress * 15f
                },
                letterSpacing = 4.sp
            )
        }
    }
    } // Box
}
