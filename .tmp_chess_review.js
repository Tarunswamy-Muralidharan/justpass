export const meta = {
  name: 'chess-fix-adversarial-review',
  description: 'Adversarially review the chess stable-identity + self-credit fix (Android + Worker + PWA) for regressions before deploy',
  phases: [
    { title: 'Review' },
    { title: 'Synthesize' },
  ],
}

const ANDROID = 'C:/Users/tmswa/AndroidStudioProjects/AttendanceWidgetLaudea'
const PWA = 'C:/Users/tmswa/WebProjects/AttendanceWidgetLaudea-Web'
const ANDROID_DIFF = 'C:/Users/tmswa/AppData/Local/Temp/android_chess.diff'
const PWA_DIFF = 'C:/Users/tmswa/AppData/Local/Temp/pwa_chess.diff'

const CONTEXT =
  'CONTEXT — a chess multiplayer fix spanning three surfaces, all already compiling/typechecking:\n' +
  '1) Cloudflare Worker (' + ANDROID + '/chess-lobby/src/worker.ts): the /ws handler now uses a client-declared stable player id from ?pid= (validated /^p_[A-Za-z0-9]{1,32}$/) as X-Player-Id, falling back to the Firebase anonymous UID. The Durable Object (chess-lobby/src/lobby.ts) is UNCHANGED — it keys presence + CHALLENGE_INCOMING.fromId + challenge routing by X-Player-Id. Stats/profiles/leaderboard/friends are keyed by p_<rollHash> in Firestore. Before: X-Player-Id was the UID, so presence/challenge ids never matched p_<rollHash> → finished games credited a phantom UID profile.\n' +
  '2) Android (Kotlin): LobbyWebSocket appends ?pid= from a pidProvider; ChessRepositoryV2 passes myId (the p_<rollHash>); friend requests prefer player.id when it is a p_ id; the V2 accepter now stamps lichessGameId (from CHALLENGE_ACCEPTED) so it ALSO persists the shared chess_challenges doc; ChessRepository.processGameResult un-claims resultChecked on a null Lichess result (not just "ongoing"); AndroidManifest adds locale to configChanges. CRUCIALLY: ChessViewModel.checkPendingResults was rewritten from a CREDIT-BOTH model (repo.processGameResult credited both fromId+toId gated by a shared resultChecked claim) to a SELF-CREDIT model (each device credits ONLY its own p_<rollHash> via repo.recordGameResult(profile.id, myResult), deduped by local match history). repo.processGameResult is now unused by this path. Abandonment is still credited by the WINNER via repo.recordAbandonmentResult (credits winner win + leaver loss); the leaver writes leftBy and never self-credits.\n' +
  '3) PWA (Next.js/TS, ' + PWA + '): CloudflareChessLobby.connect() now appends &pid=<this.myId> to the WS URL (myId is set in goOnline before connect). The PWA already used a SELF-CREDIT model (processGameResult writes only its own chess_profiles doc, polling Lichess). The PWA leftBy-listener (app/chess/page.tsx) now ALSO credits the leaver a loss+gamesPlayed (mirroring Android recordAbandonmentResult) so a PWA winner credits both sides. PWA getPlayerId already uses hex (p_${Math.abs(hash).toString(16)}) matching Android.\n\n' +
  'GOAL of the fix: stable identity end-to-end so leaderboard stats + match history follow p_<rollHash> regardless of display name; every finished game credits each player exactly once; no phantom UID profiles; no double-count when an Android player faces a PWA player; works for Android↔Android, Android↔PWA, PWA↔PWA.\n\n' +
  'Read the two diff files (' + ANDROID_DIFF + ' and ' + PWA_DIFF + ') AND the actual source files in both repos for full context before concluding. Do not trust the diff comments — verify against the real code.'

const FINDINGS = {
  type: 'object', additionalProperties: false,
  required: ['dimension', 'findings', 'overall'],
  properties: {
    dimension: { type: 'string' },
    overall: { type: 'string', enum: ['clean', 'minor-issues', 'must-fix'] },
    findings: {
      type: 'array',
      items: {
        type: 'object', additionalProperties: false,
        required: ['severity', 'where', 'issue', 'scenario', 'recommendation', 'confidence'],
        properties: {
          severity: { type: 'string', enum: ['critical', 'high', 'medium', 'low'] },
          where: { type: 'string', description: 'file:approx-line or function' },
          issue: { type: 'string' },
          scenario: { type: 'string', description: 'Concrete repro / pairing (A↔A, A↔PWA, rollout old↔new, etc.) that triggers it.' },
          recommendation: { type: 'string' },
          confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
        },
      },
    },
  },
}

const SYNTH = {
  type: 'object', additionalProperties: false,
  required: ['verdict', 'shipRecommendation', 'mustFix', 'shouldFix', 'confirmedCorrect'],
  properties: {
    verdict: { type: 'string' },
    shipRecommendation: { type: 'string', enum: ['ship', 'ship-with-noted-followups', 'fix-first'] },
    mustFix: { type: 'array', items: { type: 'string' } },
    shouldFix: { type: 'array', items: { type: 'string' } },
    confirmedCorrect: { type: 'array', items: { type: 'string' }, description: 'Claims independently verified as correct.' },
  },
}

const dims = [
  { key: 'self-credit-dedup', q: 'Review the SELF-CREDIT rewrite in ChessViewModel.checkPendingResults (' + ANDROID + '/app/src/main/java/com/justpass/app/ui/viewmodel/ChessViewModel.kt). Can a single device double-credit its own stat (e.g. two checkPendingResults coroutines racing before saveMatchToHistory persists; the 3s post-game check + 30s loop + clearAcceptedChallenge all calling it)? Is the local-history existingIds set a reliable idempotency key (it is read from _uiState each iteration — is it stale within the loop for two finished games)? Does getRecentGames(profile.id) reliably FIND the game now (challenge doc fromId/toId == p_<rollHash> after the worker fix + accepter doc-write)? Is "aborted" handled (history but no stat)? Confirm recordGameResult increments are correct. Read recordGameResult, getRecentGames, saveMatchToHistory, loadMatchHistory.' },
  { key: 'abandonment', q: 'Review the abandonment crediting across BOTH clients. Android: recordAbandonmentResult (ChessRepository.kt) credits winner win + leaver loss with a resultChecked transaction claim; watchForOpponentLeave (ChessViewModel) saves local win history; notifyGameLeft/goOffline write leftBy + save local loss history but DO NOT self-credit. PWA: the leftBy-listener (page.tsx) now processGameResult("win") (self win) PLUS increments leftBy loss+gamesPlayed. recordLossOnLeave writes leftBy + local loss, no self-credit. For each pairing (A-leaves-vs-A, A-leaves-vs-PWA, PWA-leaves-vs-A, PWA-leaves-vs-PWA): is each player credited EXACTLY once (no double, no miss)? Does the local-history gate in the NEW self-credit checkPendingResults prevent the winner from also self-crediting the same game via a later Lichess poll? Can the leaver be credited twice (once by winner recordAbandonmentResult/PWA-leaver-credit, once by their own poll)?' },
  { key: 'migration-rollout', q: 'Review the deploy/rollout safety. (a) Worker: is deploying worker.ts ALONE safe for old clients that send no ?pid= (fallback to verified.uid)? Is the pid regex safe; what are the consequences of a client declaring an arbitrary p_<id> (impersonation) given /firestore.rules (' + ANDROID + '/firestore.rules) already allows any authed user to write any chess_profiles doc? (b) Rollout window: an OLD Android build still uses CREDIT-BOTH (credits opponent too) while NEW builds + PWA self-credit. Enumerate the double-count exposure when an old-build Android plays a new-build/PWA player, and whether it is transient/self-resolving. (c) Old UID-keyed peers (pre-pid) vs new p_<rollHash> peers in the same lobby: presence id mismatch — does isFriend/displayName-fallback still work; can challenges still route? Read ChessRepositoryV2 emitOnlinePlayers + updateFriendNames.' },
  { key: 'flow-regressions', q: 'Hunt for regressions in non-stats flows from the diff. (a) pid timing: ChessRepositoryV2.ws pidProvider reads myId; myId is set in goOnline before ws.connect(); on RECONNECT (LobbyWebSocket openSocket re-runs) is myId still set? Any path where connect() runs before goOnline sets myId (so pid omitted)? (b) The V2 accepter lichessGameId stamping (takeAcceptedLichessId consumed once): does the SENDER path still work (sender already has lichessGameId via listenChallengeStatus; takeAcceptedLichessId returns null for sender → falls back to challenge.lichessGameId)? Any case where takeAcceptedLichessId is consumed by the wrong path leaving the other blank? (c) recordGameStartV2 still early-returns on blank lichessGameId — does the accepter now always have it? (d) mutual-challenge auto-accept + checkExistingChallenge + countdown still consistent now that presence ids are p_<rollHash>? (e) processGameResult is now unused by checkPendingResults but still defined — dead code only, or referenced elsewhere? Read ChessViewModel acceptChallenge/sendChallenge/watchForOpponentLeave, ChessRepositoryV2 handleChallengeAccepted/handleChallengeIncoming.' },
  { key: 'pwa-correctness', q: 'Review the PWA edits (' + PWA + '). (a) CloudflareChessLobby.connect(): &pid= uses this.myId — confirm goOnline sets myId before connect, and that the lazy connect() calls inside sendChallenge/acceptChallenge/declineChallenge happen only AFTER goOnline (so myId is set); what if connect() is the first call (myId null → pid omitted → UID fallback)? Is the URL well-formed (encodeURIComponent)? (b) The leftBy-listener leaver-credit: does fs() expose updateDoc + increment in that scope; is `doc` and `db` in scope; is the floating promise .catch safe; could it double-credit the leaver across re-fires (the `claimed` guard) or across the winner ALSO self-crediting via the Lichess poll (pendingGame cleared)? (c) Does the PWA write a chess_challenges doc for V2 games at all, and does that matter for the self-credit model (PWA reads its result from Lichess poll on pendingGame, not from the doc)? Read app/chess/page.tsx processGameResult, checkGameResult effect, recordLossOnLeave, acceptChallenge, and CloudflareChessLobby.ts goOnline/connect/sendChallenge.' },
]

phase('Review')
const reviews = (await parallel(dims.map(function (d) {
  return function () {
    return agent(
      CONTEXT + '\n\nYOUR REVIEW DIMENSION: ' + d.key + '\n\n' + d.q +
      '\n\nBe adversarial: try to construct a concrete scenario that produces a wrong stat, a double-count, a missed credit, a crash, or a broken flow. Only report a finding if you can name the scenario. Return the FINDINGS object for dimension "' + d.key + '".',
      { label: 'review:' + d.key, phase: 'Review', schema: FINDINGS }
    )
  }
}))).filter(Boolean)

phase('Synthesize')
const synth = await agent(
  'Synthesize ONE ship decision from these adversarial chess-fix reviews. Deduplicate, separate genuine must-fix defects (wrong/double/missed stats, crashes, broken matchmaking) from acceptable transient rollout effects and pre-existing issues the fix does not worsen. Be concrete.\n\nREVIEWS (JSON):\n' + JSON.stringify(reviews),
  { label: 'synthesis', phase: 'Synthesize', schema: SYNTH }
)

return { synthesis: synth, reviews: reviews }
