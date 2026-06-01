import { describe, it, expect, vi } from "vitest";
import { handleUploadMarks, handleDeleteMe, handleGetClassStats } from "./class";
import type { Env } from "./types";

function createMockD1(): D1Database {
  const runs: Array<{ sql: string; bindings: unknown[] }> = [];

  const mockStmt: D1PreparedStatement = {
    bind: vi.fn((...bindings: unknown[]) => {
      return {
        run: vi.fn(async () => {
          runs.push({ sql: "", bindings });
          return { success: true, meta: { changes: 1 } } as D1Result;
        }),
        all: vi.fn(async () => {
          runs.push({ sql: "", bindings });
          return { success: true, results: [], meta: {} } as D1Result;
        }),
        first: vi.fn(async () => null),
        raw: vi.fn(async () => []),
      } as unknown as D1PreparedStatement;
    }),
    run: vi.fn(async () => ({ success: true, meta: { changes: 1 } } as D1Result)),
    all: vi.fn(async () => ({ success: true, results: [], meta: {} } as D1Result)),
    first: vi.fn(async () => null),
    raw: vi.fn(async () => []),
  } as unknown as D1PreparedStatement;

  return {
    prepare: vi.fn(() => mockStmt),
    batch: vi.fn(async () => []),
    exec: vi.fn(async () => ({ count: 0, duration: 0 })),
    dump: vi.fn(async () => new ArrayBuffer(0)),
  } as unknown as D1Database;
}

function createMockD1WithResults(results: unknown[]): D1Database {
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

function makeEnv(db: D1Database): Env {
  return {
    LOBBY: {} as DurableObjectNamespace,
    FIREBASE_PROJECT_ID: "test",
    CLASS_MARKS_DB: db,
    PWA_CRED_KEY: "test-key",
  };
}

describe("handleUploadMarks", () => {
  it("returns 400 for invalid JSON", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/class/marks", {
      method: "POST",
      body: "not json",
    });
    const res = await handleUploadMarks(req, env, "u1");
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "invalid_json" });
  });

  it("returns 400 for missing classKey", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/class/marks", {
      method: "POST",
      body: JSON.stringify({ subjects: {}, overallAvg: 80 }),
    });
    const res = await handleUploadMarks(req, env, "u1");
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "missing_class_key" });
  });

  it("returns 400 for missing subjects", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/class/marks", {
      method: "POST",
      body: JSON.stringify({ classKey: "CSE_A", overallAvg: 80 }),
    });
    const res = await handleUploadMarks(req, env, "u1");
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "missing_subjects" });
  });

  it("returns 400 for missing overallAvg", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/class/marks", {
      method: "POST",
      body: JSON.stringify({ classKey: "CSE_A", subjects: {} }),
    });
    const res = await handleUploadMarks(req, env, "u1");
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "missing_overall_avg" });
  });

  it("returns 400 for NaN overallAvg", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/class/marks", {
      method: "POST",
      body: JSON.stringify({ classKey: "CSE_A", subjects: {}, overallAvg: NaN }),
    });
    const res = await handleUploadMarks(req, env, "u1");
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "missing_overall_avg" });
  });

  it("returns 200 on valid upload", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/class/marks", {
      method: "POST",
      body: JSON.stringify({ classKey: "cse_a", subjects: { MA101: { total: 85 } }, overallAvg: 82.5 }),
    });
    const res = await handleUploadMarks(req, env, "u1");
    expect(res.status).toBe(200);
    const json = await res.json() as { ok: boolean; uploadedAt: number };
    expect(json.ok).toBe(true);
    expect(typeof json.uploadedAt).toBe("number");
  });
});

describe("handleDeleteMe", () => {
  it("returns 200 on success", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/class/me", { method: "DELETE" });
    const res = await handleDeleteMe(req, env, "u1");
    expect(res.status).toBe(200);
    const json = await res.json() as { ok: boolean; deleted: number };
    expect(json.ok).toBe(true);
  });
});

describe("handleGetClassStats", () => {
  it("returns 400 for missing classKey", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/class/");
    const res = await handleGetClassStats(req, env, "u1", "");
    expect(res.status).toBe(400);
  });

  it("returns partial response when class size < 15", async () => {
    const rows = Array.from({ length: 5 }, (_, i) => ({
      anon_id: `u${i}`,
      subjects: JSON.stringify({ MA101: { total: 80, status: "ENTERED" } }),
      overall_avg: 75 + i,
    }));
    const env = makeEnv(createMockD1WithResults(rows));
    const req = new Request("http://t/class/CSE_A");
    const res = await handleGetClassStats(req, env, "u1", "CSE_A");
    expect(res.status).toBe(200);
    const json = await res.json() as { studentCount: number; overall: { avg: number } };
    expect(json.studentCount).toBe(5);
    expect(json.overall.avg).toBe(0);
  });

  it("computes stats, rank, and percentile for full class", async () => {
    const rows = Array.from({ length: 20 }, (_, i) => ({
      anon_id: i === 10 ? "me" : `u${i}`,
      subjects: JSON.stringify({ MA101: { total: 80 + (i % 5), status: "ENTERED" } }),
      overall_avg: 50 + i * 2, // 50, 52, ..., 88
    }));
    const env = makeEnv(createMockD1WithResults(rows));
    const req = new Request("http://t/class/CSE_A");
    const res = await handleGetClassStats(req, env, "me", "CSE_A");
    expect(res.status).toBe(200);
    const json = await res.json() as {
      studentCount: number;
      overall: { avg: number; min: number; max: number };
      yourRank: number;
      yourPercentile: number;
      overallHistogram: number[];
      subjects: Record<string, unknown>;
    };
    expect(json.studentCount).toBe(20);
    expect(json.overall.min).toBe(50);
    expect(json.overall.max).toBe(88);
    expect(json.yourRank).toBeDefined();
    expect(json.yourPercentile).toBeDefined();
    expect(json.overallHistogram.length).toBe(10);
    expect(Object.keys(json.subjects).length).toBeGreaterThan(0);
  });

  it("skips rows with unparseable JSON", async () => {
    const rows = [
      { anon_id: "u1", subjects: "not json", overall_avg: 80 },
      { anon_id: "u2", subjects: JSON.stringify({ MA101: { total: 70, status: "ENTERED" } }), overall_avg: 70 },
    ];
    // Need 15 rows to trigger full stats; repeat valid rows
    const fullRows = Array.from({ length: 15 }, (_, i) => rows[i % 2]);
    const env = makeEnv(createMockD1WithResults(fullRows));
    const req = new Request("http://t/class/CSE_A");
    const res = await handleGetClassStats(req, env, "u1", "CSE_A");
    expect(res.status).toBe(200);
  });

  it("skips NOT_ENTERED status", async () => {
    const rows = Array.from({ length: 15 }, (_, i) => ({
      anon_id: `u${i}`,
      subjects: JSON.stringify({ MA101: { total: 80, status: i === 0 ? "NOT_ENTERED" : "ENTERED" } }),
      overall_avg: 75,
    }));
    const env = makeEnv(createMockD1WithResults(rows));
    const req = new Request("http://t/class/CSE_A");
    const res = await handleGetClassStats(req, env, "u1", "CSE_A");
    expect(res.status).toBe(200);
    const json = await res.json() as { subjects: Record<string, { avg: number }> };
    expect(json.subjects.MA101.avg).toBe(80);
  });

  it("handles tie for rank correctly", async () => {
    const rows = Array.from({ length: 15 }, (_, i) => ({
      anon_id: i === 0 ? "me" : `u${i}`,
      subjects: JSON.stringify({ MA101: { total: 80, status: "ENTERED" } }),
      overall_avg: 75, // everyone same
    }));
    const env = makeEnv(createMockD1WithResults(rows));
    const req = new Request("http://t/class/CSE_A");
    const res = await handleGetClassStats(req, env, "me", "CSE_A");
    const json = await res.json() as { yourRank: number; yourPercentile: number };
    expect(json.yourRank).toBe(1);
    expect(json.yourPercentile).toBe(0);
  });
});
