export const meta = {
  name: 'chess-identity-diagnose',
  description: 'Map the chess subsystem, diagnose leaderboard/match-history/win-loss/name-change + match-exit bugs, produce a concrete fix plan',
  phases: [
    { title: 'Map' },
    { title: 'Diagnose' },
    { title: 'Verify' },
    { title: 'Plan' },
  ],
}

const ROOT = 'app/src/main/java/com/justpass/app'

const MAP_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['area', 'summary', 'identityModel', 'flow', 'nameHandling', 'suspects'],
  properties: {
    area: { type: 'string' },
    summary: { type: 'string' },
    identityModel: { type: 'string', description: 'How players/stats/history are keyed: stable playerId vs displayName/nickname. Cite file:line.' },
    flow: { type: 'string', description: 'Step-by-step: match start, play, result detected, win/loss write, match-history write. Cite file:line.' },
    nameHandling: { type: 'string', description: 'How real name / nickname / name-change (nameMode) are stored and used; any place stats or history depend on the NAME not the id.' },
    suspects: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['file', 'lines', 'issue', 'severity'], properties: { file: { type: 'string' }, lines: { type: 'string' }, issue: { type: 'string' }, severity: { type: 'string', enum: ['high', 'medium', 'low'] } } } },
    openQuestions: { type: 'string' },
  },
}

const DIAG_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['symptom', 'rootCause', 'evidence', 'confidence', 'fix', 'risk'],
  properties: {
    symptom: { type: 'string' },
    rootCause: { type: 'string' },
    evidence: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['file', 'lines', 'quote'], properties: { file: { type: 'string' }, lines: { type: 'string' }, quote: { type: 'string' } } } },
    confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
    fix: { type: 'string', description: 'Concrete fix: exact file/function and what to change.' },
    risk: { type: 'string' },
  },
}

const VERDICT_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['symptom', 'holdsUp', 'reasoning', 'refinedFix'],
  properties: { symptom: { type: 'string' }, holdsUp: { type: 'boolean' }, reasoning: { type: 'string' }, refinedFix: { type: 'string' } },
}

const PLAN_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['summary', 'rootCauses', 'edits', 'verification', 'openRisks'],
  properties: {
    summary: { type: 'string' },
    rootCauses: { type: 'array', items: { type: 'string' } },
    edits: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['file', 'change', 'rationale'], properties: { file: { type: 'string' }, change: { type: 'string' }, rationale: { type: 'string' } } } },
    firestoreRules: { type: 'string', description: 'Any /firestore.rules changes for chess_profiles/chess_challenges/chess_match_history writes, or none.' },
    backendWorker: { type: 'string', description: 'Any chess-lobby Cloudflare Worker (TS) changes (need redeploy), or none.' },
    verification: { type: 'array', items: { type: 'string' } },
    openRisks: { type: 'array', items: { type: 'string' } },
  },
}

const areas = [
  {
    key: 'repo-stats',
    prompt: 'Read ' + ROOT + '/data/repository/ChessRepository.kt IN FULL. This is the chess stats/leaderboard/match-history layer (Firestore: chess_online, chess_challenges, chess_profiles, chess_friends). Map precisely: getOrCreateProfile, updateProfile (name-change path: nickname/nameMode), recordGameResult, processGameResult, recordAbandonmentResult, markGameLeft, getLeaderboard, getRecentGames, findProfileIdByName, goOnline/goOffline/heartbeat. How is a player keyed in chess_profiles (playerId = p_<rollHash>?) Where is any result/stat/history lookup resolved by DISPLAY NAME instead of the stable id (findProfileIdByName + callers)? Trace exactly how a finished game win/loss/draw reaches recordGameResult and which id it uses; is the write merge/idempotent; could it write to the WRONG profile or NO profile for a nickname / changed name / name collision? Note fire-and-forget writes whose failure is swallowed (explains scores not added). Return the MAP for area repo-stats.',
  },
  {
    key: 'v2-worker',
    prompt: 'Read ' + ROOT + '/data/repository/ChessRepositoryV2.kt and ' + ROOT + '/data/repository/ChessLobby.kt and ' + ROOT + '/data/repository/FirestoreChessLobby.kt IN FULL, plus the Cloudflare Worker backend (glob chess-lobby/src/**/*.ts excluding node_modules; read the Durable Object / index + auth.ts + lichess.ts). Map precisely: does the V2 Durable-Object lobby identify a player on the wire by stable playerId or by displayName/nickname? What identity does it echo back in presence, challenges, and game/result messages? Is there a name vs id mismatch between worker output and what ChessRepository expects (hence findProfileIdByName)? The match lifecycle over the websocket (connect, challenge, accept, in-game, disconnect/hibernation): any path that drops a player from the match or fails to deliver the result (reported exited-from-match / hangs / glitches)? Note the known DO-hibernation rehydrate fix and whether gaps remain. Return the MAP for area v2-worker.',
  },
  {
    key: 'viewmodel',
    prompt: 'Read ' + ROOT + '/ui/viewmodel/ChessViewModel.kt IN FULL. Map precisely: where the VM gets player identity (playerId, real name, nickname, nameMode); the name-change flow (updateProfile) and whether it ever changes the id used for stats/history; the full in-game lifecycle (start match, detect Lichess result, call recordGameResult/processGameResult, record match history, refresh leaderboard); exactly which id (playerId vs name-derived) is passed when recording a result and when loading leaderboard/match-history; any place a nickname or changed name causes wrong/empty results; any race/swallowed-exception/early-exit that skips the stats/history write. Return the MAP for area viewmodel.',
  },
  {
    key: 'screen',
    prompt: 'Read ' + ROOT + '/ui/screens/ChessScreen.kt IN FULL (large). Focus on LichessGameScreen (in-app Lichess WebView), game-end detection / Play Again, MatchHistoryDialog, LeaderboardDialog, FriendsDialog. Map precisely: how game-end is detected from the WebView and how it triggers result recording; any path where detection misfires, the WebView hangs, or the user is exited from the match prematurely (lifecycle/onDispose/BackHandler); how leaderboard + match-history rows are displayed and KEYED (id vs display name); whether a nickname or name change makes a row show wrong/missing stats or duplicates; any UI assumption that display name is stable/unique. Return the MAP for area screen.',
  },
  {
    key: 'rules-model',
    prompt: 'Read /firestore.rules (chess_online, chess_profiles, chess_challenges, chess_friends, chess_match_history blocks) IN FULL, and ' + ROOT + '/data/model/ChessData.kt. Map precisely: for chess_profiles and chess_challenges (and chess_match_history if present), exactly which writes/updates are ALLOWED. Critically: would a result write incrementing wins/losses (recordGameResult set/merge of wins/losses/gamesPlayed) or a match-history write be SILENTLY REJECTED by the rules (affectedKeys/hasOnly constraints, auth conditions)? A denied write is a prime suspect for scores-not-added / win-loss-not-updated. Also map identity fields on ChessProfile/OnlinePlayer/ChessChallenge (id, displayName, nickname, nameMode, visibleName) and whether stable id and display name are cleanly separated. Return the MAP for area rules-model.',
  },
]

phase('Map')
const maps = (await parallel(areas.map(function (a) {
  return function () { return agent(a.prompt, { label: 'map:' + a.key, phase: 'Map', schema: MAP_SCHEMA }) }
}))).filter(Boolean)
const mapText = JSON.stringify(maps, null, 1)
let suspectCount = 0
for (const m of maps) { if (m.suspects) suspectCount += m.suspects.length }
log('Mapped ' + maps.length + '/5 areas; ' + suspectCount + ' suspect spots flagged')

phase('Diagnose')
const symptoms = [
  { key: 'scores-not-added', text: 'Leaderboard scores sometimes are not added at all after a finished game.' },
  { key: 'winloss-not-updated', text: 'A player win/loss/draw rate is not being updated after games.' },
  { key: 'match-exit-hang', text: 'During a match, players sometimes get exited from the match; the app glitches or hangs.' },
  { key: 'name-identity-split', text: 'The bug seems tied to name storage: players using a NICKNAME vs their REAL name, or who CHANGED their name, lose/split their leaderboard score and match history. Requirement: stats + match history must follow a STABLE identity, unaffected by changing display name/nickname.' },
]
const diagPrefix = 'You are diagnosing ONE chess-app symptom. Use the subsystem map below; re-read any cited file to confirm before concluding (do not guess). Pay special attention to stable playerId vs displayName keying, findProfileIdByName and its callers, silently-rejected Firestore writes, swallowed exceptions, and the websocket match lifecycle. Propose a concrete fix that keeps identity STABLE across name changes (real to/from nickname) so leaderboard + match history never break.'
const diags = (await parallel(symptoms.map(function (s) {
  return function () {
    return agent(diagPrefix + '\n\nSYMPTOM: ' + s.text + '\n\nSUBSYSTEM MAP (JSON):\n' + mapText,
      { label: 'diag:' + s.key, phase: 'Diagnose', schema: DIAG_SCHEMA })
  }
}))).filter(Boolean)

phase('Verify')
const verdicts = (await parallel(diags.map(function (d) {
  return function () {
    return agent('Adversarially verify this chess-bug diagnosis. Try to REFUTE it by reading the cited files yourself. Is the root cause real and complete? Does the fix resolve it without regressions (existing recorded games, presence, and the PWA client which shares these Firestore collections)? Default holdsUp=false if evidence is thin.\n\nDIAGNOSIS (JSON):\n' + JSON.stringify(d) + '\n\nSUBSYSTEM MAP (JSON):\n' + mapText,
      { label: 'verify', phase: 'Verify', schema: VERDICT_SCHEMA })
  }
}))).filter(Boolean)

phase('Plan')
const planPrompt = 'Synthesize ONE implementation-ready fix plan for the chess subsystem from the diagnoses + adversarial verdicts below. Goals in priority order: (1) STABLE identity: leaderboard stats (wins/losses/draws/games) and match history key on the stable playerId and are unaffected by a user changing display name or switching real-name vs nickname; eliminate any name-based resolution of stats/history (e.g. findProfileIdByName) by carrying the stable id end-to-end (Android, CF worker, Firestore, PWA). (2) Reliable recording: every finished game updates win/loss/draw and writes match history exactly once (idempotent), no swallowed failures, no Firestore-rule rejections. (3) Match lifecycle: stop players being kicked / hangs where the map shows a real cause. For each edit give the exact file and function-level change. Call out /firestore.rules changes and any chess-lobby Cloudflare Worker (TS) changes (note: need redeploy). Keep PWA + existing data compatible (migration/back-compat if a key changes). Provide concrete verification steps.'
const plan = await agent(planPrompt + '\n\nDIAGNOSES (JSON):\n' + JSON.stringify(diags) + '\n\nADVERSARIAL VERDICTS (JSON):\n' + JSON.stringify(verdicts) + '\n\nSUBSYSTEM MAP (JSON):\n' + mapText,
  { label: 'fix-plan', phase: 'Plan', schema: PLAN_SCHEMA })

return { plan: plan, diagnoses: diags, verdicts: verdicts, maps: maps }
