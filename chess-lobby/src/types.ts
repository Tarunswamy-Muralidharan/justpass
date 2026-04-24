// Shared types for the chess lobby Worker + Durable Object.

export interface Env {
  LOBBY: DurableObjectNamespace;
  FIREBASE_PROJECT_ID: string;
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
