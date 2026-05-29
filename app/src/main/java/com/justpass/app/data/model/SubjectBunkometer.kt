package com.justpass.app.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Subject-wise Bunkometer math — pure functions, no Android / network deps.
 *
 * Mirrors the live attendance formula used everywhere else in the app:
 *   percentage = (present + exemption) / total * 100
 * Exemptions count IN YOUR FAVOUR, so they're folded into "good" below.
 * Future bunks add to both `total` and absent, never to `good`.
 */

/** Per-subject projection for the dashboard cascade (Mode A). */
data class SubjectImpact(
    val courseCode: String,
    val courseTitle: String,
    val currentPercentage: Double,
    val projectedPercentage: Double,
    val periodsMissed: Int
) {
    val drop: Double get() = currentPercentage - projectedPercentage
}

/** Single-subject "what if I bunk N periods" projection (Mode B). */
data class BunkProjection(
    val currentPercentage: Double,
    val projectedPercentage: Double,
    val periods: Int,
    /** Max future periods you can miss while staying at/above target. */
    val budget: Int,
    val belowTarget: Boolean,
    /** If below target: periods to attend without absence to climb back to target. */
    val recoveryPeriods: Int
) {
    val drop: Double get() = currentPercentage - projectedPercentage
}

private fun LocalDate.timetableDayNumber(): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
    else -> 0 // Sunday — no classes
}

/**
 * weekday (1=Mon..6=Sat) -> (courseCode -> number of periods that subject has that day).
 */
fun TimetableResponse.periodsByWeekdayBySubject(): Map<Int, Map<String, Int>> {
    return toDayTimetables().associate { day ->
        day.dayNumber to day.sessions
            .filter { it.courseCode.isNotBlank() }
            .groupingBy { it.courseCode }
            .eachCount()
    }
}

/** Periods of one subject scheduled on a specific date (Mode B calendar tap). */
fun periodsForSubjectOnDate(
    courseCode: String,
    date: LocalDate,
    timetable: TimetableResponse
): Int {
    val dayNum = date.timetableDayNumber()
    if (dayNum == 0) return 0
    return timetable.toDayTimetables()
        .firstOrNull { it.dayNumber == dayNum }
        ?.sessions?.count { it.courseCode == courseCode } ?: 0
}

/**
 * Mode A cascade — given the working days the student plans to miss, project each
 * affected subject's new percentage. Subjects with zero periods on those days are
 * omitted. `dates` should already be holiday/Sunday filtered upstream.
 */
fun computeSubjectCascade(
    dates: List<LocalDate>,
    subjects: List<SubjectAttendance>,
    timetable: TimetableResponse
): List<SubjectImpact> {
    if (dates.isEmpty() || subjects.isEmpty()) return emptyList()
    val byWeekday = timetable.periodsByWeekdayBySubject()

    val missedBySubject = mutableMapOf<String, Int>()
    for (date in dates) {
        val dayMap = byWeekday[date.timetableDayNumber()] ?: continue
        for ((code, count) in dayMap) {
            missedBySubject[code] = (missedBySubject[code] ?: 0) + count
        }
    }

    return subjects.mapNotNull { subj ->
        val missed = missedBySubject[subj.courseCode] ?: 0
        if (missed == 0) return@mapNotNull null
        val good = subj.presentCount + subj.exemptionCount
        val newTotal = subj.totalCount + missed
        val projected = if (newTotal > 0) good.toDouble() / newTotal * 100.0 else 100.0
        SubjectImpact(
            courseCode = subj.courseCode,
            courseTitle = subj.courseTitle,
            currentPercentage = subj.attendancePercentage,
            projectedPercentage = projected,
            periodsMissed = missed
        )
    }.sortedBy { it.projectedPercentage }
}

/** Mode B — project one subject after missing [periods] more periods. */
fun projectSubjectBunk(
    subject: SubjectAttendance,
    periods: Int,
    target: Double
): BunkProjection {
    val good = subject.presentCount + subject.exemptionCount
    val total = subject.totalCount
    val newTotal = total + periods
    val projected = if (newTotal > 0) good.toDouble() / newTotal * 100.0 else 100.0

    // budget: largest n with good / (total + n) >= target/100
    //   => n <= good * 100 / target - total
    val budget = if (total > 0 && target > 0) {
        floor(good * 100.0 / target - total).toInt().coerceAtLeast(0)
    } else 0

    val belowTarget = subject.attendancePercentage < target

    // recovery: smallest r with (good + r) / (total + r) >= target/100
    //   => r >= (target/100 * total - good) / (1 - target/100)
    val recovery = if (belowTarget && total > 0 && target < 100) {
        ceil((target / 100.0 * total - good) / (1.0 - target / 100.0)).toInt().coerceAtLeast(0)
    } else 0

    return BunkProjection(
        currentPercentage = subject.attendancePercentage,
        projectedPercentage = projected,
        periods = periods,
        budget = budget,
        belowTarget = belowTarget,
        recoveryPeriods = recovery
    )
}
