import { describe, it, expect, vi } from "vitest";
import worker from "./worker";
import type { Env } from "./types";

function makeEnv(): Env {
  return {
    LOBBY: {
      idFromName: vi.fn(() => ({ toString: () => "id" })),
      get: vi.fn(() => ({
        fetch: vi.fn(async (req: Request) => new Response(null, { status: 101, webSocket: null as any })),
      })),
    } as unknown as DurableObjectNamespace,
    FIREBASE_PROJECT_ID: "test-project",
    CLASS_MARKS_DB: {} as D1Database,
    PWA_CRED_KEY: "key",
  };
}

// Mock auth module
vi.mock("./auth", () => ({
  verifyFirebaseIdToken: vi.fn(async (token: string, _projectId: string) => {
    if (token === "valid") return { uid: "user123", name: "Test" };
    return null;
  }),
}));

// Mock class module
vi.mock("./class", () => ({
  handleUploadMarks: vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 })),
  handleDeleteMe: vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 })),
  handleGetClassStats: vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 })),
}));

// Mock pwa module
vi.mock("./pwa", () => ({
  handlePwaRegister: vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 })),
  handlePwaUnregister: vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 })),
}));

// Mock pwa_cron
vi.mock("./pwa_cron", () => ({
  runPwaSyncTick: vi.fn(async () => {}),
}));

describe("worker fetch router", () => {
  it("returns 204 for OPTIONS", async () => {
    const env = makeEnv();
    const req = new Request("http://t/health", { method: "OPTIONS" });
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(204);
  });

  it("returns 200 for /health", async () => {
    const env = makeEnv();
    const req = new Request("http://t/health");
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true });
  });

  it("returns HTML for /privacy", async () => {
    const env = makeEnv();
    const req = new Request("http://t/privacy");
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(200);
    expect(res.headers.get("Content-Type")).toContain("text/html");
  });

  it("returns 500 when FIREBASE_PROJECT_ID is missing on /class", async () => {
    const env = makeEnv();
    env.FIREBASE_PROJECT_ID = "";
    const req = new Request("http://t/class/marks", { method: "POST" });
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(500);
  });

  it("returns 401 when no token on /class", async () => {
    const env = makeEnv();
    const req = new Request("http://t/class/marks", { method: "POST" });
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(401);
  });

  it("returns 401 for invalid token on /class", async () => {
    const env = makeEnv();
    const req = new Request("http://t/class/marks", {
      method: "POST",
      headers: { Authorization: "Bearer invalid" },
    });
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(401);
  });

  it("routes POST /class/marks with valid token", async () => {
    const env = makeEnv();
    const req = new Request("http://t/class/marks", {
      method: "POST",
      headers: { Authorization: "Bearer valid" },
      body: JSON.stringify({ classKey: "CSE_A", subjects: {}, overallAvg: 80 }),
    });
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(200);
  });

  it("routes GET /class/:classKey with valid token", async () => {
    const env = makeEnv();
    const req = new Request("http://t/class/unknown", {
      headers: { Authorization: "Bearer valid" },
    });
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    // Mocked handleGetClassStats returns 200
    expect(res.status).toBe(200);
  });

  it("returns 400 for /ws without Upgrade: websocket", async () => {
    const env = makeEnv();
    const req = new Request("http://t/ws");
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(400);
  });

  it("returns 401 for /ws without token", async () => {
    const env = makeEnv();
    const req = new Request("http://t/ws", {
      headers: { Upgrade: "websocket" },
    });
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(401);
  });

  it("returns 404 for unknown paths", async () => {
    const env = makeEnv();
    const req = new Request("http://t/unknown");
    const res = await worker.fetch(req, env, {} as ExecutionContext);
    expect(res.status).toBe(404);
  });
});

describe("worker scheduled", () => {
  it("calls runPwaSyncTick via waitUntil", async () => {
    const env = makeEnv();
    const waitUntil = vi.fn();
    await worker.scheduled({} as ScheduledEvent, env, { waitUntil } as ExecutionContext);
    expect(waitUntil).toHaveBeenCalled();
  });
});
