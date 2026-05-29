package com.justpass.app.ui.screens.qpapers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.justpass.app.data.model.Department
import com.justpass.app.data.model.PaperCategory
import com.justpass.app.data.model.UploadIntent
import com.justpass.app.data.model.detectDepartment
import com.justpass.app.data.model.getCurriculum
import com.justpass.app.ui.components.GlassCardShape
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.QPaperViewModel
import io.github.fletchmckee.liquid.LiquidState
import java.util.Calendar

@Composable
fun QPaperReuploadScreen(
    cardState: LiquidState,
    viewModel: QPaperViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.reuploadState.collectAsStateWithLifecycle()
    val regulation = viewModel.effectiveRegulation
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    var department by remember { mutableStateOf<Department?>(viewModel.userDepartment) }
    var semester by remember { mutableIntStateOf(state.sourcePaper?.semester ?: 1) }
    var subjectCode by remember { mutableStateOf("") }
    var subjectName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(state.sourcePaper?.categoryEnum ?: PaperCategory.CA1) }
    var examYear by remember { mutableIntStateOf(state.sourcePaper?.examYear ?: currentYear) }

    // When the destination paper lands, exit.
    LaunchedEffect(state.uploadedPaper) {
        if (state.uploadedPaper != null) {
            viewModel.clearReupload()
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        QPapersHeader(
            title = "Re-upload Elsewhere",
            subtitle = state.sourcePaper?.let { "From ${it.subjectCode} · ${it.categoryEnum.label} · ${it.examYear}" }
                ?: "Pick new destination",
            onBack = onBack,
        )

        when {
            state.isPreparing -> Box(
                modifier = Modifier.fillMaxWidth().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RoseFourLoader(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Fetching source PDF…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.bytes == null && !state.isPreparing -> Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.errorMessage ?: "Source PDF unavailable.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                // Form
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PickerRow(label = "Department") {
                        DepartmentPicker(selected = department) { department = it }
                    }
                    PickerRow(label = "Semester") {
                        SemesterPicker(selected = semester) { semester = it }
                    }
                    PickerRow(label = "Subject") {
                        SubjectPicker(
                            department = department,
                            semester = semester,
                            regulation = regulation.name,
                            selectedCode = subjectCode,
                            onPick = { code, name ->
                                subjectCode = code
                                subjectName = name
                            },
                        )
                    }
                    PickerRow(label = "Category") {
                        CategoryRadio(selected = category) { category = it }
                    }
                    PickerRow(label = "Exam year") {
                        YearPicker(year = examYear, currentYear = currentYear) { examYear = it }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    state.errorMessage?.let {
                        Text(
                            it,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }

                    val canSubmit = department != null && subjectCode.isNotBlank() && !state.isSubmitting
                    Button(
                        onClick = {
                            val dept = department ?: return@Button
                            viewModel.submitReupload(
                                UploadIntent(
                                    department = dept.name,
                                    subjectCode = subjectCode,
                                    subjectName = subjectName.ifBlank { subjectCode },
                                    semester = semester,
                                    regulation = regulation.name,
                                    category = category,
                                    examYear = examYear,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (state.isSubmitting) {
                            RoseFourLoader(modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submitting…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Re-upload as Approved", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(160.dp))
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun DepartmentPicker(selected: Department?, onPick: (Department) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    DropdownField(
        text = selected?.let { "${it.shortName} · ${it.displayName}" } ?: "Pick department",
        expanded = expanded,
        onClick = { expanded = !expanded },
    )
    AnimatedVisibility(expanded) {
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
            Column {
                Department.entries.forEach { d ->
                    DropdownRow(
                        text = "${d.shortName} · ${d.displayName}",
                        selected = selected == d,
                        onClick = { onPick(d); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SemesterPicker(selected: Int, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    DropdownField(
        text = "Semester $selected",
        expanded = expanded,
        onClick = { expanded = !expanded },
    )
    AnimatedVisibility(expanded) {
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
            Column {
                (1..8).forEach { s ->
                    DropdownRow(
                        text = "Semester $s",
                        selected = selected == s,
                        onClick = { onPick(s); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectPicker(
    department: Department?,
    semester: Int,
    regulation: String,
    selectedCode: String,
    onPick: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val subjects = remember(department, semester, regulation) {
        val reg = com.justpass.app.data.model.Regulation.entries.firstOrNull { it.name == regulation }
        if (department != null && reg != null) {
            getCurriculum(department, reg)[semester].orEmpty()
        } else emptyList()
    }
    DropdownField(
        text = if (selectedCode.isBlank()) "Pick subject" else selectedCode,
        expanded = expanded,
        enabled = subjects.isNotEmpty(),
        onClick = { if (subjects.isNotEmpty()) expanded = !expanded },
    )
    AnimatedVisibility(expanded) {
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
            Column {
                subjects.forEach { sub ->
                    DropdownRow(
                        text = "${sub.code} · ${sub.name}",
                        selected = selectedCode == sub.code,
                        onClick = { onPick(sub.code, sub.name); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRadio(selected: PaperCategory, onPick: (PaperCategory) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaperCategory.entries.forEach { cat ->
            val active = selected == cat
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Color(0xFF7C4DFF).copy(alpha = 0.25f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) Color(0xFF7C4DFF) else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onPick(cat) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    cat.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun YearPicker(year: Int, currentYear: Int, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    DropdownField(
        text = year.toString(),
        expanded = expanded,
        onClick = { expanded = !expanded },
    )
    AnimatedVisibility(expanded) {
        GlassListCard(modifier = Modifier.fillMaxWidth(), shape = GlassCardShape) {
            Column {
                (currentYear downTo (currentYear - 8)).forEach { y ->
                    DropdownRow(
                        text = y.toString(),
                        selected = year == y,
                        onClick = { onPick(y); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownField(
    text: String,
    expanded: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                Color.White.copy(alpha = if (expanded) 0.35f else 0.12f),
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                if (expanded) "▴" else "▾",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DropdownRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFF7C4DFF).copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

