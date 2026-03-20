@file:Suppress("UNUSED_PARAMETER")
package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.Component
import com.example.attendancewidgetlaudea.data.model.CourseMarks
import com.example.attendancewidgetlaudea.data.model.SubComponent
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.viewmodel.CAMarksViewModel
import io.github.fletchmckee.liquid.LiquidState

@Composable
fun CAMarksScreen(cardState: LiquidState, viewModel: CAMarksViewModel = viewModel(), onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header — lightweight glass for crisp text readability
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = com.example.attendancewidgetlaudea.ui.components.GlassCardShape
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface) }
                Text("CA Marks", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { viewModel.fetchCAMarks() }, enabled = !uiState.isLoading) { Icon(Icons.Default.Refresh, "Refresh",
                    tint = MaterialTheme.colorScheme.onSurface) }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 130.dp),
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
    val secured = course.testDetails.total.getSecuredAsDouble()
    val max = course.testDetails.total.getMaxAsDouble()
    val accentColor = getMarksAccentColor(secured, max)
    val tintColor = accentColor.copy(alpha = 0.06f)

    GlassListCard(modifier = Modifier.fillMaxWidth(), tintColor = tintColor) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Colored left accent bar
            Box(modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accentColor))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(course.courseCode, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(course.courseTitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (max <= 0.0 && (secured == null || secured <= 0.0)) {
                        // Check if any components have marks even if total is 0
                        val hasAnyMarks = course.testDetails.components.any { c ->
                            c.marks?.scaled?.getSecuredAsDouble()?.let { it > 0 } == true ||
                            (c.hasSubComponent && c.subComponents?.any { s ->
                                s.marks?.scaled?.getSecuredAsDouble()?.let { it > 0 } == true
                            } == true)
                        }
                        Text(
                            if (hasAnyMarks) "Tap to expand" else "Awaiting marks",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(course.testDetails.total.getSecuredDisplay(), fontWeight = FontWeight.Bold, fontSize = 22.sp,
                                color = accentColor)
                            Text("/ ${max.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand")
                }
                AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
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
                    Text(component.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (hasSub) { Spacer(Modifier.width(4.dp)); Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp)) }
                }
                component.marks?.let { m ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${m.scaled.getSecuredDisplay()} / ${m.scaled.getMaxAsDouble().toInt()}", fontWeight = FontWeight.Medium, fontSize = 14.sp,
                            color = getMarksColor(m.scaled.getSecuredAsDouble(), m.scaled.getMaxAsDouble()))
                        if (m.actual.getMaxAsDouble() != m.scaled.getMaxAsDouble())
                            Text("(${m.actual.getSecuredDisplay()} / ${m.actual.getMaxAsDouble().toInt()})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (hasSub) AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    component.subComponents?.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            s.marks?.let { m ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${m.scaled.getSecuredDisplay()} / ${m.scaled.getMaxAsDouble().toInt()}", fontSize = 13.sp,
                                        color = getMarksColor(m.scaled.getSecuredAsDouble(), m.scaled.getMaxAsDouble()))
                                    if (m.actual.getMaxAsDouble() != m.scaled.getMaxAsDouble())
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

private val MarksGreen = Color(0xFF4CAF50)
private val MarksYellow = Color(0xFFFFC107)
private val MarksRed = Color(0xFFF44336)
private val MarksGray = Color(0xFF9E9E9E)

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
