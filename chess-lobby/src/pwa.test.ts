import { describe, it, expect, vi } from "vitest";
import { encryptPassword, decryptPassword, handlePwaRegister, handlePwaUnregister } from "./pwa";
import type { Env } from "./types";

// 32 bytes of zeros encoded as base64
const MASTER_KEY_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

function createMockD1(): D1Database {
  const mockStmt: D1PreparedStatement = {
    bind: vi.fn(() => ({
      run: vi.fn(async () => ({ success: true, meta: { changes: 1 } } as D1Result)),
      all: vi.fn(async () => ({ success: true, results: [], meta: {} } as D1Result)),
      first: vi.fn(async () => null),
      raw: vi.fn(async () => []),
    })),
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

function makeEnv(db: D1Database, key: string = MASTER_KEY_B64): Env {
  return {
    LOBBY: {} as DurableObjectNamespace,
    FIREBASE_PROJECT_ID: "test",
    CLASS_MARKS_DB: db,
    PWA_CRED_KEY: key,
  };
}

describe("encryptPassword / decryptPassword round-trip", () => {
  it("encrypts and decrypts back to original plaintext", async () => {
    const plaintext = "mySecretPassword123";
    const encrypted = await encryptPassword(plaintext, MASTER_KEY_B64);
    expect(encrypted).not.toBe(plaintext);
    expect(typeof encrypted).toBe("string");

    const decrypted = await decryptPassword(encrypted, MASTER_KEY_B64);
    expect(decrypted).toBe(plaintext);
  });

  it("produces different ciphertexts for same plaintext (due to random IV)", async () => {
    const plaintext = "same";
    const enc1 = await encryptPassword(plaintext, MASTER_KEY_B64);
    const enc2 = await encryptPassword(plaintext, MASTER_KEY_B64);
    expect(enc1).not.toBe(enc2);
  });

  it("fails decryption with wrong key", async () => {
    const plaintext = "secret";
    const encrypted = await encryptPassword(plaintext, MASTER_KEY_B64);
    await expect(decryptPassword(encrypted, "wrongKeyBase64wrongKeyBase64wrong==")).rejects.toThrow();
  });

  it("fails decryption with tampered ciphertext", async () => {
    const plaintext = "secret";
    const encrypted = await encryptPassword(plaintext, MASTER_KEY_B64);
    const tampered = encrypted.slice(0, -4) + "AAAA";
    await expect(decryptPassword(tampered, MASTER_KEY_B64)).rejects.toThrow();
  });
});

describe("handlePwaRegister", () => {
  it("returns 500 when PWA_CRED_KEY is missing", async () => {
    const env = makeEnv(createMockD1(), "");
    const req = new Request("http://t/pwa/register", {
      method: "POST",
      body: JSON.stringify({ rollNumber: "22CS101", password: "pass", classKey: "CSE_A" }),
    });
    const res = await handlePwaRegister(req, env, "u1");
    expect(res.status).toBe(500);
    expect(await res.json()).toMatchObject({ error: "server_misconfigured" });
  });

  it("returns 400 for invalid JSON", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/pwa/register", { method: "POST", body: "not json" });
    const res = await handlePwaRegister(req, env, "u1");
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "invalid_json" });
  });

  it("returns 400 for missing fields", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/pwa/register", {
      method: "POST",
      body: JSON.stringify({ rollNumber: "22CS101" }),
    });
    const res = await handlePwaRegister(req, env, "u1");
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "missing_fields" });
  });

  it("returns 400 for invalid field types", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/pwa/register", {
      method: "POST",
      body: JSON.stringify({ rollNumber: 123, password: "pass", classKey: "CSE_A" }),
    });
    const res = await handlePwaRegister(req, env, "u1");
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "invalid_field_type" });
  });

  it("returns 200 on valid registration", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/pwa/register", {
      method: "POST",
      body: JSON.stringify({ rollNumber: "22CS101", password: "pass", classKey: "cse_a" }),
    });
    const res = await handlePwaRegister(req, env, "u1");
    expect(res.status).toBe(200);
    const json = await res.json() as { ok: boolean; registeredAt: number };
    expect(json.ok).toBe(true);
    expect(typeof json.registeredAt).toBe("number");
  });
});

describe("handlePwaUnregister", () => {
  it("returns 200 on success", async () => {
    const env = makeEnv(createMockD1());
    const req = new Request("http://t/pwa/register", { method: "DELETE" });
    const res = await handlePwaUnregister(req, env, "u1");
    expect(res.status).toBe(200);
    const json = await res.json() as { ok: boolean; deleted: number };
    expect(json.ok).toBe(true);
  });
});
