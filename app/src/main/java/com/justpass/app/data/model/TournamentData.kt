package com.justpass.app.data.model

/**
 * A tournament-creation request submitted by a user. Awaits admin approval
 * before it becomes an active tournament. Admin sees the requester's name,
 * department, and phone number (verified via Firebase Phone Auth) so the
 * approval is informed by who's actually behind the request.
 */
data class TournamentRequest(
    val id: String = "",
    val creatorPlayerId: String = "",      // p_${rollHash}
    val creatorName: String = "",
    val creatorRollNumber: String = "",
    val creatorDepartment: String = "",
    val creatorPhone: String = "",         // +91xxxxxxxxxx — verified via OTP
    val tournamentName: String = "",
    val format: String = "Blitz",          // Bullet / Blitz / Rapid / Classical
    val maxParticipants: Int = 16,         // 8 / 16 / 32
    val description: String = "",
    val status: String = "pending",        // pending / approved / rejected
    val rejectionReason: String = "",
    val createdAt: Long = 0L,
    val decidedAt: Long = 0L
)

/** Tournament that's been approved. Just a thin wrapper for now — actual
 *  bracket / matches / standings live in separate sub-collections later. */
data class Tournament(
    val id: String = "",
    val name: String = "",
    val format: String = "Blitz",
    val maxParticipants: Int = 16,
    val description: String = "",
    val creatorPlayerId: String = "",
    val creatorName: String = "",
    val createdAt: Long = 0L,
    val startedAt: Long = 0L,
    val endedAt: Long = 0L
)

/**
 * Admin gate for tournament approvals + bug-report inbox + admin
 * management. Hybrid cache:
 *
 *  - HARDCODED_PLAYER_IDS — bootstrap set baked into the APK. Always
 *    treated as admin even if Firestore is unreachable. Prevents getting
 *    locked out of the app.
 *  - dynamicPlayerIds — set populated at app start by
 *    AdminRolesRepository.listenAdminPlayerIds. Lets you grant / revoke
 *    admin access without shipping an APK.
 */
object TournamentAdmins {
    val HARDCODED_PLAYER_IDS: Set<String> = setOf(
        "p_678fd629" // Tarunswamy Muralidharan — bootstrap admin
    )

    @Volatile private var dynamicPlayerIds: Set<String> = emptySet()

    fun setDynamicAdmins(ids: Set<String>) {
        dynamicPlayerIds = ids
    }

    fun isAdmin(playerId: String?): Boolean {
        if (playerId.isNullOrBlank()) return false
        return playerId in HARDCODED_PLAYER_IDS || playerId in dynamicPlayerIds
    }
}
