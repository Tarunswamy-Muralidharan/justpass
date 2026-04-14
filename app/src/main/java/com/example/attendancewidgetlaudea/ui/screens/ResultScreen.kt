package com.example.attendancewidgetlaudea.ui.screens

import com.example.attendancewidgetlaudea.ui.components.AdBanner
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.local.SecurePreferences
import com.example.attendancewidgetlaudea.data.model.Department
import com.example.attendancewidgetlaudea.data.model.GradeEntry
import com.example.attendancewidgetlaudea.data.model.detectDepartment
import com.example.attendancewidgetlaudea.data.model.getCurriculum
import com.example.attendancewidgetlaudea.data.model.getRegulationForBatch
import com.example.attendancewidgetlaudea.ui.components.GlassCardShape
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.components.GlassListSurface
import com.example.attendancewidgetlaudea.ui.components.RoseFourLoader
import com.example.attendancewidgetlaudea.ui.viewmodel.ResultViewModel
import io.github.fletchmckee.liquid.LiquidState

private val TabShape = RoundedCornerShape(12.dp)

@Composable
fun ResultScreen(
    cardState: LiquidState,
    viewModel: ResultViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = GlassCardShape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("Semester Result", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.fetchResult() },
                    enabled = !uiState.isLoading
                ) {
                    Icon(Icons.Default.Refresh, "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        AdBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), screenName = "Result")

        // Semester tabs
        if (uiState.semesters.isNotEmpty()) {
            GlassListCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                @Suppress("DEPRECATION")
                ScrollableTabRow(
                    selectedTabIndex = uiState.semesters.indexOf(uiState.selectedSemester).coerceAtLeast(0),
                    edgePadding = 4.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    divider = {}, indicator = {}
                ) {
                    uiState.semesters.forEach { sem ->
                        val isSelected = sem == uiState.selectedSemester
                        val tabBg = if (isSelected) {
                            if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.7f)
                        } else Color.Transparent

                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectSemester(sem) },
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                                .clip(TabShape).background(tabBg),
                            text = {
                                Text(
                                    "Sem $sem",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    RoseFourLoader(modifier = Modifier.size(48.dp).align(Alignment.Center))
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.fetchResult() }) { Text("Retry") }
                    }
                }
                uiState.grades.isEmpty() -> {
                    Text("No results available",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val filteredGrades = remember(uiState.grades, uiState.selectedSemester) {
                        if (uiState.selectedSemester == 0) uiState.grades
                        else uiState.grades.filter { it.semester == uiState.selectedSemester }
                    }

                    // Calculate SGPA — use API credits if available, else look up from curriculum
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val sgpa = remember(filteredGrades) {
                        // First try API credits
                        val apiCredits = filteredGrades.sumOf { it.getCreditsValue() }
                        if (apiCredits > 0) {
                            val weighted = filteredGrades.sumOf { it.gradePoint * it.getCreditsValue() }
                            weighted.toDouble() / apiCredits
                        } else {
                            // Look up credits from curriculum data
                            val prefs = SecurePreferences.getInstance(context)
                            val batchYear = prefs.batchYear.takeIf { it > 0 }
                                ?: prefs.rollNumber?.drop(4)?.take(2)?.toIntOrNull()?.let { 2000 + it } ?: 2023
                            val dept = detectDepartment(prefs.cachedDepartment)
                                ?: detectDepartment(prefs.programmeName)
                                ?: Department.CSE
                            val reg = getRegulationForBatch(batchYear)
                            val curriculum = getCurriculum(dept, reg)
                            // Build course code -> credits map from all semesters
                            val creditMap = curriculum.values.flatten()
                                .filter { it.code != "--" }
                                .associate { it.code to it.credits }

                            var totalCredits = 0.0
                            var totalWeighted = 0.0
                            for (g in filteredGrades) {
                                // Look up credits; skip courses not in curriculum (mandatory/non-credit)
                                val c = creditMap[g.courseCode] ?: continue
                                totalCredits += c
                                totalWeighted += g.gradePoint * c
                            }
                            if (totalCredits > 0) totalWeighted / totalCredits else 0.0
                        }
                    }
                    val passCount = filteredGrades.count { it.isPassed() }
                    val examName = filteredGrades.firstOrNull()?.examName ?: ""

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // GPA summary card
                        item(key = "gpa_summary") {
                            GlassListCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    if (examName.isNotEmpty()) {
                                        Text(examName, fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                if (sgpa > 0) String.format("%.3f", sgpa) else "--",
                                                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                                color = getGpaColor(sgpa))
                                            Text("SGPA", fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("$passCount/${filteredGrades.size}", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                                color = if (passCount == filteredGrades.size) Color(0xFF4CAF50) else Color(0xFFFFC107))
                                            Text("Passed", fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${filteredGrades.size}", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface)
                                            Text("Subjects", fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        // Subject cards
                        items(filteredGrades, key = { "${it.courseCode}_${it.semester}_${it.attempt}" }) { entry ->
                            GradeCard(entry, isDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeCard(entry: GradeEntry, isDark: Boolean) {
    val grade = entry.letterGrade ?: "--"
    val gradeColor = getGradeColor(grade)
    val tintColor = gradeColor.copy(alpha = if (isDark) 0.08f else 0.05f)

    GlassListCard(modifier = Modifier.fillMaxWidth(), tintColor = tintColor) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Grade accent bar
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(gradeColor))
            Row(
                modifier = Modifier.weight(1f).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.courseCode ?: "--", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.courseTitle ?: "", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("GP: ${entry.gradePoint}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entry.attempt > 1) {
                            Text("Attempt: ${entry.attempt}", fontSize = 11.sp,
                                color = Color(0xFFFFC107))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(grade, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = gradeColor)
                    if (entry.isPassed()) {
                        GlassListSurface(
                            shape = RoundedCornerShape(4.dp),
                            tintColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                        ) {
                            Text("PASS", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else {
                        GlassListSurface(
                            shape = RoundedCornerShape(4.dp),
                            tintColor = Color(0xFFF44336).copy(alpha = 0.15f)
                        ) {
                            Text(entry.status?.uppercase()?.take(6) ?: "FAIL", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFFF44336),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun getGradeColor(grade: String): Color {
    return when (grade.uppercase()) {
        "O", "S" -> Color(0xFF4CAF50)
        "A+", "A" -> Color(0xFF66BB6A)
        "B+", "B" -> Color(0xFF42A5F5)
        "C+", "C" -> Color(0xFFFFC107)
        "D" -> Color(0xFFFF9800)
        "E" -> Color(0xFFFF5722)
        "U", "F", "RA" -> Color(0xFFF44336)
        "AB" -> Color(0xFF9E9E9E)
        else -> Color(0xFF9E9E9E)
    }
}

private fun getGpaColor(gpa: Double): Color {
    return when {
        gpa >= 9.0 -> Color(0xFF4CAF50)
        gpa >= 8.0 -> Color(0xFF66BB6A)
        gpa >= 7.0 -> Color(0xFF42A5F5)
        gpa >= 6.0 -> Color(0xFFFFC107)
        gpa > 0 -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
}
