package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.Department
import com.example.attendancewidgetlaudea.data.model.Regulation
import com.example.attendancewidgetlaudea.data.model.SyllabusSubject
import com.example.attendancewidgetlaudea.ui.components.GlassCardShapeSmall
import com.example.attendancewidgetlaudea.ui.components.GlassListCard
import com.example.attendancewidgetlaudea.ui.viewmodel.SyllabusViewModel
import io.github.fletchmckee.liquid.LiquidState

@Composable
fun SyllabusScreen(
    cardState: LiquidState,
    userDepartment: Department?,
    userRegulation: Regulation = Regulation.R2021,
    onBack: () -> Unit,
    viewModel: SyllabusViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userDepartment, userRegulation) {
        if (uiState.subjects.isEmpty() && !uiState.isLoading) {
            viewModel.loadSyllabus(userDepartment, userRegulation)
        }
    }

    // If a subject is selected, show its detail
    if (uiState.selectedSubject != null) {
        SyllabusDetailScreen(
            subject = uiState.selectedSubject!!,
            onBack = { viewModel.selectSubject(null) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Syllabus", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (uiState.department != null) {
                        val regLabel = if (uiState.regulation == Regulation.R2025) "R2025" else "R2021"
                        Text("${uiState.department!!.displayName} · $regLabel",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Search bar
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = GlassCardShapeSmall
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearch(it) },
                    placeholder = { Text("Search by code or title...", fontSize = 13.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f).height(48.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
            }
        }

        // Semester filter tabs
        if (uiState.semesters.isNotEmpty()) {
            val isDark = isSystemInDarkTheme()
            GlassListCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = GlassCardShapeSmall
            ) {
                ScrollableTabRow(
                    selectedTabIndex = if (uiState.selectedSemester == 0) 0
                        else uiState.semesters.indexOf(uiState.selectedSemester) + 1,
                    edgePadding = 4.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    divider = {}, indicator = {}
                ) {
                    // "All" tab
                    val allSelected = uiState.selectedSemester == 0
                    Tab(
                        selected = allSelected,
                        onClick = { viewModel.selectSemester(0) },
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                        text = {
                            Text("All", fontSize = 12.sp,
                                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (allSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                        }
                    )
                    uiState.semesters.forEach { sem ->
                        val selected = uiState.selectedSemester == sem
                        val label = if (sem == 0) "Electives" else "Sem $sem"
                        Tab(
                            selected = selected,
                            onClick = { viewModel.selectSemester(sem) },
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                            text = {
                                Text(label, fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val filtered = viewModel.getFilteredSubjects()
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No subjects found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.code }) { subject ->
                            SubjectCard(subject) { viewModel.selectSubject(subject) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectCard(subject: SyllabusSubject, onClick: () -> Unit) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = GlassCardShapeSmall
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(subject.code, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    if (subject.credits.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(subject.credits, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(subject.title, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (subject.semester > 0) {
                Text("S${subject.semester}", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("PE", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = Color(0xFFFFA000))
            }
        }
    }
}

@Composable
private fun SyllabusDetailScreen(subject: SyllabusSubject, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        // Header
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(subject.code, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(subject.title, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Credits + Semester info bar
        GlassListCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = GlassCardShapeSmall,
            tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (subject.semester > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${subject.semester}", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Semester", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PE", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFA000))
                        Text("Elective", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (subject.credits.isNotBlank()) {
                    val parts = subject.credits.split(" ").mapNotNull { it.toIntOrNull() }
                    if (parts.size == 4) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${parts[0]}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Lecture", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${parts[1]}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Tutorial", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${parts[2]}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Practical", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${parts[3]}", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676))
                            Text("Credits", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Syllabus content
        GlassListCard(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = GlassCardShapeSmall
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Parse and style the syllabus text
                val sections = parseSyllabusSections(subject.syllabus)
                sections.forEach { section ->
                    when {
                        section.startsWith("UNIT") || section.startsWith("COURSE OBJECTIVES") ||
                        section.startsWith("COURSE OUTCOMES") || section.startsWith("TOTAL") ||
                        section.startsWith("TEXT BOOK") || section.startsWith("REFERENCE") -> {
                            Spacer(Modifier.height(12.dp))
                            Text(section.substringBefore("\n"), fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            val body = section.substringAfter("\n", "")
                            if (body.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(body.trim(), fontSize = 12.sp, lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        else -> {
                            Text(section.trim(), fontSize = 12.sp, lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

private fun parseSyllabusSections(text: String): List<String> {
    // Split on known section headers while keeping the header
    val pattern = Regex("(?=(?:UNIT [IVX]+|COURSE OBJECTIVES|COURSE OUTCOMES|TOTAL[: ]|TEXT BOOK|REFERENCE))")
    val sections = pattern.split(text).filter { it.isNotBlank() }
    return if (sections.isEmpty()) listOf(text) else sections
}
