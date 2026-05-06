package com.justpass.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.justpass.app.data.model.Tournament
import com.justpass.app.data.model.TournamentRequest
import kotlinx.coroutines.tasks.await

/**
 * CRUD for tournament_requests + tournaments collections. Phone OTP
 * verification lives in PhoneAuthHelper; this repo handles persistence
 * once the requester's phone is verified.
 */
class TournamentRepository {

    private val db = FirebaseFirestore.getInstance()
    private val requests = db.collection("tournament_requests")
    private val tournaments = db.collection("tournaments")

    /**
     * Persist a new tournament request. Caller must have verified the phone
     * via Firebase Phone Auth before calling this — `creatorPhone` is taken
     * at face value here, security is the precondition + Firestore rules.
     */
    suspend fun submitRequest(req: TournamentRequest): String? {
        return try {
            val payload = hashMapOf(
                "creatorPlayerId" to req.creatorPlayerId,
                "creatorName" to req.creatorName,
                "creatorRollNumber" to req.creatorRollNumber,
                "creatorDepartment" to req.creatorDepartment,
                "creatorPhone" to req.creatorPhone,
                "tournamentName" to req.tournamentName,
                "format" to req.format,
                "maxParticipants" to req.maxParticipants,
                "description" to req.description,
                "status" to "pending",
                "rejectionReason" to "",
                "createdAt" to System.currentTimeMillis(),
                "decidedAt" to 0L
            )
            val ref = requests.add(payload).await()
            ref.id
        } catch (e: Exception) {
            Log.e(TAG, "submitRequest failed: ${e.message}")
            null
        }
    }

    /** Listen for pending requests — admin-only consumer. */
    fun listenPendingRequests(onUpdate: (List<TournamentRequest>) -> Unit): ListenerRegistration {
        return requests
            .whereEqualTo("status", "pending")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listenPendingRequests err: ${err.message}")
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { doc ->
                    TournamentRequest(
                        id = doc.id,
                        creatorPlayerId = doc.getString("creatorPlayerId") ?: "",
                        creatorName = doc.getString("creatorName") ?: "",
                        creatorRollNumber = doc.getString("creatorRollNumber") ?: "",
                        creatorDepartment = doc.getString("creatorDepartment") ?: "",
                        creatorPhone = doc.getString("creatorPhone") ?: "",
                        tournamentName = doc.getString("tournamentName") ?: "",
                        format = doc.getString("format") ?: "Blitz",
                        maxParticipants = doc.getLong("maxParticipants")?.toInt() ?: 16,
                        description = doc.getString("description") ?: "",
                        status = doc.getString("status") ?: "pending",
                        rejectionReason = doc.getString("rejectionReason") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        decidedAt = doc.getLong("decidedAt") ?: 0L
                    )
                } ?: emptyList()
                onUpdate(list)
            }
    }

    suspend fun approveRequest(requestId: String): Boolean {
        return try {
            val snap = requests.document(requestId).get().await()
            if (!snap.exists()) return false
            val now = System.currentTimeMillis()
            // Mirror approved request into tournaments collection so later
            // bracket/match logic can query a clean source of truth.
            val tournamentPayload = hashMapOf(
                "name" to (snap.getString("tournamentName") ?: ""),
                "format" to (snap.getString("format") ?: "Blitz"),
                "maxParticipants" to (snap.getLong("maxParticipants")?.toInt() ?: 16),
                "description" to (snap.getString("description") ?: ""),
                "creatorPlayerId" to (snap.getString("creatorPlayerId") ?: ""),
                "creatorName" to (snap.getString("creatorName") ?: ""),
                "createdAt" to now,
                "startedAt" to 0L,
                "endedAt" to 0L
            )
            tournaments.add(tournamentPayload).await()
            requests.document(requestId).update(mapOf(
                "status" to "approved",
                "decidedAt" to now
            )).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "approveRequest failed: ${e.message}")
            false
        }
    }

    suspend fun rejectRequest(requestId: String, reason: String): Boolean {
        return try {
            requests.document(requestId).update(mapOf(
                "status" to "rejected",
                "rejectionReason" to reason,
                "decidedAt" to System.currentTimeMillis()
            )).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "rejectRequest failed: ${e.message}")
            false
        }
    }

    /** List user's own pending or recently-decided requests. */
    suspend fun getMyRequests(playerId: String): List<TournamentRequest> {
        return try {
            val snap = requests
                .whereEqualTo("creatorPlayerId", playerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10)
                .get().await()
            snap.documents.map { doc ->
                TournamentRequest(
                    id = doc.id,
                    creatorPlayerId = doc.getString("creatorPlayerId") ?: "",
                    creatorName = doc.getString("creatorName") ?: "",
                    creatorRollNumber = doc.getString("creatorRollNumber") ?: "",
                    creatorDepartment = doc.getString("creatorDepartment") ?: "",
                    creatorPhone = doc.getString("creatorPhone") ?: "",
                    tournamentName = doc.getString("tournamentName") ?: "",
                    format = doc.getString("format") ?: "Blitz",
                    maxParticipants = doc.getLong("maxParticipants")?.toInt() ?: 16,
                    description = doc.getString("description") ?: "",
                    status = doc.getString("status") ?: "pending",
                    rejectionReason = doc.getString("rejectionReason") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    decidedAt = doc.getLong("decidedAt") ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMyRequests failed: ${e.message}")
            emptyList()
        }
    }

    /** Active approved tournaments. */
    suspend fun listActiveTournaments(): List<Tournament> {
        return try {
            val snap = tournaments
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()
            snap.documents.map { doc ->
                Tournament(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    format = doc.getString("format") ?: "Blitz",
                    maxParticipants = doc.getLong("maxParticipants")?.toInt() ?: 16,
                    description = doc.getString("description") ?: "",
                    creatorPlayerId = doc.getString("creatorPlayerId") ?: "",
                    creatorName = doc.getString("creatorName") ?: "",
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    startedAt = doc.getLong("startedAt") ?: 0L,
                    endedAt = doc.getLong("endedAt") ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "listActiveTournaments failed: ${e.message}")
            emptyList()
        }
    }

    companion object { private const val TAG = "TournamentRepo" }
}
