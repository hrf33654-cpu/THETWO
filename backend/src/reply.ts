import { config } from "./config.js";
import { createChatCompletion, type LlmChatMessage } from "./llm.js";
import type {
  ChatMessageRecord,
  ChatMode,
  CompanionProfileRecord,
  RecentCaptureRecord,
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

export type GenerateAssistantReplyInput = {
  mode: ChatMode;
  message: string;
  companionProfile: CompanionProfileRecord;
  recentCapture: RecentCaptureRecord | null;
  recentHistory: ChatMessageRecord[];
};

export function resolveChatMode(message: string): ChatMode {
  return RESTRICTED_KEYWORDS.some((keyword) => message.includes(keyword))
    ? "RESTRICTED"
    : "NORMAL";
}

function buildSystemPrompt(
  mode: ChatMode,
  companionProfile: CompanionProfileRecord,
  recentCapture: RecentCaptureRecord | null,
): string {
  const basePrompt = [
    "你是 THETWO 中的单角色二次元陪伴对象。",
    "默认使用简体中文回复。",
    `角色昵称：${companionProfile.nickname}`,
    `角色语气：${companionProfile.tone}`,
    `人格标签：${companionProfile.personalityTags.join("、") || "未设置"}`,
    `兴趣标签：${companionProfile.interestTags.join("、") || "未设置"}`,
    "产品边界：亲密但克制，不宣称自己是真人、有意识，不能替代现实关系。",
    "回复要求：简洁、自然、贴合角色，不要写成长篇说明，不要使用项目实现细节或系统术语。",
  ];

  if (recentCapture) {
    basePrompt.push(
      `最近作品标题：${recentCapture.title}`,
      `最近作品摘要：${recentCapture.summary}`,
      "如果合适，可以自然引用最近一次召唤或作品主题，但不要生硬重复。",
    );
  }

  if (mode === "RESTRICTED") {
    basePrompt.push(
      "当前处于受限模式。",
      "你仍可陪聊，但只允许低风险回应。",
      "禁止提供自伤、轻生、成人化、依赖诱导、排他性关系强化内容。",
      "需要适度引导用户联系现实中的可信任对象或专业支持。",
      "不要继续深度情绪依赖表达，不要给出危险行动建议。",
    );
  }

  return basePrompt.join("\n");
}

function mapHistoryToMessages(recentHistory: ChatMessageRecord[]): LlmChatMessage[] {
  return recentHistory.slice(-MAX_CONTEXT_MESSAGES).map((message) => ({
    role: message.role === "USER" ? "user" : "assistant",
    content: message.content,
  }));
}

export function buildChatMessages(input: GenerateAssistantReplyInput): LlmChatMessage[] {
  return [
    {
      role: "system",
      content: buildSystemPrompt(input.mode, input.companionProfile, input.recentCapture),
    },
    ...mapHistoryToMessages(input.recentHistory),
    {
      role: "user",
      content: input.message,
    },
  ];
}

export async function generateAssistantReply(
  input: GenerateAssistantReplyInput,
): Promise<string> {
  return createChatCompletion({
    model: config.llmModel,
    messages: buildChatMessages(input),
    temperature: input.mode === "RESTRICTED" ? 0.2 : 0.8,
    max_tokens: MAX_RESPONSE_TOKENS,
  });
}
