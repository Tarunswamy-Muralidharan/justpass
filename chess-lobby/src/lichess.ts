// Minimal Lichess helper: create an open challenge, return URLs + id.

interface TimeControlSpec {
  limit: number; // seconds
  increment: number; // seconds
}

const TIME_CONTROLS: Record<string, TimeControlSpec> = {
  BULLET_1: { limit: 60, increment: 0 },
  BLITZ_3: { limit: 180, increment: 0 },
  BLITZ_5: { limit: 300, increment: 0 },
  RAPID_10: { limit: 600, increment: 0 },
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
