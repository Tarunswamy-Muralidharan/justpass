// Shared types for the chess lobby Worker + Durable Object.

export interface Env {
  LOBBY: DurableObjectNamespace;
  FIREBASE_PROJECT_ID: string;
  CLASS_MARKS_DB: D1Database;
  // AES-GCM master key (base64-encoded 32 bytes) for encrypting PWA users'
  // SIS passwords stored in pwa_creds. Set via `wrangler secret put PWA_CRED_KEY`.
  // The cron handler decrypts to log into Keycloak on the user's behalf.
  PWA_CRED_KEY: string;
}

// Class marks comparison — payload uploaded by Android client.
export interface ClassMarksUploadBody {
  classKey: string;
  subjects: Record<string, ClassSubjectMark>;
  overallAvg: number;
}

export interface ClassSubjectMark {
  ca1?: number;
  ca2?: number;
  ca3?: number;
  total?: number;
  status?: string;
}

export interface ClassStatsResponse {
  classKey: string;
  studentCount: number;
  overall: ClassOverallStats;
  subjects: Record<string, ClassSubjectStats>;
  yourRank?: number;
  yourPercentile?: number;
  // 10-bucket distribution histogram for overall avg, indexed 0-9 (each = 10% band).
  overallHistogram: number[];
}

export interface ClassOverallStats {
  avg: number;
  min: number;
  max: number;
}

export interface ClassSubjectStats {
  avg: number;
  min: number;
  max: number;
  yourMark?: number;
  histogram: number[]; // 10 buckets, 0-9
}

export interface PlayerPublic {
  id: string;
  displayName: string;
  joinedAt: number;
}

// Client -> Server messages
export type ClientMessage =
  | { type: "JOIN"; displayName: string }
  | { type: "CHALLENGE"; toId: string; timeControl: string; challengeId?: string }
  | { type: "ACCEPT"; challengeId: string }
  | { type: "DECLINE"; challengeId: string }
  | { type: "CANCEL"; challengeId: string };

// Server -> Client messages
export type ServerMessage =
  | { type: "PRESENCE_SNAPSHOT"; players: PlayerPublic[] }
  | {
      type: "PRESENCE_DIFF";
      added: PlayerPublic[];
      removed: string[];
    }
  | {
      type: "CHALLENGE_INCOMING";
      challengeId: string;
      fromId: string;
      fromName: string;
      timeControl: string;
      expiresAt: number;
    }
  | {
      type: "CHALLENGE_ACCEPTED";
      challengeId: string;
      lichessGameId: string;
      whiteUrl: string;
      blackUrl: string;
      fromColor: "white";
    }
  | { type: "CHALLENGE_DECLINED"; challengeId: string }
  | { type: "CHALLENGE_CANCELED"; challengeId: string }
  | { type: "ERROR"; code: string; message: string };
