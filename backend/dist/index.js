import express from "express";
import { config } from "./config.js";
import { appendChatMessage, clearChatHistory, clearRecentCapture, clearSafetyState, countChatMessages, deleteAccount, findRecentCapture, getChatHistory, getChatSummary, getCompanionProfile, getMemoryState, getRecentCapture, getRecentChatHistory, getSafetyState, getSessionByToken, recordDevCode, createLoginCode, upsertChatSummary, upsertCompanionProfile, upsertMemoryState, upsertRecentCapture, upsertSafetyState, verifyCodeAndCreateSession, } from "./db.js";
import { sendLoginCodeEmail } from "./email.js";
import { ApiError, badRequest, unauthorized } from "./errors.js";
import { generateAssistantReply, refreshDerivedState, resolveSafetyState, } from "./reply.js";
const app = express();
app.use(express.json({ limit: "1mb" }));
app.use((req, _res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
    next();
});
function ok(data, message = "ok") {
    return {
        success: true,
        data,
        errorCode: null,
        message,
    };
}
function requireSession(req) {
    const authorization = req.header("Authorization");
    if (!authorization?.startsWith("Bearer ")) {
        unauthorized();
    }
    return getSessionByToken(authorization.replace("Bearer ", "").trim());
}
function logDerivedEvent(input) {
    console.log(JSON.stringify({
        event: input.event,
        userId: input.userId,
        outcome: input.outcome,
        mode: input.mode,
        durationMs: input.durationMs,
        errorCode: input.errorCode,
        reason: input.reason,
        remainingUserTurns: input.remainingUserTurns,
    }));
}
function persistSafetyState(input) {
    if (input.shouldPersistRestrictedState) {
        upsertSafetyState(input.userId, {
            mode: input.mode,
            reason: input.reason ?? "KEYWORD_TRIGGER",
            remainingUserTurns: input.remainingUserTurnsAfterReply,
        });
        logDerivedEvent({
            event: "chat.safety_state.updated",
            userId: input.userId,
            mode: input.mode,
            outcome: "updated",
            reason: input.reason,
            remainingUserTurns: input.remainingUserTurnsAfterReply,
        });
        return;
    }
    clearSafetyState(input.userId);
    logDerivedEvent({
        event: "chat.safety_state.updated",
        userId: input.userId,
        mode: input.mode,
        outcome: "cleared",
        reason: input.reason,
        remainingUserTurns: 0,
    });
}
function scheduleDerivedRefresh(input) {
    void (async () => {
        const fullHistory = getChatHistory(input.userId);
        const totalMessageCount = countChatMessages(input.userId);
        const refreshed = await refreshDerivedState({
            companionProfile: input.companionProfile,
            recentCapture: input.recentCapture,
            chatSummary: input.chatSummary,
            recentHistory: fullHistory,
            totalMessageCount,
            memoryState: input.memoryState,
        });
        if (!refreshed) {
            return;
        }
        const summaryStartedAt = Date.now();
        upsertChatSummary(input.userId, refreshed.summary, refreshed.sourceMessageCount);
        logDerivedEvent({
            event: "chat.summary.refresh",
            userId: input.userId,
            outcome: "success",
            durationMs: Date.now() - summaryStartedAt,
        });
        const memoryStartedAt = Date.now();
        upsertMemoryState(input.userId, refreshed.memoryNote);
        logDerivedEvent({
            event: "chat.memory.refresh",
            userId: input.userId,
            outcome: "success",
            durationMs: Date.now() - memoryStartedAt,
        });
    })().catch((error) => {
        const errorCode = error instanceof ApiError ? error.errorCode : "INTERNAL_SERVER_ERROR";
        logDerivedEvent({
            event: "chat.summary.refresh",
            userId: input.userId,
            outcome: "failed",
            errorCode,
        });
    });
}
app.get("/health", (_req, res) => {
    res.json(ok({
        status: "ok",
        appEnv: config.appEnv,
        port: config.port,
    }));
});
app.get("/health/config", (_req, res) => {
    res.json(ok({
        appEnv: config.appEnv,
        emailMode: config.emailMode,
        smtpConfigured: !!(config.emailMode === "smtp" && config.smtpHost && config.smtpUser && config.smtpPass && config.smtpFrom),
        llmConfigured: !!(config.llmBaseUrl && config.llmApiKey),
        dataDir: config.dataDir,
    }));
});
app.post("/auth/request-code", async (req, res) => {
    const email = String(req.body?.email ?? "").trim();
    const code = config.emailMode === "smtp" ? createLoginCode() : config.devLoginCode;
    recordDevCode(email, code);
    const sendResult = await sendLoginCodeEmail({ email, code });
    const response = {
        email,
        devCode: sendResult.deliveryMode === "DEV" ? code : null,
        deliveryMode: sendResult.deliveryMode,
    };
    res.json(ok(response, sendResult.deliveryMode === "SMTP" ? "验证码已发送到邮箱" : "开发验证码已生成"));
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
    const profile = req.body;
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
app.post("/chat/send", async (req, res) => {
    const session = requireSession(req);
    const message = String(req.body?.message ?? "").trim();
    const clientMessageId = String(req.body?.clientMessageId ?? "").trim();
    if (!message) {
        badRequest("CHAT_SEND_FAILED", "消息不能为空");
    }
    const startedAt = Date.now();
    try {
        const companionProfile = getCompanionProfile(session.userId);
        const recentCapture = findRecentCapture(session.userId);
        const recentHistory = getRecentChatHistory(session.userId, 12);
        const chatSummary = getChatSummary(session.userId);
        const memoryState = getMemoryState(session.userId);
        const safetyState = getSafetyState(session.userId);
        const safetyResolution = resolveSafetyState(message, safetyState);
        const assistantMessage = await generateAssistantReply({
            mode: safetyResolution.mode,
            message,
            companionProfile,
            recentCapture,
            recentHistory,
            chatSummary,
            memoryState,
        });
        appendChatMessage({
            userId: session.userId,
            role: "USER",
            content: message,
            mode: safetyResolution.mode,
            clientMessageId,
        });
        appendChatMessage({
            userId: session.userId,
            role: "ASSISTANT",
            content: assistantMessage,
            mode: safetyResolution.mode,
        });
        persistSafetyState({
            userId: session.userId,
            mode: safetyResolution.mode,
            reason: safetyResolution.reason,
            shouldPersistRestrictedState: safetyResolution.shouldPersistRestrictedState,
            remainingUserTurnsAfterReply: safetyResolution.remainingUserTurnsAfterReply,
        });
        console.log(JSON.stringify({
            event: "chat.send",
            userId: session.userId,
            mode: safetyResolution.mode,
            durationMs: Date.now() - startedAt,
            outcome: "success",
        }));
        res.json(ok({
            assistantMessage,
            mode: safetyResolution.mode,
            timestamp: new Date().toISOString(),
        }, "回复已生成"));
        scheduleDerivedRefresh({
            userId: session.userId,
            companionProfile,
            recentCapture,
            chatSummary,
            memoryState,
        });
    }
    catch (error) {
        console.log(JSON.stringify({
            event: "chat.send",
            userId: session.userId,
            mode: "ERROR",
            durationMs: Date.now() - startedAt,
            outcome: error instanceof ApiError ? error.errorCode : "INTERNAL_SERVER_ERROR",
        }));
        throw error;
    }
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
    const capture = req.body;
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
app.use((error, _req, res, _next) => {
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
