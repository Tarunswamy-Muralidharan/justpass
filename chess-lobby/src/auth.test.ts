import { describe, it, expect, vi, beforeEach } from "vitest";
import { verifyFirebaseIdToken } from "./auth";

// Helpers to build a fake JWT
function b64urlEncode(str: string): string {
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
}

function makeJwt(header: object, payload: object, sig: string): string {
  return `${b64urlEncode(JSON.stringify(header))}.${b64urlEncode(JSON.stringify(payload))}.${sig}`;
}

describe("verifyFirebaseIdToken", () => {
  const projectId = "test-project";

  let originalFetch: typeof fetch;

  beforeEach(() => {
    vi.restoreAllMocks();
    originalFetch = globalThis.fetch;
    // Always mock crypto so cached keys from previous tests don't break us.
    vi.spyOn(crypto.subtle, "importKey").mockResolvedValue({} as CryptoKey);
    vi.spyOn(crypto.subtle, "verify").mockResolvedValue(true);
    // Default: mock JWKS fetch so loadJwks never hits the network.
    globalThis.fetch = vi.fn(async (url: string | Request | URL) => {
      const urlStr = typeof url === "string" ? url : url.toString();
      if (urlStr.includes("googleapis.com")) {
        return new Response(JSON.stringify({
          keys: [{ kty: "RSA", kid: "k1", n: "abc", e: "AQAB", alg: "RS256" }]
        }), { status: 200 });
      }
      return originalFetch(url);
    }) as typeof fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("returns null for empty token", async () => {
    expect(await verifyFirebaseIdToken("", projectId)).toBeNull();
  });

  it("returns null for empty projectId", async () => {
    expect(await verifyFirebaseIdToken("abc.def.ghi", "")).toBeNull();
  });

  it("returns null for malformed JWT (2 parts)", async () => {
    expect(await verifyFirebaseIdToken("abc.def", projectId)).toBeNull();
  });

  it("returns null for malformed JWT (4 parts)", async () => {
    expect(await verifyFirebaseIdToken("a.b.c.d", projectId)).toBeNull();
  });

  it("returns null for invalid base64url in header", async () => {
    const token = "!!!.def.ghi";
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns null when alg is not RS256", async () => {
    const token = makeJwt({ alg: "HS256", kid: "k1" }, { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 9999999999, sub: "u1" }, "sig");
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns null when kid is missing", async () => {
    const token = makeJwt({ alg: "RS256" }, { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 9999999999, sub: "u1" }, "sig");
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns null for wrong issuer", async () => {
    const token = makeJwt({ alg: "RS256", kid: "k1" }, { iss: "https://other.google.com/test", aud: projectId, exp: 9999999999, sub: "u1" }, "sig");
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns null for wrong audience", async () => {
    const token = makeJwt({ alg: "RS256", kid: "k1" }, { iss: `https://securetoken.google.com/${projectId}`, aud: "wrong", exp: 9999999999, sub: "u1" }, "sig");
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns null for expired token", async () => {
    const token = makeJwt({ alg: "RS256", kid: "k1" }, { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 1000, sub: "u1" }, "sig");
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns null for missing sub", async () => {
    const token = makeJwt({ alg: "RS256", kid: "k1" }, { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 9999999999 }, "sig");
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns null for empty sub", async () => {
    const token = makeJwt({ alg: "RS256", kid: "k1" }, { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 9999999999, sub: "" }, "sig");
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns null when exp is not a number", async () => {
    const token = makeJwt({ alg: "RS256", kid: "k1" }, { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: "9999", sub: "u1" }, "sig");
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns uid and name on valid token", async () => {
    const token = makeJwt(
      { alg: "RS256", kid: "k1" },
      { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 9999999999, sub: "user123", name: "Tarun" },
      "sig",
    );
    const result = await verifyFirebaseIdToken(token, projectId);
    expect(result).toEqual({ uid: "user123", name: "Tarun" });
  });

  it("returns uid without name when name is not a string", async () => {
    const token = makeJwt(
      { alg: "RS256", kid: "k1" },
      { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 9999999999, sub: "user456", name: 123 },
      "sig",
    );
    const result = await verifyFirebaseIdToken(token, projectId);
    expect(result).toEqual({ uid: "user456" });
  });

  it("returns null when JWKS fetch fails and verify fails", async () => {
    // Force verify to fail so even if stale cache is used we get null.
    vi.spyOn(crypto.subtle, "verify").mockResolvedValue(false);
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn(async () => new Response("error", { status: 500 })) as typeof fetch;

    const token = makeJwt(
      { alg: "RS256", kid: "k1" },
      { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 9999999999, sub: "u1" },
      "sig",
    );

    const result = await verifyFirebaseIdToken(token, projectId);
    expect(result).toBeNull();

    globalThis.fetch = originalFetch;
  });

  it("returns null when signature verification fails", async () => {
    vi.spyOn(crypto.subtle, "verify").mockResolvedValue(false);

    const token = makeJwt(
      { alg: "RS256", kid: "k1" },
      { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 9999999999, sub: "u1" },
      "sig",
    );

    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });

  it("returns null when kid is not found in cached keys", async () => {
    // Use a kid that is definitely not in any stale cache.
    const token = makeJwt(
      { alg: "RS256", kid: "definitely-missing-kid-999" },
      { iss: `https://securetoken.google.com/${projectId}`, aud: projectId, exp: 9999999999, sub: "u1" },
      "sig",
    );
    expect(await verifyFirebaseIdToken(token, projectId)).toBeNull();
  });
});
