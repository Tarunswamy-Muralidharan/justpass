import { describe, it, expect, vi } from "vitest";
import { createLichessOpenChallenge } from "./lichess";

describe("createLichessOpenChallenge time control routing", () => {
  it("sends correct clock.limit for every known time control key", async () => {
    const testCases: Array<{ key: string; expectedLimit: number; expectedIncrement: number }> = [
      { key: "BULLET", expectedLimit: 60, expectedIncrement: 0 },
      { key: "BULLET_1_1", expectedLimit: 60, expectedIncrement: 1 },
      { key: "BLITZ_3", expectedLimit: 180, expectedIncrement: 0 },
      { key: "BLITZ_3_2", expectedLimit: 180, expectedIncrement: 2 },
      { key: "BLITZ_5", expectedLimit: 300, expectedIncrement: 0 },
      { key: "BLITZ_5_3", expectedLimit: 300, expectedIncrement: 3 },
      { key: "RAPID_10", expectedLimit: 600, expectedIncrement: 0 },
      { key: "RAPID_10_5", expectedLimit: 600, expectedIncrement: 5 },
      { key: "RAPID_15_10", expectedLimit: 900, expectedIncrement: 10 },
      { key: "CLASSICAL", expectedLimit: 1800, expectedIncrement: 0 },
      { key: "BULLET_1", expectedLimit: 60, expectedIncrement: 0 },
      { key: "RAPID_15", expectedLimit: 900, expectedIncrement: 10 },
      { key: "CLASSICAL_30", expectedLimit: 1800, expectedIncrement: 0 },
    ];

    for (const tc of testCases) {
      let capturedBody = "";
      const originalFetch = globalThis.fetch;
      globalThis.fetch = vi.fn(async (_url, init) => {
        capturedBody = (init as any)?.body ?? "";
        return new Response(
          JSON.stringify({ id: "abc", urlWhite: "https://lichess.org/w", urlBlack: "https://lichess.org/b" }),
          { status: 200 },
        );
      }) as typeof fetch;

      await createLichessOpenChallenge(tc.key);
      const params = new URLSearchParams(capturedBody);
      expect(params.get("clock.limit"), `key=${tc.key}`).toBe(String(tc.expectedLimit));
      expect(params.get("clock.increment"), `key=${tc.key}`).toBe(String(tc.expectedIncrement));

      globalThis.fetch = originalFetch;
    }
  });

  it("falls back to RAPID_10 for unknown keys", async () => {
    let capturedBody = "";
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn(async (_url, init) => {
      capturedBody = (init as any)?.body ?? "";
      return new Response(
        JSON.stringify({ id: "abc", urlWhite: "https://lichess.org/w", urlBlack: "https://lichess.org/b" }),
        { status: 200 },
      );
    }) as typeof fetch;

    await createLichessOpenChallenge("UNKNOWN_KEY");
    const params = new URLSearchParams(capturedBody);
    expect(params.get("clock.limit")).toBe("600");
    expect(params.get("clock.increment")).toBe("0");

    globalThis.fetch = originalFetch;
  });

  it("returns challenge on successful fetch", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn(async () =>
      new Response(
        JSON.stringify({
          id: "abc123",
          urlWhite: "https://lichess.org/abc123?color=white",
          urlBlack: "https://lichess.org/abc123?color=black",
        }),
        { status: 200 },
      ),
    ) as typeof fetch;

    const result = await createLichessOpenChallenge("BLITZ_5");
    expect(result).toEqual({
      gameId: "abc123",
      whiteUrl: "https://lichess.org/abc123?color=white",
      blackUrl: "https://lichess.org/abc123?color=black",
    });

    globalThis.fetch = originalFetch;
  });

  it("returns null on non-ok response", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn(async () => new Response("error", { status: 500 })) as typeof fetch;

    const result = await createLichessOpenChallenge("BLITZ_5");
    expect(result).toBeNull();

    globalThis.fetch = originalFetch;
  });

  it("returns null on fetch throw", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn(async () => {
      throw new Error("network");
    }) as typeof fetch;

    const result = await createLichessOpenChallenge("BLITZ_5");
    expect(result).toBeNull();

    globalThis.fetch = originalFetch;
  });

  it("returns null on invalid JSON", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn(async () => new Response("not json", { status: 200 })) as typeof fetch;

    const result = await createLichessOpenChallenge("BLITZ_5");
    expect(result).toBeNull();

    globalThis.fetch = originalFetch;
  });

  it("returns null when required fields are missing", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn(async () =>
      new Response(JSON.stringify({ id: "abc123" }), { status: 200 }),
    ) as typeof fetch;

    const result = await createLichessOpenChallenge("BLITZ_5");
    expect(result).toBeNull();

    globalThis.fetch = originalFetch;
  });

  it("extracts challenge.id as fallback", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn(async () =>
      new Response(
        JSON.stringify({
          challenge: { id: "fallback123" },
          urlWhite: "https://lichess.org/fallback123?color=white",
          urlBlack: "https://lichess.org/fallback123?color=black",
        }),
        { status: 200 },
      ),
    ) as typeof fetch;

    const result = await createLichessOpenChallenge("BLITZ_5");
    expect(result?.gameId).toBe("fallback123");

    globalThis.fetch = originalFetch;
  });
});
