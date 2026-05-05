// Minimal Lichess helper: create an open challenge, return URLs + id.

interface TimeControlSpec {
  limit: number; // seconds
  increment: number; // seconds
}

// Match the 10 time controls the Android `TimeControl` enum + the PWA picker
// expose. Each key is what the client sends after upper-casing its enum
// (Android `tc.name`, PWA `tc.toUpperCase()`). Previously only 6 keys were
// here and all 4 with-increment variants (BULLET_1_1, BLITZ_3_2, BLITZ_5_3,
// RAPID_10_5) silently fell through to RAPID_10 — meaning every "1 min"
// click opened a 10-minute game on Lichess.
const TIME_CONTROLS: Record<string, TimeControlSpec> = {
  BULLET: { limit: 60, increment: 0 },        // 1+0
  BULLET_1_1: { limit: 60, increment: 1 },    // 1+1
  BLITZ_3: { limit: 180, increment: 0 },      // 3+0
  BLITZ_3_2: { limit: 180, increment: 2 },    // 3+2
  BLITZ_5: { limit: 300, increment: 0 },      // 5+0
  BLITZ_5_3: { limit: 300, increment: 3 },    // 5+3
  RAPID_10: { limit: 600, increment: 0 },     // 10+0
  RAPID_10_5: { limit: 600, increment: 5 },   // 10+5
  RAPID_15_10: { limit: 900, increment: 10 }, // 15+10
  CLASSICAL: { limit: 1800, increment: 0 },   // 30+0

  // Aliases for older clients that sent the legacy short names.
  BULLET_1: { limit: 60, increment: 0 },
  RAPID_15: { limit: 900, increment: 10 },
  CLASSICAL_30: { limit: 1800, increment: 0 },
};

function resolveTimeControl(key: string): TimeControlSpec {
  return TIME_CONTROLS[key] ?? TIME_CONTROLS.RAPID_10;
}

interface LichessOpenChallengeResponse {
  id?: string;
  urlWhite?: string;
  urlBlack?: string;
  challenge?: {
    id?: string;
    url?: string;
  };
}

export interface LichessOpenChallenge {
  gameId: string;
  whiteUrl: string;
  blackUrl: string;
}

export async function createLichessOpenChallenge(
  timeControlKey: string,
): Promise<LichessOpenChallenge | null> {
  const tc = resolveTimeControl(timeControlKey);

  const body = new URLSearchParams();
  body.set("clock.limit", String(tc.limit));
  body.set("clock.increment", String(tc.increment));
  body.set("rated", "false");

  let res: Response;
  try {
    res = await fetch("https://lichess.org/api/challenge/open", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        Accept: "application/json",
      },
      body: body.toString(),
    });
  } catch {
    return null;
  }

  if (!res.ok) return null;

  let data: LichessOpenChallengeResponse;
  try {
    data = (await res.json()) as LichessOpenChallengeResponse;
  } catch {
    return null;
  }

  const gameId = data.id ?? data.challenge?.id;
  const whiteUrl = data.urlWhite;
  const blackUrl = data.urlBlack;

  if (!gameId || !whiteUrl || !blackUrl) return null;

  return { gameId, whiteUrl, blackUrl };
}
