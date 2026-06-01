package com.justpass.app

import com.justpass.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class CgpaDataTest {

    // ─── getRegulationForBatch ───
    @Test
    fun `getRegulationForBatch returns R2025 for batch 2025 and above`() {
        assertEquals(Regulation.R2025, getRegulationForBatch(2025))
        assertEquals(Regulation.R2025, getRegulationForBatch(2026))
        assertEquals(Regulation.R2025, getRegulationForBatch(2030))
    }

    @Test
    fun `getRegulationForBatch returns R2021 for batch below 2025`() {
        assertEquals(Regulation.R2021, getRegulationForBatch(2024))
        assertEquals(Regulation.R2021, getRegulationForBatch(2023))
        assertEquals(Regulation.R2021, getRegulationForBatch(2021))
    }

    // ─── detectDepartment ───
    @Test
    fun `detectDepartment returns exact short name match`() {
        assertEquals(Department.CSE, detectDepartment("CSE"))
        assertEquals(Department.ECE, detectDepartment("ECE"))
        assertEquals(Department.EEE, detectDepartment("EEE"))
    }

    @Test
    fun `detectDepartment handles B_TECH prefix`() {
        assertEquals(Department.CSE, detectDepartment("B.TECH CSE"))
        assertEquals(Department.MECH, detectDepartment("BTECH MECH"))
    }

    @Test
    fun `detectDepartment returns null for unknown input`() {
        assertNull(detectDepartment("UNKNOWN"))
        assertNull(detectDepartment(null))
        assertNull(detectDepartment(""))
    }

    @Test
    fun `detectDepartment prioritizes VLSI over generic ELECTRONICS`() {
        assertEquals(Department.VLSI, detectDepartment("Electronics Engineering (VLSI Design & Technology)"))
    }

    @Test
    fun `detectDepartment prioritizes ICE over ELECTRICAL`() {
        assertEquals(Department.ICE, detectDepartment("Electronics and Instrumentation"))
    }

    @Test
    fun `detectDepartment falls back IT to CSE`() {
        assertEquals(Department.CSE, detectDepartment("Information Technology"))
    }

    // ─── calculateSGPA ───
    @Test
    fun `calculateSGPA returns 0 for empty list`() {
        assertEquals(0.0, calculateSGPA(emptyList()), 0.001)
    }

    @Test
    fun `calculateSGPA returns 0 when no grades set`() {
        val grades = listOf(
            SubjectGrade(CurriculumSubject("CS101", "DS", 3.0), grade = null),
            SubjectGrade(CurriculumSubject("CS102", "Algo", 4.0), grade = null)
        )
        assertEquals(0.0, calculateSGPA(grades), 0.001)
    }

    @Test
    fun `calculateSGPA computes correctly`() {
        val grades = listOf(
            SubjectGrade(CurriculumSubject("CS101", "DS", 3.0), grade = LetterGrade.O),   // 10
            SubjectGrade(CurriculumSubject("CS102", "Algo", 4.0), grade = LetterGrade.A), // 8
            SubjectGrade(CurriculumSubject("CS103", "DB", 3.0), grade = LetterGrade.B_PLUS) // 7
        )
        // (3*10 + 4*8 + 3*7) / (3+4+3) = (30+32+21)/10 = 83/10 = 8.3
        assertEquals(8.3, calculateSGPA(grades), 0.001)
    }

    @Test
    fun `calculateSGPA ignores zero credit subjects`() {
        val grades = listOf(
            SubjectGrade(CurriculumSubject("HS101", "Ethics", 0.0), grade = LetterGrade.O),
            SubjectGrade(CurriculumSubject("CS101", "DS", 3.0), grade = LetterGrade.A)
        )
        assertEquals(8.0, calculateSGPA(grades), 0.001)
    }

    // ─── calculateCGPA ───
    @Test
    fun `calculateCGPA returns 0 for empty semesters`() {
        assertEquals(0.0, calculateCGPA(emptyList()), 0.001)
    }

    @Test
    fun `calculateCGPA computes across semesters`() {
        val sem1 = listOf(
            SubjectGrade(CurriculumSubject("CS101", "DS", 3.0), grade = LetterGrade.O)
        )
        val sem2 = listOf(
            SubjectGrade(CurriculumSubject("CS102", "Algo", 4.0), grade = LetterGrade.A)
        )
        // (3*10 + 4*8) / 7 = 62/7 = 8.857...
        assertEquals(62.0 / 7.0, calculateCGPA(listOf(sem1, sem2)), 0.001)
    }

    // ─── totalMarksToGradePoint ───
    @Test
    fun `totalMarksToGradePoint boundary values`() {
        assertEquals(10, totalMarksToGradePoint(91))
        assertEquals(10, totalMarksToGradePoint(100))
        assertEquals(9, totalMarksToGradePoint(81))
        assertEquals(9, totalMarksToGradePoint(90))
        assertEquals(8, totalMarksToGradePoint(71))
        assertEquals(8, totalMarksToGradePoint(80))
        assertEquals(7, totalMarksToGradePoint(61))
        assertEquals(7, totalMarksToGradePoint(70))
        assertEquals(6, totalMarksToGradePoint(56))
        assertEquals(6, totalMarksToGradePoint(60))
        assertEquals(5, totalMarksToGradePoint(50))
        assertEquals(5, totalMarksToGradePoint(55))
        assertEquals(0, totalMarksToGradePoint(49))
        assertEquals(0, totalMarksToGradePoint(0))
    }

    // ─── gradePointToMinMarks ───
    @Test
    fun `gradePointToMinMarks returns correct boundaries`() {
        assertEquals(91, gradePointToMinMarks(10))
        assertEquals(81, gradePointToMinMarks(9))
        assertEquals(71, gradePointToMinMarks(8))
        assertEquals(61, gradePointToMinMarks(7))
        assertEquals(56, gradePointToMinMarks(6))
        assertEquals(50, gradePointToMinMarks(5))
        assertEquals(0, gradePointToMinMarks(0))
    }

    // ─── gradePointToLetter ───
    @Test
    fun `gradePointToLetter returns correct labels`() {
        assertEquals("O", gradePointToLetter(10))
        assertEquals("A+", gradePointToLetter(9))
        assertEquals("A", gradePointToLetter(8))
        assertEquals("B+", gradePointToLetter(7))
        assertEquals("B", gradePointToLetter(6))
        assertEquals("C", gradePointToLetter(5))
        assertEquals("RA", gradePointToLetter(0))
    }

    // ─── calculateTargetCgpaFromLocal ───
    @Test
    fun `calculateTargetCgpaFromLocal returns already achieved when required SGPA is zero or negative`() {
        val result = calculateTargetCgpaFromLocal(
            targetCgpa = 8.0,
            currentCgpa = 8.5,
            previousCredits = 20,
            previousWeightedSum = 170,
            currentCAMarks = emptyMap(),
            currentSemSubjects = emptyMap()
        )
        assertTrue(result.isAchievable)
        assertEquals("Already achieved! Any passing grade will do.", result.message)
    }

    @Test
    fun `calculateTargetCgpaFromLocal marks as not achievable when required SGPA exceeds 10`() {
        // Need previous credits so that a 10.0 target pulls requiredSgpa above 10.0
        val result = calculateTargetCgpaFromLocal(
            targetCgpa = 10.0,
            currentCgpa = 7.0,
            previousCredits = 20,
            previousWeightedSum = 140,
            currentCAMarks = mapOf("CS101" to Pair(30.0, 40.0)),
            currentSemSubjects = mapOf("CS101" to Pair("DS", 3))
        )
        assertFalse(result.isAchievable)
        assertTrue(result.message.contains("max 10.0"))
    }

    @Test
    fun `calculateTargetCgpaFromLocal computes per-subject requirements`() {
        val result = calculateTargetCgpaFromLocal(
            targetCgpa = 8.0,
            currentCgpa = 7.0,
            previousCredits = 20,
            previousWeightedSum = 140,
            currentCAMarks = mapOf("CS101" to Pair(35.0, 40.0)),
            currentSemSubjects = mapOf("CS101" to Pair("DS", 3))
        )
        assertEquals(1, result.subjects.size)
        val sub = result.subjects[0]
        assertEquals("CS101", sub.courseCode)
        assertTrue(sub.requiredGradePoint in 5..10)
    }

    @Test
    fun `calculateTargetCgpaFromLocal includes zero credit subjects in map`() {
        val result = calculateTargetCgpaFromLocal(
            targetCgpa = 8.0,
            currentCgpa = 7.0,
            previousCredits = 20,
            previousWeightedSum = 140,
            currentCAMarks = mapOf("HS101" to Pair(0.0, 0.0)),
            currentSemSubjects = mapOf("HS101" to Pair("Nation Building", 0))
        )
        // With 0 current-sem credits, requiredSgpa = 0, so it returns "Already achieved"
        assertTrue(result.isAchievable)
        assertEquals("Already achieved! Any passing grade will do.", result.message)
    }
}
