package com.justpass.app.ui.screens

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
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
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
import kotlinx.coroutines.launch
import com.justpass.app.data.analytics.Analytics
import com.justpass.app.R
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.StudentBiodata
import com.justpass.app.data.repository.AttendanceRepository
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.LiquidGlassCard
import com.justpass.app.ui.components.LiquidGlassSurface
import io.github.fletchmckee.liquid.LiquidState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun ProfileScreen(
    cardState: LiquidState,
    displayName: String = "",
    onLogout: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTournamentApprovalClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val securePrefs = SecurePreferences.getInstance(context)
    val rollNumber = securePrefs.rollNumber ?: ""
    val attendanceData = remember { AttendanceRepository.getInstance(context).getCachedAttendance() }
    val appVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }
    }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSideEye by remember { mutableStateOf(false) }
    // Load cached profile picture immediately
    var profileBitmap by remember {
        mutableStateOf<Bitmap?>(
            securePrefs.cachedProfilePicPath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) BitmapFactory.decodeFile(path) else null
                } catch (_: Exception) { null }
            }
        )
    }
    var showFaah by remember { mutableStateOf(false) }
    // Show cached academic info immediately while loading
    var biodata by remember {
        val cachedSem = securePrefs.cachedCurrentSem
        val cachedSection = securePrefs.cachedSection
        val cachedDept = securePrefs.cachedDepartment ?: securePrefs.programmeName
        val cachedBatch = securePrefs.batchYear.takeIf { it > 0 }

        if (cachedSem > 0 || cachedSection != null || cachedDept != null) {
            mutableStateOf<StudentBiodata?>(StudentBiodata(
                currentSem = cachedSem.takeIf { it > 0 },
                section = cachedSection,
                department = cachedDept,
                programmeName = securePrefs.programmeName,
                batchYear = cachedBatch
            ))
        } else {
            mutableStateOf<StudentBiodata?>(null)
        }
    }
    var showBiodata by remember { mutableStateOf(false) }

    // Attendance target setting
    var attendanceTarget by remember { mutableIntStateOf(securePrefs.attendanceTarget) }
    var showTargetDialog by remember { mutableStateOf(false) }

    // Update checker state
    var updateState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Fetch profile picture + biodata
    LaunchedEffect(rollNumber) {
        if (rollNumber.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val repo = AttendanceRepository.getInstance(context)
                try {
                    val bytes = repo.fetchProfilePicture()
                    if (bytes != null && bytes.isNotEmpty()) {
                        profileBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        // Save to cache file for instant display next time
                        try {
                            val cacheFile = File(context.cacheDir, "profile_pic.jpg")
                            cacheFile.outputStream().use { out ->
                                profileBitmap!!.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            securePrefs.cachedProfilePicPath = cacheFile.absolutePath
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
                try {
                    biodata = repo.fetchStudentBiodata()
                    biodata?.let { bio ->
                        bio.programmeName?.let { securePrefs.programmeName = it }
                        bio.batchYear?.let { securePrefs.batchYear = it }
                        // Cache academic info for instant display next time
                        bio.currentSem?.let { securePrefs.cachedCurrentSem = it }
                        bio.section?.let { securePrefs.cachedSection = it }
                        // Detect and store short department name for reliable lookups
                        val detected = com.justpass.app.data.model.detectDepartment(bio.programmeName)
                            ?: com.justpass.app.data.model.detectDepartment(bio.department)
                        securePrefs.cachedDepartment = detected?.shortName ?: bio.programmeName ?: bio.department
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

    if (showTargetDialog) {
        var sliderValue by remember { mutableFloatStateOf(attendanceTarget.toFloat()) }
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text("Attendance Target") },
            text = {
                Column {
                    Text("Set your target attendance percentage. The dashboard will show how many days you need to reach this target.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("${sliderValue.toInt()}%", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 50f..100f,
                        steps = 49
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("50%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("100%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    attendanceTarget = sliderValue.toInt()
                    securePrefs.attendanceTarget = sliderValue.toInt()
                    showTargetDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showTargetDialog = false }) { Text("Cancel") } },
            containerColor = Color(0xFF1E2A3A)
        )
    }

    if (showUpdateDialog) {
        val state = updateState
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(when (state) {
                is UpdateCheckState.Checking -> "Checking..."
                is UpdateCheckState.UpdateAvailable -> "Update Available"
                is UpdateCheckState.UpToDate -> "Up to Date"
                is UpdateCheckState.Error -> "Error"
                else -> ""
            }) },
            text = { Text(when (state) {
                is UpdateCheckState.Checking -> "Checking for updates..."
                is UpdateCheckState.UpdateAvailable -> "Version ${state.latestVersion} is available!\nYou're on v$appVersion."
                is UpdateCheckState.UpToDate -> "You're running the latest version (v$appVersion)."
                is UpdateCheckState.Error -> "Couldn't check for updates. Please try again later."
                else -> ""
            }) },
            confirmButton = {
                when (state) {
                    is UpdateCheckState.UpdateAvailable -> {
                        TextButton(onClick = {
                            showUpdateDialog = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.downloadUrl)))
                        }) { Text("Download") }
                    }
                    else -> {
                        TextButton(onClick = { showUpdateDialog = false }) { Text("OK") }
                    }
                }
            },
            dismissButton = if (state is UpdateCheckState.UpdateAvailable) {
                { TextButton(onClick = { showUpdateDialog = false }) { Text("Later") } }
            } else null,
            containerColor = Color(0xFF1E2A3A)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = com.justpass.app.ui.components.GlassCardShape) {
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

        // Academic info — year, semester, section, department
        val bio = biodata
        if (bio != null) {
            Spacer(modifier = Modifier.height(8.dp))
            val yearOfStudy = if (bio.batchYear != null && bio.currentSem != null) {
                val year = (bio.currentSem + 1) / 2
                "${year}${when(year) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }} Year"
            } else null
            val semText = bio.currentSem?.let { "Semester $it" }
            val sectionText = bio.section?.let { "Section $it" }
            val infoLine = listOfNotNull(yearOfStudy, semText, sectionText).joinToString("  •  ")
            if (infoLine.isNotEmpty()) {
                Text(infoLine, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium)
            }
            // Show programme name (e.g., "BTECH COMPUTER SCIENCE AND BUSINESS SYSTEMS")
            // falling back to department if programme not available
            val displayDept = bio.programmeName ?: bio.department
            displayDept?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

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

        // Biodata card
        GlassListCard(modifier = Modifier.fillMaxWidth().clickable { if (!showBiodata) Analytics.logProfileAction("biodata_view"); showBiodata = !showBiodata }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Biodata", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (showBiodata) "Hide" else "Tap to view",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
                if (showBiodata) {
                    val bioVal = biodata
                    if (bioVal != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))
                        bioVal.programmeName?.let { BiodataRow("Programme", it) }
                            ?: bioVal.course?.let { BiodataRow("Course", it) }
                        bioVal.degreeName?.let { BiodataRow("Degree", it) }
                        bioVal.department?.let { BiodataRow("Department", it) }
                        bioVal.currentSem?.let { BiodataRow("Current Semester", it.toString()) }
                        bioVal.section?.let { BiodataRow("Section", it) }
                        bioVal.batchYear?.let { BiodataRow("Batch Year", it.toString()) }
                        bioVal.gender?.let { BiodataRow("Gender", it) }
                        bioVal.dateOfBirth?.let { BiodataRow("Date of Birth", it) }
                        bioVal.motherTongue?.let { BiodataRow("Mother Tongue", it) }
                        bioVal.nationality?.let { BiodataRow("Nationality", it) }
                        bioVal.bloodGroup?.let { BiodataRow("Blood Group", it) }
                        bioVal.religion?.let { BiodataRow("Religion", it) }
                        bioVal.community?.let { BiodataRow("Community", it) }
                        bioVal.email?.let { BiodataRow("Email", it) }
                        bioVal.phone?.let { BiodataRow("Phone", it) }
                        bioVal.fatherName?.let { BiodataRow("Father's Name", it) }
                        bioVal.motherName?.let { BiodataRow("Mother's Name", it) }
                        bioVal.quota?.let { BiodataRow("Quota", it) }
                        bioVal.enrolledOn?.let { BiodataRow("Enrolled On", it) }
                        bioVal.appFormNo?.let { BiodataRow("App Form No", it) }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Menu — real liquid glass
        LiquidGlassCard(cardState = cardState, modifier = Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    headlineContent = { Text("Check for Updates") },
                    leadingContent = { Icon(Icons.Default.SystemUpdate, null) },
                    supportingContent = if (updateState is UpdateCheckState.Checking) {
                        { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
                    } else null,
                    modifier = Modifier.clickable {
                        if (updateState !is UpdateCheckState.Checking) {
                            Analytics.logProfileAction("check_updates")
                            updateState = UpdateCheckState.Checking
                            showUpdateDialog = true
                            coroutineScope.launch {
                                updateState = checkForUpdate(appVersion)
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                ListItem(
                    headlineContent = { Text("Report Bug / Feature Request") },
                    leadingContent = { Icon(Icons.Default.Feedback, null) },
                    supportingContent = { Text("Help us improve JustPass", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {
                        Analytics.logProfileAction("bug_report")
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Tarunswamy-Muralidharan/-AttendanceWidgetLaudea/issues/new/choose")))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                ListItem(
                    headlineContent = { Text("Attendance Target") },
                    leadingContent = { Icon(Icons.Default.Settings, null) },
                    supportingContent = { Text("${attendanceTarget}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { Analytics.logProfileAction("attendance_target"); showTargetDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                ListItem(headlineContent = { Text("Privacy Policy") }, leadingContent = { Icon(Icons.Default.Info, null) },
                    modifier = Modifier.clickable { onPrivacyPolicyClick() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                // Tournament Approvals — admin only. Visible only when the
                // signed-in roll matches an entry in TournamentAdmins.PLAYER_IDS.
                run {
                    val myPid = remember(rollNumber) {
                        if (rollNumber.isBlank()) ""
                        else "p_${kotlin.math.abs(rollNumber.hashCode()).toString(16)}"
                    }
                    if (com.justpass.app.data.model.TournamentAdmins.isAdmin(myPid)) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                        ListItem(
                            headlineContent = { Text("Tournament Approvals") },
                            leadingContent = { Icon(Icons.Default.AdminPanelSettings, null) },
                            modifier = Modifier.clickable {
                                Analytics.logProfileAction("tournament_approvals")
                                onTournamentApprovalClick()
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                ListItem(headlineContent = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { Analytics.logProfileAction("logout"); showLogoutDialog = true }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
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
                    com.justpass.app.data.analytics.Analytics.logEasterEggTriggered("bite_me")
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
        GlassListCard(
            modifier = Modifier.fillMaxWidth().clickable {
                com.justpass.app.data.analytics.Analytics.logEasterEggTriggered("side_eye_dog")
                showSideEye = true
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tap to view others profile", fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // App Info card
        GlassListCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("App Info", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("JustPass", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Version $appVersion", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Fullscreen side eye dog meme overlay
    if (showSideEye) {
        val dogBitmap = remember {
            try {
                val inputStream = context.resources.openRawResource(R.raw.dog_side_eye)
                android.graphics.BitmapFactory.decodeStream(inputStream)
            } catch (_: Exception) { null }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showSideEye = false }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (dogBitmap != null) {
                Image(
                    bitmap = dogBitmap.asImageBitmap(),
                    contentDescription = "Side eye dog",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
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

private sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class UpdateAvailable(val latestVersion: String, val downloadUrl: String) : UpdateCheckState()
    data object UpToDate : UpdateCheckState()
    data object Error : UpdateCheckState()
}

private suspend fun checkForUpdate(currentVersion: String): UpdateCheckState = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/repos/Tarunswamy-Muralidharan/-AttendanceWidgetLaudea/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        if (conn.responseCode != 200) return@withContext UpdateCheckState.Error

        val json = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = org.json.JSONObject(json)
        val tagName = obj.optString("tag_name", "").removePrefix("v")
        val assets = obj.optJSONArray("assets")

        val downloadUrl = if (assets != null && assets.length() > 0) {
            assets.getJSONObject(0).optString("browser_download_url", "")
        } else {
            obj.optString("html_url", "")
        }

        if (tagName.isEmpty()) return@withContext UpdateCheckState.Error

        if (isNewerVersion(tagName, currentVersion)) {
            UpdateCheckState.UpdateAvailable(tagName, downloadUrl)
        } else {
            UpdateCheckState.UpToDate
        }
    } catch (_: Exception) {
        UpdateCheckState.Error
    }
}

private fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
    val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
        val r = remoteParts.getOrElse(i) { 0 }
        val l = localParts.getOrElse(i) { 0 }
        if (r > l) return true
        if (r < l) return false
    }
    return false
}

@Composable
private fun BiodataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
