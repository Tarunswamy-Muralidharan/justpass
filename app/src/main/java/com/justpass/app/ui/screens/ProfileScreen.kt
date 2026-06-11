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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onTournamentApprovalClick: () -> Unit = {},
    onBugReportClick: () -> Unit = {},
    onBugReportInboxClick: () -> Unit = {},
    onManageAdminsClick: () -> Unit = {},
    weatherScene: com.justpass.app.ui.components.WeatherScene = com.justpass.app.ui.components.WeatherScene.OFF,
    onWeatherSceneChange: (com.justpass.app.ui.components.WeatherScene) -> Unit = {},
    autoWeatherEnabled: Boolean = false,
    onAutoWeatherToggle: (Boolean) -> Unit = {}
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

    // Share-app dialogs
    var showShareDialog by remember { mutableStateOf(false) }
    var showIosGuide by remember { mutableStateOf(false) }

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
                            val cacheFile = File(context.filesDir, "profile_pic.jpg")
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

    // ── Share JustPass: pick platform ──
    if (showShareDialog) {
        val playUrl = "https://play.google.com/store/apps/details?id=com.justpass.app"
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Share JustPass") },
            text = { Text("Which device is your friend on?") },
            confirmButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    Analytics.logProfileAction("share_android")
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT,
                            "Track your attendance with JustPass 🎓\n$playUrl")
                    }
                    context.startActivity(Intent.createChooser(send, "Share JustPass"))
                }) { Text("Android") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showShareDialog = false
                    showIosGuide = true
                }) { Text("iPhone / iPad") }
            },
            containerColor = Color(0xFF1E2A3A)
        )
    }

    // ── iOS install guide + share website link ──
    if (showIosGuide) {
        val webUrl = "https://justpass-eta.vercel.app"
        AlertDialog(
            onDismissRequest = { showIosGuide = false },
            title = { Text("Install on iPhone / iPad") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("JustPass runs as a web app on iOS. Share the link below — open it in Safari, then Add to Home Screen.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Image(
                        painter = painterResource(R.drawable.ios_install_guide),
                        contentDescription = "How to install JustPass on iOS",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showIosGuide = false
                    Analytics.logProfileAction("share_ios")
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT,
                            "Get JustPass on your iPhone/iPad 🎓\nOpen in Safari, then Share → Add to Home Screen:\n$webUrl")
                    }
                    context.startActivity(Intent.createChooser(send, "Share JustPass"))
                }) { Text("Share link") }
            },
            dismissButton = {
                TextButton(onClick = { showIosGuide = false }) { Text("Close") }
            },
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
                    headlineContent = { Text("Share JustPass") },
                    supportingContent = { Text("Send to friends on Android or iPhone") },
                    leadingContent = { Icon(Icons.Default.Share, null) },
                    modifier = Modifier.clickable {
                        Analytics.logProfileAction("share_app")
                        showShareDialog = true
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
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
                val bugReplyUnread by com.justpass.app.ui.components.rememberBugReplyUnread()
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Report Bug / Feature Request")
                            if (bugReplyUnread) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF1744))
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Feedback, null) },
                    supportingContent = {
                        Text(
                            if (bugReplyUnread) "New reply waiting"
                            else "Tell me what's broken — text + screenshot",
                            fontSize = 12.sp,
                            color = if (bugReplyUnread) Color(0xFFFF1744)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable {
                        Analytics.logProfileAction("bug_report")
                        onBugReportClick()
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
                // Auto weather toggle — fetches real Neelambur weather from Open-Meteo
                ListItem(
                    headlineContent = { Text("Auto Weather") },
                    leadingContent = { Icon(Icons.Default.Refresh, null) },
                    supportingContent = {
                        Text(
                            if (autoWeatherEnabled) "On  •  Open-Meteo (Neelambur)" else "Off  •  tap to enable",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = autoWeatherEnabled,
                            onCheckedChange = onAutoWeatherToggle,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                // Weather scene picker — 16 manual test scenes per HANDOFF.md.
                // Tap row to open picker dialog with all options.
                var showScenePicker by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Weather Scene") },
                    leadingContent = { Icon(Icons.Default.Cloud, null) },
                    supportingContent = {
                        Text(
                            if (autoWeatherEnabled) "Auto: ${weatherScene.displayName}" else weatherScene.displayName + "  •  tap to change",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.clickable { if (!autoWeatherEnabled) showScenePicker = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (showScenePicker) {
                    AlertDialog(
                        onDismissRequest = { showScenePicker = false },
                        title = { Text("Pick a weather scene") },
                        text = {
                            androidx.compose.foundation.lazy.LazyColumn {
                                items(com.justpass.app.ui.components.WeatherScene.entries.toList(), key = { it.name }) { scene ->
                                    val selected = scene == weatherScene
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onWeatherSceneChange(scene)
                                                showScenePicker = false
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = selected, onClick = null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            scene.displayName,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showScenePicker = false }) { Text("Close") }
                        },
                        containerColor = Color(0xFF1E2A3A),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                // Class compare: delete my data — only shown when the feature
                // flag is on. Wipes the user's row on the Worker D1 + clears
                // local last-uploaded hash so the next sync uploads afresh.
                run {
                    val showRow = remember {
                        try {
                            com.google.firebase.remoteconfig.FirebaseRemoteConfig
                                .getInstance()
                                .getBoolean("class_compare_enabled")
                        } catch (_: Exception) { false }
                    }
                    if (showRow) {
                        var showConfirm by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()
                        ListItem(
                            headlineContent = { Text("Delete my class data") },
                            leadingContent = { Icon(Icons.Default.Info, null) },
                            supportingContent = {
                                Text(
                                    "Removes your anonymous marks from the comparison server.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.clickable { showConfirm = true },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (showConfirm) {
                            AlertDialog(
                                onDismissRequest = { showConfirm = false },
                                title = { Text("Delete my class data?") },
                                text = {
                                    Text(
                                        "Your anonymized CA marks will be removed from the class comparison server. " +
                                        "Reopening CA Marks will re-upload them automatically next sync."
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showConfirm = false
                                        scope.launch {
                                            val ok = com.justpass.app.data.repository.ClassMarksRepository
                                                .getInstance(context)
                                                .deleteMyData()
                                            android.widget.Toast.makeText(
                                                context,
                                                if (ok) "Deleted" else "Delete failed",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
                                },
                                containerColor = Color(0xFF1E2A3A),
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                    }
                }
                ListItem(headlineContent = { Text("Privacy Policy") }, leadingContent = { Icon(Icons.Default.Info, null) },
                    modifier = Modifier.clickable { onPrivacyPolicyClick() }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                // Admin tools — visible only when the signed-in roll matches
                // an entry in TournamentAdmins.PLAYER_IDS (legacy class name;
                // it now gates bug-report inbox and admin management too).
                run {
                    val myPid = remember(rollNumber) {
                        if (rollNumber.isBlank()) ""
                        else "p_${kotlin.math.abs(rollNumber.hashCode()).toString(16)}"
                    }
                    if (com.justpass.app.data.model.TournamentAdmins.isAdmin(myPid)) {
                        // Tournament Approvals — visible to admins in all
                        // builds. Tap behavior gated by the `tournament_enabled`
                        // Remote Config flag: while false we toast "Feature
                        // under development" instead of opening the screen.
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                        ListItem(
                            headlineContent = { Text("Tournament Approvals") },
                            leadingContent = { Icon(Icons.Default.AdminPanelSettings, null) },
                            modifier = Modifier.clickable {
                                Analytics.logProfileAction("tournament_approvals")
                                val rc = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
                                if (rc.getBoolean("tournament_enabled")) {
                                    onTournamentApprovalClick()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Feature under development",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                        ListItem(
                            headlineContent = { Text("Bug Report Inbox") },
                            leadingContent = { Icon(Icons.Default.Inbox, null) },
                            modifier = Modifier.clickable {
                                Analytics.logProfileAction("bug_report_inbox")
                                onBugReportInboxClick()
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline)
                        ListItem(
                            headlineContent = { Text("Manage Admins") },
                            leadingContent = { Icon(Icons.Default.AdminPanelSettings, null) },
                            modifier = Modifier.clickable {
                                Analytics.logProfileAction("manage_admins")
                                onManageAdminsClick()
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
                    5f at 715       // violent shake on impact
                    -4f at 750
                    2.5f at 790
                    -1.5f at 830
                    0f at 900
                    0f at 2000
                },
                repeatMode = RepeatMode.Restart
            ), label = "chompShake"
        )
        // Crumb burst progress — crumbs fly out right after impact, fade as they travel
        val crumbBurst by biteTransition.animateFloat(
            initialValue = 0f, targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2000
                    0f at 0
                    0f at 690
                    1f at 1500
                    1f at 2000
                },
                repeatMode = RepeatMode.Restart
            ), label = "crumbBurst"
        )
        // White impact flash at the moment the jaws slam shut
        val impactFlash by biteTransition.animateFloat(
            initialValue = 0f, targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2000
                    0f at 0
                    0f at 670
                    1f at 720
                    0f at 860
                    0f at 2000
                },
                repeatMode = RepeatMode.Restart
            ), label = "impactFlash"
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
                        // deeper squish + slight bulge + impact wobble
                        scaleY = 1f - chomp * 0.30f
                        scaleX = 1f + chomp * 0.05f
                        translationX = chompShake * 2.4f
                        rotationZ = chompShake * 0.45f
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
            // Jaws + impact effects. Tooth lengths vary per tooth so the bite reads
            // organic instead of a perfect zigzag; each tooth has a shaded inner
            // wedge for depth and a gum bar anchoring the row.
            val jawDrop = chomp * 12f
            Canvas(modifier = Modifier.fillMaxWidth().height(52.dp).align(Alignment.TopCenter)) {
                val teethCount = 9
                val teethWidth = size.width / teethCount
                val jawAlpha = (chomp * 0.95f).coerceIn(0f, 1f)
                if (jawAlpha > 0.01f) {
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFFEFD5D5).copy(alpha = jawAlpha), Color.Transparent)),
                        size = androidx.compose.ui.geometry.Size(size.width, 7f))
                }
                for (i in 0 until teethCount) {
                    val lenMul = 1f + 0.35f * kotlin.math.sin(i * 2.7f)
                    val tipY = jawDrop * 3.4f * lenMul
                    val x0 = i * teethWidth
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x0, 0f)
                        lineTo(x0 + teethWidth / 2, tipY)
                        lineTo(x0 + teethWidth, 0f)
                        close()
                    }
                    drawPath(path, Color.White.copy(alpha = jawAlpha))
                    // shaded right face for 3D depth
                    val shade = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x0 + teethWidth / 2, tipY)
                        lineTo(x0 + teethWidth, 0f)
                        lineTo(x0 + teethWidth * 0.72f, 0f)
                        close()
                    }
                    drawPath(shade, Color(0xFFB9C4D4).copy(alpha = jawAlpha * 0.8f))
                }
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(52.dp).align(Alignment.BottomCenter)) {
                val teethCount = 9
                val teethWidth = size.width / teethCount
                val jawAlpha = (chomp * 0.95f).coerceIn(0f, 1f)
                if (jawAlpha > 0.01f) {
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFFEFD5D5).copy(alpha = jawAlpha)),
                            startY = size.height - 7f, endY = size.height),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 7f),
                        size = androidx.compose.ui.geometry.Size(size.width, 7f))
                }
                for (i in 0 until teethCount) {
                    val lenMul = 1f + 0.35f * kotlin.math.sin(i * 1.9f + 1.3f)
                    val tipY = size.height - jawDrop * 3.4f * lenMul
                    val x0 = i * teethWidth
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x0, size.height)
                        lineTo(x0 + teethWidth / 2, tipY)
                        lineTo(x0 + teethWidth, size.height)
                        close()
                    }
                    drawPath(path, Color.White.copy(alpha = jawAlpha))
                    val shade = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x0 + teethWidth / 2, tipY)
                        lineTo(x0 + teethWidth, size.height)
                        lineTo(x0 + teethWidth * 0.72f, size.height)
                        close()
                    }
                    drawPath(shade, Color(0xFFB9C4D4).copy(alpha = jawAlpha * 0.8f))
                }
            }
            // Impact flash + crumbs flying out of the bite
            Canvas(modifier = Modifier.matchParentSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                if (impactFlash > 0.01f) {
                    // white shockwave ring expanding from center
                    val ringR = 30f + (1f - impactFlash) * size.width * 0.28f
                    drawCircle(Color.White.copy(alpha = impactFlash * 0.55f),
                        radius = ringR, center = androidx.compose.ui.geometry.Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f * impactFlash + 1f))
                }
                if (crumbBurst > 0.02f && crumbBurst < 0.98f) {
                    val t = crumbBurst
                    for (i in 0 until 12) {
                        // deterministic per-crumb kinematics — no allocation, no Random
                        val angle = (i / 12f) * 2f * Math.PI.toFloat() +
                            0.45f * kotlin.math.sin(i * 12.9898f)
                        val speed = 90f + 70f * (0.5f + 0.5f * kotlin.math.sin(i * 78.233f))
                        val px = cx + kotlin.math.cos(angle) * speed * t
                        val py = cy + kotlin.math.sin(angle) * speed * t * 0.6f + 160f * t * t
                        val alpha = ((1f - t) * 0.9f).coerceIn(0f, 1f)
                        val r = 2f + (i % 3) * 1.4f
                        drawCircle(
                            if (i % 4 == 0) Color(0xFFFF1744).copy(alpha = alpha)
                            else Color.White.copy(alpha = alpha * 0.85f),
                            radius = r * (1f - t * 0.4f),
                            center = androidx.compose.ui.geometry.Offset(px, py))
                    }
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
