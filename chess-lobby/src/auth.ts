// Firebase ID token verification using Web Crypto only.
// Validates against Google's public JWKS for the Firebase
// Secure Token service, then checks aud / iss / exp claims.

const JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

interface Jwk {
  kty: string;
  kid: string;
  n: string;
  e: string;
  alg?: string;
  use?: string;
}

interface JwksResponse {
  keys: Jwk[];
}

interface JwksCacheEntry {
  keys: Map<string, CryptoKey>;
  fetchedAt: number;
}

// Module-level cache. Per-isolate, NOT global — that's fine for a
// 1-hour TTL; the JWKS rotates less often than that.
const JWKS_CACHE: Map<string, JwksCacheEntry> = new Map();
const JWKS_TTL_MS = 60 * 60 * 1000;

async function loadJwks(): Promise<Map<string, CryptoKey>> {
  const cached = JWKS_CACHE.get(JWKS_URL);
  if (cached && Date.now() - cached.fetchedAt < JWKS_TTL_MS) {
    return cached.keys;
  }

  const res = await fetch(JWKS_URL);
  if (!res.ok) {
    if (cached) return cached.keys; // serve stale if refresh fails
    throw new Error(`JWKS fetch failed: ${res.status}`);
  }
  const body = (await res.json()) as JwksResponse;

  const keys = new Map<string, CryptoKey>();
  for (const jwk of body.keys) {
    try {
      const key = await crypto.subtle.importKey(
        "jwk",
        jwk as JsonWebKey,
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        false,
        ["verify"],
      );
      keys.set(jwk.kid, key);
    } catch {
      // Skip malformed keys rather than fail the whole load.
    }
  }

  const entry: JwksCacheEntry = { keys, fetchedAt: Date.now() };
  JWKS_CACHE.set(JWKS_URL, entry);
  return keys;
}

function b64urlDecode(input: string): Uint8Array {
  const pad = input.length % 4 === 0 ? "" : "=".repeat(4 - (input.length % 4));
  const b64 = (input + pad).replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlToString(input: string): string {
  return new TextDecoder().decode(b64urlDecode(input));
}

interface JwtHeader {
  alg: string;
  kid?: string;
  typ?: string;
}

interface FirebaseIdClaims {
  iss: string;
  aud: string;
  sub: string;
  exp: number;
  iat?: number;
  name?: string;
  [k: string]: unknown;
}

export async function verifyFirebaseIdToken(
  token: string,
  projectId: string,
): Promise<{ uid: string; name?: string } | null> {
  if (!token || !projectId) return null;

  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [headerB64, payloadB64, sigB64] = parts;

  let header: JwtHeader;
  let claims: FirebaseIdClaims;
  try {
    header = JSON.parse(b64urlToString(headerB64));
    claims = JSON.parse(b64urlToString(payloadB64));
  } catch {
    return null;
  }

  if (header.alg !== "RS256" || !header.kid) return null;

  const expectedIss = `https://securetoken.google.com/${projectId}`;
  if (claims.iss !== expectedIss) return null;
  if (claims.aud !== projectId) return null;

  const nowSec = Math.floor(Date.now() / 1000);
  if (typeof claims.exp !== "number" || claims.exp <= nowSec) return null;
  if (typeof claims.sub !== "string" || claims.sub.length === 0) return null;

  let keys: Map<string, CryptoKey>;
  try {
    keys = await loadJwks();
  } catch {
    return null;
  }
  const key = keys.get(header.kid);
  if (!key) return null;

  const signingInput = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const signature = b64urlDecode(sigB64);

  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    signature,
    signingInput,
  );
  if (!ok) return null;

  return {
    uid: claims.sub,
    name: typeof claims.name === "string" ? claims.name : undefined,
  };
}
