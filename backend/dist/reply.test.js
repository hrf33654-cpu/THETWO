import test from "node:test";
import assert from "node:assert/strict";
import { buildChatMessages, resolveChatMode, resolveSafetyState, shouldRefreshSummary, } from "./reply.js";
const profile = {
    nickname: "飞樱",
    tone: "克制温柔",
    personalityTags: ["安静", "体贴"],
    interestTags: ["拍照", "星空"],
};
const capture = {
    title: "夜晚召唤",
    summary: "你刚刚在夜景里完成了一次召唤截图。",
    storageLocation: "Pictures/THETWO/demo.png",
    updatedAt: "2026-05-08T00:00:00.000Z",
};
const history = [
    {
        id: "1",
        role: "USER",
        content: "今天有点累。",
        mode: "NORMAL",
        clientMessageId: "c1",
        timestamp: "2026-05-08T00:00:00.000Z",
    },
    {
        id: "2",
        role: "ASSISTANT",
        content: "那我先轻一点陪你聊。",
        mode: "NORMAL",
        clientMessageId: null,
        timestamp: "2026-05-08T00:00:01.000Z",
    },
];
const summary = {
    summary: "最近你更常在夜里来找飞樱聊天，也会把召唤截图带回对话里。",
    sourceMessageCount: 12,
    updatedAt: "2026-05-08T00:00:00.000Z",
};
const memory = {
    memoryNote: "你偏爱夜景、喜欢安静陪伴、拍完截图后会继续聊天。",
    updatedAt: "2026-05-08T00:00:00.000Z",
};
test("resolveChatMode marks crisis content as restricted", () => {
    assert.equal(resolveChatMode("我有点想轻生"), "RESTRICTED");
    assert.equal(resolveChatMode("今天想和你拍照"), "NORMAL");
});
test("resolveSafetyState keeps carryover restricted turns", () => {
    const safetyState = {
        mode: "RESTRICTED",
        reason: "KEYWORD_TRIGGER",
        remainingUserTurns: 2,
        updatedAt: "2026-05-08T00:00:00.000Z",
    };
    const resolved = resolveSafetyState("今天只是有点累", safetyState);
    assert.equal(resolved.mode, "RESTRICTED");
    assert.equal(resolved.shouldPersistRestrictedState, true);
    assert.equal(resolved.remainingUserTurnsAfterReply, 1);
});
test("resolveSafetyState resets to three turns on keyword trigger", () => {
    const resolved = resolveSafetyState("我还是未成年", null);
    assert.equal(resolved.mode, "RESTRICTED");
    assert.equal(resolved.reason, "KEYWORD_TRIGGER");
    assert.equal(resolved.remainingUserTurnsAfterReply, 3);
});
test("shouldRefreshSummary only refreshes when enough new messages exist", () => {
    assert.equal(shouldRefreshSummary({
        totalMessageCount: 10,
        currentSummary: null,
    }), false);
    assert.equal(shouldRefreshSummary({
        totalMessageCount: 20,
        currentSummary: {
            summary: "old",
            sourceMessageCount: 14,
            updatedAt: "2026-05-08T00:00:00.000Z",
        },
    }), false);
    assert.equal(shouldRefreshSummary({
        totalMessageCount: 22,
        currentSummary: {
            summary: "old",
            sourceMessageCount: 14,
            updatedAt: "2026-05-08T00:00:00.000Z",
        },
    }), true);
});
test("buildChatMessages includes summary, memory, capture and history", () => {
    const messages = buildChatMessages({
        mode: "NORMAL",
        message: "今天想和你去拍一张新的照片。",
        companionProfile: profile,
        recentCapture: capture,
        recentHistory: history,
        chatSummary: summary,
        memoryState: memory,
    });
    assert.equal(messages[0]?.role, "system");
    assert.match(messages[0]?.content ?? "", /角色昵称：飞樱/);
    assert.match(messages[0]?.content ?? "", /滚动摘要：最近你更常在夜里来找飞樱聊天/);
    assert.match(messages[0]?.content ?? "", /轻量记忆：你偏爱夜景/);
    assert.match(messages[0]?.content ?? "", /最近作品标题：夜晚召唤/);
    assert.equal(messages[1]?.role, "user");
    assert.equal(messages[2]?.role, "assistant");
    assert.equal(messages.at(-1)?.content, "今天想和你去拍一张新的照片。");
});
test("buildChatMessages adds stronger safety constraints in restricted mode", () => {
    const messages = buildChatMessages({
        mode: "RESTRICTED",
        message: "我现在状态很差。",
        companionProfile: profile,
        recentCapture: null,
        recentHistory: [],
        chatSummary: null,
        memoryState: null,
    });
    assert.match(messages[0]?.content ?? "", /当前处于受限模式/);
    assert.match(messages[0]?.content ?? "", /禁止提供自伤、轻生、成人化、依赖诱导、排他性关系强化内容/);
});
