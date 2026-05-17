import fs from "node:fs";
import crypto from "node:crypto";
import { DatabaseSync } from "node:sqlite";
import { config } from "./config.js";
import { badRequest, notFound, unauthorized } from "./errors.js";
import {
  type ChatSummaryRecord,
  type ChatMessageRecord,
  type ChatMode,
  type CompanionProfileRecord,
  type MemoryStateRecord,
  type RecentCaptureRecord,
  type SafetyStateRecord,
  type SessionRecord,
} from "./types.js";

fs.mkdirSync(config.dataDir, { recursive: true });

export const db = new DatabaseSync(config.dbPath);
db.exec("PRAGMA foreign_keys = ON;");
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_codes (
  email TEXT PRIMARY KEY,
  code TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS companion_profiles (
  user_id TEXT PRIMARY KEY,
  nickname TEXT NOT NULL,
  tone TEXT NOT NULL,
  personality_tags TEXT NOT NULL,
  interest_tags TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_messages (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  role TEXT NOT NULL,
  content TEXT NOT NULL,
  mode TEXT NOT NULL,
  client_message_id TEXT,
  created_at TEXT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS recent_captures (
  user_id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  summary TEXT NOT NULL,
  storage_location TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_summaries (
  user_id TEXT PRIMARY KEY,
  summary TEXT NOT NULL,
  source_message_count INTEGER NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS memory_states (
  user_id TEXT PRIMARY KEY,
  memory_note TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS safety_states (
  user_id TEXT PRIMARY KEY,
  mode TEXT NOT NULL,
  reason TEXT NOT NULL,
  remaining_user_turns INTEGER NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
`);

function now(): string {
  return new Date().toISOString();
}

function randomId(): string {
  return crypto.randomUUID();
}

function randomToken(): string {
  return crypto.randomBytes(24).toString("hex");
}

function parseJsonArray(value: string): string[] {
  const parsed = JSON.parse(value);
  return Array.isArray(parsed) ? parsed.map(String) : [];
}

export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export function recordDevCode(email: string, code: string): void {
  if (!isValidEmail(email)) {
    badRequest("INVALID_EMAIL", "请输入有效邮箱");
  }

  db.prepare(`
    INSERT INTO auth_codes (email, code, created_at)
    VALUES (?, ?, ?)
    ON CONFLICT(email) DO UPDATE SET
      code = excluded.code,
      created_at = excluded.created_at
  `).run(email, code, now());
}

export function createLoginCode(): string {
  return crypto.randomInt(100000, 1000000).toString();
}

export function verifyCodeAndCreateSession(email: string, code: string): SessionRecord {
  if (!isValidEmail(email)) {
    badRequest("INVALID_EMAIL", "请输入有效邮箱");
  }

  const authCode = db.prepare(`
    SELECT email, code
    FROM auth_codes
    WHERE email = ?
  `).get(email) as { email: string; code: string; created_at: string } | undefined;

  if (!authCode || authCode.code !== code) {
    badRequest("INVALID_CODE", "验证码错误");
  }

  const issuedAt = Date.parse(authCode.created_at);
  const ttlMs = config.authCodeTtlMinutes * 60 * 1000;
  if (!Number.isFinite(issuedAt) || Date.now() - issuedAt > ttlMs) {
    badRequest("INVALID_CODE", "验证码已过期，请重新获取");
  }

  let user = db.prepare(`
    SELECT id, email
    FROM users
    WHERE email = ?
  `).get(email) as { id: string; email: string } | undefined;

  if (!user) {
    user = { id: randomId(), email };
    db.prepare(`
      INSERT INTO users (id, email, created_at)
      VALUES (?, ?, ?)
    `).run(user.id, user.email, now());
  }

  const token = randomToken();
  db.prepare(`
    INSERT INTO sessions (token, user_id, created_at)
    VALUES (?, ?, ?)
  `).run(token, user.id, now());

  const profileExists = Boolean(
    db.prepare(`
      SELECT 1
      FROM companion_profiles
      WHERE user_id = ?
    `).get(user.id),
  );

  return {
    userId: user.id,
    email: user.email,
    sessionToken: token,
    profileCompleted: profileExists,
  };
}

export function getSessionByToken(token: string): SessionRecord {
  const session = db.prepare(`
    SELECT users.id as userId, users.email as email, sessions.token as sessionToken
    FROM sessions
    INNER JOIN users ON users.id = sessions.user_id
    WHERE sessions.token = ?
  `).get(token) as {
    userId: string;
    email: string;
    sessionToken: string;
  } | undefined;

  if (!session) {
    unauthorized();
  }

  const profileExists = Boolean(
    db.prepare(`
      SELECT 1
      FROM companion_profiles
      WHERE user_id = ?
    `).get(session.userId),
  );

  return {
    ...session,
    profileCompleted: profileExists,
  };
}

export function upsertCompanionProfile(
  userId: string,
  profile: CompanionProfileRecord,
): CompanionProfileRecord {
  const timestamp = now();
  db.prepare(`
    INSERT INTO companion_profiles (
      user_id,
      nickname,
      tone,
      personality_tags,
      interest_tags,
      updated_at
    ) VALUES (?, ?, ?, ?, ?, ?)
    ON CONFLICT(user_id) DO UPDATE SET
      nickname = excluded.nickname,
      tone = excluded.tone,
      personality_tags = excluded.personality_tags,
      interest_tags = excluded.interest_tags,
      updated_at = excluded.updated_at
  `).run(
    userId,
    profile.nickname,
    profile.tone,
    JSON.stringify(profile.personalityTags),
    JSON.stringify(profile.interestTags),
    timestamp,
  );
  return profile;
}

export function getCompanionProfile(userId: string): CompanionProfileRecord {
  const record = db.prepare(`
    SELECT nickname, tone, personality_tags, interest_tags
    FROM companion_profiles
    WHERE user_id = ?
  `).get(userId) as {
    nickname: string;
    tone: string;
    personality_tags: string;
    interest_tags: string;
  } | undefined;

  if (!record) {
    notFound("PROFILE_REQUIRED", "当前用户还没有角色资料");
  }

  return {
    nickname: record.nickname,
    tone: record.tone,
    personalityTags: parseJsonArray(record.personality_tags),
    interestTags: parseJsonArray(record.interest_tags),
  };
}

export function appendChatMessage(input: {
  userId: string;
  role: "USER" | "ASSISTANT";
  content: string;
  mode: ChatMode;
  clientMessageId?: string | null;
}): ChatMessageRecord {
  const message: ChatMessageRecord = {
    id: randomId(),
    role: input.role,
    content: input.content,
    mode: input.mode,
    clientMessageId: input.clientMessageId ?? null,
    timestamp: now(),
  };

  db.prepare(`
    INSERT INTO chat_messages (
      id,
      user_id,
      role,
      content,
      mode,
      client_message_id,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?)
  `).run(
    message.id,
    input.userId,
    message.role,
    message.content,
    message.mode,
    message.clientMessageId,
    message.timestamp,
  );

  return message;
}

export function getChatHistory(userId: string): ChatMessageRecord[] {
  const rows = db.prepare(`
    SELECT id, role, content, mode, client_message_id as clientMessageId, created_at as timestamp
    FROM chat_messages
    WHERE user_id = ?
    ORDER BY created_at ASC
  `).all(userId) as ChatMessageRecord[];

  return rows;
}

export function getRecentChatHistory(userId: string, limit: number): ChatMessageRecord[] {
  const normalizedLimit = Number.isInteger(limit) && limit > 0 ? limit : 12;
  const rows = db.prepare(`
    SELECT id, role, content, mode, client_message_id as clientMessageId, created_at as timestamp
    FROM chat_messages
    WHERE user_id = ?
    ORDER BY created_at DESC
    LIMIT ?
  `).all(userId, normalizedLimit) as ChatMessageRecord[];

  return rows.reverse();
}

export function clearChatHistory(userId: string): void {
  db.prepare(`DELETE FROM chat_messages WHERE user_id = ?`).run(userId);
  clearDerivedChatState(userId);
}

export function countChatMessages(userId: string): number {
  const row = db.prepare(`
    SELECT COUNT(*) as total
    FROM chat_messages
    WHERE user_id = ?
  `).get(userId) as { total: number };

  return Number(row.total ?? 0);
}

export function upsertRecentCapture(
  userId: string,
  capture: Omit<RecentCaptureRecord, "updatedAt">,
): RecentCaptureRecord {
  const record: RecentCaptureRecord = {
    ...capture,
    updatedAt: now(),
  };

  db.prepare(`
    INSERT INTO recent_captures (
      user_id,
      title,
      summary,
      storage_location,
      updated_at
    ) VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(user_id) DO UPDATE SET
      title = excluded.title,
      summary = excluded.summary,
      storage_location = excluded.storage_location,
      updated_at = excluded.updated_at
  `).run(
    userId,
    record.title,
    record.summary,
    record.storageLocation,
    record.updatedAt,
  );

  return record;
}

export function getRecentCapture(userId: string): RecentCaptureRecord {
  const record = db.prepare(`
    SELECT title, summary, storage_location as storageLocation, updated_at as updatedAt
    FROM recent_captures
    WHERE user_id = ?
  `).get(userId) as RecentCaptureRecord | undefined;

  if (!record) {
    notFound("CAPTURE_UPDATE_FAILED", "当前没有最近作品回流记录");
  }

  return record;
}

export function findRecentCapture(userId: string): RecentCaptureRecord | null {
  const record = db.prepare(`
    SELECT title, summary, storage_location as storageLocation, updated_at as updatedAt
    FROM recent_captures
    WHERE user_id = ?
  `).get(userId) as RecentCaptureRecord | undefined;

  return record ?? null;
}

export function clearRecentCapture(userId: string): void {
  db.prepare(`DELETE FROM recent_captures WHERE user_id = ?`).run(userId);
}

export function deleteAccount(userId: string): void {
  db.prepare(`DELETE FROM users WHERE id = ?`).run(userId);
}

export function getChatSummary(userId: string): ChatSummaryRecord | null {
  const record = db.prepare(`
    SELECT summary, source_message_count as sourceMessageCount, updated_at as updatedAt
    FROM chat_summaries
    WHERE user_id = ?
  `).get(userId) as ChatSummaryRecord | undefined;

  return record ?? null;
}

export function upsertChatSummary(
  userId: string,
  summary: string,
  sourceMessageCount: number,
): ChatSummaryRecord {
  const record: ChatSummaryRecord = {
    summary,
    sourceMessageCount,
    updatedAt: now(),
  };

  db.prepare(`
    INSERT INTO chat_summaries (
      user_id,
      summary,
      source_message_count,
      updated_at
    ) VALUES (?, ?, ?, ?)
    ON CONFLICT(user_id) DO UPDATE SET
      summary = excluded.summary,
      source_message_count = excluded.source_message_count,
      updated_at = excluded.updated_at
  `).run(
    userId,
    record.summary,
    record.sourceMessageCount,
    record.updatedAt,
  );

  return record;
}

export function getMemoryState(userId: string): MemoryStateRecord | null {
  const record = db.prepare(`
    SELECT memory_note as memoryNote, updated_at as updatedAt
    FROM memory_states
    WHERE user_id = ?
  `).get(userId) as MemoryStateRecord | undefined;

  return record ?? null;
}

export function upsertMemoryState(
  userId: string,
  memoryNote: string,
): MemoryStateRecord {
  const record: MemoryStateRecord = {
    memoryNote,
    updatedAt: now(),
  };

  db.prepare(`
    INSERT INTO memory_states (
      user_id,
      memory_note,
      updated_at
    ) VALUES (?, ?, ?)
    ON CONFLICT(user_id) DO UPDATE SET
      memory_note = excluded.memory_note,
      updated_at = excluded.updated_at
  `).run(
    userId,
    record.memoryNote,
    record.updatedAt,
  );

  return record;
}

export function getSafetyState(userId: string): SafetyStateRecord | null {
  const record = db.prepare(`
    SELECT mode, reason, remaining_user_turns as remainingUserTurns, updated_at as updatedAt
    FROM safety_states
    WHERE user_id = ?
  `).get(userId) as SafetyStateRecord | undefined;

  return record ?? null;
}

export function upsertSafetyState(
  userId: string,
  input: {
    mode: ChatMode;
    reason: string;
    remainingUserTurns: number;
  },
): SafetyStateRecord {
  const record: SafetyStateRecord = {
    mode: input.mode,
    reason: input.reason,
    remainingUserTurns: input.remainingUserTurns,
    updatedAt: now(),
  };

  db.prepare(`
    INSERT INTO safety_states (
      user_id,
      mode,
      reason,
      remaining_user_turns,
      updated_at
    ) VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(user_id) DO UPDATE SET
      mode = excluded.mode,
      reason = excluded.reason,
      remaining_user_turns = excluded.remaining_user_turns,
      updated_at = excluded.updated_at
  `).run(
    userId,
    record.mode,
    record.reason,
    record.remainingUserTurns,
    record.updatedAt,
  );

  return record;
}

export function clearSafetyState(userId: string): void {
  db.prepare(`DELETE FROM safety_states WHERE user_id = ?`).run(userId);
}

export function clearDerivedChatState(userId: string): void {
  db.prepare(`DELETE FROM chat_summaries WHERE user_id = ?`).run(userId);
  db.prepare(`DELETE FROM memory_states WHERE user_id = ?`).run(userId);
  clearSafetyState(userId);
}
