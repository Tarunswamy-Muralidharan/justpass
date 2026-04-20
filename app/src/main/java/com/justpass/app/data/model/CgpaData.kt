package com.justpass.app.data.model

/**
 * CGPA Calculator data models and curriculum data.
 * R2021 regulation (Anna University) for batch <= 2024
 * R2025 regulation (PSGiTech autonomous) for batch >= 2025
 */

data class CurriculumSubject(
    val code: String,
    val name: String,
    val credits: Double,
    val isElective: Boolean = false
)

enum class LetterGrade(val gradePoint: Int, val label: String) {
    O(10, "O"),
    A_PLUS(9, "A+"),
    A(8, "A"),
    B_PLUS(7, "B+"),
    B(6, "B"),
    C(5, "C"),
    RA(0, "RA"),
    AB(0, "AB");

    companion object {
        val gradableGrades = listOf(O, A_PLUS, A, B_PLUS, B, C)
    }
}

data class SubjectGrade(
    val subject: CurriculumSubject,
    val grade: LetterGrade? = null,
    val customName: String? = null
)

enum class Department(val displayName: String, val shortName: String) {
    CSE("Computer Science and Engineering", "CSE"),
    EEE("Electrical and Electronics Engineering", "EEE"),
    ECE("Electronics and Communication Engineering", "ECE"),
    MECH("Mechanical Engineering", "MECH"),
    CIVIL("Civil Engineering", "CIVIL"),
    AIDS("AI and Data Science", "AI&DS"),
    CSBS("CS and Business Systems", "CSBS")
}

enum class Regulation(val displayName: String) {
    R2021("R2021 (Anna University)"),
    R2025("R2025 (PSGiTech Autonomous)")
}

fun getRegulationForBatch(batchYear: Int): Regulation {
    return if (batchYear >= 2025) Regulation.R2025 else Regulation.R2021
}

fun detectDepartment(input: String?): Department? {
    val name = input?.uppercase()?.trim() ?: return null
    // Exact short-name match first (handles department field like "CSE", "ECE")
    Department.entries.forEach { dept ->
        if (name == dept.shortName || name == "B.TECH ${dept.shortName}" || name == "BTECH ${dept.shortName}") {
            return dept
        }
    }
    // Substring matching for programme names
    return when {
        name.contains("BUSINESS SYSTEM") || name.contains("CSBS") -> Department.CSBS
        name.contains("ARTIFICIAL INTELLIGENCE") || name.contains("DATA SCIENCE") ||
                name.contains("AI&DS") || name.contains("AIDS") || name.contains("AI DS") -> Department.AIDS
        (name.contains("COMPUTER SCIENCE") || name.contains("CSE")) &&
                !name.contains("BUSINESS") -> Department.CSE
        name.contains("ELECTRICAL") || name.contains("EEE") -> Department.EEE
        name.contains("ELECTRONICS") || name.contains("COMMUNICATION") ||
                name.contains("ECE") -> Department.ECE
        name.contains("MECHANICAL") || name.contains("MECH") -> Department.MECH
        name.contains("CIVIL") -> Department.CIVIL
        name.contains("INFORMATION TECHNOLOGY") || name.contains(" IT") -> Department.CSE // IT falls back to CSE curriculum
        else -> null
    }
}

fun calculateSGPA(grades: List<SubjectGrade>): Double {
    val graded = grades.filter { it.grade != null && it.subject.credits > 0 }
    if (graded.isEmpty()) return 0.0
    val totalWeighted = graded.sumOf { it.subject.credits * (it.grade?.gradePoint ?: 0) }
    val totalCredits = graded.sumOf { it.subject.credits }
    return if (totalCredits > 0) totalWeighted / totalCredits else 0.0
}

fun calculateCGPA(allSemesterGrades: List<List<SubjectGrade>>): Double {
    val allGraded = allSemesterGrades.flatten().filter { it.grade != null && it.subject.credits > 0 }
    if (allGraded.isEmpty()) return 0.0
    val totalWeighted = allGraded.sumOf { it.subject.credits * (it.grade?.gradePoint ?: 0) }
    val totalCredits = allGraded.sumOf { it.subject.credits }
    return if (totalCredits > 0) totalWeighted / totalCredits else 0.0
}

// ===================== TARGET CGPA CALCULATOR =====================

/**
 * R2025 absolute grading: marks range → grade point.
 * Used as best estimate (relative grading depends on class curve).
 */
data class TargetSubjectResult(
    val courseCode: String,
    val courseTitle: String,
    val credits: Int,
    val caMarksScored: Double,   // What the student got in CA so far (out of caMarksMax)
    val caMarksMax: Double,      // Max CA marks (typically 40)
    val requiredGradePoint: Int, // Grade point needed
    val requiredGrade: String,   // Letter grade needed (O, A+, A, etc.)
    val requiredTotalMarks: Int, // Total marks needed (CA + ESE combined, out of 100)
    val requiredEseMarks: Int,   // ESE marks needed (out of 100 on paper, scaled to 60)
    val eseOutOf: Int = 100,     // ESE paper is out of 100
    val isPossible: Boolean,     // Can they achieve this grade?
    val isAlreadySecured: Boolean = false, // CA marks alone already secure the grade
    val ca2Needed: Int? = null,       // Test marks needed in next component (out of testMax)
    val ca2Max: Int = 65,             // Test paper max marks (for display "X/65")
    val ca2Name: String = "IAT-2",   // Name of the pending component
    val hasCa2Pending: Boolean = false, // True if next component not yet entered
    val hasCa1Pending: Boolean = false, // True if IAT-1 also not entered
    val ca1Needed: Int? = null,       // Test marks needed in IAT-1 (if pending)
    val ca1Max: Int = 65              // IAT-1 test paper max
)

data class TargetCgpaResult(
    val targetCgpa: Double,
    val currentCgpa: Double,
    val previousCredits: Int,
    val previousWeightedSum: Int, // Σ(credits × gradePoint) from past semesters
    val currentSemCredits: Int,
    val requiredSgpa: Double,     // SGPA needed this semester
    val subjects: List<TargetSubjectResult>,
    val isAchievable: Boolean,
    val message: String           // Summary like "Need A+ in 3, A in 2 subjects"
)

/** Convert total marks (out of 100) to grade point using absolute grading scale */
fun totalMarksToGradePoint(totalMarks: Int): Int = when {
    totalMarks >= 91 -> 10 // O
    totalMarks >= 81 -> 9  // A+
    totalMarks >= 71 -> 8  // A
    totalMarks >= 61 -> 7  // B+
    totalMarks >= 56 -> 6  // B
    totalMarks >= 50 -> 5  // C
    else -> 0              // RA
}

/** Grade point to minimum total marks needed */
fun gradePointToMinMarks(gp: Int): Int = when (gp) {
    10 -> 91
    9 -> 81
    8 -> 71
    7 -> 61
    6 -> 56
    5 -> 50
    else -> 0
}

/** Grade point to letter grade string */
fun gradePointToLetter(gp: Int): String = when (gp) {
    10 -> "O"
    9 -> "A+"
    8 -> "A"
    7 -> "B+"
    6 -> "B"
    5 -> "C"
    else -> "RA"
}

/**
 * Calculate what grades/marks are needed in the current semester to achieve target CGPA.
 *
 * @param targetCgpa Desired cumulative CGPA
 * @param previousGrades All grade entries from past semesters (from Results API)
 * @param currentSemester Current semester number
 * @param currentCAMarks CA marks for current semester subjects (courseCode → Pair(scored, max))
 * @param currentSemSubjects Current semester subjects with credits (courseCode → credits)
 */
/**
 * Per-subject CA component breakdown for target calculation.
 * @param ca1Scored CA-1 marks scored (null if not entered)
 * @param ca1Max CA-1 max marks
 * @param ca2Scored CA-2 marks scored (null if not entered)
 * @param ca2Max CA-2 max marks
 */
/**
 * Per-subject CA component breakdown for target calculation.
 * Components vary by course type:
 * - Theory: IAT-1 (test+assignment, scaled to 20) + IAT-2 (test+assignment, scaled to 20) = 40 CA
 * - Theory+Lab: IAT-1 (test+assignment, scaled to 25) + IA LAB (record+model, scaled to 25) = 50 CA
 * - Lab only: INTERNAL (observation+test, scaled to 60) = 60 CA
 */
data class CaComponentData(
    val ca1Scored: Double? = null,  // IAT-1 scaled marks
    val ca1Max: Double = 20.0,      // IAT-1 scaled max
    val ca2Scored: Double? = null,  // IAT-2 or IA LAB scaled marks
    val ca2Max: Double = 20.0,      // IAT-2 or IA LAB scaled max
    val ca2Name: String = "IAT-2",  // Component name (IAT-2, IA LAB, INTERNAL)
    val testMax: Double = 65.0,     // Actual test paper max marks (for display)
    val testScaled: Double = 60.0,  // Test scaled max
    val ca2TestMax: Double = 65.0,  // IAT-2/component test max (actual, for "min X/65")
    val ca2TestScaled: Double = 60.0 // IAT-2/component test scaled max
)

fun calculateTargetCgpa(
    targetCgpa: Double,
    previousGrades: List<GradeEntry>,
    currentSemester: Int,
    currentCAMarks: Map<String, Pair<Double, Double>>,
    currentSemSubjects: Map<String, Pair<String, Int>>,
    caComponents: Map<String, CaComponentData> = emptyMap()
): TargetCgpaResult {
    val pastGrades = previousGrades.filter { it.semester < currentSemester && it.isPassed() }
    val prevCredits = pastGrades.sumOf { it.getCreditsValue() }
    val prevWeightedSum = pastGrades.sumOf { it.gradePoint * it.getCreditsValue() }
    val prevCgpa = if (prevCredits > 0) prevWeightedSum.toDouble() / prevCredits else 0.0
    return calculateTargetCgpaFromLocal(targetCgpa, prevCgpa, prevCredits, prevWeightedSum,
        currentCAMarks, currentSemSubjects, caComponents)
}

/**
 * Calculate target CGPA using pre-computed previous semester data (from GPA calculator).
 */
fun calculateTargetCgpaFromLocal(
    targetCgpa: Double,
    currentCgpa: Double,
    previousCredits: Int,
    previousWeightedSum: Int,
    currentCAMarks: Map<String, Pair<Double, Double>>,
    currentSemSubjects: Map<String, Pair<String, Int>>,
    caComponents: Map<String, CaComponentData> = emptyMap()
): TargetCgpaResult {

    val currentSemCredits = currentSemSubjects.values.sumOf { it.second }
    val totalCredits = previousCredits + currentSemCredits

    // Required weighted sum this semester
    val requiredTotalWeighted = (targetCgpa * totalCredits).toInt()
    val requiredThisSem = requiredTotalWeighted - previousWeightedSum
    val requiredSgpa = if (currentSemCredits > 0) requiredThisSem.toDouble() / currentSemCredits else 0.0

    val isNotAchievable = requiredSgpa > 10.0

    if (requiredSgpa <= 0) {
        return TargetCgpaResult(
            targetCgpa = targetCgpa, currentCgpa = currentCgpa,
            previousCredits = previousCredits, previousWeightedSum = previousWeightedSum,
            currentSemCredits = currentSemCredits, requiredSgpa = requiredSgpa,
            subjects = emptyList(), isAchievable = true,
            message = "Already achieved! Any passing grade will do."
        )
    }

    // Calculate per-subject breakdown even if not fully achievable
    // so user can see what grades they'd aim for (best case O for everything)
    val minUniformGP = if (isNotAchievable) 10 // show O grade as best case
        else kotlin.math.ceil(requiredSgpa).toInt().coerceIn(5, 10)

    val subjects = currentSemSubjects.map { (code, titleCredits) ->
        val (title, credits) = titleCredits
        val caData = currentCAMarks[code]
        val caScored = caData?.first ?: 0.0
        val caMax = caData?.second ?: 40.0
        val comp = caComponents[code]

        // Marking scheme varies by course type:
        //   Theory: Total(100) = CA(40) + Sem Exam(60). CA = IAT-1(20) + IAT-2(20)
        //   Theory+Lab: Total(100) = CA(50) + Sem Exam(50). CA = IAT-1(25) + IA LAB(25)
        //   Lab: Total(100) = CA(60) + Sem Exam(40). CA = INTERNAL(60)

        val hasCa1Pending = comp != null && comp.ca1Scored == null
        val hasCa2Pending = comp != null && comp.ca2Scored == null && !hasCa1Pending

        // IAT-1 scaled marks
        val iat1Scaled = if (comp != null && comp.ca1Scored != null && comp.ca1Max > 0)
            comp.ca1Scored else 0.0
        val iat1ScaledMax = comp?.ca1Max ?: 20.0

        // CA-2 / IA LAB scaled max
        val iat2ScaledMax = comp?.ca2Max ?: 20.0
        val totalCaMax = iat1ScaledMax + iat2ScaledMax // e.g. 40, 50, or 60

        // Best possible CA (assume max in pending components)
        val bestPossibleCaScaled = when {
            hasCa1Pending -> totalCaMax // both pending → can get full CA
            hasCa2Pending -> iat1Scaled + iat2ScaledMax // IAT-1 done + max IAT-2
            else -> if (caMax > 0) (caScored / caMax * totalCaMax) else 0.0
        }

        // ESE portion: Total(100) - CA portion
        val eseScaledMax = 100.0 - totalCaMax // 60, 50, or 40

        val reqGP = minUniformGP
        val reqTotalMarks = gradePointToMinMarks(reqGP)

        // ESE needed assuming best possible CA
        val eseScaledNeeded = reqTotalMarks - bestPossibleCaScaled
        val eseRawNeeded = if (eseScaledNeeded > 0 && eseScaledMax > 0)
            kotlin.math.ceil(eseScaledNeeded / eseScaledMax * 100.0).toInt() else 0

        val isPossible = eseRawNeeded <= 100
        val eseMinPass = 45
        val finalEseNeeded = eseRawNeeded.coerceAtLeast(eseMinPass)

        // Calculate minimum TEST marks needed in pending component(s)
        // Structure: IAT has sub-components (Test out of 65, Assignment out of 40)
        //   IAT actual (100) = test_actual/65 * testWeight + assignment_actual/40 * assignmentWeight
        //   Where testWeight = ca2TestScaled (e.g. 60), assignmentWeight = 100 - testWeight (e.g. 40)
        //   Then IAT actual (100) → scaled to ca2Max (e.g. 20) for overall CA
        // Minimum test pass threshold: 33/65 (approximately 50%)
        val minTestPass = 33

        var ca2Needed: Int? = null
        var ca2TestMax = comp?.ca2TestMax?.toInt() ?: 65
        var ca2Name = comp?.ca2Name ?: "IAT-2"
        var ca1Needed: Int? = null
        var ca1TestMax = comp?.testMax?.toInt() ?: 65

        // Helper: given needed IAT scaled marks and component structure, calc minimum test actual
        fun calcMinTest(iatScaledNeeded: Double, iatScaledMax: Double, testActMax: Double, testWeight: Double, assignWeight: Double): Int {
            val maxTest = testActMax.toInt()
            val minPass = minTestPass.coerceAtMost(maxTest) // handle small test maxes (e.g. 25)
            if (iatScaledNeeded <= 0) return minPass
            if (iatScaledMax <= 0) return minPass
            // Convert IAT scaled needed to IAT actual (out of 100)
            val iatActualNeeded = iatScaledNeeded / iatScaledMax * 100.0
            // Assume full assignment marks: assignment contributes assignWeight out of 100
            val testContribNeeded = (iatActualNeeded - assignWeight).coerceAtLeast(0.0)
            // Convert test contribution (internal weight) to actual marks
            val testActual = if (testWeight > 0) kotlin.math.ceil(testContribNeeded / testWeight * testActMax).toInt() else 0
            return testActual.coerceIn(minPass, maxTest)
        }

        if (hasCa2Pending && comp != null) {
            val minCaScaled = (reqTotalMarks - eseScaledMax).coerceAtLeast(0.0)
            val iat2ScaledNeeded = (minCaScaled - iat1Scaled).coerceAtLeast(0.0)
            val assignWeight = 100.0 - comp.ca2TestScaled // e.g. 100 - 60 = 40
            ca2Needed = calcMinTest(iat2ScaledNeeded, comp.ca2Max, comp.ca2TestMax, comp.ca2TestScaled, assignWeight)
            ca2TestMax = comp.ca2TestMax.toInt()
            ca2Name = comp.ca2Name
        }

        if (hasCa1Pending && comp != null) {
            // Both pending: each component needs to contribute its share
            val minCaScaled = (reqTotalMarks - eseScaledMax).coerceAtLeast(0.0)
            // Split proportionally by scaled max
            val comp1Share = (minCaScaled * comp.ca1Max / totalCaMax).coerceAtLeast(0.0)
            val comp2Share = (minCaScaled * comp.ca2Max / totalCaMax).coerceAtLeast(0.0)

            val assignWeight1 = 100.0 - comp.testScaled
            ca1Needed = calcMinTest(comp1Share, comp.ca1Max, comp.testMax, comp.testScaled, assignWeight1)
            ca1TestMax = comp.testMax.toInt()

            val assignWeight2 = 100.0 - comp.ca2TestScaled
            ca2Needed = calcMinTest(comp2Share, comp.ca2Max, comp.ca2TestMax, comp.ca2TestScaled, assignWeight2)
            ca2TestMax = comp.ca2TestMax.toInt()
            ca2Name = comp.ca2Name
        }

        TargetSubjectResult(
            courseCode = code,
            courseTitle = title,
            credits = credits,
            caMarksScored = caScored,
            caMarksMax = caMax,
            requiredGradePoint = reqGP,
            requiredGrade = gradePointToLetter(reqGP),
            requiredTotalMarks = reqTotalMarks,
            requiredEseMarks = finalEseNeeded,
            isPossible = isPossible,
            isAlreadySecured = eseRawNeeded <= eseMinPass,
            ca2Needed = ca2Needed,
            ca2Max = ca2TestMax,
            ca2Name = ca2Name,
            hasCa2Pending = hasCa2Pending,
            hasCa1Pending = hasCa1Pending,
            ca1Needed = ca1Needed,
            ca1Max = ca1TestMax
        )
    }.sortedByDescending { it.credits }

    // Build summary message
    val gradeCounts = subjects.filter { it.isPossible }.groupBy { it.requiredGrade }
        .map { (grade, list) -> "${grade} in ${list.size}" }
    val impossibleCount = subjects.count { !it.isPossible }
    val message = buildString {
        if (isNotAchievable) {
            append("Would need SGPA ${String.format("%.2f", requiredSgpa)} (max 10.0). Showing best case (all O).")
        } else {
            if (gradeCounts.isNotEmpty()) {
                append("Need ")
                append(gradeCounts.joinToString(", "))
            }
            if (impossibleCount > 0) {
                if (isNotEmpty()) append(". ")
                append("$impossibleCount subject${if (impossibleCount > 1) "s" else ""} may not be achievable")
            }
        }
    }

    return TargetCgpaResult(
        targetCgpa = targetCgpa, currentCgpa = currentCgpa,
        previousCredits = previousCredits, previousWeightedSum = previousWeightedSum,
        currentSemCredits = currentSemCredits, requiredSgpa = requiredSgpa,
        subjects = subjects, isAchievable = !isNotAchievable && impossibleCount == 0,
        message = message
    )
}

// ===================== R2021 CURRICULUM DATA =====================

private fun sub(code: String, name: String, credits: Double, elective: Boolean = false) =
    CurriculumSubject(code, name, credits, elective)

private fun elective(name: String, credits: Double = 3.0) =
    CurriculumSubject("--", name, credits, true)

// Common Semester 1 for all R2021 branches
private val r2021Sem1Common = listOf(
    sub("HS3152", "Professional English - I", 3.0),
    sub("MA3151", "Matrices and Calculus", 4.0),
    sub("PH3151", "Engineering Physics", 3.0),
    sub("CY3151", "Engineering Chemistry", 3.0),
    sub("GE3151", "Problem Solving and Python Programming", 3.0),
    sub("GE3152", "Heritage of Tamils", 1.0),
    sub("GE3171", "Python Programming Lab", 2.0),
    sub("BS3171", "Physics and Chemistry Lab", 2.0),
    sub("GE3172", "English Laboratory", 1.0)
) // 22 credits

// ---- CSE R2021 ----
private val r2021CseSem2 = listOf(
    sub("HS3252", "Professional English - II", 2.0),
    sub("MA3251", "Statistics and Numerical Methods", 4.0),
    sub("PH3256", "Physics for Information Science", 3.0),
    sub("BE3251", "Basic Electrical and Electronics Engg", 3.0),
    sub("GE3251", "Engineering Graphics", 4.0),
    sub("CS3251", "Programming in C", 3.0),
    sub("GE3252", "Tamils and Technology", 1.0),
    sub("GE3271", "Engineering Practices Lab", 2.0),
    sub("CS3271", "Programming in C Lab", 2.0),
    sub("GE3272", "Communication Lab", 2.0)
)
private val r2021CseSem3 = listOf(
    sub("MA3354", "Discrete Mathematics", 4.0),
    sub("CS3351", "Digital Principles and Computer Organization", 4.0),
    sub("CS3352", "Foundations of Data Science", 3.0),
    sub("CS3301", "Data Structures", 3.0),
    sub("CS3391", "Object Oriented Programming", 3.0),
    sub("CS3311", "Data Structures Lab", 1.5),
    sub("CS3381", "Object Oriented Programming Lab", 1.5),
    sub("CS3361", "Data Science Lab", 2.0),
    sub("GE3361", "Professional Development", 1.0)
)
private val r2021CseSem4 = listOf(
    sub("CS3452", "Theory of Computation", 3.0),
    sub("CS3491", "AI and Machine Learning", 4.0),
    sub("CS3492", "Database Management Systems", 3.0),
    sub("CS3401", "Algorithms", 4.0),
    sub("CS3451", "Introduction to Operating Systems", 3.0),
    sub("GE3451", "Environmental Sciences", 2.0),
    sub("CS3461", "Operating Systems Lab", 1.5),
    sub("CS3481", "DBMS Lab", 1.5)
)
private val r2021CseSem5 = listOf(
    sub("CS3591", "Computer Networks", 4.0),
    sub("CS3501", "Compiler Design", 4.0),
    sub("CB3491", "Cryptography and Cyber Security", 3.0),
    sub("CS3551", "Distributed Computing", 3.0),
    elective("Professional Elective I"),
    elective("Professional Elective II")
)
private val r2021CseSem6 = listOf(
    sub("CCS356", "Object Oriented Software Engineering", 4.0),
    sub("CS3691", "Embedded Systems and IoT", 4.0),
    elective("Open Elective I"),
    elective("Professional Elective III"),
    elective("Professional Elective IV"),
    elective("Professional Elective V"),
    elective("Professional Elective VI")
)
private val r2021CseSem7 = listOf(
    sub("GE3791", "Human Values and Ethics", 2.0),
    elective("Management Elective"),
    elective("Open Elective II"),
    elective("Open Elective III"),
    elective("Open Elective IV"),
    sub("CS3711", "Summer Internship", 2.0)
)
private val r2021CseSem8 = listOf(sub("CS3811", "Project Work / Internship", 10.0))

// ---- EEE R2021 ----
private val r2021EeeSem2 = listOf(
    sub("HS3252", "Professional English - II", 2.0),
    sub("MA3251", "Statistics and Numerical Methods", 4.0),
    sub("PH3202", "Physics for Electrical Engg", 3.0),
    sub("BE3255", "Basic Civil and Mechanical Engg", 3.0),
    sub("GE3251", "Engineering Graphics", 4.0),
    sub("EE3251", "Electric Circuit Analysis", 4.0),
    sub("GE3252", "Tamils and Technology", 1.0),
    sub("GE3271", "Engineering Practices Lab", 2.0),
    sub("EE3271", "Electric Circuits Lab", 2.0),
    sub("GE3272", "Communication Lab", 2.0)
)
private val r2021EeeSem3 = listOf(
    sub("MA3303", "Probability and Complex Functions", 4.0),
    sub("EE3301", "Electromagnetic Fields", 4.0),
    sub("EE3302", "Digital Logic Circuits", 3.0),
    sub("EC3301", "Electron Devices and Circuits", 3.0),
    sub("EE3303", "Electrical Machines - I", 3.0),
    sub("CS3353", "C Programming and Data Structures", 3.0),
    sub("EC3311", "Electronic Devices Lab", 1.5),
    sub("EE3311", "Electrical Machines Lab - I", 1.5),
    sub("CS3362", "C Programming Lab", 1.5),
    sub("GE3361", "Professional Development", 1.0)
)
private val r2021EeeSem4 = listOf(
    sub("GE3451", "Environmental Sciences", 2.0),
    sub("EE3401", "Transmission and Distribution", 3.0),
    sub("EE3402", "Linear Integrated Circuits", 3.0),
    sub("EE3403", "Measurements and Instrumentation", 3.0),
    sub("EE3404", "Microprocessor and Microcontroller", 3.0),
    sub("EE3405", "Electrical Machines - II", 3.0),
    sub("EE3411", "Electrical Machines Lab - II", 1.5),
    sub("EE3412", "Linear and Digital Circuits Lab", 1.5),
    sub("EE3413", "Microprocessor Lab", 1.5)
)
private val r2021EeeSem5 = listOf(
    sub("EE3501", "Power System Analysis", 3.0),
    sub("EE3591", "Power Electronics", 3.0),
    sub("EE3503", "Control Systems", 3.0),
    elective("Professional Elective I"),
    elective("Professional Elective II"),
    elective("Professional Elective III"),
    sub("EE3511", "Power Electronics Lab", 1.5),
    sub("EE3512", "Control and Instrumentation Lab", 2.0)
)
private val r2021EeeSem6 = listOf(
    sub("EE3601", "Protection and Switchgear", 3.0),
    sub("EE3602", "Power System Operation and Control", 3.0),
    elective("Open Elective I"),
    elective("Professional Elective IV"),
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    sub("EE3611", "Power System Lab", 1.5)
)
private val r2021EeeSem7 = listOf(
    sub("EE3701", "High Voltage Engineering", 3.0),
    sub("GE3791", "Human Values and Ethics", 2.0),
    elective("Management Elective"),
    elective("Open Elective II"),
    elective("Open Elective III"),
    elective("Open Elective IV"),
    elective("Professional Elective VII")
)
private val r2021EeeSem8 = listOf(sub("EE3811", "Project Work / Internship", 10.0))

// ---- ECE R2021 ----
private val r2021EceSem2 = listOf(
    sub("HS3252", "Professional English - II", 2.0),
    sub("MA3251", "Statistics and Numerical Methods", 4.0),
    sub("PH3254", "Physics for Electronics Engg", 3.0),
    sub("BE3254", "Electrical and Instrumentation Engg", 3.0),
    sub("GE3251", "Engineering Graphics", 4.0),
    sub("EC3251", "Circuit Analysis", 4.0),
    sub("GE3252", "Tamils and Technology", 1.0),
    sub("GE3271", "Engineering Practices Lab", 2.0),
    sub("EC3271", "Circuits Analysis Lab", 1.0),
    sub("GE3272", "Communication Lab", 2.0)
)
private val r2021EceSem3 = listOf(
    sub("MA3355", "Random Processes and Linear Algebra", 4.0),
    sub("CS3353", "C Programming and Data Structures", 3.0),
    sub("EC3354", "Signals and Systems", 4.0),
    sub("EC3353", "Electronic Devices and Circuits", 3.0),
    sub("EC3351", "Control Systems", 3.0),
    sub("EC3352", "Digital Systems Design", 4.0),
    sub("EC3361", "Electronic Devices Lab", 1.5),
    sub("CS3362", "C Programming Lab", 1.5),
    sub("GE3361", "Professional Development", 1.0)
)
private val r2021EceSem4 = listOf(
    sub("EC3452", "Electromagnetic Fields", 3.0),
    sub("EC3401", "Networks and Security", 4.0),
    sub("EC3451", "Linear Integrated Circuits", 3.0),
    sub("EC3492", "Digital Signal Processing", 4.0),
    sub("EC3491", "Communication Systems", 3.0),
    sub("GE3451", "Environmental Sciences", 2.0),
    sub("EC3461", "Communication Systems Lab", 1.5),
    sub("EC3462", "Linear Integrated Circuits Lab", 1.5)
)
private val r2021EceSem5 = listOf(
    sub("EC3501", "Wireless Communication", 4.0),
    sub("EC3552", "VLSI and Chip Design", 3.0),
    sub("EC3551", "Transmission Lines and RF Systems", 3.0),
    elective("Professional Elective I"),
    elective("Professional Elective II"),
    elective("Professional Elective III"),
    sub("EC3561", "VLSI Laboratory", 2.0)
)
private val r2021EceSem6 = listOf(
    sub("ET3491", "Embedded Systems and IoT Design", 4.0),
    sub("CS3491", "AI and Machine Learning", 4.0),
    elective("Open Elective I"),
    elective("Professional Elective IV"),
    elective("Professional Elective V"),
    elective("Professional Elective VI")
)
private val r2021EceSem7 = listOf(
    sub("GE3791", "Human Values and Ethics", 2.0),
    elective("Management Elective"),
    elective("Open Elective II"),
    elective("Open Elective III"),
    elective("Open Elective IV"),
    sub("EC3711", "Summer Internship", 2.0)
)
private val r2021EceSem8 = listOf(sub("EC3811", "Project Work / Internship", 10.0))

// ---- MECH R2021 ----
private val r2021MechSem2 = listOf(
    sub("HS3252", "Professional English - II", 2.0),
    sub("MA3251", "Statistics and Numerical Methods", 4.0),
    sub("PH3251", "Materials Science", 3.0),
    sub("BE3251", "Basic Electrical and Electronics Engg", 3.0),
    sub("GE3251", "Engineering Graphics", 4.0),
    sub("GE3252", "Tamils and Technology", 1.0),
    sub("GE3271", "Engineering Practices Lab", 2.0),
    sub("BE3271", "Basic EEE Lab", 2.0),
    sub("GE3272", "Communication Lab", 2.0)
)
private val r2021MechSem3 = listOf(
    sub("MA3351", "Transforms and PDEs", 4.0),
    sub("ME3351", "Engineering Mechanics", 3.0),
    sub("ME3391", "Engineering Thermodynamics", 3.0),
    sub("CE3391", "Fluid Mechanics and Machinery", 4.0),
    sub("ME3392", "Engineering Materials and Metallurgy", 3.0),
    sub("ME3393", "Manufacturing Processes", 3.0),
    sub("ME3381", "Computer Aided Machine Drawing", 2.0),
    sub("ME3382", "Manufacturing Technology Lab", 2.0),
    sub("GE3361", "Professional Development", 1.0)
)
private val r2021MechSem4 = listOf(
    sub("ME3491", "Theory of Machines", 3.0),
    sub("ME3451", "Thermal Engineering", 4.0),
    sub("ME3492", "Hydraulics and Pneumatics", 3.0),
    sub("ME3493", "Manufacturing Technology", 3.0),
    sub("CE3491", "Strength of Materials", 3.0),
    sub("GE3451", "Environmental Sciences", 2.0),
    sub("CE3481", "Strength of Materials and Fluid Lab", 2.0),
    sub("ME3461", "Thermal Engineering Lab", 2.0)
)
private val r2021MechSem5 = listOf(
    sub("ME3591", "Design of Machine Elements", 4.0),
    sub("ME3592", "Metrology and Measurements", 3.0),
    elective("Professional Elective I"),
    elective("Professional Elective II"),
    elective("Professional Elective III"),
    sub("ME3511", "Summer Internship", 1.0),
    sub("ME3581", "Metrology and Dynamics Lab", 2.0)
)
private val r2021MechSem6 = listOf(
    sub("ME3691", "Heat and Mass Transfer", 4.0),
    elective("Professional Elective IV"),
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    elective("Professional Elective VII"),
    elective("Open Elective I"),
    sub("ME3681", "CAD/CAM Lab", 2.0),
    sub("ME3682", "Heat Transfer Lab", 2.0)
)
private val r2021MechSem7 = listOf(
    sub("ME3791", "Mechatronics and IoT", 3.0),
    sub("ME3792", "Computer Integrated Manufacturing", 3.0),
    sub("GE3791", "Human Values and Ethics", 2.0),
    sub("GE3792", "Industrial Management", 3.0),
    elective("Open Elective II"),
    elective("Open Elective III"),
    elective("Open Elective IV"),
    sub("ME3781", "Mechatronics and IoT Lab", 2.0),
    sub("ME3711", "Summer Internship", 1.0)
)
private val r2021MechSem8 = listOf(sub("ME3811", "Project Work / Internship", 10.0))

// ---- CIVIL R2021 ----
private val r2021CivilSem2 = listOf(
    sub("HS3252", "Professional English - II", 2.0),
    sub("MA3251", "Statistics and Numerical Methods", 4.0),
    sub("PH3201", "Physics for Civil Engg", 3.0),
    sub("BE3252", "Basic EEI Engg", 3.0),
    sub("GE3251", "Engineering Graphics", 4.0),
    sub("GE3252", "Tamils and Technology", 1.0),
    sub("GE3271", "Engineering Practices Lab", 2.0),
    sub("BE3272", "Basic EEI Lab", 2.0),
    sub("GE3272", "Communication Lab", 2.0)
)
private val r2021CivilSem3 = listOf(
    sub("MA3351", "Transforms and PDEs", 4.0),
    sub("ME3351", "Engineering Mechanics", 3.0),
    sub("CE3301", "Fluid Mechanics", 3.0),
    sub("CE3302", "Construction Materials and Technology", 3.0),
    sub("CE3303", "Water Supply and Wastewater Engg", 4.0),
    sub("CE3351", "Surveying and Levelling", 3.0),
    sub("CE3361", "Surveying and Levelling Lab", 1.5),
    sub("CE3311", "Water and Wastewater Analysis Lab", 1.5),
    sub("GE3361", "Professional Development", 1.0)
)
private val r2021CivilSem4 = listOf(
    sub("CE3401", "Applied Hydraulics Engg", 4.0),
    sub("CE3402", "Strength of Materials", 3.0),
    sub("CE3403", "Concrete Technology", 3.0),
    sub("CE3404", "Soil Mechanics", 3.0),
    sub("CE3405", "Highway and Railway Engg", 3.0),
    sub("GE3451", "Environmental Sciences", 2.0),
    sub("CE3411", "Hydraulic Engg Lab", 1.5),
    sub("CE3412", "Materials Testing Lab", 2.0),
    sub("CE3413", "Soil Mechanics Lab", 1.5)
)
private val r2021CivilSem5 = listOf(
    sub("CE3501", "Design of RC Structural Elements", 3.0),
    sub("CE3502", "Structural Analysis I", 3.0),
    sub("CE3503", "Foundation Engineering", 3.0),
    elective("Professional Elective I"),
    elective("Professional Elective II"),
    elective("Professional Elective III"),
    sub("CE3511", "Highway Engg Lab", 2.0),
    sub("CE3512", "Survey Camp", 1.0)
)
private val r2021CivilSem6 = listOf(
    sub("CE3601", "Design of Steel Structural Elements", 3.0),
    sub("CE3602", "Structural Analysis II", 3.0),
    sub("AG3601", "Engineering Geology", 3.0),
    elective("Professional Elective IV"),
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    elective("Open Elective I"),
    sub("CE3611", "Building Drawing Lab", 2.0)
)
private val r2021CivilSem7 = listOf(
    sub("CE3701", "Estimation, Costing and Valuation", 3.0),
    sub("AI3404", "Hydrology and Water Resources", 3.0),
    sub("GE3791", "Human Values and Ethics", 2.0),
    sub("GE3752", "Total Quality Management", 3.0),
    elective("Open Elective II"),
    elective("Open Elective III"),
    elective("Open Elective IV")
)
private val r2021CivilSem8 = listOf(sub("CE3811", "Project Work / Internship", 10.0))

// ---- AIDS R2021 ----
private val r2021AidsSem2 = listOf(
    sub("HS3252", "Professional English - II", 2.0),
    sub("MA3251", "Statistics and Numerical Methods", 4.0),
    sub("PH3256", "Physics for Information Science", 3.0),
    sub("BE3251", "Basic Electrical and Electronics Engg", 3.0),
    sub("GE3251", "Engineering Graphics", 4.0),
    sub("AD3251", "Data Structures Design", 3.0),
    sub("GE3252", "Tamils and Technology", 1.0),
    sub("GE3271", "Engineering Practices Lab", 2.0),
    sub("AD3271", "Data Structures Lab", 2.0),
    sub("GE3272", "Communication Lab", 2.0)
)
private val r2021AidsSem3 = listOf(
    sub("MA3354", "Discrete Mathematics", 4.0),
    sub("CS3351", "Digital Principles and Computer Org", 4.0),
    sub("AD3391", "Database Design and Management", 3.0),
    sub("AD3351", "Design and Analysis of Algorithms", 4.0),
    sub("AD3301", "Data Exploration and Visualization", 4.0),
    sub("AL3391", "Artificial Intelligence", 3.0),
    sub("AD3381", "Database Lab", 1.5),
    sub("AD3311", "AI Laboratory", 1.5),
    sub("GE3361", "Professional Development", 1.0)
)
private val r2021AidsSem4 = listOf(
    sub("MA3391", "Probability and Statistics", 4.0),
    sub("AL3452", "Operating Systems", 4.0),
    sub("AL3451", "Machine Learning", 3.0),
    sub("AD3491", "Fundamentals of Data Science", 3.0),
    sub("CS3591", "Computer Networks", 4.0),
    sub("GE3451", "Environmental Sciences", 2.0),
    sub("AD3411", "Data Science Lab", 2.0),
    sub("AD3461", "Machine Learning Lab", 2.0)
)
private val r2021AidsSem5 = listOf(
    sub("AD3501", "Deep Learning", 3.0),
    sub("CW3551", "Data and Information Security", 3.0),
    sub("CS3551", "Distributed Computing", 3.0),
    sub("CCS334", "Big Data Analytics", 3.0),
    elective("Professional Elective I"),
    elective("Professional Elective II"),
    sub("AD3511", "Deep Learning Lab", 2.0),
    sub("AD3512", "Summer Internship", 2.0)
)
private val r2021AidsSem6 = listOf(
    sub("CS3691", "Embedded Systems and IoT", 4.0),
    elective("Open Elective I"),
    elective("Professional Elective III"),
    elective("Professional Elective IV"),
    elective("Professional Elective V"),
    elective("Professional Elective VI")
)
private val r2021AidsSem7 = listOf(
    sub("GE3791", "Human Values and Ethics", 2.0),
    elective("Management Elective"),
    elective("Open Elective II"),
    elective("Open Elective III"),
    elective("Open Elective IV")
)
private val r2021AidsSem8 = listOf(sub("AD3811", "Project Work / Internship", 10.0))

// ---- CSBS R2021 ----
private val r2021CsbsSem2 = listOf(
    sub("HS3252", "Professional English - II", 2.0),
    sub("MA3251", "Statistics and Numerical Methods", 4.0),
    sub("PH3256", "Physics for Information Science", 3.0),
    sub("BE3251", "Basic Electrical and Electronics Engg", 3.0),
    sub("GE3251", "Engineering Graphics", 4.0),
    sub("AD3251", "Data Structures Design", 3.0),
    sub("GE3252", "Tamils and Technology", 1.0),
    sub("GE3271", "Engineering Practices Lab", 2.0),
    sub("AD3271", "Data Structures Lab", 2.0),
    sub("GE3272", "Communication Lab", 2.0)
)
private val r2021CsbsSem3 = listOf(
    sub("MA3354", "Discrete Mathematics", 4.0),
    sub("CS3351", "Digital Principles and Computer Org", 4.0),
    sub("CW3301", "Fundamentals of Economics", 3.0),
    sub("CS3391", "Object Oriented Programming", 3.0),
    sub("AD3351", "Design and Analysis of Algorithms", 4.0),
    sub("AD3491", "Fundamentals of Data Science", 3.0),
    sub("CW3311", "Business Communication Lab I", 1.5),
    sub("CS3381", "OOP Lab", 1.5),
    sub("GE3361", "Professional Development", 1.0)
)
private val r2021CsbsSem4 = listOf(
    sub("MA3391", "Probability and Statistics", 4.0),
    sub("CS3492", "Database Management Systems", 3.0),
    sub("AL3452", "Operating Systems", 4.0),
    sub("CW3401", "Introduction to Business Systems", 3.0),
    sub("AL3451", "Machine Learning", 3.0),
    sub("GE3451", "Environmental Sciences", 2.0),
    sub("CS3481", "DBMS Lab", 1.5),
    sub("AD3461", "Machine Learning Lab", 2.0),
    sub("CW3411", "Business Communication Lab II", 1.5)
)
private val r2021CsbsSem5 = listOf(
    sub("CS3691", "Embedded Systems and IoT", 4.0),
    sub("CW3501", "Fundamentals of Management", 3.0),
    sub("CW3551", "Data and Information Security", 3.0),
    elective("Professional Elective I"),
    elective("Professional Elective II"),
    sub("CW3511", "Summer Internship", 2.0)
)
private val r2021CsbsSem6 = listOf(
    sub("CW3601", "Business Analytics", 3.0),
    sub("CCS356", "Object Oriented Software Engg", 4.0),
    elective("Open Elective I"),
    elective("Professional Elective III"),
    elective("Professional Elective IV"),
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    sub("CW3611", "Business Analytics Lab", 2.0)
)
private val r2021CsbsSem7 = listOf(
    sub("GE3791", "Human Values and Ethics", 2.0),
    elective("Management Elective"),
    elective("Open Elective II"),
    elective("Open Elective III"),
    elective("Open Elective IV")
)
private val r2021CsbsSem8 = listOf(sub("CW3811", "Project Work / Internship", 10.0))

// ===================== R2025 CURRICULUM DATA =====================
// PSGiTech Autonomous — Full 8 semesters for all departments

// ---- CSE R2025 ----
private val r2025CseSem1 = listOf(
    sub("25MA101", "Calculus and its Applications", 4.0),
    sub("25CS101", "C Programming", 3.0),
    sub("25EE101", "Basics of Electrical and Electronic Systems", 3.0),
    sub("25HS101", "English Language Proficiency", 4.0),
    sub("25HS102", "Heritage of Tamils", 1.0),
    sub("25CS111", "C Programming Laboratory", 2.0),
    sub("25EE112", "Engineering Skills Laboratory", 2.0),
    sub("25GE111", "Design Thinking for Innovation", 1.0)
)
private val r2025CseSem2 = listOf(
    sub("25MA202", "Transforms and Applications", 4.0),
    sub("25PH204", "Sensors for Engineering Applications", 3.0),
    sub("25CY202", "Applied Chemistry", 3.0),
    sub("25AD201", "Python Programming", 3.0),
    sub("25CS201", "Digital Design", 4.0),
    sub("25HS201", "Tamils and Technology", 1.0),
    sub("25AD211", "Python Programming Lab", 2.0),
    sub("25BS212", "Physics and Chemistry Lab", 2.0),
    sub("25GE211", "Engineering Graphics", 2.0),
    elective("Language Elective", 2.0)
)
private val r2025CseSem3 = listOf(
    sub("25MA302", "Linear Algebra", 4.0),
    sub("25MA305", "Discrete Structures", 4.0),
    sub("25CS301", "Data Structures", 3.0),
    sub("25CS302", "Computer Organization and Architecture", 4.0),
    sub("25HS301", "Project and Finance Management", 3.0),
    sub("25CS311", "Data Structures Lab", 2.0),
    sub("25CS312", "OOP Lab", 2.0),
    sub("25EEC02", "Foundations for Problem Solving", 1.0)
)
private val r2025CseSem4 = listOf(
    sub("25MA402", "Statistical Methods and Stochastic Processes", 4.0),
    sub("25CS401", "Database Management Systems", 3.0),
    sub("25CS402", "Design and Analysis of Algorithms", 4.0),
    sub("25CS403", "Theory of Computation", 4.0),
    sub("25CS404", "Software Engineering", 4.0),
    sub("25CS411", "DBMS Lab", 2.0),
    sub("25CS412", "Application Development Lab", 2.0),
    sub("25CSE01", "Mini Project I", 1.0),
    sub("25EEC03", "Problem Solving", 1.0)
)
private val r2025CseSem5 = listOf(
    sub("25CS501", "Operating Systems", 3.0),
    sub("25CS502", "Compiler Design", 3.0),
    sub("25CS503", "Computer Networks", 3.0),
    sub("25CS504", "Artificial Intelligence", 4.0),
    elective("Professional Elective I"),
    sub("25CS511", "System Software Lab", 2.0),
    sub("25CS512", "Computer Networks Lab", 2.0),
    sub("25CSE02", "Internship I", 1.0),
    sub("25EEC04", "Aptitude Skills", 1.0)
)
private val r2025CseSem6 = listOf(
    sub("25MA602", "Graph Theory", 4.0),
    sub("25CS601", "Machine Learning", 3.0),
    sub("25CS602", "Embedded Systems", 4.0),
    elective("Professional Elective II"),
    elective("Open Elective I"),
    sub("25CS611", "Machine Learning Lab", 2.0),
    sub("25CSE04", "Mini Project II", 1.0),
    sub("25EEC06", "Enhancing Problem Solving", 1.0)
)
private val r2025CseSem7 = listOf(
    sub("25CS701", "Parallel and Distributed Systems", 3.0),
    sub("25CS702", "Cryptography and Network Security", 4.0),
    elective("Professional Elective III"),
    elective("Professional Elective IV"),
    elective("Open Elective II"),
    sub("25CS711", "Parallel Systems Lab", 2.0),
    sub("25CSE05", "Project Work I", 2.0),
    sub("25CSE06", "Internship II", 1.0)
)
private val r2025CseSem8 = listOf(
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    sub("25CSE07", "Project Work II", 4.0)
)

// ---- EEE R2025 ----
private val r2025EeeSem1 = listOf(
    sub("25MA101", "Calculus and its Applications", 4.0),
    sub("25PH103", "Physics for Electrical Engg", 3.0),
    sub("25CY103", "Chemistry for Electrical Engg", 3.0),
    sub("25ME101", "Basics of Mechanical Engg", 4.0),
    sub("25HS101", "English Language Proficiency", 4.0),
    sub("25HS102", "Heritage of Tamils", 1.0),
    sub("25BS112", "Basic Sciences Lab", 2.0),
    sub("25GE111", "Design Thinking", 1.0),
    sub("25GE112", "Engineering Graphics", 2.0),
    sub("25EE111", "Python Programming Lab", 1.0)
)
private val r2025EeeSem2 = listOf(
    sub("25MA201", "Complex Variables and Transforms", 4.0),
    sub("25EE201", "Electric Circuits and Networks", 4.0),
    sub("25EE202", "Electromagnetic Fields", 4.0),
    sub("25PH203", "Semiconductor Devices", 3.0),
    sub("25EE203", "Programming in C", 3.0),
    sub("25HS201", "Tamils and Technology", 1.0),
    elective("Language Elective", 2.0),
    sub("25EE211", "Circuits and Devices Lab", 1.0),
    sub("25EE212", "Programming in C Lab", 1.0)
)
private val r2025EeeSem3 = listOf(
    sub("25MA304", "Matrix Theory and Numerical Methods", 4.0),
    sub("25EE301", "Electronic Circuits", 4.0),
    sub("25EE302", "Measurements and Instrumentation", 3.0),
    sub("25EE303", "DC Machines and Transformers", 3.0),
    sub("25HS301", "Project and Finance Management", 3.0),
    sub("25EE311", "DC Machines Lab", 2.0),
    sub("25EE312", "Electronic Circuits Lab", 1.0),
    sub("25EEC02", "Foundations of Problem Solving", 1.0)
)
private val r2025EeeSem4 = listOf(
    sub("25MA403", "Stochastic Processes", 4.0),
    sub("25EE401", "Generation, Transmission, Distribution", 3.0),
    sub("25EE402", "Digital Electronics", 4.0),
    sub("25EE403", "Linear Integrated Circuits", 3.0),
    sub("25EE404", "AC Machines", 4.0),
    sub("25EE411", "AC Machines Lab", 2.0),
    sub("25EE412", "Digital Electronics and LIC Lab", 2.0),
    sub("25EEE01", "Mini-Project I", 1.0),
    sub("25EEC03", "Problem Solving", 1.0)
)
private val r2025EeeSem5 = listOf(
    sub("25EE501", "Control Systems", 4.0),
    sub("25EE502", "Power Electronics", 4.0),
    sub("25EE503", "Microprocessor and Microcontrollers", 3.0),
    sub("25EE504", "Digital Signal Processing", 4.0),
    elective("Professional Elective I"),
    sub("25EE511", "Power Electronics Lab", 1.0),
    sub("25EE512", "Microprocessor Lab", 2.0),
    sub("25EE513", "Instrumentation Lab", 1.0),
    sub("25EEE02", "Internship I", 1.0),
    sub("25EEC04", "Aptitude Skills", 1.0)
)
private val r2025EeeSem6 = listOf(
    sub("25EE601", "Power System Analysis", 4.0),
    sub("25EE602", "Electric Drives and Control", 3.0),
    sub("25EE603", "Data Structures using C++", 4.0),
    elective("Open Elective I"),
    elective("Professional Elective II"),
    sub("25EE611", "Electric Drives Lab", 1.0),
    sub("25EEE04", "Mini-project II", 1.0),
    sub("25EEC05", "Problem Solving with Code", 1.0)
)
private val r2025EeeSem7 = listOf(
    sub("25EE701", "Power System Protection", 4.0),
    sub("25EE702", "Electrical Machine Design", 4.0),
    elective("Professional Elective III"),
    elective("Professional Elective IV"),
    elective("Open Elective II"),
    sub("25EE711", "Power System Lab", 1.0),
    sub("25EEE05", "Project Work I", 2.0),
    sub("25EEE06", "Internship II", 1.0)
)
private val r2025EeeSem8 = listOf(
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    sub("25EEE07", "Project Work II", 4.0)
)

// ---- ECE R2025 ----
private val r2025EceSem1 = listOf(
    sub("25MA101", "Calculus and its Applications", 4.0),
    sub("25PH103", "Physics for Electrical Engg", 3.0),
    sub("25CY102", "Chemistry for Electronics Engg", 3.0),
    sub("25EC101", "Problem Solving and C Programming", 5.0),
    sub("25HS101", "English Language Proficiency", 4.0),
    sub("25HS102", "Heritage of Tamils", 1.0),
    sub("25GE111", "Design Thinking", 1.0),
    sub("25GE112", "Engineering Graphics", 2.0),
    sub("25BS112", "Basic Sciences Lab", 2.0)
)
private val r2025EceSem2 = listOf(
    sub("25MA201", "Complex Variables and Transforms", 4.0),
    sub("25PH204", "Sensors for Engineering", 3.0),
    sub("25EC201", "Electron Devices", 4.0),
    sub("25EC202", "Network Analysis", 4.0),
    sub("25EC203", "OOP with Python", 4.0),
    sub("25HS201", "Tamils and Technology", 1.0),
    elective("Language Elective", 2.0),
    sub("25EC211", "Devices and Circuits Lab", 2.0)
)
private val r2025EceSem3 = listOf(
    sub("25MA304", "Matrix Theory and Numerical Methods", 4.0),
    sub("25EC301", "Analog Electronics", 3.0),
    sub("25EC302", "Digital Electronics", 3.0),
    sub("25EC303", "Electromagnetic Fields and Waves", 4.0),
    sub("25HS301", "Project and Finance Management", 3.0),
    sub("25EC311", "Analog Electronics Lab", 1.0),
    sub("25EC312", "Digital Electronics Lab", 1.0),
    sub("25EEC02", "Foundations of Problem Solving", 1.0)
)
private val r2025EceSem4 = listOf(
    sub("25MA404", "Probability and Random Processes", 4.0),
    sub("25EC401", "Linear Integrated Circuits", 3.0),
    sub("25EC402", "Signals and Systems", 3.0),
    sub("25EC403", "Computer Architecture", 4.0),
    sub("25EC404", "Data Structures and Algorithms", 5.0),
    sub("25EC411", "LIC Lab", 1.0),
    sub("25EC412", "Signals Lab", 1.0),
    sub("25ECE01", "Mini Project I", 1.0),
    sub("25EEC03", "Problem Solving", 1.0)
)
private val r2025EceSem5 = listOf(
    sub("25EC501", "Analog Communication", 3.0),
    sub("25EC502", "Embedded Systems", 3.0),
    sub("25EC503", "Control Systems", 4.0),
    sub("25EC504", "Computer Networks", 5.0),
    sub("25EC505", "Antennas and Wave Propagation", 4.0),
    sub("25EC511", "Analog Communication Lab", 2.0),
    sub("25EC512", "Embedded Systems Lab", 2.0),
    sub("25ECE02", "Internship I", 1.0),
    sub("25EEC04", "Aptitude Skills", 1.0)
)
private val r2025EceSem6 = listOf(
    sub("25EC601", "Digital Signal Processing", 3.0),
    sub("25EC602", "Digital Communication", 3.0),
    sub("25EC603", "VLSI Design", 3.0),
    elective("Professional Elective I"),
    elective("Open Elective I"),
    sub("25EC611", "DSP Lab", 2.0),
    sub("25EC612", "VLSI Lab", 2.0),
    sub("25EC613", "Digital Comm Lab", 1.0),
    sub("25ECE04", "Mini Project II", 1.0),
    sub("25EEC05", "Problem Solving with Code", 1.0)
)
private val r2025EceSem7 = listOf(
    sub("25EC701", "RF Passive and Active Circuits", 3.0),
    elective("Professional Elective II"),
    elective("Professional Elective III"),
    elective("Professional Elective IV"),
    elective("Open Elective II"),
    sub("25EC711", "RF Lab", 1.0),
    sub("25ECE05", "Project Work I", 2.0),
    sub("25ECE06", "Internship II", 1.0)
)
private val r2025EceSem8 = listOf(
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    sub("25ECE07", "Project Work II", 4.0)
)

// ---- MECH R2025 ----
private val r2025MechSem1 = listOf(
    sub("25MA101", "Calculus and its Applications", 4.0),
    sub("25PH102", "Physics for Mechanical Engg", 3.0),
    sub("25CY104", "Chemistry of Engineering Materials", 3.0),
    sub("25EE102", "Basics of EEE", 3.0),
    sub("25HS101", "English Language Proficiency", 4.0),
    sub("25HS102", "Heritage of Tamils", 1.0),
    sub("25ME111", "Engineering Drawing with CAD", 2.0),
    sub("25GE111", "Design Thinking", 1.0),
    sub("25BS112", "Basic Sciences Lab", 2.0)
)
private val r2025MechSem2 = listOf(
    sub("25MA201", "Complex Variables and Transforms", 4.0),
    sub("25ME201", "Engineering Mechanics", 4.0),
    sub("25ME202", "Manufacturing Processes", 3.0),
    sub("25ME203", "Fluid Mechanics", 4.0),
    sub("25ME204", "Industrial Metallurgy", 3.0),
    sub("25HS201", "Tamils and Technology", 1.0),
    sub("25EE213", "EEE Lab", 1.0),
    sub("25ME211", "Makers Lab", 1.0),
    elective("Language Elective", 2.0)
)
private val r2025MechSem3 = listOf(
    sub("25MA306", "Computational Mathematics", 4.0),
    sub("25HS301", "Project and Finance Management", 3.0),
    sub("25ME301", "Mechanics of Materials", 3.0),
    sub("25ME302", "Kinematics of Machinery", 4.0),
    sub("25ME303", "Engineering Thermodynamics", 4.0),
    sub("25ME311", "Metallurgy and Mechanics Lab", 2.0),
    sub("25ME312", "Manufacturing Processes Lab", 2.0),
    sub("25EEC02", "Foundations of Problem Solving", 1.0)
)
private val r2025MechSem4 = listOf(
    sub("25MA405", "Probability and Statistics", 3.0),
    sub("25ME401", "Metal Cutting Theory", 3.0),
    sub("25ME402", "Dynamics of Machinery", 4.0),
    sub("25ME403", "IC Engines and Thermal Systems", 3.0),
    elective("Professional Elective I"),
    sub("25ME411", "Machine Drawing", 2.0),
    sub("25ME412", "Python Programming Lab", 2.0),
    sub("25EEC03", "Problem Solving", 1.0),
    sub("25MEE01", "Mini Project I", 1.0)
)
private val r2025MechSem5 = listOf(
    sub("25ME501", "Design of Machine Elements", 4.0),
    sub("25ME502", "Cooling and Propulsion", 4.0),
    sub("25ME503", "Operations Research", 3.0),
    sub("25ME504", "Turbomachinery", 4.0),
    sub("25ME505", "Metrology and Instrumentation", 3.0),
    sub("25ME511", "Fluid Mechanics Lab", 2.0),
    sub("25ME512", "Thermal Engineering Lab", 2.0),
    sub("25MEE02", "Internship I", 1.0),
    sub("25EEC04", "Aptitude Skills", 1.0)
)
private val r2025MechSem6 = listOf(
    sub("25ME601", "Mechanical System Design", 4.0),
    sub("25ME602", "Heat and Mass Transfer", 4.0),
    sub("25ME603", "Design for Manufacture", 3.0),
    elective("Professional Elective II"),
    elective("Open Elective I"),
    sub("25ME611", "Heat Transfer Lab", 2.0),
    sub("25ME612", "Dynamics and Metrology Lab", 2.0),
    sub("25EEC05", "Problem Solving with Code", 1.0),
    sub("25MEE04", "Mini Project II", 1.0)
)
private val r2025MechSem7 = listOf(
    sub("25ME701", "Finite Element Analysis", 3.0),
    sub("25ME702", "Industrial Automation", 3.0),
    elective("Professional Elective III"),
    elective("Professional Elective IV"),
    elective("Open Elective II"),
    sub("25ME711", "FEA Lab", 1.0),
    sub("25ME712", "Manufacturing Automation Lab", 1.0),
    sub("25MEE05", "Project Work I", 2.0),
    sub("25MEE06", "Internship II", 1.0)
)
private val r2025MechSem8 = listOf(
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    sub("25MEE07", "Project Work II", 4.0)
)

// ---- CIVIL R2025 ----
private val r2025CivilSem1 = listOf(
    sub("25MA101", "Calculus and its Applications", 4.0),
    sub("25PH101", "Physics for Civil Engg", 3.0),
    sub("25CY101", "Chemistry of Building Materials I", 3.0),
    sub("25CE101", "Engineering Geology", 3.0),
    sub("25HS101", "English Language Proficiency", 4.0),
    sub("25HS102", "Heritage of Tamils", 1.0),
    sub("25BS111", "Basic Sciences Lab I", 2.0),
    sub("25CE111", "Engineering Drawing", 2.0),
    sub("25GE111", "Design Thinking", 1.0)
)
private val r2025CivilSem2 = listOf(
    sub("25MA201", "Complex Variables and Transforms", 4.0),
    sub("25CE201", "Engineering Statics and Dynamics", 4.0),
    sub("25PH201", "Applied Physics", 3.0),
    sub("25CY201", "Chemistry of Building Materials II", 3.0),
    sub("25HS201", "Tamils and Technology", 1.0),
    elective("Language Elective", 2.0),
    sub("25BS211", "Basic Sciences Lab II", 2.0),
    sub("25CE211", "Engineering Practices Lab", 1.0),
    sub("25CE212", "Python Programming Lab", 2.0)
)
private val r2025CivilSem3 = listOf(
    sub("25MA304", "Matrix Theory and Numerical Methods", 4.0),
    sub("25CE301", "Mechanics of Solids I", 4.0),
    sub("25CE302", "Construction Materials and Practices", 3.0),
    sub("25CE303", "Surveying", 4.0),
    sub("25HS301", "Project and Finance Management", 3.0),
    sub("25CE311", "Strength of Materials Lab", 1.0),
    sub("25CE312", "Survey Practice", 2.0),
    sub("25EEC02", "Foundations of Problem Solving", 1.0)
)
private val r2025CivilSem4 = listOf(
    sub("25CE401", "Mechanics of Solids II", 4.0),
    sub("25CE402", "Hydraulic Engineering", 4.0),
    sub("25CE403", "Basic Structural Steel Design", 4.0),
    sub("25CE404", "Highway and Railway Engg", 3.0),
    elective("Open Elective I"),
    sub("25CE411", "Concrete and Highway Lab", 2.0),
    sub("25CEE01", "Mini Project I", 1.0),
    sub("25EEC03", "Problem Solving", 1.0)
)
private val r2025CivilSem5 = listOf(
    sub("25CE501", "Structural Analysis I", 4.0),
    sub("25CE502", "Design of RC Elements", 4.0),
    sub("25CE503", "Mechanics of Soil", 4.0),
    sub("25CE504", "Water Supply Engineering", 3.0),
    elective("Open Elective II"),
    sub("25CE511", "Soil Mechanics Lab", 1.0),
    sub("25CE512", "Hydraulics Lab", 1.0),
    sub("25CEE02", "Internship I", 1.0),
    sub("25EEC04", "Aptitude Skills", 1.0)
)
private val r2025CivilSem6 = listOf(
    sub("25CE601", "Structural Analysis II", 4.0),
    sub("25CE602", "Construction Project Management", 3.0),
    sub("25CE603", "Waste Water Engineering", 4.0),
    sub("25CE604", "Foundation Engineering", 3.0),
    sub("25CE605", "Design of Steel Structures", 4.0),
    sub("25CE611", "Environmental Engg Lab", 2.0),
    sub("25CE612", "Building Planning Lab", 1.0),
    sub("25CEE04", "Mini Project II", 1.0),
    sub("25EEC05", "Problem Solving with Code", 1.0)
)
private val r2025CivilSem7 = listOf(
    sub("25CE701", "Estimation and Costing", 4.0),
    elective("Professional Elective I"),
    elective("Professional Elective II"),
    elective("Professional Elective III"),
    elective("Professional Elective IV"),
    sub("25CE711", "Design and Detailing", 3.0),
    sub("25CE712", "Computer Analysis Lab", 2.0),
    sub("25CEE05", "Project Work I", 2.0),
    sub("25CEE06", "Internship II", 1.0)
)
private val r2025CivilSem8 = listOf(
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    sub("25CEE07", "Project Work II", 4.0)
)

// ---- AIDS R2025 ----
private val r2025AidsSem1 = listOf(
    sub("25MA101", "Calculus and its Applications", 4.0),
    sub("25CS101", "C Programming", 3.0),
    sub("25EE101", "Basics of Electrical and Electronic Systems", 3.0),
    sub("25HS101", "English Language Proficiency", 4.0),
    sub("25HS102", "Heritage of Tamils", 1.0),
    sub("25CE111", "C Programming Lab", 2.0),
    sub("25EE112", "Engineering Skills Lab", 2.0),
    sub("25GE111", "Design Thinking", 1.0)
)
private val r2025AidsSem2 = listOf(
    sub("25MA202", "Transforms and Applications", 4.0),
    sub("25MA203", "Discrete Mathematics", 4.0),
    sub("25CY202", "Applied Chemistry", 3.0),
    sub("25AD201", "Python Programming", 3.0),
    sub("25AD202", "Digital Principles and Computer Org", 3.0),
    sub("25HS201", "Tamils and Technology", 1.0),
    sub("25AD211", "Python Programming Lab", 2.0),
    sub("25CY211", "Chemistry Lab", 2.0),
    sub("25GE211", "Engineering Graphics", 2.0),
    elective("Language Elective", 2.0)
)
private val r2025AidsSem3 = listOf(
    sub("25MA301", "Linear Algebra and Applications", 4.0),
    sub("25MA303", "Probability and Statistics", 4.0),
    sub("25CS301", "Data Structures", 3.0),
    sub("25AD301", "Fundamentals of Data Science", 3.0),
    sub("25AD302", "Data Curation and Visualization", 3.0),
    sub("25CS311", "Data Structures Lab", 2.0),
    sub("25AD311", "Data Analytics Lab", 2.0),
    sub("25CS312", "OOP Lab", 2.0),
    sub("25EEC02", "Foundations for Problem Solving", 1.0)
)
private val r2025AidsSem4 = listOf(
    sub("25MA401", "Optimization Techniques", 4.0),
    sub("25CS401", "Database Management Systems", 3.0),
    sub("25AD401", "Algorithms", 4.0),
    sub("25AD402", "Artificial Intelligence Systems", 4.0),
    sub("25AD403", "Machine Learning for Data Science", 3.0),
    sub("25CS411", "DBMS Lab", 2.0),
    sub("25AD412", "AI and ML Lab", 2.0),
    sub("25AD413", "R Programming Lab", 2.0),
    sub("25ADE01", "Mini Project I", 1.0),
    sub("25EEC03", "Problem Solving", 1.0)
)
private val r2025AidsSem5 = listOf(
    sub("25HS501", "Project and Finance Management", 3.0),
    sub("25AD501", "Deep Learning", 3.0),
    sub("25AD502", "Operating System Principles", 3.0),
    sub("25AD503", "Data Communication Networks", 3.0),
    elective("Professional Elective I"),
    sub("25AD511", "Deep Learning Lab", 2.0),
    sub("25AD512", "OS Lab", 2.0),
    sub("25ADE02", "Internship I", 1.0),
    sub("25EEC04", "Aptitude Skills", 1.0)
)
private val r2025AidsSem6 = listOf(
    sub("25MA601", "Graph Theory and Mining", 4.0),
    sub("25AD601", "Reinforcement Learning", 4.0),
    sub("25AD602", "Gen AI and Small Language Models", 3.0),
    elective("Professional Elective II"),
    elective("Open Elective I"),
    sub("25AD611", "Gen AI Lab", 2.0),
    sub("25ADE04", "Mini Project II", 1.0),
    sub("25EEC06", "Enhancing Problem Solving", 1.0)
)
private val r2025AidsSem7 = listOf(
    sub("25AD701", "Big Data and Advanced DB", 4.0),
    sub("25AD702", "Computer Vision", 4.0),
    elective("Professional Elective III"),
    elective("Professional Elective IV"),
    elective("Open Elective II"),
    sub("25ADE05", "Project Work I", 2.0),
    sub("25ADE06", "Internship II", 1.0)
)
private val r2025AidsSem8 = listOf(
    elective("Professional Elective V"),
    elective("Professional Elective VI"),
    sub("25ADE07", "Project Work II", 4.0)
)

// ===================== CURRICULUM LOOKUP =====================

fun getCurriculum(dept: Department, regulation: Regulation): Map<Int, List<CurriculumSubject>> {
    if (regulation == Regulation.R2025) {
        return when (dept) {
            Department.CSE -> mapOf(
                1 to r2025CseSem1, 2 to r2025CseSem2, 3 to r2025CseSem3, 4 to r2025CseSem4,
                5 to r2025CseSem5, 6 to r2025CseSem6, 7 to r2025CseSem7, 8 to r2025CseSem8
            )
            Department.EEE -> mapOf(
                1 to r2025EeeSem1, 2 to r2025EeeSem2, 3 to r2025EeeSem3, 4 to r2025EeeSem4,
                5 to r2025EeeSem5, 6 to r2025EeeSem6, 7 to r2025EeeSem7, 8 to r2025EeeSem8
            )
            Department.ECE -> mapOf(
                1 to r2025EceSem1, 2 to r2025EceSem2, 3 to r2025EceSem3, 4 to r2025EceSem4,
                5 to r2025EceSem5, 6 to r2025EceSem6, 7 to r2025EceSem7, 8 to r2025EceSem8
            )
            Department.MECH -> mapOf(
                1 to r2025MechSem1, 2 to r2025MechSem2, 3 to r2025MechSem3, 4 to r2025MechSem4,
                5 to r2025MechSem5, 6 to r2025MechSem6, 7 to r2025MechSem7, 8 to r2025MechSem8
            )
            Department.CIVIL -> mapOf(
                1 to r2025CivilSem1, 2 to r2025CivilSem2, 3 to r2025CivilSem3, 4 to r2025CivilSem4,
                5 to r2025CivilSem5, 6 to r2025CivilSem6, 7 to r2025CivilSem7, 8 to r2025CivilSem8
            )
            Department.AIDS -> mapOf(
                1 to r2025AidsSem1, 2 to r2025AidsSem2, 3 to r2025AidsSem3, 4 to r2025AidsSem4,
                5 to r2025AidsSem5, 6 to r2025AidsSem6, 7 to r2025AidsSem7, 8 to r2025AidsSem8
            )
            Department.CSBS -> mapOf(
                // CSBS not available in R2025 PSGiTech, fall back to CSE
                1 to r2025CseSem1, 2 to r2025CseSem2, 3 to r2025CseSem3, 4 to r2025CseSem4,
                5 to r2025CseSem5, 6 to r2025CseSem6, 7 to r2025CseSem7, 8 to r2025CseSem8
            )
        }
    }

    // R2021
    return when (dept) {
        Department.CSE -> mapOf(
            1 to r2021Sem1Common, 2 to r2021CseSem2, 3 to r2021CseSem3, 4 to r2021CseSem4,
            5 to r2021CseSem5, 6 to r2021CseSem6, 7 to r2021CseSem7, 8 to r2021CseSem8
        )
        Department.EEE -> mapOf(
            1 to r2021Sem1Common, 2 to r2021EeeSem2, 3 to r2021EeeSem3, 4 to r2021EeeSem4,
            5 to r2021EeeSem5, 6 to r2021EeeSem6, 7 to r2021EeeSem7, 8 to r2021EeeSem8
        )
        Department.ECE -> mapOf(
            1 to r2021Sem1Common, 2 to r2021EceSem2, 3 to r2021EceSem3, 4 to r2021EceSem4,
            5 to r2021EceSem5, 6 to r2021EceSem6, 7 to r2021EceSem7, 8 to r2021EceSem8
        )
        Department.MECH -> mapOf(
            1 to r2021Sem1Common, 2 to r2021MechSem2, 3 to r2021MechSem3, 4 to r2021MechSem4,
            5 to r2021MechSem5, 6 to r2021MechSem6, 7 to r2021MechSem7, 8 to r2021MechSem8
        )
        Department.CIVIL -> mapOf(
            1 to r2021Sem1Common, 2 to r2021CivilSem2, 3 to r2021CivilSem3, 4 to r2021CivilSem4,
            5 to r2021CivilSem5, 6 to r2021CivilSem6, 7 to r2021CivilSem7, 8 to r2021CivilSem8
        )
        Department.AIDS -> mapOf(
            1 to r2021Sem1Common, 2 to r2021AidsSem2, 3 to r2021AidsSem3, 4 to r2021AidsSem4,
            5 to r2021AidsSem5, 6 to r2021AidsSem6, 7 to r2021AidsSem7, 8 to r2021AidsSem8
        )
        Department.CSBS -> mapOf(
            1 to r2021Sem1Common, 2 to r2021CsbsSem2, 3 to r2021CsbsSem3, 4 to r2021CsbsSem4,
            5 to r2021CsbsSem5, 6 to r2021CsbsSem6, 7 to r2021CsbsSem7, 8 to r2021CsbsSem8
        )
    }
}
