export const meta = {
  name: 'chess-glitches-diagnose',
  description: 'Diagnose 4 chess matchmaking/WebView glitches (mutual-challenge race, accept-not-working, cant-move/spectator, no-chat) and produce coordinated fixes',
  phases: [
    { title: 'Investigate' },
    { title: 'Synthesize' },
  ],
}

const ROOT = 'C:/Users/tmswa/AndroidStudioProjects/AttendanceWidgetLaudea'
const VM = ROOT + '/app/src/main/java/com/justpass/app/ui/viewmodel/ChessViewModel.kt'
const V2 = ROOT + '/app/src/main/java/com/justpass/app/data/repository/ChessRepositoryV2.kt'
const REPO = ROOT + '/app/src/main/java/com/justpass/app/data/repository/ChessRepository.kt'
const SCREEN = ROOT + '/app/src/main/java/com/justpass/app/ui/screens/ChessScreen.kt'
const LOBBY = ROOT + '/chess-lobby/src/lobby.ts'

const CONTEXT =
  'JustPass chess (Android, Kotlin/Compose). Live 2-player test: Moto=Tarunswamy, Realme=Poornesh (DISTINCT accounts, valid setup). Backend V2 = Cloudflare Durable Object over WebSocket (' + LOBBY + '), default-on. Match flow: lobby Play -> TimeControlDialog -> sendChallenge; receiver gets incoming -> Accept; worker handleAccept creates a Lichess OPEN challenge (createLichessOpenChallenge) and sends CHALLENGE_ACCEPTED{whiteUrl,blackUrl,lichessGameId,fromColor:white}; sender opens whiteUrl, acceptor opens blackUrl in an in-app Lichess WebView (LichessGameScreen in ' + SCREEN + ').\n\n' +
  'ALREADY FIXED (do NOT re-propose): the acceptor-becomes-spectator root cause was missing CookieManager — ChessScreen now calls CookieManager.setAcceptCookie(true)+setAcceptThirdPartyCookies(webView,true) in the WebView factory and flush() in onPageFinished (lichess seat is held by an anon session cookie across the ?color=black 303 redirect). Shipped in v3.0.5, installed on both phones.\n\n' +
  'FOUR GLITCHES the user still reproduces on v3.0.5 (hands-on, two phones):\n' +
  'G1) Both phones press the SAME time control (e.g. 1 min) on each other at the SAME time -> the match opens on only ONE phone (or not at all). This is the MUTUAL-CHALLENGE race.\n' +
  'G2) Sometimes the Accept / Join button does nothing on one phone even when tapped — the game never opens for that phone.\n' +
  'G3) Even when both phones DID enter the match, on one phone the user could NOT touch/move the chess pieces (board unresponsive).\n' +
  'G4) On that phone, the in-game chat was not displayed at all.\n\n' +
  'SEED ANALYSIS of G1 (verify/refute): In ChessViewModel.listenIncomingChallenges the mutual-challenge handler auto-accepts: `if (sentChallengeId != null && challenge.fromId == sentChallengeToId) { declineChallenge(ourId); acceptChallenge(theirId) }`. When BOTH sides do this simultaneously, BOTH CANCEL their own challenge AND ACCEPT the other’s. On the worker (lobby.ts handleAccept/handleCancel), the CANCEL of challenge X can race the ACCEPT of challenge X: if a CANCEL deletes the challenge before the peer’s ACCEPT lands, handleAccept finds nothing (idempotent no-op) and NO Lichess game is created for that side -> acceptChallenge deferred times out (ACCEPT_TIMEOUT_MS) -> that phone never opens. Net: 0 or 1 game depending on arrival order, and possibly TWO Lichess games if both accepts win. Likely fix: DETERMINISTIC mutual resolution — only the player with the smaller playerId accepts the peer’s challenge; the larger-id player cancels its own and WAITS for CHALLENGE_ACCEPTED (does not also accept). Confirm against the real code + worker.\n\n' +
  'G3/G4 hypothesis: either the phone is still seatless (spectator) despite the cookie fix (e.g. two Lichess games created by the G1 race, so each phone is in a DIFFERENT game and one is alone/observer), OR a WebView TOUCH issue (ChessScreen has a comment that LAYER_TYPE_HARDWARE inside a Compose Dialog can swallow touch on Android 16; verify current layer + focus + that the board receives touches), OR the chat CSS injection (chatJs) not running / running on a spectator page. Determine which, per the real code.'

const FIND = {
  type: 'object', additionalProperties: false,
  required: ['glitch', 'rootCause', 'confidence', 'evidence', 'fix'],
  properties: {
    glitch: { type: 'string' },
    rootCause: { type: 'string' },
    confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
    evidence: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['file', 'lines', 'quote'], properties: { file: { type: 'string' }, lines: { type: 'string' }, quote: { type: 'string' } } } },
    fix: { type: 'string', description: 'Exact file/function change. Minimal + robust. Note any worker (lobby.ts) change needs redeploy.' },
    risk: { type: 'string' },
  },
}

const PLAN = {
  type: 'object', additionalProperties: false,
  required: ['summary', 'edits', 'workerChanges', 'verification', 'openRisks'],
  properties: {
    summary: { type: 'string' },
    edits: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['file', 'change', 'rationale'], properties: { file: { type: 'string' }, change: { type: 'string' }, rationale: { type: 'string' } } } },
    workerChanges: { type: 'string' },
    verification: { type: 'array', items: { type: 'string' } },
    openRisks: { type: 'array', items: { type: 'string' } },
  },
}

const glitches = [
  { key: 'G1-mutual-race', q: 'G1: simultaneous mutual challenge opens on only one phone. Read ChessViewModel.kt (sendChallenge, listenIncomingChallenges mutual-accept branch, acceptChallenge, cancelSentChallenge, checkExistingChallenge usage) and ChessRepositoryV2.kt (sendChallenge, handleChallengeIncoming, handleChallengeAccepted, handleChallengeDeclined, acceptChallenge deferred + ACCEPT_TIMEOUT_MS) and chess-lobby/src/lobby.ts (handleChallenge, handleAccept, handleCancel, handleDecline — note each ACCEPT creates a fresh Lichess open challenge). Confirm/refute the seed analysis; nail the exact race; design a DETERMINISTIC mutual-challenge resolution that always yields exactly ONE shared game.' },
  { key: 'G2-accept-fails', q: 'G2: Accept does nothing on one phone. Read ChessViewModel.acceptChallenge + ChessRepositoryV2.acceptChallenge (the CompletableDeferred + pendingAcceptCompletables + ACCEPT_TIMEOUT_MS=10s) + lobby.ts handleAccept, and the challenge-expiry (CHALLENGE_TTL_MS=15s + alarm). Enumerate why an Accept tap can produce no game: challenge already expired/cancelled on the worker; ws not OPEN (send dropped — LobbyWebSocket queues but ACCEPT may be dropped if socket down); deferred times out; CHALLENGE_ACCEPTED not delivered to the acceptor; lichess create failed. Propose fixes (e.g. surface an error + retry instead of silent no-open; ensure ACCEPT is sent only when ws OPEN; extend/justify timeouts).' },
  { key: 'G3-cant-move', q: 'G3: in-game, one phone cannot touch/move pieces though it is IN the match. Read ChessScreen.kt LichessGameScreen WebView factory FULLY (layer type, isFocusable/requestFocus, AndroidView, touch handling; the comment re LAYER_TYPE_HARDWARE swallowing touch on Android 16) + how the URL is chosen (gameUrl vs opponentUrl) + the cookie fix. Determine whether G3 is (a) still-spectator (no seat — would the G1 race put the phone in a different/observer game?), or (b) a WebView touch/focus problem (board renders but ignores taps). Give the concrete fix for whichever the code supports; if touch, propose the exact WebView setting/focus fix.' },
  { key: 'G4-no-chat', q: 'G4: in-game chat not displayed on one phone. Read ChessScreen.kt the chatJs / chatCss injection + onPageFinished evaluateJavascript sequence + the renameJs/hideJs. Determine why chat may not render: injection runs before Lichess paints .mchat; spectator page has no .mchat or a different DOM; CSS hides it; injection race with the 303 redirect (onPageFinished fires for ?color=black then redirect -> does injection re-run on the final page?). Propose a robust fix (re-inject after redirect / retry until .mchat exists / not depend on a single onPageFinished).' },
]

phase('Investigate')
const finds = (await parallel(glitches.map(function (g) {
  return function () {
    return agent(CONTEXT + '\n\nYOUR GLITCH: ' + g.key + '\n\n' + g.q + '\n\nRead the REAL code (cite file:line). Return the FIND object for "' + g.key + '".',
      { label: 'diag:' + g.key, phase: 'Investigate', schema: FIND })
  }
}))).filter(Boolean)

phase('Synthesize')
const plan = await agent(
  'Synthesize ONE coordinated fix plan for the 4 chess glitches from these diagnoses. Order edits, call out interactions (e.g. fixing G1 may resolve G3 if the race was splitting games), separate Android edits from chess-lobby Worker (lobby.ts) changes that need redeploy, and give concrete two-phone verification steps. Keep fixes minimal + robust.\n\nDIAGNOSES (JSON):\n' + JSON.stringify(finds),
  { label: 'fix-plan', phase: 'Synthesize', schema: PLAN })

return { plan: plan, diagnoses: finds }
