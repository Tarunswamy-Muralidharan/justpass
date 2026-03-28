package com.example.attendancewidgetlaudea.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.attendancewidgetlaudea.ui.viewmodel.ResultViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgpaCalculatorScreen(
    onBack: () -> Unit,
    userDepartment: Department? = null,
    userBatchYear: Int? = null,
    viewModel: CgpaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val resultViewModel: ResultViewModel = viewModel()
    val resultState by resultViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (uiState.semesterGrades.isEmpty()) {
            viewModel.initialize(userDepartment, userBatchYear)
        }
    }

    LaunchedEffect(resultState.grades) {
        if (resultState.grades.isNotEmpty()) {
            viewModel.applyResults(resultState.grades)
        }
    }

    // Build elective suggestions from regulation data + result data
    val electiveSuggestions = remember(uiState.department, uiState.regulation, resultState.grades) {
        val allElectives = getAllElectives(uiState.department, uiState.regulation).map { "${it.name} (${it.code})" }
        // Also include any elective names from results that aren't in the curriculum
        val resultNames = resultState.grades
            .filter { entry ->
                val curriculum = getCurriculum(uiState.department, uiState.regulation)
                val curriculumCodes = curriculum.values.flatten()
                    .filter { !it.isElective }
                    .map { it.code.trim().uppercase() }
                    .toSet()
                val code = entry.courseCode?.trim()?.uppercase() ?: ""
                code.isNotEmpty() && code !in curriculumCodes
            }
            .mapNotNull { it.courseTitle?.trim() }
        (allElectives + resultNames).distinct()
    }

    var showDeptPicker by remember { mutableStateOf(false) }
    var ocrProcessing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // OCR: parse grades from image or PDF
    val scope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        ocrProcessing = true
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        if (mimeType == "application/pdf") {
            // PDF: render each page to bitmap and OCR
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw Exception("Can't open PDF")
                    val renderer = PdfRenderer(fd)
                    val allText = StringBuilder()
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        val image = InputImage.fromBitmap(bmp, 0)
                        val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
                        allText.appendLine(result.text)
                        bmp.recycle()
                    }
                    renderer.close()
                    fd.close()
                    val parsed = parseGradesFromOcr(allText.toString())
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (parsed.isNotEmpty()) {
                            viewModel.applyOcrGrades(parsed)
                            Toast.makeText(context, "Found ${parsed.size} grades from PDF", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No grades detected in PDF. Try a clearer file.", Toast.LENGTH_LONG).show()
                        }
                        ocrProcessing = false
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "PDF error: ${e.message}", Toast.LENGTH_LONG).show()
                        ocrProcessing = false
                    }
                }
            }
        } else {
            // Image: direct OCR
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        android.util.Log.d("OCR_DEBUG", "Full text:\n${visionText.text}")
                        for (block in visionText.textBlocks) {
                            for (bline in block.lines) {
                                android.util.Log.d("OCR_LINE", bline.text)
                            }
                        }
                        val parsed = parseGradesFromOcr(visionText.text)
                        if (parsed.isNotEmpty()) {
                            viewModel.applyOcrGrades(parsed)
                            Toast.makeText(context, "Found ${parsed.size} grades", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No grades detected. Try a clearer image.", Toast.LENGTH_LONG).show()
                        }
                        ocrProcessing = false
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "OCR failed: ${it.message}", Toast.LENGTH_LONG).show()
                        ocrProcessing = false
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                ocrProcessing = false
            }
        }
    }

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

        Spacer(modifier = Modifier.height(8.dp))

        // Import grades from image/PDF — camera tilt + flash animation
        val infiniteTransition = rememberInfiniteTransition(label = "cam")
        // Tilt: 0° → -12° → 0° → snap pause
        val camTilt by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ), label = "cam_tilt"
        )
        // Flash: brief white flash at the "snap" moment
        val flashAlpha by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ), label = "cam_flash"
        )
        // Convert linear 0-1 to tilt: tilt left (0-0.3), snap back (0.3-0.4), hold (0.4-1.0)
        val tiltAngle = when {
            camTilt < 0.3f -> -12f * (camTilt / 0.3f)
            camTilt < 0.4f -> -12f * (1f - (camTilt - 0.3f) / 0.1f)
            else -> 0f
        }
        // Flash only at snap moment (0.3-0.45)
        val flashValue = when {
            flashAlpha in 0.30f..0.35f -> (flashAlpha - 0.30f) / 0.05f
            flashAlpha in 0.35f..0.45f -> 1f - (flashAlpha - 0.35f) / 0.10f
            else -> 0f
        }
        GlassListCard(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !ocrProcessing) {
                filePickerLauncher.launch("*/*")
            },
            shape = GlassCardShapeSmall,
            tintColor = Color(0xFF7C4DFF).copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            Color(0xFF7C4DFF).copy(alpha = 0.18f + flashValue * 0.3f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (ocrProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = Color(0xFF7C4DFF))
                    } else {
                        Icon(Icons.Default.CameraAlt, null,
                            tint = Color.White.copy(alpha = 0.7f + flashValue * 0.3f),
                            modifier = Modifier.size(20.dp).rotate(tiltAngle).scale(1f + flashValue * 0.1f))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (ocrProcessing) "Scanning for grades..." else "Import Grades from Image",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("Upload a photo or PDF of your grade sheet",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                    Text(String.format("%.3f", cgpa), fontSize = 36.sp, fontWeight = FontWeight.Black,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Semester ${uiState.selectedSemester}", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("${String.format("%.1f", totalCredits)} credits", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (currentSgpa > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("SGPA", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format("%.3f", currentSgpa), fontSize = 24.sp,
                            fontWeight = FontWeight.Bold, color = getGpaColor(currentSgpa))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                FilledTonalButton(
                    onClick = { viewModel.resetSemester(uiState.selectedSemester) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFFF5252).copy(alpha = 0.12f)
                    )
                ) {
                    Text("Reset", fontSize = 11.sp, color = Color(0xFFFF5252),
                        fontWeight = FontWeight.SemiBold)
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
                                if (sg.subject.isElective && !sg.customName.isNullOrBlank())
                                    sg.customName else sg.subject.name,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (expanded) 3 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row {
                                Text(
                                    if (sg.subject.isElective) "Elective" else sg.subject.code,
                                    fontSize = 10.sp,
                                    color = if (sg.subject.isElective) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
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

                    // Elective name field + Grade picker (expanded)
                    if (expanded) {
                        if (sg.subject.isElective) {
                            Spacer(modifier = Modifier.height(8.dp))
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            val query = sg.customName ?: ""
                            val filtered = remember(query, electiveSuggestions) {
                                if (query.isBlank()) electiveSuggestions
                                else electiveSuggestions.filter { it.contains(query, ignoreCase = true) }
                            }
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded && filtered.isNotEmpty(),
                                onExpandedChange = { dropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { name ->
                                        viewModel.setElectiveName(uiState.selectedSemester, index, name)
                                        dropdownExpanded = true
                                    },
                                    placeholder = {
                                        Text(
                                            if (electiveSuggestions.isNotEmpty()) "Search or type elective name"
                                            else "Enter elective name",
                                            fontSize = 12.sp
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        cursorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded && filtered.isNotEmpty(),
                                    onDismissRequest = { dropdownExpanded = false },
                                    containerColor = Color(0xFF1E2A3A)
                                ) {
                                    filtered.forEach { suggestion ->
                                        DropdownMenuItem(
                                            text = { Text(suggestion, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = {
                                                viewModel.setElectiveName(uiState.selectedSemester, index, suggestion)
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
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

/** Parse course codes and grades from OCR text.
 *  ML Kit reads tables column-by-column, so we extract course codes and
 *  grade points separately and match them positionally.
 *  Grade points (10,9,8,7,6,5) are far more reliable via OCR than letter grades. */
private fun parseGradesFromOcr(text: String): List<Pair<String, String>> {
    val upper = text.uppercase()
    val lines = upper.lines().map { it.trim() }.filter { it.isNotEmpty() }

    // 1) Extract course codes (2-4 letters + 3-4 digits, e.g., CS3492, GE3151)
    val codePattern = Regex("^[A-Z]{2,4}\\d{3,4}$")
    val courseCodes = lines.filter { codePattern.matches(it) }

    if (courseCodes.isEmpty()) return emptyList()

    // 2) Try line-by-line first (code and grade on same line)
    val lineResults = mutableListOf<Pair<String, String>>()
    val fullCodePattern = Regex("[A-Z]{2,4}\\d{3,4}")
    for (line in lines) {
        val codeMatch = fullCodePattern.find(line) ?: continue
        val afterCode = line.substring(codeMatch.range.last + 1)
        // Look for "CREDITS GRADE GP PASS" pattern
        val rowMatch = Regex("\\d+\\s+(O|A\\+?|B\\+?|C|RA|AB)\\s+\\d+").find(afterCode)
        if (rowMatch != null) {
            lineResults.add(codeMatch.value to rowMatch.groupValues[1])
        }
    }
    if (lineResults.size >= courseCodes.size / 2) return lineResults.distinctBy { it.first }

    // 3) Column-by-column approach: match course codes with grade points positionally
    // Grade points are standalone numbers: 10, 9, 8, 7, 6, 5, 0
    // They appear as a consecutive block of single numbers after the grades column
    val gpToGrade = mapOf(10 to "O", 9 to "A+", 8 to "A", 7 to "B+", 6 to "B", 5 to "C", 0 to "RA")

    // Find consecutive runs of lines that are valid grade points
    val numberLines = lines.mapIndexedNotNull { idx, line ->
        val num = line.toIntOrNull()
        if (num != null && num in gpToGrade) idx to num else null
    }

    // Find the longest consecutive run of grade-point-like numbers
    // that matches the number of course codes
    var bestRun = listOf<Pair<Int, Int>>()
    var currentRun = mutableListOf<Pair<Int, Int>>()
    for (i in numberLines.indices) {
        if (currentRun.isEmpty() || numberLines[i].first == numberLines[i - 1].first + 1) {
            currentRun.add(numberLines[i])
        } else {
            if (currentRun.size > bestRun.size) bestRun = currentRun.toList()
            currentRun = mutableListOf(numberLines[i])
        }
    }
    if (currentRun.size > bestRun.size) bestRun = currentRun.toList()

    // Match: if the run length equals course code count, pair them
    if (bestRun.size == courseCodes.size) {
        return courseCodes.zip(bestRun.map { gpToGrade[it.second]!! })
    }

    // 4) Fallback: try matching grade points that are >= courseCodes count
    // Take the first N grade points that match
    val allGradePoints = lines.mapNotNull { line ->
        val num = line.toIntOrNull()
        if (num != null && num in gpToGrade) gpToGrade[num] else null
    }
    if (allGradePoints.size >= courseCodes.size) {
        return courseCodes.zip(allGradePoints.take(courseCodes.size))
    }

    return emptyList()
}
