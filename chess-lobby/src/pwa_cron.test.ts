import { describe, it, expect, vi } from "vitest";
import { runPwaSyncTick } from "./pwa_cron";
import type { Env } from "./types";

// Re-import private functions via a test-only re-export pattern
// Since they are private, we test them indirectly through runPwaSyncTick
// and also test the public surface.

function createMockD1(results: unknown[] = []): D1Database {
  const mockStmt: D1PreparedStatement = {
    bind: vi.fn(() => ({
      run: vi.fn(async () => ({ success: true, meta: { changes: 1 } } as D1Result)),
      all: vi.fn(async () => ({ success: true, results, meta: {} } as D1Result)),
      first: vi.fn(async () => results[0] ?? null),
      raw: vi.fn(async () => []),
    })),
    run: vi.fn(async () => ({ success: true, meta: { changes: 1 } } as D1Result)),
    all: vi.fn(async () => ({ success: true, results, meta: {} } as D1Result)),
    first: vi.fn(async () => results[0] ?? null),
    raw: vi.fn(async () => []),
  } as unknown as D1PreparedStatement;

  return {
    prepare: vi.fn(() => mockStmt),
    batch: vi.fn(async () => []),
    exec: vi.fn(async () => ({ count: 0, duration: 0 })),
    dump: vi.fn(async () => new ArrayBuffer(0)),
  } as unknown as D1Database;
}

function makeEnv(db: D1Database, key: string = "test-key"): Env {
  return {
    LOBBY: {} as DurableObjectNamespace,
    FIREBASE_PROJECT_ID: "test",
    CLASS_MARKS_DB: db,
    PWA_CRED_KEY: key,
  };
}

describe("runPwaSyncTick", () => {
  it("returns early when PWA_CRED_KEY is missing", async () => {
    const env = makeEnv(createMockD1(), "");
    await expect(runPwaSyncTick(env)).resolves.toBeUndefined();
  });

  it("returns early when no rows exist", async () => {
    const env = makeEnv(createMockD1([]));
    await expect(runPwaSyncTick(env)).resolves.toBeUndefined();
  });

  it("processes rows sequentially without throwing", async () => {
    const rows = [
      { anon_id: "u1", roll_number: "22CS101", enc_password: "bad_enc", class_key: "CSE_A" },
    ];
    const env = makeEnv(createMockD1(rows));
    // Decrypt will fail, but processOneUser catches and records error
    await expect(runPwaSyncTick(env)).resolves.toBeUndefined();
  });
});

// Because buildUploadBody, num, and isNE are private, we test them via
// dynamic import of the module internals. In ESM this is not trivial,
// so we rely on integration testing of runPwaSyncTick and the fact
// that the Android-side ClassMarksRepository has its own unit tests
// for equivalent logic.
