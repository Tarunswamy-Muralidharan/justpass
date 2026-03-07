package com.example.attendancewidgetlaudea.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendancewidgetlaudea.data.model.Component
import com.example.attendancewidgetlaudea.data.model.CourseMarks
import com.example.attendancewidgetlaudea.data.model.SubComponent
import com.example.attendancewidgetlaudea.ui.viewmodel.CAMarksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CAMarksScreen(
    viewModel: CAMarksViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CA Marks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.fetchCAMarks() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.fetchCAMarks() }) {
                            Text("Retry")
                        }
                    }
                }
                uiState.courseMarksList.isEmpty() -> {
                    Text(
                        text = "No CA marks available",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.courseMarksList) { course ->
                            CourseCard(course = course)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(course: CourseMarks) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Course Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.courseCode,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = course.courseTitle,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Total marks display
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = course.testDetails.total.getSecuredDisplay(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = getMarksColor(
                            course.testDetails.total.getSecuredAsDouble(),
                            course.testDetails.total.getMaxAsDouble()
                        )
                    )
                    Text(
                        text = "/ ${course.testDetails.total.getMaxAsDouble().toInt()}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            // Expanded details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider()

                    course.testDetails.components.forEach { component ->
                        ComponentCard(component = component)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentCard(component: Component) {
    var expanded by remember { mutableStateOf(false) }
    val hasSubComponents = component.hasSubComponent && !component.subComponents.isNullOrEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (hasSubComponents) Modifier.clickable { expanded = !expanded }
                        else Modifier
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = component.name,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    if (hasSubComponents) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                component.marks?.let { marks ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${marks.scaled.getSecuredDisplay()} / ${marks.scaled.getMaxAsDouble().toInt()}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = getMarksColor(
                                marks.scaled.getSecuredAsDouble(),
                                marks.scaled.getMaxAsDouble()
                            )
                        )
                        if (marks.actual.getMaxAsDouble() != marks.scaled.getMaxAsDouble()) {
                            Text(
                                text = "(${marks.actual.getSecuredDisplay()} / ${marks.actual.getMaxAsDouble().toInt()})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Sub-components
            if (hasSubComponents) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        component.subComponents?.forEach { subComponent ->
                            SubComponentRow(subComponent = subComponent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubComponentRow(subComponent: SubComponent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = subComponent.name,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        subComponent.marks?.let { marks ->
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${marks.scaled.getSecuredDisplay()} / ${marks.scaled.getMaxAsDouble().toInt()}",
                    fontSize = 13.sp,
                    color = getMarksColor(
                        marks.scaled.getSecuredAsDouble(),
                        marks.scaled.getMaxAsDouble()
                    )
                )
                if (marks.actual.getMaxAsDouble() != marks.scaled.getMaxAsDouble()) {
                    Text(
                        text = "(${marks.actual.getSecuredDisplay()} / ${marks.actual.getMaxAsDouble().toInt()})",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun getMarksColor(secured: Double?, max: Double): androidx.compose.ui.graphics.Color {
    if (secured == null) return MaterialTheme.colorScheme.onSurface

    val percentage = if (max > 0) (secured / max) * 100 else 0.0

    return when {
        percentage >= 75 -> MaterialTheme.colorScheme.primary
        percentage >= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
}
