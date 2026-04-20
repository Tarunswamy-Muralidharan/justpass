package com.justpass.app.data.model

data class AttendanceData(
    val presentCount: Int,              // Without exemption
    val presentWithExemptionCount: Int,  // With exemption (present + exemption)
    val absentCount: Int,
    val enteredTillDate: Int,           // Total classes entered
    val notEnteredTillDate: Int,        // Classes not yet entered
    val attendancePercentage: Double,    // Without exemption
    val attendanceWithExemption: Double, // With exemption (main percentage)
    val exemptionCount: Int,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromResponse(response: AttendanceResponse): AttendanceData {
            val withExemption = response.attendanceWithExemption ?: response.attendancePercentage
            val presentWithExemption = response.presentWithExemptionCount ?: response.presentCount
            return AttendanceData(
                presentCount = response.presentCount,
                presentWithExemptionCount = presentWithExemption,
                absentCount = response.absentCount,
                enteredTillDate = response.enteredTillDate ?: (response.presentCount + response.absentCount),
                notEnteredTillDate = response.notEnteredTillDate ?: 0,
                attendancePercentage = response.attendancePercentage,
                attendanceWithExemption = withExemption,
                exemptionCount = response.exemptionCount ?: 0
            )
        }

        fun empty(): AttendanceData {
            return AttendanceData(
                presentCount = 0,
                presentWithExemptionCount = 0,
                absentCount = 0,
                enteredTillDate = 0,
                notEnteredTillDate = 0,
                attendancePercentage = 0.0,
                attendanceWithExemption = 0.0,
                exemptionCount = 0,
                lastUpdated = 0
            )
        }
    }
}