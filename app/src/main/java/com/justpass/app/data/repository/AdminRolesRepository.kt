package com.justpass.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Manages the dynamic list of admin players. Stored in two parallel
 * Firestore collections to satisfy Firestore rules:
 *
 *  - `admin_roles/{playerId}` — keyed by `p_${rollHash}`. Carries metadata
 *    (name, addedAt, addedBy). This is the human-readable source of truth.
 *
 *  - `admin_uids/{firebaseUid}` — keyed by Firebase Auth UID. Only used
 *    by the rules engine to authorise writes (rules can't compute the
 *    hash from a roll number, so we mirror UIDs into a flat collection
 *    that exists()-checks cleanly).
 *
 * Bootstrap (one-time, via Firebase Console):
 *  - Create `admin_roles/p_678fd629` with field name="Tarunswamy".
 *  - Create `admin_uids/{your Firebase Auth UID}` with field playerId="p_678fd629".
 * After that, existing admins can add others via the in-app UI; the new
 * admin's UID is registered automatically the first time they open the
 * app (see [registerSelfUidIfAdmin]).
 */
class AdminRolesRepository {

    private val db = FirebaseFirestore.getInstance()
    private val rolesCollection = db.collection("admin_roles")
    private val uidsCollection = db.collection("admin_uids")
    private val auth = FirebaseAuth.getInstance()

    data class AdminEntry(
        val playerId: String,
        val name: String,
        val addedAt: Long,
        val addedBy: String
    )

    /**
     * Realtime listener over the admin set. Fires immediately with the
     * current snapshot, then on every change. Caller hands the resulting
     * Set<String> off to [com.justpass.app.data.model.TournamentAdmins]
     * cache so isAdmin() lookups elsewhere become non-network.
     */
    fun listenAdminPlayerIds(onUpdate: (Set<String>) -> Unit): ListenerRegistration {
        return rolesCollection.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "listenAdminPlayerIds err: ${err.message}")
                onUpdate(emptySet())
                return@addSnapshotListener
            }
            val ids = snap?.documents?.map { it.id }?.toSet() ?: emptySet()
            onUpdate(ids)
        }
    }

    /** Snapshot list with metadata, for the management UI. */
    suspend fun listAdmins(): List<AdminEntry> {
        return try {
            val snap = rolesCollection.get().await()
            snap.documents.map { d ->
                AdminEntry(
                    playerId = d.id,
                    name = d.getString("name") ?: "",
                    addedAt = d.getLong("addedAt") ?: 0L,
                    addedBy = d.getString("addedBy") ?: ""
                )
            }.sortedByDescending { it.addedAt }
        } catch (e: Exception) {
            Log.e(TAG, "listAdmins err: ${e.message}")
            emptyList()
        }
    }

    /**
     * Add a new admin by their player id (`p_${rollHash}`). The new admin's
     * Firebase UID is NOT known yet — they'll self-register it on their next
     * app launch via [registerSelfUidIfAdmin]. Until then, they can read
     * admin-gated UI but cannot write to admin_roles themselves.
     */
    suspend fun addAdmin(playerId: String, name: String): Boolean {
        val me = auth.currentUser?.uid ?: return false
        return try {
            rolesCollection.document(playerId).set(mapOf(
                "playerId" to playerId,
                "name" to name,
                "addedAt" to System.currentTimeMillis(),
                "addedBy" to me
            )).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "addAdmin err: ${e.message}")
            false
        }
    }

    /**
     * Remove an admin. Also removes any admin_uids entries that point to
     * this playerId so the removed user immediately loses write access on
     * their next request.
     */
    suspend fun removeAdmin(playerId: String): Boolean {
        return try {
            rolesCollection.document(playerId).delete().await()
            // Best-effort sweep of stale admin_uids entries pointing to this
            // playerId. Failure here doesn't roll back the role deletion.
            try {
                val orphaned = uidsCollection.whereEqualTo("playerId", playerId).get().await()
                orphaned.documents.forEach { it.reference.delete().await() }
            } catch (_: Exception) { /* best-effort */ }
            true
        } catch (e: Exception) {
            Log.e(TAG, "removeAdmin err: ${e.message}")
            false
        }
    }

    /**
     * Self-registration step. Call after auth + chess profile setup, with
     * the current user's playerId. If the user is in admin_roles but their
     * Firebase UID is not yet in admin_uids, register it. This is what lets
     * a freshly-added admin start writing once they open the app.
     */
    suspend fun registerSelfUidIfAdmin(myPlayerId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val roleDoc = rolesCollection.document(myPlayerId).get().await()
            if (!roleDoc.exists()) return
            val uidDoc = uidsCollection.document(uid).get().await()
            if (uidDoc.exists()) return
            uidsCollection.document(uid).set(mapOf(
                "playerId" to myPlayerId,
                "registeredAt" to System.currentTimeMillis()
            )).await()
            Log.d(TAG, "Self-registered admin uid=$uid playerId=$myPlayerId")
        } catch (e: Exception) {
            Log.w(TAG, "registerSelfUidIfAdmin err: ${e.message}")
        }
    }

    companion object { private const val TAG = "AdminRolesRepo" }
}
