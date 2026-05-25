package com.justpass.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.justpass.app.data.local.SecurePreferences
import com.justpass.app.data.model.TournamentAdmins
import com.justpass.app.data.repository.BugReportRepository
import com.justpass.app.data.repository.ChessRepository

/**
 * Live "do I have any unread bug-report replies" flag. For regular users
 * this listens to their own reports + watches `userUnread`. For admins
 * (developer), listens to all reports + watches `adminUnread`. Caller
 * uses the returned [State] to drive a red dot indicator on the profile
 * picture / Bug Report list tile.
 *
 * Note: Firestore snapshot listener is automatically detached on
 * composition exit.
 */
@Composable
fun rememberBugReplyUnread(): State<Boolean> {
    val context = LocalContext.current
    val unread = remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val securePrefs = SecurePreferences.getInstance(context)
        val roll = securePrefs.rollNumber.orEmpty()
        val playerId = if (roll.isNotBlank()) ChessRepository().getPlayerId(roll) else ""
        val isAdmin = TournamentAdmins.isAdmin(playerId)

        val repo = BugReportRepository(context)
        val registration = when {
            isAdmin -> repo.listenAllReports { list ->
                unread.value = list.any { it.adminUnread }
            }
            playerId.isNotBlank() -> repo.listenMyReports(playerId) { list ->
                unread.value = list.any { it.userUnread }
            }
            else -> null
        }
        onDispose { registration?.remove() }
    }

    return unread
}
