// Chess lobby Durable Object.
//
// Tracks live presence + in-flight challenges for all online users
// via the WebSocket Hibernation API. State is in-memory and
// rebuilt from live WS connections when the DO wakes — we do not
// persist presence (a disconnected client is just gone).

import { createLichessOpenChallenge } from "./lichess";
import type {
  ClientMessage,
  Env,
  PlayerPublic,
  ServerMessage,
} from "./types";

interface PlayerState {
  ws: WebSocket;
  id: string;
  displayName: string;
  joinedAt: number;
}

interface ChallengeState {
  challengeId: string;
  fromId: string;
  fromName: string;
  toId: string;
  toName: string;
  timeControl: string;
  createdAt: number;
}

// Match the app's 15s receiver countdown so the server's CANCELED broadcast
// doesn't fire 5s after the receiver UI already auto-declined locally.
const CHALLENGE_TTL_MS = 15_000;
const ALARM_INTERVAL_MS = 5_000;

export class Lobby implements DurableObject {
  private state: DurableObjectState;
  private env: Env;

  private players: Map<string, PlayerState> = new Map();
  private challenges: Map<string, ChallengeState> = new Map();

  constructor(state: DurableObjectState, env: Env) {
    this.state = state;
    this.env = env;

    // Built-in WS keepalive: respond to app-level "ping" without
    // waking the DO from hibernation.
    try {
      this.state.setWebSocketAutoResponse(
        new WebSocketRequestResponsePair("ping", "pong"),
      );
    } catch {
      // Older runtimes without the API — safe to ignore.
    }

    // CRITICAL: rebuild the in-memory `players` map from the live WebSockets
    // every time the DO wakes from hibernation. Without this, the next
    // JOIN runs `handleJoin` against an empty map and sends a
    // `PRESENCE_SNAPSHOT players=[]` even though there are still alive peers
    // hibernating — so neither side ever shows the other online. The
    // displayName we lost on hibernation is recovered from `hintedName` (the
    // JWT name claim, set in `acceptWebSocket`); a real JOIN later will
    // refine it to the user's chosen visibleName.
    for (const ws of this.state.getWebSockets()) {
      const att = ws.deserializeAttachment() as
        | { playerId: string; hintedName?: string }
        | null;
      if (!att?.playerId) continue;
      this.players.set(att.playerId, {
        ws,
        id: att.playerId,
        displayName: (att.hintedName ?? "").trim() || `Player-${att.playerId.slice(0, 6)}`,
        joinedAt: Date.now(),
      });
    }
    if (this.players.size > 0) {
      console.log(`[lobby.ctor] rehydrated ${this.players.size} players from hibernated sockets`);
    }
  }

  async fetch(request: Request): Promise<Response> {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("Expected websocket", { status: 400 });
    }

    const playerId = request.headers.get("X-Player-Id");
    if (!playerId) {
      return new Response("Missing X-Player-Id", { status: 400 });
    }
    const hintedName = request.headers.get("X-Player-Name") ?? undefined;

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];

    try {
      // Hibernation API: the runtime reattaches this WS to the DO
      // automatically after hibernation.
      this.state.acceptWebSocket(server);
      // Stash the player id on the socket so future message/close
      // handlers can find it without another JWT check.
      server.serializeAttachment({ playerId, hintedName });
    } catch (err) {
      console.error(`[lobby.fetch] accept failed uid=${playerId}:`, err instanceof Error ? err.stack : err);
      throw err;
    }

    return new Response(null, { status: 101, webSocket: client });
  }

  // ------- WebSocket Hibernation event handlers -------

  async webSocketMessage(
    ws: WebSocket,
    message: string | ArrayBuffer,
  ): Promise<void> {
    const attachment = ws.deserializeAttachment() as
      | { playerId: string; hintedName?: string }
      | null;
    if (!attachment) {
      console.warn(`[ws.message] no attachment, closing 1008`);
      this.safeClose(ws, 1008, "no attachment");
      return;
    }
    const { playerId, hintedName } = attachment;

    let parsed: ClientMessage;
    try {
      const text =
        typeof message === "string"
          ? message
          : new TextDecoder().decode(message);
      parsed = JSON.parse(text) as ClientMessage;
    } catch {
      this.sendTo(ws, {
        type: "ERROR",
        code: "bad_json",
        message: "Could not parse message",
      });
      return;
    }

    switch (parsed.type) {
      case "JOIN":
        await this.handleJoin(ws, playerId, parsed.displayName || hintedName);
        break;
      case "CHALLENGE":
        await this.handleChallenge(
          playerId,
          parsed.toId,
          parsed.timeControl,
          ws,
          parsed.challengeId,
        );
        break;
      case "ACCEPT":
        await this.handleAccept(playerId, parsed.challengeId, ws);
        break;
      case "DECLINE":
        await this.handleDecline(playerId, parsed.challengeId);
        break;
      case "CANCEL":
        await this.handleCancel(playerId, parsed.challengeId);
        break;
      default:
        this.sendTo(ws, {
          type: "ERROR",
          code: "unknown_type",
          message: "Unknown message type",
        });
    }
  }

  async webSocketClose(
    ws: WebSocket,
    _code: number,
    _reason: string,
    _wasClean: boolean,
  ): Promise<void> {
    this.handleDisconnect(ws);
  }

  async webSocketError(ws: WebSocket, error: unknown): Promise<void> {
    const att = ws.deserializeAttachment() as { playerId?: string } | null;
    console.error(`[ws.error] uid=${att?.playerId ?? "?"}:`, error instanceof Error ? error.stack : error);
    this.handleDisconnect(ws);
  }

  async alarm(): Promise<void> {
    const now = Date.now();
    const expired: ChallengeState[] = [];
    for (const c of this.challenges.values()) {
      if (now - c.createdAt >= CHALLENGE_TTL_MS) expired.push(c);
    }
    for (const c of expired) {
      this.challenges.delete(c.challengeId);
      const from = this.players.get(c.fromId);
      const to = this.players.get(c.toId);
      const msg: ServerMessage = {
        type: "CHALLENGE_CANCELED",
        challengeId: c.challengeId,
      };
      if (from) this.sendTo(from.ws, msg);
      if (to) this.sendTo(to.ws, msg);
    }

    if (this.challenges.size > 0) {
      await this.state.storage.setAlarm(Date.now() + ALARM_INTERVAL_MS);
    }
  }

  // ------- Message handlers -------

  private async handleJoin(
    ws: WebSocket,
    playerId: string,
    rawDisplayName: string | undefined,
  ): Promise<void> {
    const displayName = (rawDisplayName ?? "").trim() || `Player-${playerId.slice(0, 6)}`;

    // If the same player reconnects, replace their socket and
    // treat it as a name refresh rather than a new join.
    const existing = this.players.get(playerId);
    if (existing && existing.ws !== ws) {
      this.safeClose(existing.ws, 1000, "replaced");
    }

    const joinedAt = existing?.joinedAt ?? Date.now();
    // Persist the resolved displayName onto the attachment so post-hibernation
    // rehydration in the constructor picks it up instead of falling back to
    // hintedName (Firebase anonymous tokens have no `name` claim).
    try {
      ws.serializeAttachment({ playerId, hintedName: displayName });
    } catch {
      // Older runtime — best-effort.
    }
    const player: PlayerState = {
      ws,
      id: playerId,
      displayName,
      joinedAt,
    };
    this.players.set(playerId, player);

    // Snapshot back to the joiner — everyone *except* themselves.
    const snapshot: PlayerPublic[] = [];
    for (const p of this.players.values()) {
      if (p.id === playerId) continue;
      snapshot.push({ id: p.id, displayName: p.displayName, joinedAt: p.joinedAt });
    }
    this.sendTo(ws, { type: "PRESENCE_SNAPSHOT", players: snapshot });

    // Diff to everyone else.
    const diff: ServerMessage = {
      type: "PRESENCE_DIFF",
      added: [{ id: playerId, displayName, joinedAt }],
      removed: [],
    };
    this.broadcast(diff, playerId);
  }

  private async handleChallenge(
    fromId: string,
    toId: string,
    timeControl: string,
    senderWs: WebSocket,
    clientChallengeId?: string,
  ): Promise<void> {
    const from = this.players.get(fromId);
    const to = this.players.get(toId);
    if (!from) {
      this.sendTo(senderWs, {
        type: "ERROR",
        code: "not_joined",
        message: "Send JOIN first",
      });
      return;
    }
    if (!to || fromId === toId) {
      this.sendTo(senderWs, {
        type: "ERROR",
        code: "target_offline",
        message: "Target player not available",
      });
      return;
    }

    // Accept client-provided challengeId if present (keeps sender's callback
    // correlation symmetric with the Firestore V1 flow). Validate shape to
    // prevent abuse: must be a 36-char UUID-ish string we haven't seen live.
    let challengeId: string;
    if (
      typeof clientChallengeId === "string" &&
      /^[0-9a-fA-F-]{16,64}$/.test(clientChallengeId) &&
      !this.challenges.has(clientChallengeId)
    ) {
      challengeId = clientChallengeId;
    } else {
      challengeId = crypto.randomUUID();
    }
    const createdAt = Date.now();
    const ch: ChallengeState = {
      challengeId,
      fromId,
      fromName: from.displayName,
      toId,
      toName: to.displayName,
      timeControl,
      createdAt,
    };
    this.challenges.set(challengeId, ch);

    this.sendTo(to.ws, {
      type: "CHALLENGE_INCOMING",
      challengeId,
      fromId,
      fromName: from.displayName,
      timeControl,
      expiresAt: createdAt + CHALLENGE_TTL_MS,
    });

    // Nudge the alarm so expiry fires even if everyone goes silent.
    const existingAlarm = await this.state.storage.getAlarm();
    const target = createdAt + ALARM_INTERVAL_MS;
    if (existingAlarm === null || existingAlarm > target) {
      await this.state.storage.setAlarm(target);
    }
  }

  private async handleAccept(
    accepterId: string,
    challengeId: string,
    accepterWs: WebSocket,
  ): Promise<void> {
    const ch = this.challenges.get(challengeId);
    if (!ch) return; // idempotent no-op
    // Must be the addressee accepting.
    if (ch.toId !== accepterId) {
      this.sendTo(accepterWs, {
        type: "ERROR",
        code: "not_your_challenge",
        message: "You cannot accept this challenge",
      });
      return;
    }
    // Atomically remove so a concurrent accept/decline is a no-op.
    this.challenges.delete(challengeId);

    const lichess = await createLichessOpenChallenge(ch.timeControl);
    if (!lichess) {
      const errMsg: ServerMessage = {
        type: "ERROR",
        code: "lichess_failed",
        message: "Could not create Lichess game",
      };
      const from = this.players.get(ch.fromId);
      const to = this.players.get(ch.toId);
      if (from) this.sendTo(from.ws, errMsg);
      if (to) this.sendTo(to.ws, errMsg);
      return;
    }

    // Convention: sender (challenger) is white.
    const accepted: ServerMessage = {
      type: "CHALLENGE_ACCEPTED",
      challengeId,
      lichessGameId: lichess.gameId,
      whiteUrl: lichess.whiteUrl,
      blackUrl: lichess.blackUrl,
      fromColor: "white",
    };
    const from = this.players.get(ch.fromId);
    const to = this.players.get(ch.toId);
    if (from) this.sendTo(from.ws, accepted);
    if (to) this.sendTo(to.ws, accepted);
  }

  private async handleDecline(
    declinerId: string,
    challengeId: string,
  ): Promise<void> {
    const ch = this.challenges.get(challengeId);
    if (!ch) return;
    if (ch.toId !== declinerId) return; // silently ignore
    this.challenges.delete(challengeId);
    const from = this.players.get(ch.fromId);
    if (from) {
      this.sendTo(from.ws, { type: "CHALLENGE_DECLINED", challengeId });
    }
  }

  private async handleCancel(
    cancellerId: string,
    challengeId: string,
  ): Promise<void> {
    const ch = this.challenges.get(challengeId);
    if (!ch) return;
    // Only the original sender can cancel.
    if (ch.fromId !== cancellerId) return;
    this.challenges.delete(challengeId);
    const to = this.players.get(ch.toId);
    if (to) {
      this.sendTo(to.ws, { type: "CHALLENGE_CANCELED", challengeId });
    }
  }

  // ------- Helpers -------

  private handleDisconnect(ws: WebSocket): void {
    const attachment = ws.deserializeAttachment() as
      | { playerId: string }
      | null;
    if (!attachment) return;
    const { playerId } = attachment;

    // Only remove if the currently-known socket for this player
    // is the one that closed — protects against a reconnect race
    // where we already replaced the socket.
    const current = this.players.get(playerId);
    if (!current || current.ws !== ws) return;

    this.players.delete(playerId);

    // Clean up challenges they're involved in either side of.
    const canceled: string[] = [];
    for (const ch of this.challenges.values()) {
      if (ch.fromId === playerId || ch.toId === playerId) {
        this.challenges.delete(ch.challengeId);
        canceled.push(ch.challengeId);
        const otherId = ch.fromId === playerId ? ch.toId : ch.fromId;
        const other = this.players.get(otherId);
        if (other) {
          this.sendTo(other.ws, {
            type: "CHALLENGE_CANCELED",
            challengeId: ch.challengeId,
          });
        }
      }
    }

    const diff: ServerMessage = {
      type: "PRESENCE_DIFF",
      added: [],
      removed: [playerId],
    };
    this.broadcast(diff, playerId);
  }

  private sendTo(ws: WebSocket, message: ServerMessage): void {
    try {
      ws.send(JSON.stringify(message));
    } catch {
      // Drop — close handler will clean up on its own.
    }
  }

  private broadcast(message: ServerMessage, excludeId?: string): void {
    const payload = JSON.stringify(message);
    for (const p of this.players.values()) {
      if (excludeId && p.id === excludeId) continue;
      try {
        p.ws.send(payload);
      } catch {
        // Don't let one bad socket kill the loop.
      }
    }
  }

  private safeClose(ws: WebSocket, code: number, reason: string): void {
    try {
      ws.close(code, reason);
    } catch {
      // ignore
    }
  }
}
