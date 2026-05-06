package com.justpass.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.R
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.ui.components.GlassCardShape
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.GlassListSurface
import com.justpass.app.ui.components.LiquidGlassCard
import com.justpass.app.ui.viewmodel.DashboardViewModel
import com.justpass.app.data.model.TargetCgpaResult
import io.github.fletchmckee.liquid.LiquidState
import com.justpass.app.data.analytics.Analytics
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    cardState: LiquidState,
    viewModel: DashboardViewModel = viewModel(),
    displayName: String = "",
    onLogout: () -> Unit,
    onAbsentDaysClick: () -> Unit = {},
    onSubjectAttendanceClick: () -> Unit = {},
    onExemptionsClick: () -> Unit = {},
    onResultClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onCircularsClick: () -> Unit = {},
    onCgpaClick: () -> Unit = {},
    onExamSeatClick: () -> Unit = {},
    onSyllabusClick: () -> Unit = {},
    onChessClick: () -> Unit = {},
    onLiteRtClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val securePrefs = remember { SecurePreferences.getInstance(context) }
    val attendanceTarget = remember { securePrefs.attendanceTarget }

    // Load cached profile picture
    val profileBitmap = remember {
        securePrefs.cachedProfilePicPath?.let { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) BitmapFactory.decodeFile(path) else null
            } catch (_: Exception) { null }
        }
    }

    // Fetch biodata (department, programmeName) on first Dashboard load if missing
    // Safe here because login WebView is fully destroyed before Dashboard renders
    // Preload interstitial ad
    LaunchedEffect(Unit) {
        com.justpass.app.ui.components.InterstitialAdManager.preload(context)
    }

    // Refresh CGPA from saved grades whenever dashboard becomes visible
    // (e.g. after user imports results in GPA Calculator)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadCalculatorCgpa()
                viewModel.loadTargetCgpa()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (securePrefs.programmeName == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val repo = com.justpass.app.data.repository.AttendanceRepository.getInstance(context)
                    val bio = repo.fetchStudentBiodata()
                    bio?.let {
                        it.programmeName?.let { p -> securePrefs.programmeName = p }
                        it.batchYear?.let { b -> securePrefs.batchYear = b }
                        it.currentSem?.let { s -> securePrefs.cachedCurrentSem = s }
                        it.section?.let { s -> securePrefs.cachedSection = s }
                        val detected = com.justpass.app.data.model.detectDepartment(it.programmeName)
                            ?: com.justpass.app.data.model.detectDepartment(it.department)
                        securePrefs.cachedDepartment = detected?.shortName ?: it.programmeName ?: it.department
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Glow animation around the header card on refresh — persists 2s after refresh ends
    var refreshGlowKey by remember { mutableIntStateOf(0) }
    val glowAnim = remember { Animatable(0f) }
    LaunchedEffect(refreshGlowKey) {
        if (refreshGlowKey > 0) {
            glowAnim.snapTo(0f)
            glowAnim.animateTo(1f, tween(2500, easing = LinearEasing))
        }
    }

    var showAiBubble by remember { mutableStateOf(false) }
    var showCgpaSetup by remember { mutableStateOf(false) }
    var showCgpaDetail by remember { mutableStateOf(false) }
    var pendingDetailShow by remember { mutableStateOf(false) }
    var targetCgpa by remember { mutableFloatStateOf(securePrefs.targetCgpa) }
    val cgpaResult = uiState.targetCgpaResult

    // Auto-show detail dialog when result arrives after setting target
    LaunchedEffect(cgpaResult) {
        if (pendingDetailShow && cgpaResult != null) {
            pendingDetailShow = false
            showCgpaDetail = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            com.justpass.app.data.analytics.Analytics.logPullToRefresh()
            refreshGlowKey++
            viewModel.refreshAttendance()
        },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        indicator = {} // Hidden — the glass comet glow is our refresh indicator
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            // 130.dp covers the floating bottom bar's fixed footprint
            // (barHeight 68 + bumpHeight 30 + outer bottom gap 12 + breathing
            // room ~20). The .navigationBarsPadding() then adds whatever the
            // current device's gesture/3-button inset is on top — so the
            // layout works on phones with 0px gesture bars *and* on phones
            // with chunky 3-button nav bars without the last card hiding.
            .padding(bottom = 130.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header — liquid glass with light sweep on refresh
        Box(modifier = Modifier.fillMaxWidth()) {
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Refresh button on the left
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
                    // Welcome text in the middle
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = if (displayName.isNotEmpty()) "Welcome, ${displayName.split(" ").firstOrNull() ?: displayName}" else "JustPass",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(uiState.rollNumber, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Profile picture on the right — with pulsing ripple rings
                    val pulseTransition = rememberInfiniteTransition(label = "profilePulse")
                    val pulse1Alpha by pulseTransition.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "p1A"
                    )
                    val pulse1Scale by pulseTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "p1S"
                    )
                    // Second ripple, offset by half cycle
                    val pulse2Alpha by pulseTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "p2A"
                    )
                    val pulse2Scale by pulseTransition.animateFloat(
                        initialValue = 1.3f,
                        targetValue = 1.8f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "p2S"
                    )
                    // Manually compute second ripple alpha from first (offset by half)
                    val ripple2Alpha = if (pulse1Scale < 1.3f) (pulse1Scale - 1f) / 0.3f * 0.7f
                                       else (1.6f - pulse1Scale) / 0.3f * 0.7f

                    val primaryColor = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clickable { onProfileClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        // Ripple ring 1
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .graphicsLayer {
                                    scaleX = pulse1Scale
                                    scaleY = pulse1Scale
                                    alpha = pulse1Alpha
                                }
                                .border(2.dp, primaryColor, CircleShape)
                        )
                        // Ripple ring 2 (staggered)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .graphicsLayer {
                                    scaleX = pulse1Scale * 0.85f + 0.15f
                                    scaleY = pulse1Scale * 0.85f + 0.15f
                                    alpha = (pulse1Alpha * 0.6f).coerceIn(0f, 0.6f)
                                }
                                .border(1.5.dp, primaryColor.copy(alpha = 0.5f), CircleShape)
                        )
                        // Profile pic — centered
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.5.dp, primaryColor.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profileBitmap != null) {
                                Image(
                                    bitmap = profileBitmap.asImageBitmap(),
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person, "Profile",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Smooth comet light travelling inside the glass border
            val g = glowAnim.value
            if (uiState.isRefreshing || g in 0.001f..0.999f) {
                val orbTransition = rememberInfiniteTransition(label = "orb")
                val lightPos by orbTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "lp"
                )
                val fadeAlpha = if (uiState.isRefreshing) 1f else ((1f - g) * 2f).coerceIn(0f, 1f)

                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    val perimeter = 2f * (w + h)
                    val r = 28f

                    // Clip inside the glass card shape
                    val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, r, r)) }
                    clipPath(clip) {
                        fun posOnBorder(t: Float): Offset {
                            val d = ((t % 1f + 1f) % 1f) * perimeter
                            return when {
                                d < w -> Offset(d, 0f)
                                d < w + h -> Offset(w, d - w)
                                d < 2 * w + h -> Offset(w - (d - w - h), h)
                                else -> Offset(0f, h - (d - 2 * w - h))
                            }
                        }

                        // Smooth comet trail — 20 overlapping gradient circles
                        val trailLen = 20
                        for (i in 0 until trailLen) {
                            val t = lightPos - i * 0.012f
                            val pt = posOnBorder(t)
                            val progress = i.toFloat() / trailLen
                            val a = (1f - progress) * fadeAlpha

                            // Color shifts from white→purple→pink→blue along the trail
                            val coreColor = when {
                                progress < 0.15f -> Color.White
                                progress < 0.4f -> Color(0xFFAD5FFF)
                                progress < 0.7f -> Color(0xFFD60A47)
                                else -> Color(0xFF471EEC)
                            }

                            val radius = 90f - progress * 50f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        coreColor.copy(alpha = a * 0.85f),
                                        coreColor.copy(alpha = a * 0.3f),
                                        Color.Transparent
                                    ),
                                    center = pt,
                                    radius = radius
                                ),
                                radius = radius,
                                center = pt
                            )
                        }

                        // Bright white core at the head
                        val head = posOnBorder(lightPos)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.95f * fadeAlpha),
                            radius = 6f,
                            center = head
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── CGPA narrow tile with car indicator animation ──
        val indicatorAnim = rememberInfiniteTransition(label = "cgpaIndicator")
        val indicatorPos by indicatorAnim.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ), label = "indPos"
        )
        GlassListCard(
            modifier = Modifier.fillMaxWidth().clickable {
                if (!uiState.hasGpaData) onCgpaClick()
                else if (targetCgpa > 0f && cgpaResult != null) showCgpaDetail = true
                else showCgpaSetup = true
            },
            shape = GlassCardShapeSmall
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (uiState.hasGpaData && uiState.calculatorCgpa != null) {
                        // Has GPA data
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.School, contentDescription = null,
                                tint = Color(0xFF42A5F5), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CGPA", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                String.format("%.2f", uiState.calculatorCgpa),
                                fontSize = 20.sp, fontWeight = FontWeight.Black,
                                color = Color(0xFF42A5F5)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (targetCgpa > 0f) {
                                Text("Target ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                Text(
                                    String.format("%.1f", targetCgpa),
                                    fontSize = 22.sp, fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E676)
                                )
                                if (cgpaResult == null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF00E676).copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                Text("Set target", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFFAB00))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                tint = if (targetCgpa > 0f) Color(0xFF00E676).copy(alpha = 0.6f) else Color(0xFFFFAB00),
                                modifier = Modifier.size(20.dp))
                        }
                    } else {
                        // No GPA data — prompt to use calculator
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.School, contentDescription = null,
                                tint = Color(0xFFFFAB00), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Target CGPA", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text("Update grades in GPA Calculator first", fontSize = 11.sp,
                                    color = Color(0xFFFFAB00).copy(alpha = 0.8f))
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                            tint = Color(0xFFFFAB00).copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
                // Sequential chevron indicator — Audi-style sweeping turn signal
                if (uiState.hasGpaData && uiState.calculatorCgpa != null) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val w = size.width
                        val h = size.height
                        val cy = h / 2f
                        val numChevrons = 10
                        val chevronWidth = 10f
                        val chevronHeight = h * 0.35f
                        val startX = w * 0.38f // start after CGPA number
                        val endX = if (targetCgpa > 0f) w * 0.55f else w * 0.58f // end before target/set text
                        val totalSpan = endX - startX
                        val spacing = totalSpan / numChevrons

                        val litCount = (indicatorPos * (numChevrons + 3)).toInt()

                        for (i in 0 until numChevrons) {
                            val alpha = when {
                                i < litCount - 4 -> 0.06f
                                i < litCount - 2 -> 0.18f
                                i < litCount -> {
                                    val dist = litCount - i
                                    if (dist == 1) 0.85f else 0.5f
                                }
                                else -> 0.04f
                            }

                            val cx = startX + i * spacing
                            val amber = Color(0xFFFFAB00).copy(alpha = alpha)

                            val path = Path().apply {
                                moveTo(cx, cy - chevronHeight / 2f)
                                lineTo(cx + chevronWidth, cy)
                                lineTo(cx, cy + chevronHeight / 2f)
                            }
                            drawPath(
                                path = path,
                                color = amber,
                                style = Stroke(width = 3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Attendance % — real liquid glass with color tint
        val attendanceTint = getAttendanceTintColor(uiState.attendanceData.attendanceWithExemption)
        LiquidGlassCard(cardState = cardState,
            modifier = Modifier.fillMaxWidth().clickable { Analytics.logTileClicked("attendance"); onSubjectAttendanceClick() },
            tintColor = attendanceTint) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Attendance (with exemption)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                SlotMachineNumber(
                    value = "${String.format("%.1f", uiState.attendanceData.attendanceWithExemption)}%",
                    fontSize = 52.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (uiState.attendanceData.attendanceWithExemption != uiState.attendanceData.attendancePercentage) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Without exemption: ${String.format("%.1f", uiState.attendanceData.attendancePercentage)}%",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                // Warning: hours needed for target %
                val targetPct = attendanceTarget / 100.0
                if (uiState.attendanceData.attendanceWithExemption < attendanceTarget && uiState.attendanceData.enteredTillDate > 0) {
                    val present = uiState.attendanceData.presentWithExemptionCount
                    val total = uiState.attendanceData.enteredTillDate
                    val hoursNeeded = kotlin.math.ceil((targetPct * total - present) / (1.0 - targetPct)).toInt()
                    if (hoursNeeded > 0) {
                        val approxDays = kotlin.math.ceil(hoursNeeded / 6.0).toInt()
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Attend $hoursNeeded more hours (~$approxDays days) to reach $attendanceTarget%",
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = Color(0xFFFF8A80))
                    }
                }

                // Stats inside the main card
                if (uiState.attendanceData.enteredTillDate > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 4.dp, vertical = 6.dp)) {
                            Text(uiState.attendanceData.presentWithExemptionCount.toString(),
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676), maxLines = 1)
                            Text("Present", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        GlassListSurface(
                            shape = RoundedCornerShape(12.dp),
                            tintColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                            modifier = Modifier.weight(1f, fill = false).clickable { onExemptionsClick() }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(uiState.attendanceData.exemptionCount.toString(),
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
                                Text("Exempt", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f), maxLines = 1)
                            }
                        }
                        GlassListSurface(
                            shape = RoundedCornerShape(12.dp),
                            tintColor = Color(0xFFFF5252).copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f, fill = false).clickable { onAbsentDaysClick() }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(uiState.attendanceData.absentCount.toString(),
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252), maxLines = 1)
                                Text("Absent", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF5252).copy(alpha = 0.85f), maxLines = 1)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 4.dp, vertical = 6.dp)) {
                            Text(uiState.attendanceData.enteredTillDate.toString(),
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text("Total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (uiState.attendanceData.notEnteredTillDate > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f, fill = false).padding(horizontal = 4.dp, vertical = 6.dp)) {
                                Text(uiState.attendanceData.notEnteredTillDate.toString(),
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text("Pending", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap for subject-wise details →",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            }
        }

        // ── Target CGPA Dialogs ──
        TargetCgpaDialogs(
            viewModel = viewModel,
            securePrefs = securePrefs,
            showSetup = showCgpaSetup,
            onDismissSetup = { showCgpaSetup = false },
            showDetail = showCgpaDetail,
            onDismissDetail = {
                showCgpaDetail = false
                com.justpass.app.ui.components.InterstitialAdManager.show(context as android.app.Activity)
            },
            cgpaResult = cgpaResult,
            targetCgpa = targetCgpa,
            onTargetChanged = { newTarget -> targetCgpa = newTarget },
            onShowSetup = { showCgpaSetup = true },
            onRequestDetail = { pendingDetailShow = true }
        )

        // ── Remote Announcement Dialog ──
        val announcement = uiState.announcement
        if (announcement != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAnnouncement() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFFAB00), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(announcement.title, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                text = {
                    Text(announcement.message, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        lineHeight = 20.sp)
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissAnnouncement() }) {
                        Text("Got it", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E2A3A)
            )
        }

        // ── Server Down Banner ──
        val serverError = uiState.errorMessage
        if (serverError != null && serverError.contains("server", ignoreCase = true)) {
            Spacer(modifier = Modifier.height(8.dp))
            GlassListCard(
                modifier = Modifier.fillMaxWidth(),
                shape = GlassCardShapeSmall
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "LAUDEA server is down. Showing cached data.",
                        fontSize = 13.sp,
                        color = Color(0xFFFF8A80),
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, "Dismiss", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Leave calculator
        if (uiState.attendanceData.enteredTillDate > 0) {
            var leaveDays by remember { mutableFloatStateOf(0f) }
            var showLeavePopup by remember { mutableStateOf(false) }
            var leaveStartDate by remember { mutableStateOf(java.time.LocalDate.now().plusDays(1)) }
            var showDatePicker by remember { mutableStateOf(false) }
            val days = leaveDays.toInt()
            val present = uiState.attendanceData.presentWithExemptionCount
            val total = uiState.attendanceData.enteredTillDate
            val currentPct = uiState.attendanceData.attendanceWithExemption
            val leaveHours = if (days > 0) viewModel.calculateLeaveHours(leaveStartDate, days, uiState.holidays) else 0
            val newTotal = total + leaveHours
            val newPercentage = if (days > 0 && newTotal > 0) (present.toDouble() / newTotal) * 100.0 else currentPct
            val drop = currentPct - newPercentage
            val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy") }

            // Inline card — tap to expand
            GlassListCard(
                modifier = Modifier.fillMaxWidth().clickable { showLeavePopup = true },
                shape = GlassCardShapeSmall
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bunkometer", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Speed, contentDescription = "Bunkometer",
                            tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp))
                    }
                    if (days == 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("See how bunking affects your attendance", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    if (days > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "$days day${if (days > 1) "s" else ""} → ${String.format("%.1f", newPercentage)}%",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (newPercentage < attendanceTarget) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Fullscreen leave calculator popup
            if (showLeavePopup) {
                var pickerMode by remember { mutableIntStateOf(0) } // 0 = slider, 1 = calendar
                val selectedDates = remember { mutableStateListOf<java.time.LocalDate>() }
                var calendarMonth by remember { mutableStateOf(java.time.YearMonth.now()) }

                // Calculate values based on mode
                val effectiveDays: Int
                val effectiveHours: Int
                val effectiveNewPct: Double
                val effectiveDrop: Double
                val effectiveMissedDays: List<java.time.LocalDate>

                if (pickerMode == 0) {
                    effectiveDays = days
                    effectiveHours = leaveHours
                    effectiveNewPct = newPercentage
                    effectiveDrop = drop
                    effectiveMissedDays = if (days > 0) viewModel.getWorkingDaysInLeaveRange(leaveStartDate, days, uiState.holidays) else emptyList()
                } else {
                    val workingSelected = selectedDates.filter {
                        it.dayOfWeek != java.time.DayOfWeek.SUNDAY && it !in uiState.holidays
                    }
                    effectiveDays = workingSelected.size
                    effectiveHours = viewModel.calculateHoursForDates(workingSelected.toSet())
                    val calNewTotal = total + effectiveHours
                    effectiveNewPct = if (effectiveDays > 0 && calNewTotal > 0) (present.toDouble() / calNewTotal) * 100.0 else currentPct
                    effectiveDrop = currentPct - effectiveNewPct
                    effectiveMissedDays = workingSelected.sorted()
                }

                AlertDialog(
                    onDismissRequest = {
                        showLeavePopup = false
                        (context as? Activity)?.let { activity ->
                            com.justpass.app.ui.components.InterstitialAdManager.show(activity)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null,
                                tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bunkometer", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            // Mode toggle: Slider vs Calendar
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            ) {
                                listOf("Consecutive" to 0, "Pick Days" to 1).forEach { (label, mode) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (pickerMode == mode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { pickerMode = mode }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                            color = if (pickerMode == mode) Color.White
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (pickerMode == 0) {
                                // ── Slider mode ──
                                // Date picker row
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .clickable { showDatePicker = true }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Starting from", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(leaveStartDate.format(dateFormatter),
                                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text("Change", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Slider(
                                    value = leaveDays,
                                    onValueChange = { leaveDays = it; if (it.toInt() > 0) Analytics.logSliderUsed("leave_calculator", it.toInt()) },
                                    valueRange = 0f..30f,
                                    steps = 29,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                )
                            } else {
                                // ── Calendar pick mode ──
                                // Month navigation
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { calendarMonth = calendarMonth.minusMonths(1) }) {
                                        Text("◀", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        "${calendarMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${calendarMonth.year}",
                                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = { calendarMonth = calendarMonth.plusMonths(1) }) {
                                        Text("▶", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                // Day headers
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                                        Text(d, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                // Calendar grid
                                val firstDay = calendarMonth.atDay(1)
                                val startDow = (firstDay.dayOfWeek.value - 1) // Mon=0
                                val daysInMonth = calendarMonth.lengthOfMonth()
                                val today = java.time.LocalDate.now()
                                val weeks = (startDow + daysInMonth + 6) / 7

                                for (week in 0 until weeks) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        for (col in 0..6) {
                                            val dayNum = week * 7 + col - startDow + 1
                                            if (dayNum < 1 || dayNum > daysInMonth) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            } else {
                                                val date = calendarMonth.atDay(dayNum)
                                                val isSelected = date in selectedDates
                                                val isSunday = date.dayOfWeek == java.time.DayOfWeek.SUNDAY
                                                val isHoliday = date in uiState.holidays
                                                val isPast = date.isBefore(today)
                                                val isDisabled = isSunday || isHoliday || isPast

                                                val dayHours = if (!isDisabled) viewModel.getHoursForDate(date) else 0
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .padding(2.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            when {
                                                                isSelected -> MaterialTheme.colorScheme.primary
                                                                isHoliday -> Color(0xFF8E24AA).copy(alpha = 0.15f)
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        .then(
                                                            if (!isDisabled) Modifier.clickable {
                                                                if (isSelected) selectedDates.remove(date)
                                                                else selectedDates.add(date)
                                                            } else Modifier
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            "$dayNum",
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = when {
                                                                isSelected -> Color.White
                                                                isDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                                                else -> MaterialTheme.colorScheme.onSurface
                                                            }
                                                        )
                                                        if (isSelected) {
                                                            Text(
                                                                "${dayHours}h",
                                                                fontSize = 8.sp,
                                                                color = Color.White.copy(alpha = 0.8f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Legend
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Selected", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF8E24AA).copy(alpha = 0.4f)))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Holiday", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                if (selectedDates.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = { selectedDates.clear() },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) { Text("Clear all", fontSize = 12.sp) }
                                }
                            }

                            // ── Results (shared by both modes) ──
                            if (effectiveDays > 0) {
                                Spacer(modifier = Modifier.height(8.dp))

                                // Big day count
                                Text(
                                    "$effectiveDays day${if (effectiveDays > 1) "s" else ""}",
                                    fontSize = 36.sp, fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${String.format("%.1f", effectiveNewPct)}%",
                                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                            color = if (effectiveNewPct < attendanceTarget) Color(0xFFFF5252) else Color(0xFF00E676))
                                        Text("New attendance", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("-${String.format("%.1f", effectiveDrop)}%",
                                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF8A80))
                                        Text("Drop", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("$effectiveHours",
                                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("Hours missed", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                // Show missed days
                                if (effectiveMissedDays.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFFF5252).copy(alpha = 0.10f))
                                            .padding(10.dp)
                                    ) {
                                        Text("Days you'll miss:",
                                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFFF5252).copy(alpha = 0.9f))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        effectiveMissedDays.forEach { date ->
                                            val dayName = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                                            val hrs = viewModel.getHoursForDate(date)
                                            Text("• ${date.dayOfMonth} ${date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)} ($dayName) — $hrs hrs",
                                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                        }
                                    }
                                }

                                // Holidays in range (slider mode only)
                                if (pickerMode == 0) {
                                    val crossedHolidays = viewModel.getHolidaysInLeaveRange(leaveStartDate, days, uiState.holidays)
                                    if (crossedHolidays.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF8E24AA).copy(alpha = 0.12f))
                                                .padding(10.dp)
                                        ) {
                                            Text("Holidays — NOT counted above",
                                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF00E676))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            crossedHolidays.forEach { (date, name) ->
                                                Text("• ${date.dayOfMonth} ${date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)} — $name (0 hrs)",
                                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                            }
                                        }
                                    }
                                }
                            } else if (pickerMode == 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Slide to see how your attendance changes",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Tap dates on the calendar to select days to bunk",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showLeavePopup = false
                            (context as? Activity)?.let { activity ->
                                com.justpass.app.ui.components.InterstitialAdManager.show(activity)
                            }
                        }) {
                            Text("Done")
                        }
                    },
                    containerColor = Color(0xFF1E2A3A)
                )
            }

            // Custom holiday-aware date picker
            if (showDatePicker) {
                var pickerMonth by remember { mutableStateOf(java.time.YearMonth.from(leaveStartDate)) }
                var tempSelectedDate by remember { mutableStateOf(leaveStartDate) }
                val holidaysInMonth = uiState.holidays.filter { (date, _) ->
                    java.time.YearMonth.from(date) == pickerMonth
                }

                AlertDialog(
                    onDismissRequest = { showDatePicker = false },
                    title = {
                        Text("Select start date", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Month navigation
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { pickerMonth = pickerMonth.minusMonths(1) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous",
                                        tint = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    "${pickerMonth.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${pickerMonth.year}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(onClick = { pickerMonth = pickerMonth.plusMonths(1) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next",
                                        tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Day headers
                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                                    Text(d, modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Calendar grid
                            val firstDay = pickerMonth.atDay(1)
                            val daysInMonth = pickerMonth.lengthOfMonth()
                            val sundayOffset = firstDay.dayOfWeek.value % 7
                            val today = java.time.LocalDate.now()
                            val rows = ((sundayOffset + daysInMonth + 6) / 7)
                            var dayCounter = 1

                            for (row in 0 until rows) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    for (col in 0..6) {
                                        val cellIndex = row * 7 + col
                                        if (cellIndex < sundayOffset || dayCounter > daysInMonth) {
                                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        } else {
                                            val date = pickerMonth.atDay(dayCounter)
                                            val isHoliday = date in uiState.holidays
                                            val isToday = date == today
                                            val isSelected = date == tempSelectedDate
                                            val isSunday = col == 0

                                            Box(
                                                modifier = Modifier.weight(1f).aspectRatio(1f).padding(1.dp)
                                                    .then(
                                                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                        else if (isToday) Modifier.border(1.5.dp, Color(0xFF00E676), RoundedCornerShape(8.dp))
                                                        else Modifier
                                                    )
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .then(if (isHoliday) Modifier.background(Color(0xFF8E24AA).copy(alpha = 0.25f)) else Modifier)
                                                    .clickable { tempSelectedDate = date },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("$dayCounter", fontSize = 13.sp,
                                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = when {
                                                            isSelected -> MaterialTheme.colorScheme.primary
                                                            isToday -> Color(0xFF00E676)
                                                            isSunday || isHoliday -> Color(0xFF8E24AA)
                                                            else -> MaterialTheme.colorScheme.onSurface
                                                        })
                                                    if (isHoliday) {
                                                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF8E24AA)))
                                                    }
                                                }
                                            }
                                            dayCounter++
                                        }
                                    }
                                }
                            }

                            // Holidays this month
                            if (holidaysInMonth.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                holidaysInMonth.forEach { (date, name) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8E24AA)))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${date.dayOfMonth} ${date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)} — $name",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            leaveStartDate = tempSelectedDate
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    },
                    containerColor = Color(0xFF1E2A3A)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Result + Calendar tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardTile("Semester Result", "Grades & GPA", Icons.Default.School, Color(0xFF42A5F5),
                Modifier.weight(1f)) { Analytics.logTileClicked("result"); onResultClick() }
            DashboardTile("Calendar", "Holidays & events", Icons.Default.DateRange, Color(0xFF8E24AA),
                Modifier.weight(1f)) { Analytics.logTileClicked("calendar"); onCalendarClick() }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DashboardTile("GPA Calculator", "Calculate SGPA & CGPA", Icons.Default.Calculate, Color(0xFF00E676),
            Modifier.fillMaxWidth()) { Analytics.logTileClicked("gpa_calculator"); onCgpaClick() }

        Spacer(modifier = Modifier.height(8.dp))

        DashboardTile("Circulars", "Notices & updates from college", Icons.Default.Mail, Color(0xFFFFC107),
            Modifier.fillMaxWidth()) { Analytics.logTileClicked("circulars"); onCircularsClick() }

        Spacer(modifier = Modifier.height(8.dp))

        DashboardTile("Exam Seat Finder", "Find your hall & seat", Icons.Default.EventSeat, Color(0xFFFF8A65),
            Modifier.fillMaxWidth()) { Analytics.logTileClicked("exam_seat"); onExamSeatClick() }

        Spacer(modifier = Modifier.height(8.dp))

        DashboardTile("Syllabus", "R2021 subject-wise syllabus", Icons.Default.MenuBook, Color(0xFF7C4DFF),
            Modifier.fillMaxWidth()) { Analytics.logTileClicked("syllabus"); onSyllabusClick() }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.attendanceData.lastUpdated > 0) {
            Text("Last updated: ${formatTimestamp(uiState.attendanceData.lastUpdated)}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
    } // PullToRefreshBox

    // ── Floating AI Assistant FAB ──
    val firstName = displayName.split(" ").firstOrNull() ?: "there"
    val exampleQuestions = remember { listOf(
        "Can I bunk tomorrow?",
        "Show my CA marks",
        "What's my lowest attendance?",
        "How many classes can I skip?"
    ) }

    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 160.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.End
    ) {
        AnimatedVisibility(
            visible = showAiBubble,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    slideInVertically(spring(stiffness = Spring.StiffnessMedium)) { it / 2 } +
                    scaleIn(spring(stiffness = Spring.StiffnessMedium), initialScale = 0.8f),
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 } +
                    scaleOut(tween(150), targetScale = 0.8f)
        ) {
            GlassListCard(
                modifier = Modifier.widthIn(max = 260.dp).padding(bottom = 10.dp),
                shape = RoundedCornerShape(16.dp),
                tintColor = Color(0xFFD6E4F0).copy(alpha = 0.75f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Hi $firstName! I'm your JustPass AI advisor \uD83C\uDF93",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    exampleQuestions.forEach { question ->
                        Surface(
                            onClick = { showAiBubble = false; onLiteRtClick() },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFE3F2FD),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                question, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        // FAB circle
        FloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (showAiBubble) {
                    showAiBubble = false
                    onLiteRtClick()
                } else {
                    showAiBubble = true
                }
            },
            containerColor = Color(0xFF4285F4),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(52.dp)
        ) {
            Icon(Icons.Default.School, "AI Advisor", Modifier.size(24.dp))
        }
    }
    // 100% attendance fireworks easter egg
    var showFireworks by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.attendanceData.attendanceWithExemption) {
        if (uiState.attendanceData.attendanceWithExemption >= 100.0 && uiState.attendanceData.enteredTillDate > 0) {
            showFireworks = true
            kotlinx.coroutines.delay(2500)
            showFireworks = false
        }
    }
    if (showFireworks) {
        FireworksOverlay()
    }
    } // Box
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    GlassListCard(modifier = modifier, shape = GlassCardShapeSmall) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
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

@Composable
private fun DashboardTile(
    title: String, subtitle: String, icon: ImageVector, iconTint: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    GlassListCard(modifier = modifier.clickable(onClick = onClick), shape = GlassCardShapeSmall) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(icon, title, tint = iconTint, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetCgpaDialogs(
    viewModel: DashboardViewModel,
    securePrefs: SecurePreferences,
    showSetup: Boolean,
    onDismissSetup: () -> Unit,
    showDetail: Boolean,
    onDismissDetail: () -> Unit,
    cgpaResult: TargetCgpaResult?,
    targetCgpa: Float,
    onTargetChanged: (Float) -> Unit = {},
    onShowSetup: () -> Unit = {},
    onRequestDetail: () -> Unit = {}
) {
    // ── Setup dialog ──
    if (showSetup) {
        // Dropdown options: 6.0, 6.5, 7.0, ... 10.0
        val cgpaOptions = remember { (12..20).map { it / 2f } } // 6.0 to 10.0 in 0.5 steps
        var selectedCgpa by remember {
            mutableFloatStateOf(if (targetCgpa > 0f) targetCgpa else 8.0f)
        }
        var expanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { onDismissSetup() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, null, tint = Color(0xFF42A5F5), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Target CGPA", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            },
            text = {
                Column {
                    Text("What CGPA do you want to achieve?", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = String.format("%.1f", selectedCgpa),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Target CGPA") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF42A5F5)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            containerColor = Color(0xFF1E2A3A)
                        ) {
                            cgpaOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            String.format("%.1f", option),
                                            fontWeight = if (option == selectedCgpa) FontWeight.Bold else FontWeight.Normal,
                                            color = if (option == selectedCgpa) Color(0xFF42A5F5) else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = { selectedCgpa = option; expanded = false }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Based on absolute grading (91→O, 81→A+, 71→A, etc.)",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTargetCgpa(selectedCgpa)
                    onTargetChanged(selectedCgpa)
                    onDismissSetup()
                    onRequestDetail()
                }) {
                    Text("Set", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (targetCgpa > 0f) {
                    TextButton(onClick = {
                        viewModel.updateTargetCgpa(0f)
                        onTargetChanged(0f)
                        onDismissSetup()
                    }) { Text("Remove", color = Color(0xFFFF5252)) }
                }
                TextButton(onClick = { onDismissSetup() }) { Text("Cancel") }
            },
            containerColor = Color(0xFF1E2A3A)
        )
    }

    // ── Detail dialog with per-subject breakdown ──
    if (showDetail && cgpaResult != null) {
        AlertDialog(
            onDismissRequest = { onDismissDetail() },
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, null, tint = Color(0xFF42A5F5), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Target: ${String.format("%.1f", cgpaResult.targetCgpa)} CGPA",
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Current CGPA: ${String.format("%.2f", cgpaResult.currentCgpa)} | Need SGPA: ${String.format("%.2f", cgpaResult.requiredSgpa)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (!cgpaResult.isAchievable) {
                        GlassListSurface(
                            shape = RoundedCornerShape(10.dp),
                            tintColor = Color(0xFFFF5252).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                cgpaResult.message,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF8A80),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    cgpaResult.subjects.forEach { subj ->
                        if (subj.credits == 0) {
                            // 0-credit audit course — just pass
                            GlassListSurface(
                                shape = RoundedCornerShape(10.dp),
                                tintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(subj.courseTitle, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                                    Text("${subj.courseCode} · 0 credits (audit)", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Just score above 33/65 in each test",
                                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                        color = Color(0xFF00E676))
                                }
                            }
                        } else {
                            // Graded course — full breakdown
                            GlassListSurface(
                                shape = RoundedCornerShape(10.dp),
                                tintColor = if (subj.isPossible) Color(0xFF42A5F5).copy(alpha = 0.08f)
                                           else Color(0xFFFF5252).copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(subj.courseTitle, fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2)
                                            Text("${subj.courseCode} · ${subj.credits} credits",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (subj.isPossible) Color(0xFF42A5F5).copy(alpha = 0.2f)
                                                    else Color(0xFFFF5252).copy(alpha = 0.2f)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(subj.requiredGrade, fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (subj.isPossible) Color(0xFF42A5F5)
                                                       else Color(0xFFFF5252))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (subj.hasCa2Pending || subj.hasCa1Pending) {
                                                Text("IAT-1: ${String.format("%.0f", subj.caMarksScored)}/${String.format("%.0f", subj.caMarksMax)}",
                                                    fontSize = 12.sp, color = Color(0xFF00E676))
                                            } else {
                                                Text("CA: ${String.format("%.0f", subj.caMarksScored)}/${String.format("%.0f", subj.caMarksMax)}",
                                                    fontSize = 12.sp, color = Color(0xFF00E676))
                                            }
                                            Text("Sem Exam: min ${subj.requiredEseMarks}/100",
                                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                color = when {
                                                    !subj.isPossible -> Color(0xFFFF5252)
                                                    subj.isAlreadySecured -> Color(0xFF00E676)
                                                    subj.requiredEseMarks >= 80 -> Color(0xFFFF9800)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                })
                                        }

                                        if (subj.hasCa1Pending && subj.ca1Needed != null) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text("Test 1 needed: min ${subj.ca1Needed}/${subj.ca1Max}",
                                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFAB00))
                                        }
                                        if ((subj.hasCa2Pending || subj.hasCa1Pending) && subj.ca2Needed != null) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            val label = if (subj.ca2Name.contains("LAB", true)) "Lab" else "Test 2"
                                            Text("$label needed: min ${subj.ca2Needed}/${subj.ca2Max}",
                                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFAB00))
                                        }
                                    }

                                    if (subj.isAlreadySecured) {
                                        Text("Just pass the exam (45+)",
                                            fontSize = 11.sp, color = Color(0xFF00E676).copy(alpha = 0.8f))
                                    } else if (!subj.isPossible) {
                                        Text("Cannot achieve this grade",
                                            fontSize = 11.sp, color = Color(0xFFFF5252).copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("* Based on absolute grading scale. Actual grades may vary with relative grading.",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            },
            confirmButton = {
                TextButton(onClick = { onDismissDetail() }) {
                    Text("Close", color = Color(0xFF42A5F5))
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissDetail(); onShowSetup() }) {
                    Text("Change Target", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = Color(0xFF1E2A3A)
        )
    }
}

/**
 * iOS-style rolling number picker — each digit scrolls smoothly top-to-bottom
 * like an alarm time picker, with 3D perspective depth.
 */
@Composable
private fun SlotMachineNumber(
    value: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 52.sp,
    fontWeight: FontWeight = FontWeight.Black,
    color: Color = Color.White,
    letterSpacing: androidx.compose.ui.unit.TextUnit = (-1).sp
) {
    // Measure digit height for smooth scrolling
    val density = androidx.compose.ui.platform.LocalDensity.current
    val digitHeightDp = with(density) { fontSize.toPx() * 1.2f }

    Row(verticalAlignment = Alignment.CenterVertically) {
        value.forEachIndexed { index, char ->
            if (char.isDigit()) {
                val targetDigit = char.digitToInt()
                val reelDuration = remember { 1400 + (index * 180) + (0..300).random() }
                val scrollPos = remember { Animatable(0f) }

                LaunchedEffect(targetDigit) {
                    val cycles = 2 + (0..1).random() // 2-3 full rotations
                    val totalScroll = (cycles * 10 + targetDigit).toFloat()
                    scrollPos.snapTo(0f)
                    scrollPos.animateTo(
                        totalScroll,
                        animationSpec = tween(
                            durationMillis = reelDuration,
                            easing = androidx.compose.animation.core.EaseOutQuart
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .height(with(density) { (digitHeightDp * 1.1f).toDp() })
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    val frac = scrollPos.value - scrollPos.value.toInt()
                    val curr = scrollPos.value.toInt() % 10
                    val prev = (curr - 1 + 10) % 10
                    val next = (curr + 1) % 10

                    // Three digits stacked: prev (above), current (center), next (below).
                    // Scale + alpha formulas on prev/next are designed so the state of a
                    // digit at frac=1 matches its state at frac=0 one position up the
                    // stack — that way the integer tick (e.g. scrollPos 26.999 → 27.000)
                    // renders no visible jump. Previously `prev` was fixed at scale 0.85
                    // and alpha 0.3, while `curr` was 1.0/1.0 — so the moment a digit
                    // transitioned from curr to prev you'd see it snap-shrink and snap-fade
                    // ("correction after settling" complaint).

                    // Previous digit — scrolling out the top. Starts as a clone of the
                    // prior curr (scale 1, alpha 1) at frac=0 and fades/shrinks as it
                    // rolls away.
                    Text(
                        prev.toString(),
                        fontSize = fontSize, fontWeight = fontWeight,
                        color = color.copy(alpha = (1f - frac).coerceIn(0f, 1f)),
                        letterSpacing = letterSpacing,
                        modifier = Modifier.graphicsLayer {
                            translationY = (-digitHeightDp) - (frac * digitHeightDp)
                            val s = 1f - 0.15f * frac
                            scaleX = s; scaleY = s
                        }
                    )
                    // Current digit — center, full opacity.
                    Text(
                        curr.toString(),
                        fontSize = fontSize, fontWeight = fontWeight,
                        color = color,
                        letterSpacing = letterSpacing,
                        modifier = Modifier.graphicsLayer {
                            translationY = -(frac * digitHeightDp)
                        }
                    )
                    // Next digit — coming up from below. Reaches scale 1 / alpha 1 at
                    // frac=1, matching the curr state it's about to become.
                    Text(
                        next.toString(),
                        fontSize = fontSize, fontWeight = fontWeight,
                        color = color.copy(alpha = frac.coerceIn(0f, 1f)),
                        letterSpacing = letterSpacing,
                        modifier = Modifier.graphicsLayer {
                            translationY = digitHeightDp - (frac * digitHeightDp)
                            val s = 0.85f + 0.15f * frac
                            scaleX = s; scaleY = s
                        }
                    )
                }
            } else {
                Text(char.toString(), fontSize = fontSize, fontWeight = fontWeight,
                    color = color, letterSpacing = letterSpacing)
            }
        }
    }
}

/**
 * Fireworks celebration for 100% attendance — particles burst from center.
 */
@Composable
private fun FireworksOverlay() {
    val particles = remember {
        List(60) {
            val angle = Math.random() * 2 * Math.PI
            val speed = 200f + (Math.random() * 400f).toFloat()
            val color = listOf(
                Color(0xFFFF5252), Color(0xFF00E676), Color(0xFF42A5F5),
                Color(0xFFFFD600), Color(0xFFE040FB), Color(0xFFFF6D00),
                Color(0xFF00E5FF), Color(0xFFFFEA00)
            ).random()
            Triple(angle.toFloat(), speed, color)
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2000, easing = LinearEasing))
    }
    val t = progress.value

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 3f
        particles.forEach { (angle, speed, color) ->
            val x = cx + kotlin.math.cos(angle.toDouble()).toFloat() * speed * t
            val y = cy + kotlin.math.sin(angle.toDouble()).toFloat() * speed * t + 200f * t * t // gravity
            val alpha = (1f - t).coerceIn(0f, 1f)
            val radius = (4f * (1f - t)).coerceAtLeast(1f)
            drawCircle(color = color.copy(alpha = alpha), radius = radius, center = Offset(x, y))
        }
    }
}
