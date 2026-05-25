package com.justpass.app.data.model

/**
 * Single message inside a bug-report conversation thread. `from` is
 * either `"user"` (the original reporter) or `"admin"` (developer).
 */
data class BugReportMessage(
    val from: String = "user",
    val text: String = "",
    val timestamp: Long = 0L
)

/**
 * User-submitted bug report or feature request. Image is optional and
 * uploaded to Cloud Storage at bug_reports/{id}/img.jpg before this doc
 * is written to Firestore — by the time the doc exists, the URL is
 * already valid + viewable.
 *
 * As of 2026-05-25 the previous single `adminReply` field is replaced
 * by a `messages` array so reporter + admin can carry on a thread.
 * `userUnread` / `adminUnread` flip when the opposite side posts a new
 * message; the receiving client clears its own flag when the thread is
 * opened. Dot indicators on dashboard + profile screen subscribe to
 * these flags.
 */
data class BugReport(
    val id: String = "",
    val reporterPlayerId: String = "",
    val reporterName: String = "",
    val reporterRollNumber: String = "",
    val reporterDepartment: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val deviceModel: String = "",
    val osVersion: String = "",
    val appVersion: String = "",
    val status: String = "open",
    val resolution: String = "",
    val createdAt: Long = 0L,
    val resolvedAt: Long = 0L,
    val messages: List<BugReportMessage> = emptyList(),
    val userUnread: Boolean = false,
    val adminUnread: Boolean = false,
    // Kept for backwards compat — old single-reply field. New writes
    // append to `messages` instead.
    val adminReply: String = "",
    val repliedAt: Long = 0L
)
