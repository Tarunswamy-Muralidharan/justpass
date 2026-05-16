package com.justpass.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Class marks comparison — local data model mirroring the Cloudflare D1
 * route shapes. Server-side aggregation done in the Worker; the client just
 * uploads its own marks and reads precomputed stats.
 *
 * Layered between SIS API (CourseMarks / CAMarksResponse) and the Worker
 * payload so the upload-side translation is one place.
 */

/** Body for POST /class/marks */
data class ClassMarksUploadBody(
    @SerializedName("classKey") val classKey: String,
    @SerializedName("subjects") val subjects: Map<String, ClassSubjectMark>,
    @SerializedName("overallAvg") val overallAvg: Double,
)

/** Per-subject snapshot uploaded for comparison. Only what the leaderboard needs. */
data class ClassSubjectMark(
    @SerializedName("ca1") val ca1: Double? = null,
    @SerializedName("ca2") val ca2: Double? = null,
    @SerializedName("ca3") val ca3: Double? = null,
    @SerializedName("total") val total: Double? = null,
    @SerializedName("status") val status: String? = null,
)

/** Response from GET /class/:classKey */
data class ClassStatsResponse(
    @SerializedName("classKey") val classKey: String,
    @SerializedName("studentCount") val studentCount: Int,
    @SerializedName("overall") val overall: ClassOverallStats,
    @SerializedName("subjects") val subjects: Map<String, ClassSubjectStats>,
    @SerializedName("yourRank") val yourRank: Int? = null,
    @SerializedName("yourPercentile") val yourPercentile: Int? = null,
    @SerializedName("overallHistogram") val overallHistogram: List<Int> = emptyList(),
)

data class ClassOverallStats(
    @SerializedName("avg") val avg: Double,
    @SerializedName("min") val min: Double,
    @SerializedName("max") val max: Double,
)

data class ClassSubjectStats(
    @SerializedName("avg") val avg: Double,
    @SerializedName("min") val min: Double,
    @SerializedName("max") val max: Double,
    @SerializedName("yourMark") val yourMark: Double? = null,
    @SerializedName("histogram") val histogram: List<Int> = emptyList(),
)
