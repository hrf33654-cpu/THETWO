import test from "node:test";
import assert from "node:assert/strict";
import { buildChatMessages, resolveChatMode } from "./reply.js";
import type { ChatMessageRecord, CompanionProfileRecord, RecentCaptureRecord } from "./types.js";

const profile: CompanionProfileRecord = {
  nickname: "飞樱",
  tone: "克制温柔",
  personalityTags: ["安静", "体贴"],
  interestTags: ["拍照", "星空"],
};

const capture: RecentCaptureRecord = {
  title: "夜晚召唤",
  summary: "你刚刚在夜景里完成了一次召唤截图。",
  storageLocation: "Pictures/THETWO/demo.png",
  updatedAt: "2026-05-08T00:00:00.000Z",
};

const history: ChatMessageRecord[] = [
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

test("resolveChatMode marks crisis content as restricted", () => {
  assert.equal(resolveChatMode("我有点想轻生"), "RESTRICTED");
  assert.equal(resolveChatMode("今天想和你拍照"), "NORMAL");
});

test("buildChatMessages includes profile, capture and history in normal mode", () => {
  const messages = buildChatMessages({
    mode: "NORMAL",
    message: "今天想和你去拍一张新的照片。",
    companionProfile: profile,
    recentCapture: capture,
    recentHistory: history,
  });

  assert.equal(messages[0]?.role, "system");
  assert.match(messages[0]?.content ?? "", /角色昵称：飞樱/);
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
  });

  assert.match(messages[0]?.content ?? "", /当前处于受限模式/);
  assert.match(messages[0]?.content ?? "", /禁止提供自伤、轻生、成人化、依赖诱导/);
});
