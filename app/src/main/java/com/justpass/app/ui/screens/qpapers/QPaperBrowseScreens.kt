package com.justpass.app.ui.screens.qpapers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justpass.app.data.model.CurriculumSubject
import com.justpass.app.data.model.Department
import com.justpass.app.data.model.PaperCategory
import com.justpass.app.data.model.QPaper
import com.justpass.app.data.model.detectDepartment
import com.justpass.app.data.model.getCurriculum
import com.justpass.app.ui.components.GlassCardShape
import com.justpass.app.ui.components.GlassCardShapeSmall
import com.justpass.app.ui.components.GlassListCard
import com.justpass.app.ui.components.RoseFourLoader
import com.justpass.app.ui.viewmodel.QPaperViewModel
import io.github.fletchmckee.liquid.LiquidState

// ─── shared header ─────────────────────────────────────────────────────

@Composable
internal fun QPapersHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = GlassCardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

// ─── 1. Department picker ──────────────────────────────────────────────

@Composable
fun QPaperDepartmentScreen(
    cardState: LiquidState,
    viewModel: QPaperViewModel,
    isAdmin: Boolean,
    onPickDepartment: (String) -> Unit,
    onOpenAdminQueue: () -> Unit,
    onBack: () -> Unit,
) {
    val myDept = viewModel.userDepartment

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(
            title = "Previous Year Papers",
            subtitle = "Tap your department",
            onBack = onBack,
            trailing = if (isAdmin) ({
                IconButton(onClick = onOpenAdminQueue) {
                    Icon(
                        Icons.Default.AdminPanelSettings, "Admin queue",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }) else null,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Department.entries.toList(), key = { it.name }) { dept ->
                DepartmentCard(
                    dept = dept,
                    isMine = dept == myDept,
                    onClick = { onPickDepartment(dept.shortName) },
                )
            }
        }
    }
}

@Composable
private fun DepartmentCard(
    dept: Department,
    isMine: Boolean,
    onClick: () -> Unit,
) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = GlassCardShapeSmall,
        tintColor = if (isMine) Color(0xFF00E676).copy(alpha = 0.08f) else Color.Unspecified,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dept.shortName.take(3),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dept.shortName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    dept.displayName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isMine) {
                Text(
                    "YOURS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676),
                )
            }
        }
    }
}

// ─── 2. Semester picker ────────────────────────────────────────────────

@Composable
fun QPaperSemesterListScreen(
    cardState: LiquidState,
    department: String,
    viewModel: QPaperViewModel,
    onPickSemester: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val regulation = viewModel.userRegulation
    val semesters = remember(department, regulation) {
        val dept = detectDepartment(department)
        if (dept == null) emptyList<Int>()
        else getCurriculum(dept, regulation).keys.sorted()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(
            title = department,
            subtitle = "${regulation.displayName} · pick a semester",
            onBack = onBack,
        )

        if (semesters.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No curriculum data for this department yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(semesters, key = { it }) { sem ->
                GlassListCard(
                    modifier = Modifier.fillMaxWidth().clickable { onPickSemester(sem) },
                    shape = GlassCardShapeSmall,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Semester $sem",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─── 3. Subject list ───────────────────────────────────────────────────

@Composable
fun QPaperSubjectListScreen(
    cardState: LiquidState,
    department: String,
    semester: Int,
    viewModel: QPaperViewModel,
    onPickSubject: (code: String, name: String) -> Unit,
    onBack: () -> Unit,
) {
    val regulation = viewModel.userRegulation
    val subjects = remember(department, semester, regulation) {
        val dept = detectDepartment(department)
        if (dept == null) emptyList<CurriculumSubject>()
        else getCurriculum(dept, regulation)[semester] ?: emptyList()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(
            title = "$department · Sem $semester",
            subtitle = "${regulation.displayName} · ${subjects.size} subjects",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(subjects, key = { it.code }) { subj ->
                SubjectCard(subj, onClick = { onPickSubject(subj.code, subj.name) })
            }
            if (subjects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No subjects for this semester yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectCard(subj: CurriculumSubject, onClick: () -> Unit) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = GlassCardShapeSmall,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                subj.code,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subj.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── 4. Category detail (CA1 / CA2 / Sem tabs) ─────────────────────────

@Composable
fun QPaperCategoryScreen(
    cardState: LiquidState,
    route: QPaperRoute.CategoryDetail,
    viewModel: QPaperViewModel,
    onOpenViewer: (QPaper) -> Unit,
    onContribute: (PaperCategory, examYear: Int) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.categoryState.collectAsState()
    val regulation = viewModel.userRegulation.name

    LaunchedEffect(route.subjectCode) {
        viewModel.loadCategoryDetail(route.department, route.subjectCode, regulation)
    }

    var selectedCategory by remember { mutableStateOf(PaperCategory.CA1) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        QPapersHeader(
            title = route.subjectCode,
            subtitle = route.subjectName,
            onBack = onBack,
        )

        TabRow(
            selectedTabIndex = PaperCategory.all.indexOf(selectedCategory),
            modifier = Modifier.padding(horizontal = 16.dp),
            containerColor = Color.Transparent,
        ) {
            PaperCategory.all.forEach { cat ->
                Tab(
                    selected = cat == selectedCategory,
                    onClick = { selectedCategory = cat },
                    text = {
                        Text(
                            cat.label,
                            fontSize = 13.sp,
                            fontWeight = if (cat == selectedCategory) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RoseFourLoader(modifier = Modifier.size(48.dp))
            }
            else -> {
                val papers = state.papersByCategory[selectedCategory].orEmpty()
                if (papers.isEmpty()) {
                    EmptyCategoryState(
                        category = selectedCategory,
                        onContribute = { year -> onContribute(selectedCategory, year) },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 160.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(papers, key = { it.id }) { p ->
                            PaperRowCard(p, onClick = { onOpenViewer(p) })
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            ContributeMoreCard(
                                category = selectedCategory,
                                onContribute = { year -> onContribute(selectedCategory, year) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperRowCard(p: QPaper, onClick: () -> Unit) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = GlassCardShapeSmall,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "PDF",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE53935),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${p.categoryEnum.label} · ${p.examYear}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${p.viewCount} views",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Open →",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyCategoryState(
    category: PaperCategory,
    onContribute: (examYear: Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📭", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Seniors haven't uploaded yet",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Be the first to upload a ${category.label} paper. Your juniors will thank you.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        YearPickerButton(label = "Contribute a ${category.label} paper", onPick = onContribute)
    }
}

@Composable
private fun ContributeMoreCard(
    category: PaperCategory,
    onContribute: (examYear: Int) -> Unit,
) {
    GlassListCard(
        modifier = Modifier.fillMaxWidth(),
        shape = GlassCardShapeSmall,
        tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Got another ${category.label} paper to share?",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            YearPickerButton(label = "Contribute ${category.label}", onPick = onContribute)
        }
    }
}

@Composable
private fun YearPickerButton(label: String, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Years 2022..2030 give enough range without going wild
            (2030 downTo 2022).forEach { y ->
                DropdownMenuItem(
                    text = { Text("Year $y") },
                    onClick = {
                        expanded = false
                        onPick(y)
                    },
                )
            }
        }
    }
}
