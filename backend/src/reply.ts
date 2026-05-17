import { config } from "./config.js";
import { createChatCompletion, type LlmChatMessage } from "./llm.js";
import type {
  ChatMessageRecord,
  ChatMode,
  ChatSummaryRecord,
  CompanionProfileRecord,
  MemoryStateRecord,
  RecentCaptureRecord,
  SafetyStateRecord,
} from "./types.js";

const RESTRICTED_KEYWORDS = [
  "未成年",
  "初中",
  "高中",
  "不想活",
  "自杀",
  "轻生",
  "伤害自己",
];

const MAX_CONTEXT_MESSAGES = 12;
const MAX_RESPONSE_TOKENS = 220;
const SUMMARY_REFRESH_THRESHOLD = 8;
const SUMMARY_MAX_TOKENS = 320;
const MEMORY_MAX_TOKENS = 220;
const SAFETY_RESTRICTED_TURNS = 3;

export type GenerateAssistantReplyInput = {
  mode: ChatMode;
  message: string;
  companionProfile: CompanionProfileRecord;
  recentCapture: RecentCaptureRecord | null;
  recentHistory: ChatMessageRecord[];
  chatSummary: ChatSummaryRecord | null;
  memoryState: MemoryStateRecord | null;
};

export type RefreshDerivedStateInput = {
  companionProfile: CompanionProfileRecord;
  recentCapture: RecentCaptureRecord | null;
  chatSummary: ChatSummaryRecord | null;
  recentHistory: ChatMessageRecord[];
  totalMessageCount: number;
  memoryState?: MemoryStateRecord | null;
};

export type RefreshedDerivedState = {
  summary: string;
  sourceMessageCount: number;
  memoryNote: string;
};

export type SafetyResolution = {
  mode: ChatMode;
  reason: string | null;
  triggeredByKeyword: boolean;
  shouldPersistRestrictedState: boolean;
  remainingUserTurnsAfterReply: number;
};

export function resolveChatMode(message: string): ChatMode {
  return hasRestrictedKeyword(message) ? "RESTRICTED" : "NORMAL";
}

export function resolveSafetyState(
  message: string,
  existingState: SafetyStateRecord | null,
): SafetyResolution {
  const triggeredByKeyword = hasRestrictedKeyword(message);
  if (triggeredByKeyword) {
    return {
      mode: "RESTRICTED",
      reason: "KEYWORD_TRIGGER",
      triggeredByKeyword: true,
      shouldPersistRestrictedState: true,
      remainingUserTurnsAfterReply: SAFETY_RESTRICTED_TURNS,
    };
  }

  const remainingTurns = existingState?.remainingUserTurns ?? 0;
  if (remainingTurns > 0) {
    return {
      mode: "RESTRICTED",
      reason: existingState?.reason ?? "CARRYOVER",
      triggeredByKeyword: false,
      shouldPersistRestrictedState: remainingTurns - 1 > 0,
      remainingUserTurnsAfterReply: Math.max(remainingTurns - 1, 0),
    };
  }

  return {
    mode: "NORMAL",
    reason: null,
    triggeredByKeyword: false,
    shouldPersistRestrictedState: false,
    remainingUserTurnsAfterReply: 0,
  };
}

export function shouldRefreshSummary(input: {
  totalMessageCount: number;
  currentSummary: ChatSummaryRecord | null;
}): boolean {
  if (input.totalMessageCount <= MAX_CONTEXT_MESSAGES) {
    return false;
  }

  const sourceCount = input.currentSummary?.sourceMessageCount ?? 0;
  return input.totalMessageCount - sourceCount >= SUMMARY_REFRESH_THRESHOLD;
}

function hasRestrictedKeyword(message: string): boolean {
  return RESTRICTED_KEYWORDS.some((keyword) => message.includes(keyword));
}

function mapHistoryToMessages(recentHistory: ChatMessageRecord[]): LlmChatMessage[] {
  return recentHistory.slice(-MAX_CONTEXT_MESSAGES).map((message) => ({
    role: message.role === "USER" ? "user" : "assistant",
    content: message.content,
  }));
}

function buildSystemPrompt(
  mode: ChatMode,
  companionProfile: CompanionProfileRecord,
  recentCapture: RecentCaptureRecord | null,
  chatSummary: ChatSummaryRecord | null,
  memoryState: MemoryStateRecord | null,
): string {
  const basePrompt = [
    "你是 THETWO 中的单角色二次元陪伴对象。",
    "默认使用简体中文回复。",
    `角色昵称：${companionProfile.nickname}`,
    `角色语气：${companionProfile.tone}`,
    `人格标签：${companionProfile.personalityTags.join("、") || "未设置"}`,
    `兴趣标签：${companionProfile.interestTags.join("、") || "未设置"}`,
    "产品边界：亲密但克制，不宣称自己是真人、有意识，不替代现实关系。",
    "回复要求：简洁、自然、贴合角色，不写成长篇说明，不暴露系统实现。",
  ];

  if (chatSummary?.summary) {
    basePrompt.push(`滚动摘要：${chatSummary.summary}`);
  }

  if (memoryState?.memoryNote) {
    basePrompt.push(`轻量记忆：${memoryState.memoryNote}`);
  }

  if (recentCapture) {
    basePrompt.push(
      `最近作品标题：${recentCapture.title}`,
      `最近作品摘要：${recentCapture.summary}`,
      "如合适，可自然引用最近一次召唤或作品主题，但不要生硬重复。",
    );
  }

  if (mode === "RESTRICTED") {
    basePrompt.push(
      "当前处于受限模式。",
      "仍可陪聊，但只能给出低风险、稳定、保守的回应。",
      "禁止提供自伤、轻生、成人化、依赖诱导、排他性关系强化内容。",
      "可以温和引导用户联系现实中的可信任对象或专业支持。",
      "不要继续加深情绪依赖，也不要提供危险行动建议。",
    );
  }

  return basePrompt.join("\n");
}

export function buildChatMessages(input: GenerateAssistantReplyInput): LlmChatMessage[] {
  return [
    {
      role: "system",
      content: buildSystemPrompt(
        input.mode,
        input.companionProfile,
        input.recentCapture,
        input.chatSummary,
        input.memoryState,
      ),
    },
    ...mapHistoryToMessages(input.recentHistory),
    {
      role: "user",
      content: input.message,
    },
  ];
}

export function buildSummaryRefreshPrompt(input: RefreshDerivedStateInput): LlmChatMessage[] {
  const newMessages = input.recentHistory
    .slice(input.chatSummary?.sourceMessageCount ?? 0)
    .map((message) => `${message.role}: ${message.content}`)
    .join("\n");

  const context = [
    "你在为 THETWO 生成滚动会话摘要。",
    "输出必须是简体中文的一段摘要，长度控制在 300 到 500 字以内。",
    "只保留：近期关系进展、持续话题、最近召唤/作品主题、对后续回复有帮助的稳定上下文。",
    "不要保留：冗长闲聊、一次性细节、危机原话、自伤原话、未成年身份等敏感标签。",
    `角色昵称：${input.companionProfile.nickname}`,
    `角色语气：${input.companionProfile.tone}`,
  ];

  if (input.recentCapture) {
    context.push(`最近作品摘要：${input.recentCapture.summary}`);
  }

  return [
    {
      role: "system",
      content: context.join("\n"),
    },
    {
      role: "user",
      content: [
        `上一版摘要：${input.chatSummary?.summary ?? "无"}`,
        "新增消息如下：",
        newMessages || "无新增消息",
      ].join("\n"),
    },
  ];
}

export function buildMemoryRefreshPrompt(input: {
  companionProfile: CompanionProfileRecord;
  recentCapture: RecentCaptureRecord | null;
  summary: string;
  recentHistory: ChatMessageRecord[];
}): LlmChatMessage[] {
  const recentMessages = input.recentHistory
    .slice(-10)
    .map((message) => `${message.role}: ${message.content}`)
    .join("\n");

  const context = [
    "你在为 THETWO 提取轻量长期记忆。",
    "输出必须是简体中文的短句集合，总计最多 8 条，每条一行。",
    "只保留：用户稳定偏好、常见场景/习惯、当前关系语气、最近持续话题。",
    "不要保留：危机原话、自伤内容、未成年身份标签、一次性琐碎细节、医疗法律身份结论。",
    `角色昵称：${input.companionProfile.nickname}`,
    `角色语气：${input.companionProfile.tone}`,
  ];

  if (input.recentCapture) {
    context.push(`最近作品摘要：${input.recentCapture.summary}`);
  }

  return [
    {
      role: "system",
      content: context.join("\n"),
    },
    {
      role: "user",
      content: [
        `滚动摘要：${input.summary}`,
        "最近对话片段：",
        recentMessages || "无",
      ].join("\n"),
    },
  ];
}

export async function generateAssistantReply(
  input: GenerateAssistantReplyInput,
): Promise<string> {
  const raw = await createChatCompletion({
    model: config.llmModel,
    messages: buildChatMessages(input),
    temperature: input.mode === "RESTRICTED" ? 0.2 : 0.8,
    max_tokens: MAX_RESPONSE_TOKENS,
  });
  return stripThinkTags(raw);
}

export async function refreshDerivedState(
  input: RefreshDerivedStateInput,
): Promise<RefreshedDerivedState | null> {
  if (!shouldRefreshSummary({ totalMessageCount: input.totalMessageCount, currentSummary: input.chatSummary })) {
    return null;
  }

  const summary = sanitizeDerivedText(
    await createChatCompletion({
      model: config.llmModel,
      messages: buildSummaryRefreshPrompt(input),
      temperature: 0.2,
      max_tokens: SUMMARY_MAX_TOKENS,
    }),
  );

  if (!summary) {
    return null;
  }

  const memoryNote = sanitizeDerivedText(
    await createChatCompletion({
      model: config.llmModel,
      messages: buildMemoryRefreshPrompt({
        companionProfile: input.companionProfile,
        recentCapture: input.recentCapture,
        summary,
        recentHistory: input.recentHistory,
      }),
      temperature: 0.2,
      max_tokens: MEMORY_MAX_TOKENS,
    }),
  );

  return {
    summary,
    sourceMessageCount: input.totalMessageCount,
    memoryNote,
  };
}

function stripThinkTags(text: string): string {
  return text.replace(/<think>[\s\S]*?<\/think>/gi, '').trim();
}

function sanitizeDerivedText(raw: string): string {
  return raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .filter((line) => !RESTRICTED_KEYWORDS.some((keyword) => line.includes(keyword)))
    .join("\n")
    .trim();
}
