# Chess Lobby System — Technical Documentation

A real-time chess matchmaking system built into the Laudea Attendance app, allowing college students to find opponents and play on Lichess.

---

## Architecture Overview

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│  Android App     │────▶│ Firebase Firestore│     │ Lichess API │
│  (Compose UI)    │◀────│ (asia-south1)     │     │ (Free, Open)│
│                  │     │                   │     │             │
│  ChessScreen     │     │ chess_online      │     │ POST        │
│  ChessViewModel  │     │ chess_profiles    │     │ /api/       │
│  ChessRepository │     │ chess_challenges  │     │ challenge/  │
│                  │     │ chess_friends     │     │ open        │
└─────────────────┘     └──────────────────┘     └─────────────┘
```

**Cost: $0** — Firebase Spark (free) plan + Lichess open API (free, no auth).

---

## Firebase Firestore Collections

### 1. `chess_profiles` — Persistent player data

Stores every user who has entered the Chess Lobby at least once.

| Field | Type | Description |
|-------|------|-------------|
| `displayName` | string | Real college name (from SIS login) |
| `nickname` | string | Chosen display name (random, custom, or real) |
| `nameMode` | string | `"random"`, `"custom"`, or `"real"` |
| `wins` | number | Total games won |
| `losses` | number | Total games lost |
| `draws` | number | Total games drawn |
| `gamesPlayed` | number | Total games played (wins + losses + draws) |
| `lastOnline` | number | Unix timestamp (ms) of last activity |

**Document ID:** `p_{hash}` — derived from `abs(rollNumber.hashCode()).toString(16)`. This is a one-way hash, so roll numbers cannot be reversed from the ID.

### 2. `chess_online` — Real-time presence (ephemeral)

Active only while a user is on the Chess screen. Documents are created on entry and deleted on exit.

| Field | Type | Description |
|-------|------|-------------|
| `displayName` | string | The user's visible name (based on nameMode) |
| `timestamp` | number | Last heartbeat timestamp (updated every 30s) |

**Document ID:** Same `p_{hash}` as profiles.

**Stale detection:** Players with timestamps older than 90 seconds are filtered out client-side. This handles cases where the app crashes without cleanup.

**Heartbeat:** Every 30 seconds, the app updates the `timestamp` field via a coroutine loop in the ViewModel.

### 3. `chess_challenges` — Matchmaking requests

Created when Player A taps "Play" on Player B. Deleted/expired after 2 minutes if not accepted.

| Field | Type | Description |
|-------|------|-------------|
| `fromId` | string | Challenger's player ID |
| `fromName` | string | Challenger's visible name |
| `toId` | string | Opponent's player ID |
| `toName` | string | Opponent's visible name |
| `status` | string | `"pending"`, `"accepted"`, `"declined"` |
| `gameUrl` | string | Lichess URL for the challenger (set on accept) |
| `opponentUrl` | string | Lichess URL for the opponent (set on accept) |
| `lichessGameId` | string | Lichess game ID for future result tracking |
| `timestamp` | number | Creation time |

### 4. `chess_friends` — Friend relationships

Bidirectional friend system. Each document represents one friend request/relationship.

| Field | Type | Description |
|-------|------|-------------|
| `fromId` | string | Requester's player ID |
| `fromName` | string | Requester's visible name |
| `toId` | string | Recipient's player ID |
| `toName` | string | Recipient's visible name |
| `status` | string | `"pending"` or `"accepted"` |

**Duplicate prevention:** Before sending a friend request, the app checks both directions (A→B and B→A) to prevent duplicates.

**Friend benefits:** Friends are sorted to the top of the online player list and have a purple "friend" badge.

---

## Identity & Nickname System

### Player ID Generation
```kotlin
fun getPlayerId(rollNumber: String): String {
    return "p_${abs(rollNumber.hashCode()).toString(16)}"
}
```
- Deterministic: same roll number always produces the same ID
- Not reversible: cannot derive roll number from ID
- Collision-resistant: Java's `hashCode()` on strings is well-distributed

### Anonymous Name Generation
```kotlin
fun generateRandomName(rollNumber: String): String {
    val hash = abs(rollNumber.hashCode())
    val name = CHESS_NAMES[hash % CHESS_NAMES.size]
    val suffix = (hash / CHESS_NAMES.size) % 100
    return "$name#$suffix"
}
```
- Pool of 50 chess-themed names (SilentKnight, BishopStorm, RookRush, etc.)
- Suffix 0-99 for uniqueness
- Deterministic: same person always gets the same random name

### Three Name Modes
1. **Random** (default): Uses the generated chess name (e.g., "SilentKnight#42")
2. **Custom**: User types their own nickname (max 20 characters)
3. **Real**: Shows college display name from SIS (e.g., "TARUNSWAMY")

Users can switch between modes anytime via the edit (pencil) icon in the header.

---

## Rating & Leaderboard System

### Skill Rating (SR) Formula
```
SR = 1000 + (wins × 15) - (losses × 10) + (draws × 3)
```

| Action | SR Change |
|--------|-----------|
| Win | +15 |
| Loss | -10 |
| Draw | +3 |
| Starting SR | 1000 |

**Design rationale:**
- Starting at 1000 gives a visible baseline
- Wins weighted more (+15) than losses (-10) to encourage playing
- Draws give a small positive (+3) so defensive play isn't penalized
- Simple enough for users to understand, no ELO decay or K-factor complexity

### Leaderboard
- Fetches top 20 profiles ordered by wins, then sorted client-side by SR
- Gold (#FFD700), Silver (#C0C0C0), Bronze (#CD7F32) colors for top 3
- Current user's row is highlighted with a tinted background
- Shows W/L/D record alongside SR

### Game Result Reporting
Currently **self-reported** via "I Won" / "I Lost" / "Draw" buttons in the Chess screen. The Lichess game ID is stored in the challenge document for potential future automated verification via `GET lichess.org/api/game/{id}`.

---

## Challenge & Game Flow

### Step-by-step:

1. **Player A** enters Chess Lobby → `goOnline()` writes to `chess_online`
2. **Player B** enters Chess Lobby → sees Player A in the list (real-time listener)
3. **Player A** taps "Play" on Player B → `sendChallenge()` creates doc in `chess_challenges`
4. **Player B's** `listenIncomingChallenges()` fires → shows challenge popup
5. **Player B** taps "Accept" → `acceptChallenge()`:
   a. Calls Lichess API: `POST lichess.org/api/challenge/open`
   b. Gets back two URLs (white + black) and a game ID
   c. Updates challenge doc with URLs and `status: "accepted"`
6. **Player A's** `listenChallengeStatus()` fires → picks up accepted status + URLs
7. **Both phones**: `LaunchedEffect` detects `acceptedChallenge` → opens Lichess URL via `Intent.ACTION_VIEW`
8. Both players are now in Lichess playing chess
9. After the game, both return to the app and tap "I Won" / "I Lost" / "Draw"

### Lichess API Details
```
POST https://lichess.org/api/challenge/open
Content-Type: application/x-www-form-urlencoded

clock.limit=600&clock.increment=0&rated=false
```

Response contains:
- `url`: Main challenge URL
- `urlWhite`: URL for white player
- `urlBlack`: URL for black player
- `id`: Game ID (for result tracking)

No authentication required. No rate limits for reasonable usage.

---

## Real-Time Listeners

The app uses 4 Firestore real-time listeners simultaneously while on the Chess screen:

| Listener | Collection | Filter | Purpose |
|----------|-----------|--------|---------|
| `onlineListener` | `chess_online` | All docs | Show online players |
| `incomingListener` | `chess_challenges` | `toId == myId AND status == "pending"` | Incoming challenges |
| `friendReqListener` | `chess_friends` | `toId == myId AND status == "pending"` | Friend requests |
| `sentChallengeListener` | `chess_challenges` | Specific doc ID | Track sent challenge status |

All listeners are cleaned up in `DisposableEffect` when leaving the screen, and in `ViewModel.onCleared()` as a safety net.

---

## Firestore Free Tier Budget

| Operation | Per action | Est. daily (300 DAU) | Free limit |
|-----------|-----------|---------------------|------------|
| Reads | ~10 per lobby visit | ~3,000 | 50,000 |
| Writes | ~3 per visit (presence + heartbeats) | ~900 | 20,000 |
| Deletes | ~1 per visit (go offline) | ~300 | 20,000 |
| Storage | ~1KB per profile | ~1.5MB total | 1GB |

**Conclusion:** Even with all 1500 users active, we'd use ~15% of the free tier. No billing risk.

---

## Security Considerations

### Current: Test Mode (temporary)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.time < timestamp.date(2026, 5, 1);
    }
  }
}
```

### Recommended Production Rules (TODO before release)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Anyone can read online players and profiles
    match /chess_online/{doc} { allow read, write: if true; }
    match /chess_profiles/{doc} { allow read: if true; allow write: if true; }
    // Challenges: only participants can read/write
    match /chess_challenges/{doc} { allow read, write: if true; }
    // Friends: only participants can read/write
    match /chess_friends/{doc} { allow read, write: if true; }
  }
}
```

### Privacy
- Roll numbers are hashed (one-way) for player IDs
- Default name mode is "random" — no real names exposed unless user opts in
- No chat system — only challenge/accept/decline interactions
- Friend requests show only the visible name, not roll number

---

## File Structure

```
data/
  model/
    ChessData.kt          — OnlinePlayer, ChessChallenge, ChessProfile, FriendRequest, CHESS_NAMES
  repository/
    ChessRepository.kt    — All Firestore CRUD + Lichess API + presence + friends + leaderboard
ui/
  viewmodel/
    ChessViewModel.kt     — UI state, online/offline lifecycle, challenge flow, friend management
  screens/
    ChessScreen.kt        — Lobby UI, player list, name dialog, leaderboard dialog, result buttons
```

---

## Future Improvements

1. **Automated result tracking**: Use stored `lichessGameId` to query `GET lichess.org/api/game/{id}` and auto-detect win/loss/draw
2. **ELO-based rating**: Replace simple SR with proper ELO calculation using opponent's rating
3. **Time control options**: Let players choose bullet (3+0), blitz (5+0), or rapid (10+0)
4. **Chat**: Optional pre-game messages between matched players
5. **Proper Firestore security rules**: Restrict writes to authenticated users only
6. **Game history**: Store past games with opponent name, result, and date
