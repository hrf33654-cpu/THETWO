import express, { type NextFunction, type Request, type Response } from "express";
import { config } from "./config.js";
import {
  appendChatMessage,
  clearChatHistory,
  clearRecentCapture,
  deleteAccount,
  getChatHistory,
  getCompanionProfile,
  getRecentCapture,
  getSessionByToken,
  recordDevCode,
  upsertCompanionProfile,
  upsertRecentCapture,
  verifyCodeAndCreateSession,
} from "./db.js";
import { ApiError, badRequest, unauthorized } from "./errors.js";
import { generateAssistantReply, resolveChatMode } from "./reply.js";
import type { ApiResponse, CompanionProfileRecord, RecentCaptureRecord, SessionRecord } from "./types.js";

const app = express();
app.use(express.json({ limit: "1mb" }));

app.use((req, _res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

function ok<T>(data: T, message = "ok"): ApiResponse<T> {
  return {
    success: true,
    data,
    errorCode: null,
    message,
  };
}

function requireSession(req: Request): SessionRecord {
  const authorization = req.header("Authorization");
  if (!authorization?.startsWith("Bearer ")) {
    unauthorized();
  }
  return getSessionByToken(authorization.replace("Bearer ", "").trim());
}

app.get("/health", (_req, res) => {
  res.json(
    ok({
      status: "ok",
      appEnv: config.appEnv,
      port: config.port,
    }),
  );
});

app.post("/auth/request-code", (req, res) => {
  const email = String(req.body?.email ?? "").trim();
  recordDevCode(email, config.devLoginCode);
  res.json(
    ok(
      {
        email,
        devCode: config.devLoginCode,
      },
      "验证码已生成",
    ),
  );
});

app.post("/auth/verify-code", (req, res) => {
  const email = String(req.body?.email ?? "").trim();
  const code = String(req.body?.code ?? "").trim();
  if (!code) {
    badRequest("INVALID_CODE", "请输入验证码");
  }
  const session = verifyCodeAndCreateSession(email, code);
  res.json(ok(session, "登录成功"));
});

app.get("/me", (req, res) => {
  const session = requireSession(req);
  res.json(ok(session));
});

app.put("/me/companion-profile", (req, res) => {
  const session = requireSession(req);
  const profile = req.body as Partial<CompanionProfileRecord>;
  if (!profile.nickname || !profile.tone) {
    badRequest("PROFILE_REQUIRED", "角色资料不完整");
  }
  if (!Array.isArray(profile.personalityTags) || !Array.isArray(profile.interestTags)) {
    badRequest("PROFILE_REQUIRED", "角色标签格式错误");
  }

  const saved = upsertCompanionProfile(session.userId, {
    nickname: String(profile.nickname).trim(),
    tone: String(profile.tone).trim(),
    personalityTags: profile.personalityTags.map(String),
    interestTags: profile.interestTags.map(String),
  });

  res.json(ok(saved, "角色资料已保存"));
});

app.get("/me/companion-profile", (req, res) => {
  const session = requireSession(req);
  const profile = getCompanionProfile(session.userId);
  res.json(ok(profile));
});

app.post("/chat/send", (req, res) => {
  const session = requireSession(req);
  const message = String(req.body?.message ?? "").trim();
  const clientMessageId = String(req.body?.clientMessageId ?? "").trim();
  if (!message) {
    badRequest("CHAT_SEND_FAILED", "消息不能为空");
  }

  const mode = resolveChatMode(message);
  appendChatMessage({
    userId: session.userId,
    role: "USER",
    content: message,
    mode,
    clientMessageId,
  });

  const assistantMessage = generateAssistantReply(message, mode);
  appendChatMessage({
    userId: session.userId,
    role: "ASSISTANT",
    content: assistantMessage,
    mode,
  });

  res.json(
    ok(
      {
        assistantMessage,
        mode,
        timestamp: new Date().toISOString(),
      },
      "回复已生成",
    ),
  );
});

app.get("/chat/history", (req, res) => {
  const session = requireSession(req);
  res.json(ok({ messages: getChatHistory(session.userId) }));
});

app.delete("/chat/history", (req, res) => {
  const session = requireSession(req);
  clearChatHistory(session.userId);
  res.json(ok({ cleared: true }, "聊天记录已清空"));
});

app.put("/me/recent-capture", (req, res) => {
  const session = requireSession(req);
  const capture = req.body as Partial<RecentCaptureRecord>;
  if (!capture.title || !capture.summary || !capture.storageLocation) {
    badRequest("CAPTURE_UPDATE_FAILED", "最近作品回流数据不完整");
  }

  const saved = upsertRecentCapture(session.userId, {
    title: String(capture.title).trim(),
    summary: String(capture.summary).trim(),
    storageLocation: String(capture.storageLocation).trim(),
  });
  res.json(ok(saved, "最近作品回流已保存"));
});

app.get("/me/recent-capture", (req, res) => {
  const session = requireSession(req);
  res.json(ok(getRecentCapture(session.userId)));
});

app.delete("/me/recent-capture", (req, res) => {
  const session = requireSession(req);
  clearRecentCapture(session.userId);
  res.json(ok({ cleared: true }, "最近作品回流已清除"));
});

app.delete("/me", (req, res) => {
  const session = requireSession(req);
  deleteAccount(session.userId);
  res.json(ok({ deleted: true }, "账号数据已删除"));
});

app.use((error: unknown, _req: Request, res: Response, _next: NextFunction) => {
  if (error instanceof ApiError) {
    res.status(error.statusCode).json({
      success: false,
      data: null,
      errorCode: error.errorCode,
      message: error.message,
    });
    return;
  }

  const message = error instanceof Error ? error.message : "未知错误";
  res.status(500).json({
    success: false,
    data: null,
    errorCode: "INTERNAL_SERVER_ERROR",
    message,
  });
});

app.listen(config.port, () => {
  console.log(`THETWO backend listening on http://127.0.0.1:${config.port}`);
});
