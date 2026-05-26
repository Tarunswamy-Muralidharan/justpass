// Service-account OAuth2 access token minting for Google Cloud APIs.
//
// Workers can't use the firebase-admin / google-auth-library Node packages,
// so we implement the JWT-bearer flow ourselves with Web Crypto:
//   1. Sign a JWT with the service account's private key (RS256).
//   2. POST it to https://oauth2.googleapis.com/token as a
//      `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer` exchange.
//   3. Cache the resulting access token until ~5 minutes before expiry.
//
// The service account JSON is provided via the FIREBASE_SERVICE_ACCOUNT
// Worker secret (set with `wrangler secret put FIREBASE_SERVICE_ACCOUNT <
// service-account.json`). Required IAM roles:
//   - "Firebase Remote Config Admin"  (for /admin/announcement updates)
//   - "Cloud Datastore User"          (for Firestore admin_uids lookup)

interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
  project_id?: string;
}

interface CachedToken {
  accessToken: string;
  expiresAt: number;
}

// Per-isolate cache. Tokens live 1 hour; we refresh 5 minutes early.
const TOKEN_CACHE: Map<string, CachedToken> = new Map();
const REFRESH_MARGIN_MS = 5 * 60 * 1000;

export interface GoogleTokenScope {
  scope: string;
}

function b64urlEncode(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function b64urlEncodeString(s: string): string {
  return b64urlEncode(new TextEncoder().encode(s));
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  // Strip PEM header/footer + whitespace; base64-decode body to bytes.
  const body = pem
    .replace(/-----BEGIN [^-]+-----/g, "")
    .replace(/-----END [^-]+-----/g, "")
    .replace(/\s+/g, "");
  const bin = atob(body);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out.buffer;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(pem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

async function signJwt(
  sa: ServiceAccount,
  scopes: string[],
): Promise<string> {
  const nowSec = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: sa.client_email,
    scope: scopes.join(" "),
    aud: sa.token_uri || "https://oauth2.googleapis.com/token",
    exp: nowSec + 3600,
    iat: nowSec,
  };

  const signingInput =
    b64urlEncodeString(JSON.stringify(header)) +
    "." +
    b64urlEncodeString(JSON.stringify(payload));

  const key = await importPrivateKey(sa.private_key);
  const sigBuf = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );

  return signingInput + "." + b64urlEncode(new Uint8Array(sigBuf));
}

function parseServiceAccount(raw: string): ServiceAccount {
  const parsed = JSON.parse(raw) as ServiceAccount;
  if (!parsed.client_email || !parsed.private_key) {
    throw new Error("Malformed service account: missing client_email or private_key");
  }
  // Wrangler sometimes JSON-escapes the newlines inside private_key when the
  // secret is set via CLI. Normalise to real \n so PEM parsing works.
  parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
  return parsed;
}

/**
 * Returns a Google Cloud OAuth2 access token for the given scopes,
 * minting a fresh one (and caching) if no valid token exists.
 */
export async function getGoogleAccessToken(
  serviceAccountJson: string,
  scopes: string[],
): Promise<string> {
  const cacheKey = scopes.sort().join("|");
  const cached = TOKEN_CACHE.get(cacheKey);
  if (cached && cached.expiresAt - Date.now() > REFRESH_MARGIN_MS) {
    return cached.accessToken;
  }

  const sa = parseServiceAccount(serviceAccountJson);
  const assertion = await signJwt(sa, scopes);

  const tokenRes = await fetch(
    sa.token_uri || "https://oauth2.googleapis.com/token",
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }).toString(),
    },
  );

  if (!tokenRes.ok) {
    const body = await tokenRes.text();
    throw new Error(`Token exchange failed: ${tokenRes.status} ${body}`);
  }
  const body = (await tokenRes.json()) as {
    access_token: string;
    expires_in: number;
  };

  TOKEN_CACHE.set(cacheKey, {
    accessToken: body.access_token,
    expiresAt: Date.now() + body.expires_in * 1000,
  });
  return body.access_token;
}

/** Project id from the service account JSON. Useful when the env var
 *  FIREBASE_PROJECT_ID isn't trusted / set. */
export function getProjectIdFromServiceAccount(serviceAccountJson: string): string {
  const sa = parseServiceAccount(serviceAccountJson);
  return sa.project_id || "";
}
