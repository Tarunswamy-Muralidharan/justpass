package com.justpass.app.data.model

/**
 * User-submitted bug report or feature request. Image is optional and
 * uploaded to Cloud Storage at bug_reports/{id}/img.jpg before this doc
 * is written to Firestore — by the time the doc exists, the URL is
 * already valid + viewable.
 */
data class BugReport(
    val id: String = "",
    val reporterPlayerId: String = "",  // p_${rollHash}
    val reporterName: String = "",
    val reporterRollNumber: String = "",
    val reporterDepartment: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",          // Cloud Storage download URL (empty if no image)
    val deviceModel: String = "",
    val osVersion: String = "",
    val appVersion: String = "",
    val status: String = "open",        // open / fixed / wontfix / duplicate
    val resolution: String = "",
    val createdAt: Long = 0L,
    val resolvedAt: Long = 0L
)
