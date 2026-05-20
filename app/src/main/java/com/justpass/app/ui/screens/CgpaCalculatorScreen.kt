package com.justpass.app.ui.screens

import com.justpass.app.ui.components.AdBanner
import com.justpass.app.ui.components.AnimatedSlideInTabs
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
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
import com.justpass.app.data.model.*
import com.justpass.app.ui.components.GlassCardShape
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.viewmodel.CgpaViewModel
import com.justpass.app.ui.viewmodel.ResultViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgpaCalculatorScreen(
    onBack: () -> Unit,
    userDepartment: Department? = null,
    userBatchYear: Int? = null,
    cardState: io.github.fletchmckee.liquid.LiquidState? = null,
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
            // PDF: ML Kit OCR pipeline (PdfRenderer → Bitmap → preprocess → OCR)
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    var parsed = emptyList<Pair<String, String>>()
                    val fd2 = context.contentResolver.openFileDescriptor(uri, "r") ?: throw Exception("Can't open PDF")
                    val renderer = PdfRenderer(fd2)
                    val allOcrText = StringBuilder()
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        val processed = preprocessForOcr(bmp)
                        bmp.recycle()
                        val image = InputImage.fromBitmap(processed, 0)
                        val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
                        allOcrText.appendLine(result.text)
                        processed.recycle()
                    }
                    renderer.close()
                    fd2.close()
                    parsed = parseGradesFromOcr(allOcrText.toString())

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (parsed.isNotEmpty()) {
                            val semesters = viewModel.applyOcrGrades(parsed)
                            val semText = if (semesters.size > 1) " across Sem ${semesters.sorted().joinToString(", ")}" else ""
                            val tier = if (parsed.isNotEmpty()) "" else " (OCR)"
                            Toast.makeText(context, "Found ${parsed.size} grades$semText", Toast.LENGTH_SHORT).show()
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
            // Image: upscale 2x for better OCR, then spatial matching
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // Load bitmap and upscale for better small-text recognition
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val original = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (original == null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Cannot read image", Toast.LENGTH_LONG).show()
                            ocrProcessing = false
                        }
                        return@launch
                    }

                    // Scale up 2x — helps ML Kit detect small grade point digits
                    val scaled = Bitmap.createScaledBitmap(original, original.width * 2, original.height * 2, true)
                    original.recycle()

                    // Preprocess: grayscale + contrast + threshold for colored backgrounds
                    val processed = preprocessForOcr(scaled)
                    scaled.recycle()

                    val image = InputImage.fromBitmap(processed, 0)
                    val visionText = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
                    processed.recycle()

                    android.util.Log.d("OCR_DEBUG", "Full text:\n${visionText.text}")
                    for (block in visionText.textBlocks) {
                        for (bline in block.lines) {
                            android.util.Log.d("OCR_LINE", bline.text)
                        }
                    }

                    // Try spatial matching first (uses bounding boxes), fall back to text-based
                    val parsed = parseGradesSpatial(visionText).ifEmpty {
                        parseGradesFromOcr(visionText.text)
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (parsed.isNotEmpty()) {
                            val semesters = viewModel.applyOcrGrades(parsed)
                            val semText = if (semesters.size > 1) " across Sem ${semesters.sorted().joinToString(", ")}" else ""
                            val passCount = visionText.text.uppercase().lines().count { it.trim() == "PASS" }
                            val total = if (passCount > 0) passCount else parsed.size
                            if (parsed.size < total) {
                                Toast.makeText(context, "Found ${parsed.size}/$total grades$semText. Check remaining manually.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Found ${parsed.size} grades$semText", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No grades detected. Try a clearer, closer photo.", Toast.LENGTH_LONG).show()
                        }
                        ocrProcessing = false
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "OCR error: ${e.message}", Toast.LENGTH_LONG).show()
                        ocrProcessing = false
                    }
                }
            }
        }
    }

    var gpaMode by remember { mutableIntStateOf(0) } // 0 = Calculator, 1 = Results

    if (gpaMode == 1) {
        // ── Embedded Results view ──
        ResultScreen(
            cardState = io.github.fletchmckee.liquid.rememberLiquidState(),
            onBack = { gpaMode = 0 }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp)
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

        AdBanner(modifier = Modifier.padding(vertical = 4.dp), screenName = "CgpaCalculator")

        Spacer(modifier = Modifier.height(8.dp))

        // Results tile — tap to view semester results
        GlassListCard(
            modifier = Modifier.fillMaxWidth()
                .clickable { gpaMode = 1 },
            shape = GlassCardShapeSmall,
            tintColor = Color(0xFF4CAF50).copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.School, null, Modifier.size(22.dp),
                    tint = Color(0xFF4CAF50))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Semester Results", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("View grades from SIS", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

        // Import grades — 4-phase camera animation:
        // 1) Ready (0.0-0.20)  2) Focusing (0.20-0.45)  3) Capture (0.45-0.65)  4) Saved (0.65-1.0)
        val infiniteTransition = rememberInfiniteTransition(label = "cam")
        val camPhase by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3500, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ), label = "cam_phase"
        )

        // Phase-derived values
        // Focus brackets: fade in during phase 2, out during phase 3
        val bracketAlpha = when {
            camPhase < 0.20f -> 0f
            camPhase < 0.30f -> (camPhase - 0.20f) / 0.10f
            camPhase < 0.45f -> 1f
            camPhase < 0.55f -> 1f - (camPhase - 0.45f) / 0.10f
            else -> 0f
        }
        // Focus bracket scale: starts wide, tightens
        val bracketScale = when {
            camPhase < 0.20f -> 1.3f
            camPhase < 0.45f -> 1.3f - 0.3f * ((camPhase - 0.20f) / 0.25f)
            else -> 1f
        }
        // Red focus dot: appears during focusing
        val redDotAlpha = when {
            camPhase < 0.25f -> 0f
            camPhase < 0.30f -> (camPhase - 0.25f) / 0.05f
            camPhase < 0.45f -> if ((camPhase * 20).toInt() % 2 == 0) 1f else 0.3f // blink
            else -> 0f
        }
        // Flash: burst at capture moment
        val flashValue = when {
            camPhase < 0.45f -> 0f
            camPhase < 0.50f -> (camPhase - 0.45f) / 0.05f
            camPhase < 0.60f -> 1f - (camPhase - 0.50f) / 0.10f
            else -> 0f
        }
        // Camera scale: slight pulse on capture
        val camScale = when {
            camPhase < 0.45f -> 1f
            camPhase < 0.50f -> 1f + 0.15f * ((camPhase - 0.45f) / 0.05f)
            camPhase < 0.55f -> 1.15f - 0.15f * ((camPhase - 0.50f) / 0.05f)
            else -> 1f
        }
        // Checkmark + image: fade in during saved phase
        val savedAlpha = when {
            camPhase < 0.65f -> 0f
            camPhase < 0.75f -> (camPhase - 0.65f) / 0.10f
            camPhase < 0.92f -> 1f
            else -> 1f - (camPhase - 0.92f) / 0.08f // fade out before restart
        }
        // Camera icon fades during saved phase
        val camAlpha = when {
            camPhase < 0.65f -> 1f
            camPhase < 0.75f -> 1f - 0.5f * ((camPhase - 0.65f) / 0.10f)
            camPhase < 0.92f -> 0.5f
            else -> 0.5f + 0.5f * ((camPhase - 0.92f) / 0.08f)
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
                            Color(0xFF7C4DFF).copy(alpha = 0.18f + flashValue * 0.4f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (ocrProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = Color(0xFF7C4DFF))
                    } else {
                        // Focus brackets (phase 2)
                        if (bracketAlpha > 0f) {
                            Canvas(modifier = Modifier.size(28.dp).scale(bracketScale)) {
                                val s = size.width
                                val len = s * 0.25f
                                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.5.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                val bracketColor = Color.White.copy(alpha = bracketAlpha * 0.9f)
                                // Top-left
                                drawLine(bracketColor, Offset(0f, len), Offset(0f, 0f), stroke.width)
                                drawLine(bracketColor, Offset(0f, 0f), Offset(len, 0f), stroke.width)
                                // Top-right
                                drawLine(bracketColor, Offset(s - len, 0f), Offset(s, 0f), stroke.width)
                                drawLine(bracketColor, Offset(s, 0f), Offset(s, len), stroke.width)
                                // Bottom-left
                                drawLine(bracketColor, Offset(0f, s - len), Offset(0f, s), stroke.width)
                                drawLine(bracketColor, Offset(0f, s), Offset(len, s), stroke.width)
                                // Bottom-right
                                drawLine(bracketColor, Offset(s - len, s), Offset(s, s), stroke.width)
                                drawLine(bracketColor, Offset(s, s - len), Offset(s, s), stroke.width)
                            }
                        }
                        // Camera icon
                        Icon(Icons.Default.CameraAlt, null,
                            tint = Color.White.copy(alpha = 0.7f * camAlpha + flashValue * 0.3f),
                            modifier = Modifier.size(20.dp).scale(camScale))
                        // Red focus dot (phase 2)
                        if (redDotAlpha > 0f) {
                            Box(modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(5.dp)
                                .background(Color(0xFFFF1744).copy(alpha = redDotAlpha), CircleShape))
                        }
                        // Saved: checkmark badge (phase 4)
                        if (savedAlpha > 0f) {
                            Box(modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(1.dp)
                                .size(14.dp)
                                .background(Color(0xFF00C853).copy(alpha = savedAlpha), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null,
                                    tint = Color.White.copy(alpha = savedAlpha),
                                    modifier = Modifier.size(9.dp))
                            }
                        }
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

        // Semester tabs (slide-in-from-right with stagger)
        AnimatedSlideInTabs(
            items = uiState.availableSemesters.map { "Sem $it" },
            selectedIndex = uiState.availableSemesters.indexOf(uiState.selectedSemester).coerceAtLeast(0),
            onSelect = { idx -> viewModel.selectSemester(uiState.availableSemesters[idx]) },
            animationKey = uiState.availableSemesters,
            liquidState = cardState,
            showSubTextRow = true,
            subText = { idx ->
                val sgpa = viewModel.getSGPA(uiState.availableSemesters[idx])
                if (sgpa > 0) String.format("%.1f", sgpa) to getGpaColor(sgpa) else null
            },
        )

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

/** Spatial OCR parser — uses ML Kit bounding boxes to match course codes
 *  with grade points by Y-coordinate (same row in the table).
 *  Two-pass: GPs first (most reliable), then letter grades for remaining.
 *  This prevents misalignment when ML Kit skips reading some cells. */
private fun parseGradesSpatial(visionText: Text): List<Pair<String, String>> {
    val codeRegex = Regex("\\b([A-Z]{2}\\d{4})\\b")
    val gpToGrade = mapOf(10 to "O", 9 to "A+", 8 to "A", 7 to "B+", 6 to "B", 5 to "C", 0 to "RA")
    val letterGrades = listOf("A+", "B+", "RA", "AB", "O", "A", "B", "C")

    // Fuzzy grade point
    fun fuzzyGP(s: String): Int? {
        s.trim().toIntOrNull()?.let { if (it in gpToGrade) return it }
        val t = s.trim()
        if (t.length == 2 && t[0] in "1IlL") return 10
        return null
    }

    // Fuzzy letter grade (conservative — no ambiguous mappings)
    fun fuzzyGrade(s: String): String? {
        val t = s.replace(Regex("\\s+"), "").uppercase()
        if (t in letterGrades) return t
        return when (t) {
            "0" -> "O"      // zero → O
            "B4" -> "B+"    // B+ misread
            "8+" -> "B+"    // B+ misread
            "8" -> "B"      // B misread
            else -> null     // "E", "Q" etc. too ambiguous — skip
        }
    }

    // Collect all lines with bounding box center Y and X
    data class OcrItem(val text: String, val centerY: Float, val centerX: Float, val right: Float)

    val allItems = mutableListOf<OcrItem>()
    for (block in visionText.textBlocks) {
        for (line in block.lines) {
            val box = line.boundingBox ?: continue
            allItems.add(OcrItem(
                text = line.text.uppercase().trim(),
                centerY = (box.top + box.bottom) / 2f,
                centerX = (box.left + box.right) / 2f,
                right = box.right.toFloat()
            ))
        }
    }

    if (allItems.isEmpty()) return emptyList()

    // Estimate row height from course code Y-coordinate gaps
    val codeItems = allItems.filter {
        val m = codeRegex.find(it.text)
        m != null && !m.groupValues[1].startsWith("NM")
    }
    if (codeItems.size < 2) return emptyList()

    val sortedCodes = codeItems.sortedBy { it.centerY }
    val yGaps = sortedCodes.zipWithNext().map { (a, b) -> b.centerY - a.centerY }.filter { it > 5 }
    val rowHeight = if (yGaps.isNotEmpty()) yGaps.average().toFloat() else 30f
    // Tight tolerance: less than half a row height to avoid cross-row matching
    val yTolerance = rowHeight * 0.45f

    android.util.Log.d("OCR_SPATIAL", "Row height: $rowHeight, tolerance: $yTolerance, codes: ${sortedCodes.size}")
    for (c in sortedCodes) {
        val code = codeRegex.find(c.text)?.groupValues?.get(1) ?: "?"
        android.util.Log.d("OCR_SPATIAL", "  Code: $code at Y=${c.centerY.toInt()} X=${c.centerX.toInt()}")
    }

    // Identify the grade point column: GP items that are to the right of course codes
    // and within the table area (filter out GPA/CGPA summary numbers)
    val codeMaxX = sortedCodes.maxOf { it.right }
    val codeMinY = sortedCodes.minOf { it.centerY } - rowHeight
    val codeMaxY = sortedCodes.maxOf { it.centerY } + rowHeight

    val gpItems = allItems.mapNotNull { item ->
        val gp = fuzzyGP(item.text)
        // Must be: to the right of codes, within table Y range, short text (1-2 chars)
        if (gp != null && item.centerX > codeMaxX && item.text.trim().length <= 2
            && item.centerY >= codeMinY && item.centerY <= codeMaxY)
            Triple(item, gp, gpToGrade[gp]!!) else null
    }

    val letterItems = allItems.mapNotNull { item ->
        val g = fuzzyGrade(item.text)
        // Must be: to the right of codes, within table Y range, short text
        if (g != null && item.centerX > codeMaxX && item.text.trim().length <= 2
            && item.centerY >= codeMinY && item.centerY <= codeMaxY)
            item to g else null
    }

    android.util.Log.d("OCR_SPATIAL", "GP items in table: ${gpItems.size}, letter items: ${letterItems.size}")
    for (gp in gpItems) android.util.Log.d("OCR_SPATIAL", "  GP: ${gp.second} at Y=${gp.first.centerY.toInt()} X=${gp.first.centerX.toInt()}")

    // === PASS 1: Order-preserving GP matching ===
    // Both codes and GPs are in the same table, so they share top-to-bottom order.
    // This handles perspective skew where a GP's Y drifts closer to an adjacent row.
    val results = mutableMapOf<String, String>()
    val sortedGPs = gpItems.sortedBy { it.first.centerY }
    val validCodes = sortedCodes.mapNotNull { item ->
        val code = codeRegex.find(item.text)?.groupValues?.get(1)
        if (code != null && !code.startsWith("NM")) item to code else null
    }

    // For each GP (in Y order), find the best code (in Y order) that hasn't been matched,
    // only looking forward from the last matched code position to preserve row order.
    var nextCodeIdx = 0
    val wideYTolerance = rowHeight * 1.2f // wider tolerance since we enforce order

    for ((gpItem, gpVal, gradeName) in sortedGPs) {
        var bestIdx = -1
        var bestDist = Float.MAX_VALUE

        for (i in nextCodeIdx until validCodes.size) {
            val dist = kotlin.math.abs(validCodes[i].first.centerY - gpItem.centerY)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
            // If distance is growing and we've passed the GP's Y level, stop looking
            if (validCodes[i].first.centerY > gpItem.centerY + wideYTolerance) break
        }

        if (bestIdx >= 0 && bestDist < wideYTolerance) {
            val code = validCodes[bestIdx].second
            results[code] = gradeName
            nextCodeIdx = bestIdx + 1
            android.util.Log.d("OCR_SPATIAL", "$code → $gradeName (GP $gpVal, ΔY=${bestDist.toInt()}, ordered)")
        }
    }

    // === PASS 2: For unmatched codes, try letter grades ===
    val usedLetterItems = mutableSetOf<OcrItem>()
    for (codeItem in sortedCodes) {
        val code = codeRegex.find(codeItem.text)?.groupValues?.get(1) ?: continue
        if (code.startsWith("NM") || code in results) continue

        val letterMatch = letterItems
            .filter { it.first !in usedLetterItems }
            .filter { kotlin.math.abs(it.first.centerY - codeItem.centerY) < yTolerance }
            .minByOrNull { kotlin.math.abs(it.first.centerY - codeItem.centerY) }

        if (letterMatch != null) {
            results[code] = letterMatch.second
            usedLetterItems.add(letterMatch.first)
            android.util.Log.d("OCR_SPATIAL", "$code → ${letterMatch.second} (letter, ΔY=${kotlin.math.abs(letterMatch.first.centerY - codeItem.centerY).toInt()})")
        } else {
            android.util.Log.d("OCR_SPATIAL", "$code → NO MATCH (no GP or grade on same row)")
        }
    }

    val resultList = results.map { it.key to it.value }
    android.util.Log.d("OCR_SPATIAL", "Spatial result: ${resultList.size}/${sortedCodes.size} — $resultList")
    return resultList
}

/** Robust OCR grade parser for Anna University grade sheets.
 *  Handles any layout ML Kit produces: row-by-row, column-by-column, mixed blocks.
 *  Supports multi-semester PDFs by splitting on semester headers and parsing each section.
 *  Uses fuzzy matching for OCR errors and combines multiple data sources. */
/** Preprocess bitmap for better OCR on colored backgrounds (teal headers, light green cells).
 *  Converts to grayscale, boosts contrast, then applies adaptive thresholding to produce
 *  clean black text on white background. */
private fun preprocessForOcr(src: Bitmap): Bitmap {
    val width = src.width
    val height = src.height

    // Step 1: Convert to high-contrast grayscale
    val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(grayscale)
    val paint = Paint()
    // Increase contrast: multiply by 1.8, shift by -80
    val contrast = 1.8f
    val shift = -80f
    val cm = ColorMatrix(floatArrayOf(
        0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, shift,
        0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, shift,
        0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, shift,
        0f, 0f, 0f, 1f, 0f
    ))
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)

    // Step 2: Adaptive thresholding — pixels darker than threshold become black, rest white
    val pixels = IntArray(width * height)
    grayscale.getPixels(pixels, 0, width, 0, 0, width, height)
    val threshold = 140 // Tuned for teal/green backgrounds with dark text
    for (i in pixels.indices) {
        val gray = pixels[i] and 0xFF // Blue channel (all channels same after grayscale)
        pixels[i] = if (gray < threshold) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    grayscale.setPixels(pixels, 0, width, 0, 0, width, height)
    return grayscale
}

private fun parseGradesFromOcr(text: String): List<Pair<String, String>> {
    val upper = text.uppercase()

    // Detect multi-semester documents by looking for semester headers
    val semesterHeaderRegex = Regex("SEMESTER\\s*[-:]?\\s*[IVXL]+|SEM\\s*[-:]?\\s*\\d+|EXAMINATION\\s*[-:]?\\s*[A-Z]+\\s*\\d{4}", RegexOption.IGNORE_CASE)
    val headers = semesterHeaderRegex.findAll(upper).toList()

    // If multiple semester headers found, split and parse each section independently
    if (headers.size >= 2) {
        android.util.Log.d("OCR_PARSE", "Multi-semester PDF detected: ${headers.size} sections")
        val allGrades = mutableListOf<Pair<String, String>>()
        val seenCodes = mutableSetOf<String>()
        for (i in headers.indices) {
            val start = headers[i].range.first
            val end = if (i + 1 < headers.size) headers[i + 1].range.first else upper.length
            val section = upper.substring(start, end)
            android.util.Log.d("OCR_PARSE", "Parsing section ${i + 1}: ${headers[i].value}")
            val sectionGrades = parseGradesFromOcrSection(section)
            for (grade in sectionGrades) {
                if (grade.first !in seenCodes) {
                    allGrades.add(grade)
                    seenCodes.add(grade.first)
                }
            }
        }
        android.util.Log.d("OCR_PARSE", "Multi-semester total: ${allGrades.size} grades across ${headers.size} semesters")
        if (allGrades.isNotEmpty()) return allGrades
    }

    // Single semester or no headers — parse as one block
    return parseGradesFromOcrSection(upper)
}

/** Parse a single section (one semester) of OCR text for grades. */
private fun parseGradesFromOcrSection(text: String): List<Pair<String, String>> {
    val upper = text.uppercase()
    val lines = upper.lines().map { it.trim() }.filter { it.isNotEmpty() }

    // Strict: exactly 2 uppercase letters + exactly 4 digits (Anna University format)
    val codeRegex = Regex("\\b([A-Z]{2}\\d{4})\\b")
    val gpToGrade = mapOf(10 to "O", 9 to "A+", 8 to "A", 7 to "B+", 6 to "B", 5 to "C", 0 to "RA")
    val letterGrades = listOf("A+", "B+", "RA", "AB", "O", "A", "B", "C")
    // Roman numerals that appear as semester labels in grade sheets
    val romanNumerals = setOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII",
        "VL", "VIL", "VLL", "VII", "VILL", "VI", "VIL", "VLL")

    // --- Extract all unique course codes, filtering out noise ---
    val allCodes = mutableListOf<String>()
    for (line in lines) {
        for (m in codeRegex.findAll(line)) {
            val code = m.groupValues[1]
            // Skip NM prefix (Naan Mudhalvan — not counted in GPA)
            if (code.startsWith("NM")) continue
            if (code !in allCodes) allCodes.add(code)
        }
    }
    android.util.Log.d("OCR_PARSE", "Found ${allCodes.size} codes: $allCodes")
    if (allCodes.isEmpty()) return emptyList()

    // Count PASS lines to know expected subject count
    val passCount = lines.count { it == "PASS" }
    val expectedCount = if (passCount > 0) passCount else allCodes.size
    android.util.Log.d("OCR_PARSE", "Expected subjects: $expectedCount (PASS=$passCount, codes=${allCodes.size})")

    // --- Fuzzy grade point parser ---
    fun fuzzyGradePoint(s: String): Int? {
        val trimmed = s.trim()
        trimmed.toIntOrNull()?.let { if (it in gpToGrade) return it }
        // OCR misreads of "10": 1X, 1O, IO, lO, l0, I0
        if (trimmed.length == 2 && trimmed[0] in "1IlL") {
            val cleaned = trimmed.replace(Regex("[XxOoIl]"), "0")
            cleaned.toIntOrNull()?.let { if (it in gpToGrade) return it }
            return 10
        }
        return null
    }

    // --- Fuzzy letter grade ---
    fun fuzzyLetterGrade(s: String): String? {
        val t = s.replace(Regex("\\s+"), "")
        if (t in letterGrades) return t
        return when (t) {
            "0" -> "O"      // O misread as zero
            "Q" -> "O"      // O misread as Q
            "8+" -> "B+"    // B misread as 8
            "8" -> "B"      // B misread as 8
            "B4" -> "B+"    // B+ misread as B4
            "RI" -> "RA"
            "PA" -> "RA"
            else -> null
        }
    }

    // --- Check if a line is noise (Roman numeral, header, etc.) ---
    fun isNoiseLine(s: String): Boolean {
        val up = s.uppercase().replace(Regex("\\s+"), "")
        if (up in romanNumerals) return true
        if (Regex("^V+I*L*$").matches(up)) return true // catches Vi, Vll, VIl, etc.
        return false
    }

    // ====== Strategy 1: Row matching (code + grade on the same line) ======
    val rowResults = mutableListOf<Pair<String, String>>()
    for (line in lines) {
        val codeMatch = codeRegex.find(line) ?: continue
        val code = codeMatch.groupValues[1]
        if (code.startsWith("NM") || code !in allCodes) continue
        val afterCode = line.substring(codeMatch.range.last + 1).trim()
        if (afterCode.isEmpty()) continue

        val fullRow = Regex("\\b\\d+\\s+(O|A\\+|A|B\\+|B|C|RA|AB)\\s+\\d+").find(afterCode)
        if (fullRow != null) { rowResults.add(code to fullRow.groupValues[1]); continue }

        val gradeGp = Regex("\\b(O|A\\+|A|B\\+|B|C|RA|AB)\\s+(10|[0-9])\\b").find(afterCode)
        if (gradeGp != null) { rowResults.add(code to gradeGp.groupValues[1]); continue }

        val tokens = afterCode.split(Regex("\\s+"))
        val lastGp = tokens.lastOrNull()?.let { fuzzyGradePoint(it) }
        if (lastGp != null) { rowResults.add(code to gpToGrade[lastGp]!!); continue }

        for (t in tokens.reversed()) {
            val fg = fuzzyLetterGrade(t)
            if (fg != null) { rowResults.add(code to fg); break }
        }
    }
    val distinctRow = rowResults.distinctBy { it.first }
    android.util.Log.d("OCR_PARSE", "Strategy 1 (row): ${distinctRow.size} — $distinctRow")
    if (distinctRow.size >= allCodes.size / 2) return distinctRow

    // ====== Strategy 2: Grade points after POINT header (most reliable for column layout) ======
    val pointHeaderIdx = lines.indexOfLast { "POINT" in it && !it.contains("AVERAGE") && !it.contains("EARNED") }
    if (pointHeaderIdx >= 0) {
        val gpsAfterHeader = mutableListOf<String>()
        for (i in (pointHeaderIdx + 1) until lines.size) {
            if (isNoiseLine(lines[i])) continue
            val gp = fuzzyGradePoint(lines[i])
            if (gp != null) {
                gpsAfterHeader.add(gpToGrade[gp]!!)
            } else if (lines[i] == "PASS" || lines[i] == "FAIL") {
                break
            }
        }
        android.util.Log.d("OCR_PARSE", "Strategy 2 (post-POINT): ${gpsAfterHeader.size} GPs: $gpsAfterHeader")
        if (gpsAfterHeader.size >= allCodes.size / 2) {
            val result = allCodes.take(gpsAfterHeader.size).zip(gpsAfterHeader)
            return result
        }
    }

    // ====== Strategy 3: All letter grades near GRADE headers (multi-semester) ======
    // Collect ALL standalone letter grades that appear after any "GRADE" header in the document
    val letterResults3 = mutableListOf<String>()
    var foundGradeHeader = false
    for (i in lines.indices) {
        val line = lines[i]
        if (line.uppercase().trim() == "GRADE") { foundGradeHeader = true; continue }
        if (!foundGradeHeader) continue
        if (isNoiseLine(line)) continue
        // Skip semester numbers, dates, headers, noise
        if (line.matches(Regex("^\\d{1,2}$")) && line.toIntOrNull()?.let { it in 1..8 } == true) continue
        val fg = fuzzyLetterGrade(line.replace(Regex("\\s+"), ""))
        if (fg != null) letterResults3.add(fg)
    }
    android.util.Log.d("OCR_PARSE", "Strategy 3 (post-GRADE multi): ${letterResults3.size} grades: $letterResults3")
    // Only use if we got enough grades (at least 80% of codes)
    if (letterResults3.size >= allCodes.size * 4 / 5) {
        val result = allCodes.take(letterResults3.size).zip(letterResults3)
        return result
    }

    // ====== Strategy 4: Column matching — fuzzy GP runs with gap tolerance ======
    val gradePointLines = mutableListOf<Pair<Int, Int>>()
    for ((idx, line) in lines.withIndex()) {
        if (isNoiseLine(line)) continue
        val gp = fuzzyGradePoint(line)
        if (gp != null) gradePointLines.add(idx to gp)
    }

    val gpRuns = findGapTolerantRuns(gradePointLines, maxGap = 3)
    val bestGpRun = gpRuns.filter { it.size >= 3 }
        .minByOrNull { kotlin.math.abs(it.size - allCodes.size) }
    if (bestGpRun != null && bestGpRun.size >= allCodes.size / 2) {
        val grades = bestGpRun.map { gpToGrade[it.second]!! }
        val result = allCodes.take(grades.size).zip(grades)
        android.util.Log.d("OCR_PARSE", "Strategy 4 (GP runs): ${result.size} — $result")
        return result
    }

    // ====== Strategy 5: Column matching — letter grade runs ======
    val gradeLines = mutableListOf<Pair<Int, String>>()
    for ((idx, line) in lines.withIndex()) {
        if (isNoiseLine(line)) continue
        val stripped = line.replace(Regex("\\s+"), "")
        val fg = fuzzyLetterGrade(stripped)
        if (fg != null) gradeLines.add(idx to fg)
    }

    val gradeRuns = findGapTolerantRunsStr(gradeLines, maxGap = 3)
    val bestGradeRun = gradeRuns.filter { it.size >= 3 }
        .minByOrNull { kotlin.math.abs(it.size - allCodes.size) }
    if (bestGradeRun != null && bestGradeRun.size >= allCodes.size / 2) {
        val grades = bestGradeRun.map { it.second }
        val result = allCodes.take(grades.size).zip(grades)
        android.util.Log.d("OCR_PARSE", "Strategy 5 (letter runs): ${result.size} — $result")
        return result
    }

    // ====== Strategy 6: All fuzzy GPs from tokens ======
    val allGps = mutableListOf<String>()
    for (line in lines) {
        if (isNoiseLine(line)) continue
        for (token in line.split(Regex("\\s+"))) {
            val gp = fuzzyGradePoint(token)
            if (gp != null) allGps.add(gpToGrade[gp]!!)
        }
    }
    if (allGps.size >= allCodes.size) {
        val result = allCodes.zip(allGps.takeLast(allCodes.size))
        android.util.Log.d("OCR_PARSE", "Strategy 6 (all GPs): ${result.size}")
        return result
    }

    // ====== Strategy 7: All standalone letter grades in document order ======
    // For multi-semester PDFs where grades appear as standalone lines (O, A+, A, B+, etc.)
    val allLetterGrades = mutableListOf<String>()
    for (line in lines) {
        if (isNoiseLine(line)) continue
        val stripped = line.replace(Regex("\\s+"), "").uppercase()
        // Only match lines that are PURELY a grade (not part of other text)
        if (stripped.length <= 2) {
            val fg = fuzzyLetterGrade(stripped)
            if (fg != null) allLetterGrades.add(fg)
        }
    }
    android.util.Log.d("OCR_PARSE", "Strategy 7 (all standalone grades): ${allLetterGrades.size} — $allLetterGrades")
    if (allLetterGrades.size >= allCodes.size / 2) {
        // If more grades than codes, take the first N (they appear in order)
        val result = allCodes.zip(allLetterGrades.take(allCodes.size))
        return result
    }

    // ====== Fallback ======
    if (distinctRow.isNotEmpty()) return distinctRow

    android.util.Log.d("OCR_PARSE", "No grades detected from any strategy")
    return emptyList()
}

/** Find runs of items allowing gaps (non-matching lines between matches). */
private fun findGapTolerantRuns(items: List<Pair<Int, Int>>, maxGap: Int): List<List<Pair<Int, Int>>> {
    val runs = mutableListOf<List<Pair<Int, Int>>>()
    var cur = mutableListOf<Pair<Int, Int>>()
    for (i in items.indices) {
        if (cur.isEmpty() || items[i].first <= items[i - 1].first + maxGap + 1) {
            cur.add(items[i])
        } else {
            if (cur.size >= 2) runs.add(cur.toList())
            cur = mutableListOf(items[i])
        }
    }
    if (cur.size >= 2) runs.add(cur.toList())
    return runs
}

/** Find runs of string-valued items allowing gaps. */
private fun findGapTolerantRunsStr(items: List<Pair<Int, String>>, maxGap: Int): List<List<Pair<Int, String>>> {
    val runs = mutableListOf<List<Pair<Int, String>>>()
    var cur = mutableListOf<Pair<Int, String>>()
    for (i in items.indices) {
        if (cur.isEmpty() || items[i].first <= items[i - 1].first + maxGap + 1) {
            cur.add(items[i])
        } else {
            if (cur.size >= 2) runs.add(cur.toList())
            cur = mutableListOf(items[i])
        }
    }
    if (cur.size >= 2) runs.add(cur.toList())
    return runs
}
