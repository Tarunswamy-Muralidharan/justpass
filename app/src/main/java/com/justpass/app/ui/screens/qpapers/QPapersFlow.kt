package com.justpass.app.ui.screens.qpapers

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.justpass.app.data.model.QPaper
import com.justpass.app.data.model.UploadIntent
import com.justpass.app.ui.viewmodel.QPaperViewModel
import io.github.fletchmckee.liquid.LiquidState

/**
 * Internal route inside the QPapers feature. The parent (MainActivity)
 * sees QPapers as one stop; the nested drill-down lives entirely here.
 */
sealed class QPaperRoute {
    data object DepartmentList : QPaperRoute()
    data class SemesterList(val department: String) : QPaperRoute()
    data class SubjectList(
        val department: String,
        val semester: Int,
    ) : QPaperRoute()
    data class CategoryDetail(
        val department: String,
        val subjectCode: String,
        val subjectName: String,
        val semester: Int,
    ) : QPaperRoute()
    data class Viewer(val paper: QPaper) : QPaperRoute()
    data class Upload(val intent: UploadIntent) : QPaperRoute()
    data object ThankYou : QPaperRoute()
    data object AdminQueue : QPaperRoute()
    data object AdminHistory : QPaperRoute()
}

@Composable
fun QPapersFlow(
    cardState: LiquidState,
    isAdmin: Boolean,
    initialRoute: QPaperRoute = QPaperRoute.DepartmentList,
    initialUploadIntent: UploadIntent? = null,
    onBack: () -> Unit,
    viewModel: QPaperViewModel = viewModel(),
) {
    // History stack — push on navigate, pop on back. Persist across config
    // changes so rotation doesn't kick the user back to the start.
    val stack = rememberSaveable(saver = QPaperRouteStackSaver) {
        mutableListOf<QPaperRoute>(initialRoute)
    }
    var version by remember { mutableIntStateOf(0) } // forces recompose on stack mutations

    fun push(r: QPaperRoute) { stack.add(r); version++ }
    fun pop() {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); version++ } else onBack()
    }

    // If the parent injected an initial UploadIntent (e.g. coming from a
    // contextual banner in CA Marks), push that on first composition.
    LaunchedEffect(Unit) {
        if (initialUploadIntent != null && stack.last() !is QPaperRoute.Upload) {
            push(QPaperRoute.Upload(initialUploadIntent))
        }
    }

    BackHandler { pop() }

    val current = remember(version) { stack.last() }

    Crossfade(targetState = current, animationSpec = tween(200), label = "qpaperFlow") { route ->
        when (route) {
            QPaperRoute.DepartmentList -> QPaperDepartmentScreen(
                cardState = cardState,
                viewModel = viewModel,
                isAdmin = isAdmin,
                onPickDepartment = { dept -> push(QPaperRoute.SemesterList(dept)) },
                onOpenAdminQueue = { push(QPaperRoute.AdminQueue) },
                onBack = { pop() },
            )

            is QPaperRoute.SemesterList -> QPaperSemesterListScreen(
                cardState = cardState,
                department = route.department,
                viewModel = viewModel,
                onPickSemester = { sem ->
                    push(QPaperRoute.SubjectList(route.department, sem))
                },
                onBack = { pop() },
            )

            is QPaperRoute.SubjectList -> QPaperSubjectListScreen(
                cardState = cardState,
                department = route.department,
                semester = route.semester,
                viewModel = viewModel,
                onPickSubject = { code, name ->
                    push(QPaperRoute.CategoryDetail(route.department, code, name, route.semester))
                },
                onBack = { pop() },
            )

            is QPaperRoute.CategoryDetail -> QPaperCategoryScreen(
                cardState = cardState,
                route = route,
                viewModel = viewModel,
                onOpenViewer = { paper -> push(QPaperRoute.Viewer(paper)) },
                onContribute = { category, examYear ->
                    push(
                        QPaperRoute.Upload(
                            UploadIntent(
                                department = route.department,
                                subjectCode = route.subjectCode,
                                subjectName = route.subjectName,
                                semester = route.semester,
                                regulation = viewModel.effectiveRegulation.name,
                                category = category,
                                examYear = examYear,
                            )
                        )
                    )
                },
                onBack = { pop() },
            )

            is QPaperRoute.Viewer -> QPaperViewerScreen(
                paper = route.paper,
                viewModel = viewModel,
                onBack = { pop() },
            )

            is QPaperRoute.Upload -> QPaperUploadScreen(
                cardState = cardState,
                intent = route.intent,
                viewModel = viewModel,
                onDone = {
                    // Pop the upload, push the thank-you
                    if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    push(QPaperRoute.ThankYou)
                },
                onBack = { pop() },
            )

            QPaperRoute.ThankYou -> QPaperThankYouScreen(
                cardState = cardState,
                viewModel = viewModel,
                onPickGap = { gap ->
                    // Replace thank-you with a fresh upload screen for the gap
                    if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    push(QPaperRoute.Upload(gap))
                },
                onDone = {
                    // Collapse back to departments
                    while (stack.size > 1) stack.removeAt(stack.lastIndex)
                    version++
                },
            )

            QPaperRoute.AdminQueue -> QPaperAdminScreen(
                cardState = cardState,
                viewModel = viewModel,
                onOpenViewer = { paper -> push(QPaperRoute.Viewer(paper)) },
                onOpenHistory = { push(QPaperRoute.AdminHistory) },
                onBack = { pop() },
            )

            QPaperRoute.AdminHistory -> QPaperHistoryScreen(
                cardState = cardState,
                viewModel = viewModel,
                onOpenViewer = { paper -> push(QPaperRoute.Viewer(paper)) },
                onBack = { pop() },
            )
        }
    }
}

/**
 * rememberSaveable saver for the route stack. Routes are simple data classes
 * so we serialise to a list of string + payload pairs and restore on the way
 * back. Keeps drill-down state across rotation.
 *
 * NOTE: Viewer / Upload routes carry a non-trivial object (QPaper / UploadIntent)
 * that doesn't survive parcelisation cleanly here. Those routes won't survive
 * rotation perfectly — the user lands back on the most recent simple ancestor.
 * Good enough for v1; can swap to androidx.navigation later if needed.
 */
private val QPaperRouteStackSaver = androidx.compose.runtime.saveable.listSaver<MutableList<QPaperRoute>, String>(
    save = { stack ->
        stack.mapNotNull { r ->
            when (r) {
                QPaperRoute.DepartmentList -> "dept"
                is QPaperRoute.SemesterList -> "sem:${r.department}"
                is QPaperRoute.SubjectList -> "sub:${r.department}:${r.semester}"
                is QPaperRoute.CategoryDetail -> "cat:${r.department}:${r.subjectCode}:${r.subjectName}:${r.semester}"
                QPaperRoute.AdminQueue -> "admin"
                QPaperRoute.AdminHistory -> "adminhistory"
                QPaperRoute.ThankYou -> "thank"
                // Viewer / Upload carry rich objects — collapse to closest ancestor on restore.
                is QPaperRoute.Viewer, is QPaperRoute.Upload -> null
            }
        }.ifEmpty { listOf("dept") }
    },
    restore = { saved ->
        val out = mutableListOf<QPaperRoute>()
        for (key in saved) {
            val parts = key.split(":")
            out.add(
                when (parts[0]) {
                    "dept" -> QPaperRoute.DepartmentList
                    "sem" -> QPaperRoute.SemesterList(parts[1])
                    "sub" -> QPaperRoute.SubjectList(parts[1], parts[2].toInt())
                    "cat" -> QPaperRoute.CategoryDetail(parts[1], parts[2], parts[3], parts[4].toInt())
                    "admin" -> QPaperRoute.AdminQueue
                    "adminhistory" -> QPaperRoute.AdminHistory
                    "thank" -> QPaperRoute.ThankYou
                    else -> QPaperRoute.DepartmentList
                }
            )
        }
        out
    }
)
