package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.*
import com.example.attendancewidgetlaudea.ui.components.GlassCardShape
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.viewmodel.CgpaViewModel

@Composable
fun CgpaCalculatorScreen(
    onBack: () -> Unit,
    userDepartment: Department? = null,
    userBatchYear: Int? = null,
    viewModel: CgpaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState.semesterGrades.isEmpty()) {
            viewModel.initialize(userDepartment, userBatchYear)
        }
    }

    var showDeptPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 130.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("GPA Calculator", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Department selector + regulation info
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Department", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(uiState.department.displayName, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = { showDeptPicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Change", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(uiState.regulation.displayName, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CGPA overview card
        val cgpa = viewModel.getCGPA()
        val filledCount = viewModel.getFilledSemesterCount()
        if (filledCount > 0) {
            GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CGPA", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(String.format("%.2f", cgpa), fontSize = 36.sp, fontWeight = FontWeight.Black,
                        color = getGpaColor(cgpa))
                    Text("across $filledCount semester${if (filledCount > 1) "s" else ""}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Per-semester SGPA row
                    val sgpas = uiState.availableSemesters.mapNotNull { sem ->
                        val sgpa = viewModel.getSGPA(sem)
                        if (sgpa > 0) sem to sgpa else null
                    }
                    if (sgpas.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            sgpas.forEach { (sem, sgpa) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(String.format("%.1f", sgpa), fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold, color = getGpaColor(sgpa))
                                    Text("Sem $sem", fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Semester tabs
        ScrollableTabRow(
            selectedTabIndex = uiState.availableSemesters.indexOf(uiState.selectedSemester).coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp,
            divider = {}
        ) {
            uiState.availableSemesters.forEach { sem ->
                val sgpa = viewModel.getSGPA(sem)
                Tab(
                    selected = uiState.selectedSemester == sem,
                    onClick = { viewModel.selectSemester(sem) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Sem $sem", fontSize = 13.sp)
                            if (sgpa > 0) {
                                Text(String.format("%.1f", sgpa), fontSize = 10.sp,
                                    color = getGpaColor(sgpa), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Current semester SGPA
        val currentSgpa = viewModel.getSGPA(uiState.selectedSemester)
        val currentGrades = uiState.semesterGrades[uiState.selectedSemester] ?: emptyList()
        val totalCredits = currentGrades.sumOf { it.subject.credits }

        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Semester ${uiState.selectedSemester}", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("${String.format("%.1f", totalCredits)} credits", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (currentSgpa > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("SGPA", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format("%.2f", currentSgpa), fontSize = 24.sp,
                            fontWeight = FontWeight.Bold, color = getGpaColor(currentSgpa))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Subject grade cards
        currentGrades.forEachIndexed { index, sg ->
            var expanded by remember(uiState.selectedSemester, index) { mutableStateOf(false) }
            GlassListCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                shape = GlassCardShapeSmall
            ) {
                Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                sg.subject.name,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (expanded) 3 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row {
                                Text(
                                    if (sg.subject.isElective) "Elective" else sg.subject.code,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(" | ", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    "${String.format("%.1f", sg.subject.credits)} cr",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Grade chip
                        val gradeColor = sg.grade?.let { getGradeColor(it) }
                            ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        Box(
                            modifier = Modifier
                                .background(gradeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                sg.grade?.label ?: "Tap",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = gradeColor
                            )
                        }
                    }

                    // Grade picker (expanded)
                    if (expanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LetterGrade.gradableGrades.forEach { grade ->
                                val isSelected = sg.grade == grade
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) getGradeColor(grade).copy(alpha = 0.2f)
                                            else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.setGrade(
                                                uiState.selectedSemester, index,
                                                if (isSelected) null else grade
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(grade.label, fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) getGradeColor(grade) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${grade.gradePoint}", fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grade scale reference
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShapeSmall) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Grade Scale", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LetterGrade.gradableGrades.forEach { g ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(g.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = getGradeColor(g))
                            Text("${g.gradePoint}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("SGPA = \u03A3(Credits \u00D7 GP) / \u03A3(Credits)", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // Department picker dialog
    if (showDeptPicker) {
        AlertDialog(
            onDismissRequest = { showDeptPicker = false },
            title = { Text("Select Department") },
            text = {
                Column {
                    Department.entries.forEach { dept ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectDepartment(dept)
                                    showDeptPicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.department == dept,
                                onClick = {
                                    viewModel.selectDepartment(dept)
                                    showDeptPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(dept.shortName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(dept.displayName, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeptPicker = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF1E2A3A)
        )
    }
}

@Composable
private fun getGpaColor(gpa: Double): Color {
    return when {
        gpa >= 9.0 -> Color(0xFF00E676)
        gpa >= 8.0 -> Color(0xFF69F0AE)
        gpa >= 7.0 -> Color(0xFFFFEA00)
        gpa >= 6.0 -> Color(0xFFFFAB40)
        gpa >= 5.0 -> Color(0xFFFF8A80)
        else -> Color(0xFFFF5252)
    }
}

@Composable
private fun getGradeColor(grade: LetterGrade): Color {
    return when (grade) {
        LetterGrade.O -> Color(0xFF00E676)
        LetterGrade.A_PLUS -> Color(0xFF69F0AE)
        LetterGrade.A -> Color(0xFF40C4FF)
        LetterGrade.B_PLUS -> Color(0xFFFFEA00)
        LetterGrade.B -> Color(0xFFFFAB40)
        LetterGrade.C -> Color(0xFFFF8A80)
        LetterGrade.RA, LetterGrade.AB -> Color(0xFFFF5252)
    }
}
