package com.justpass.app.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.justpass.app.data.model.ChessChallenge
import com.justpass.app.data.model.OnlinePlayer

/**
 * Thin adapter that makes the existing [ChessRepository] conform to the
 * [ChessLobby] interface. No business logic — every method is a direct
 * delegation. This means [ChessRepository] stays completely unmodified and
 * the adapter is purely additive; the Remote Config flag flips between this
 * and [ChessRepositoryV2] without touching any caller.
 */
class FirestoreChessLobby(private val repo: ChessRepository) : ChessLobby {

    override suspend fun goOnline(playerId: String, displayName: String) =
        repo.goOnline(playerId, displayName)

    override suspend fun goOffline(playerId: String) =
        repo.goOffline(playerId)

    override suspend fun heartbeat(playerId: String) =
        repo.heartbeat(playerId)

    override fun listenOnlinePlayers(
        myId: String,
        friendIds: Set<String>,
        onUpdate: (List<OnlinePlayer>) -> Unit
    ): ListenerRegistration =
        repo.listenOnlinePlayers(myId, friendIds, onUpdate)

    override suspend fun sendChallenge(
        fromId: String,
        fromName: String,
        toId: String,
        toName: String,
        timeControl: String
    ): String? =
        repo.sendChallenge(fromId, fromName, toId, toName, timeControl)

    override suspend fun checkExistingChallenge(fromId: String, toId: String): ChessChallenge? =
        repo.checkExistingChallenge(fromId, toId)

    override fun listenIncomingChallenges(
        myId: String,
        onChallenge: (ChessChallenge) -> Unit,
        onChallengeRemoved: (challengeId: String) -> Unit
    ): ListenerRegistration =
        repo.listenIncomingChallenges(myId, onChallenge, onChallengeRemoved)

    override fun listenChallengeStatus(
        challengeId: String,
        onUpdate: (ChessChallenge) -> Unit
    ): ListenerRegistration =
        repo.listenChallengeStatus(challengeId, onUpdate)

    override suspend fun acceptChallenge(
        challengeId: String,
        timeControl: String
    ): Pair<String, String>? =
        repo.acceptChallenge(challengeId, timeControl)

    override suspend fun declineChallenge(challengeId: String) =
        repo.declineChallenge(challengeId)

    override suspend fun cleanupExpiredChallenges() =
        repo.cleanupExpiredChallenges()
}
