package com.justpass.app.data.model

import com.google.firebase.firestore.Exclude

/**
 * Previous-year question paper — Firestore-mapped representation.
 *
 * Stored at `qpapers/{paperId}`. Public-readable when `status == "approved"`,
 * admin-only otherwise. CRUCIALLY contains NO contributor identity — the
 * uploader's name/roll lives in `qpapers_contributors/{paperId}` so a reader
 * can never link a paper to a person.
 *
 * The PDF itself sits on Cloudinary at [cloudinaryUrl]. [cloudinaryPublicId]
 * is kept so admin can reference it during cleanup (Cloudinary's destroy
 * API needs the public_id, signing happens server-side outside this app).
 */
data class QPaper(
    @get:Exclude val id: String = "",
    val department: String = "",        // CSE / EEE / ECE / MECH / CIVIL / AIDS / CSBS
    val subjectCode: String = "",       // e.g. 23CS101, 19CS501
    val subjectName: String = "",
    val category: String = "",          // ca1 / ca2 / sem
    val examYear: Int = 0,              // 2020..2030
    val semester: Int = 0,              // 1..8
    val regulation: String = "",        // R2021 / R2025
    val cloudinaryUrl: String = "",     // https://res.cloudinary.com/{cloud}/raw/upload/.../{paperId}.pdf
    val cloudinaryPublicId: String = "",
    val status: String = "pending",     // pending / approved / rejected
    val contributedAt: Long = 0L,
    val approvedAt: Long? = null,
    val approvedBy: String? = null,     // uid of approving admin
    val viewCount: Int = 0,
) {
    @get:Exclude
    val categoryEnum: PaperCategory
        get() = PaperCategory.fromKey(category) ?: PaperCategory.CA1

    @get:Exclude
    val isApproved: Boolean get() = status == "approved"

    @get:Exclude
    val isPending: Boolean get() = status == "pending"
}

/**
 * Categories of papers. Persisted as lowercase keys ('ca1', 'ca2', 'sem')
 * for stable Firestore queries that don't care about case folding.
 */
enum class PaperCategory(val key: String, val label: String) {
    CA1("ca1", "CA1"),
    CA2("ca2", "CA2"),
    SEM("sem", "Sem Paper");

    companion object {
        fun fromKey(key: String): PaperCategory? =
            entries.firstOrNull { it.key == key.lowercase() }

        val all: List<PaperCategory> get() = entries.toList()
    }
}

/**
 * Internal contributor record — stored at `qpapers_contributors/{paperId}`.
 * Admin-only via Firestore Rules, plus self-readable so the contributor
 * can list their own contributions in the UI.
 *
 * Used for two things:
 *  1. Admin verification UI: see who uploaded what.
 *  2. "Already contributed for this slot" check in the upload screen
 *     — query by (uid, subjectCode, category, examYear, regulation).
 */
data class QPaperContributor(
    @get:Exclude val paperId: String = "",
    val uid: String = "",                // Firebase Auth UID
    val displayName: String = "",
    val rollNumber: String = "",
    val submittedAt: Long = 0L,
    // Denormalised search fields so we can query "have I already
    // contributed for THIS specific slot" without joining qpapers.
    val subjectCode: String = "",
    val category: String = "",
    val examYear: Int = 0,
    val regulation: String = "",
)

/**
 * What the user has selected in the upload form before they pick a file.
 * Drives the "already contributed" check on screen mount.
 */
data class UploadIntent(
    val department: String,
    val subjectCode: String,
    val subjectName: String,
    val semester: Int,
    val regulation: String,
    val category: PaperCategory,
    val examYear: Int,
)

/**
 * Result of a Cloudinary unsigned upload. Returned by [CloudinaryUploader].
 */
data class CloudinaryUploadResult(
    val secureUrl: String,
    val publicId: String,
    val bytes: Long,
    val format: String,
)
