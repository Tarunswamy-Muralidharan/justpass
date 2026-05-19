@file:Suppress("UNUSED_PARAMETER")
package com.justpass.app.ui.screens

import com.justpass.app.ui.components.AdBanner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.data.model.Component
import com.justpass.app.data.model.CourseMarks
import com.justpass.app.data.model.SubComponent
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.CAMarksViewModel
import io.github.fletchmckee.liquid.LiquidState

@Composable
fun CAMarksScreen(
    cardState: LiquidState,
    viewModel: CAMarksViewModel = viewModel(),
    onBack: () -> Unit,
    onClassCompareClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Compare icon visibility: Remote Config flag AND this user's class has
    // already crossed the k=15 anonymity floor. We never render "need N more
    // classmates" — the feature is invisible to the user until their class
    // hits 15 contributors.
    val flagEnabled = remember {
        try {
            com.google.firebase.remoteconfig.FirebaseRemoteConfig
                .getInstance()
                .getBoolean("class_compare_enabled")
        } catch (_: Exception) { false }
    }
    var unlocked by remember {
        mutableStateOf(
            com.justpass.app.data.local.SecurePreferences
                .getInstance(context).classCompareUnlocked
        )
    }
    val showCompare = flagEnabled && unlocked

    // Background probe — when the flag is on but this user hasn't unlocked
    // yet, ping the Worker to see if the class size has crossed 15. Cheap
    // single GET; result is cached in SecurePreferences so future visits
    // skip the call once unlocked.
    LaunchedEffect(flagEnabled, unlocked) {
        if (flagEnabled && !unlocked) {
            try {
                val crossed = com.justpass.app.data.repository.ClassMarksRepository
                    .getInstance(context).probeClassUnlocked()
                if (crossed) unlocked = true
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header — lightweight glass for crisp text readability
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = com.justpass.app.ui.components.GlassCardShape
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface) }
                Text("CA Marks", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface)
                if (showCompare) {
                    IconButton(onClick = onClassCompareClick) {
                        Icon(Icons.Default.Leaderboard, "Compare with class",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                IconButton(onClick = { viewModel.fetchCAMarks() }, enabled = !uiState.isLoading) { Icon(Icons.Default.Refresh, "Refresh",
                    tint = MaterialTheme.colorScheme.onSurface) }
            }
        }

        AdBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), screenName = "CAMarks")

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> RoseFourLoader(modifier = Modifier.size(48.dp).align(Alignment.Center))
                uiState.errorMessage != null -> {
                    Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.errorMessage ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.fetchCAMarks() }) { Text("Retry") }
                    }
                }
                uiState.courseMarksList.isEmpty() -> Text("No CA marks available", modifier = Modifier.align(Alignment.Center))
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.courseMarksList) { CourseCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: CourseMarks) {
    var expanded by remember { mutableStateOf(false) }
    val secured = course.testDetails.total.safeScaled.getSecuredAsDouble()
    val max = course.testDetails.total.safeScaled.getMaxAsDouble()
    val accentColor = getMarksAccentColor(secured, max)
    val tintColor = accentColor.copy(alpha = 0.12f)

    GlassListCard(modifier = Modifier.fillMaxWidth(), tintColor = tintColor) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Colored left accent bar
            Box(modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(accentColor))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f, fill = true).padding(end = 12.dp)) {
                        Text(
                            course.courseCode,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            course.courseTitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                    if (max <= 0.0 && (secured == null || secured <= 0.0)) {
                        val hasAnyMarks = course.testDetails.components.any { c ->
                            c.marks?.scaled?.getSecuredAsDouble()?.let { it > 0 } == true ||
                            (c.hasSubComponent && c.subComponents?.any { s ->
                                s.marks?.scaled?.getSecuredAsDouble()?.let { it > 0 } == true
                            } == true)
                        }
                        Box(
                            modifier = Modifier.width(132.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                if (hasAnyMarks) "Tap to expand" else "Awaiting marks",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    } else {
                        val tabularStyle = androidx.compose.ui.text.TextStyle(
                            fontFeatureSettings = "tnum"
                        )
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .background(
                                    accentColor.copy(alpha = 0.25f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    course.testDetails.total.safeScaled.getSecuredDisplay(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = accentColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    style = tabularStyle,
                                    modifier = Modifier.width(78.dp)
                                )
                                Text(
                                    " / ${max.toInt()}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                    style = tabularStyle,
                                    modifier = Modifier.width(56.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top
                    ) + fadeIn(animationSpec = tween(durationMillis = 250, delayMillis = 50)),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top
                    ) + fadeOut(animationSpec = tween(durationMillis = 150))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        course.testDetails.components.forEach { ComponentCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentCard(component: Component) {
    var expanded by remember { mutableStateOf(false) }
    val hasSub = component.hasSubComponent && !component.subComponents.isNullOrEmpty()
    GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().then(if (hasSub) Modifier.clickable { expanded = !expanded } else Modifier).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(component.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (hasSub) { Spacer(Modifier.width(4.dp)); Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp)) }
                }
                component.marks?.let { m ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${m.safeScaled.getSecuredDisplay()} / ${m.safeScaled.getMaxAsDouble().toInt()}", fontWeight = FontWeight.Medium, fontSize = 14.sp,
                            color = getMarksColor(m.safeScaled.getSecuredAsDouble(), m.safeScaled.getMaxAsDouble()))
                        if (m.actual.getMaxAsDouble() != m.safeScaled.getMaxAsDouble())
                            Text("(${m.actual.getSecuredDisplay()} / ${m.actual.getMaxAsDouble().toInt()})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (hasSub) AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 40)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(durationMillis = 140))
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    component.subComponents?.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            s.marks?.let { m ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${m.safeScaled.getSecuredDisplay()} / ${m.safeScaled.getMaxAsDouble().toInt()}", fontSize = 13.sp,
                                        color = getMarksColor(m.safeScaled.getSecuredAsDouble(), m.safeScaled.getMaxAsDouble()))
                                    if (m.actual.getMaxAsDouble() != m.safeScaled.getMaxAsDouble())
                                        Text("(${m.actual.getSecuredDisplay()} / ${m.actual.getMaxAsDouble().toInt()})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val MarksGreen = Color(0xFF00E676)
private val MarksYellow = Color(0xFFFFD600)
private val MarksRed = Color(0xFFFF5252)
private val MarksGray = Color(0xFFB0BEC5)

private fun getMarksAccentColor(secured: Double?, max: Double): Color {
    if (secured == null || max <= 0.0) return MarksGray
    val pct = (secured / max) * 100
    return when {
        pct >= 75 -> MarksGreen
        pct >= 50 -> MarksYellow
        else -> MarksRed
    }
}

@Composable
private fun getMarksColor(secured: Double?, max: Double): Color {
    if (secured == null) return MaterialTheme.colorScheme.onSurface
    val pct = if (max > 0) (secured / max) * 100 else 0.0
    return when {
        pct >= 75 -> MarksGreen
        pct >= 50 -> MarksYellow
        else -> MarksRed
    }
}
