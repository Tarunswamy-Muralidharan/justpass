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

/** Player IDs that can approve / reject tournament requests. Hardcoded
 *  for now — derived from the dev's roll-number hash. */
object TournamentAdmins {
    val PLAYER_IDS: Set<String> = setOf(
        "p_678fd629" // Tarunswamy Muralidharan
    )

    fun isAdmin(playerId: String?): Boolean =
        playerId != null && playerId in PLAYER_IDS
}
