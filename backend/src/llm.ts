import { config } from "./config.js";
import { badGateway, gatewayTimeout, serviceUnavailable } from "./errors.js";

export type LlmChatMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

type ChatCompletionRequest = {
  model: string;
  messages: LlmChatMessage[];
  temperature: number;
  max_tokens: number;
};

type ChatCompletionResponse = {
  choices?: Array<{
    message?: {
      content?: string | Array<{ type?: string; text?: string }>;
    };
  }>;
  error?: {
    code?: string;
    type?: string;
    message?: string;
  };
};

type LlmFailure = {
  errorCode: string;
  message: string;
  details: Record<string, unknown>;
};

function normalizeBaseUrl(baseUrl: string): string {
  return baseUrl.replace(/\/+$/, "");
}

function resolveChatCompletionsUrl(baseUrl: string): string {
  return `${normalizeBaseUrl(baseUrl)}/chat/completions`;
}

function extractAssistantContent(response: ChatCompletionResponse): string {
  const content = response.choices?.[0]?.message?.content;
  if (typeof content === "string") {
    return content.trim();
  }
  if (Array.isArray(content)) {
    return content
      .map((part) => (part.type === "text" || !part.type ? (part.text ?? "") : ""))
      .join("")
      .trim();
  }
  return "";
}

function classifyUpstreamFailure(input: {
  status: number;
  statusText: string;
  rawBody: string;
  parsedBody: ChatCompletionResponse | null;
}): LlmFailure {
  const upstreamCode = input.parsedBody?.error?.code?.trim() || null;
  const upstreamType = input.parsedBody?.error?.type?.trim() || null;
  const upstreamMessage = input.parsedBody?.error?.message?.trim() || input.rawBody.trim();
  const normalized = [upstreamCode, upstreamType, upstreamMessage]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();

  const details = {
    upstreamStatus: input.status,
    upstreamStatusText: input.statusText,
    upstreamCode,
    upstreamType,
  };

  if (input.status === 401 || input.status === 403 || normalized.includes("invalid api key") || normalized.includes("unauthorized")) {
    return {
      errorCode: "LLM_AUTH_FAILED",
      message: "模型服务鉴权失败，请检查 LLM_API_KEY、供应商控制台权限与套餐绑定。",
      details,
    };
  }

  if (
    input.status === 429 ||
    normalized.includes("usage limit exceeded") ||
    normalized.includes("rate limit") ||
    normalized.includes("quota") ||
    normalized.includes("insufficient_quota") ||
    normalized.includes("credit")
  ) {
    return {
      errorCode: "LLM_RATE_LIMITED",
      message: "模型服务当前返回额度或频率限制，请检查供应商套餐、账户余额或稍后重试。",
      details,
    };
  }

  if (
    input.status === 404 ||
    normalized.includes("model not found") ||
    normalized.includes("no such model") ||
    normalized.includes("does not exist")
  ) {
    return {
      errorCode: "LLM_MODEL_NOT_FOUND",
      message: "模型服务未找到当前配置的模型，请检查 LLM_MODEL 与供应商接口兼容性。",
      details,
    };
  }

  if (input.status >= 500) {
    return {
      errorCode: "LLM_UPSTREAM_UNAVAILABLE",
      message: "模型服务暂时不可用，请稍后重试并查看上游服务状态。",
      details,
    };
  }

  return {
    errorCode: "LLM_UPSTREAM_FAILED",
    message: upstreamMessage || `模型服务返回状态 ${input.status}`,
    details,
  };
}

export async function createChatCompletion(request: ChatCompletionRequest): Promise<string> {
  if (!config.llmBaseUrl || !config.llmApiKey || !config.llmModel) {
    serviceUnavailable("LLM_NOT_CONFIGURED", "模型服务尚未配置完成");
  }

  const controller = new AbortController();
  const timeoutHandle = setTimeout(() => controller.abort(), config.llmTimeoutMs);

  try {
    const response = await fetch(resolveChatCompletionsUrl(config.llmBaseUrl), {
      method: "POST",
      headers: {
        Authorization: `Bearer ${config.llmApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: request.model,
        messages: request.messages,
        temperature: request.temperature,
        max_tokens: request.max_tokens,
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      const raw = await response.text();
      let parsed: ChatCompletionResponse | null = null;
      try {
        parsed = JSON.parse(raw) as ChatCompletionResponse;
      } catch {
        parsed = null;
      }
      const failure = classifyUpstreamFailure({
        status: response.status,
        statusText: response.statusText,
        rawBody: raw,
        parsedBody: parsed,
      });
      badGateway(failure.errorCode, failure.message, failure.details);
    }

    const payload = (await response.json()) as ChatCompletionResponse;
    const content = extractAssistantContent(payload);
    if (!content) {
      badGateway("LLM_EMPTY_REPLY", "模型未返回有效回复");
    }

    return content;
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") {
      gatewayTimeout("LLM_TIMEOUT", "模型响应超时");
    }
    if (error instanceof TypeError) {
      serviceUnavailable(
        "LLM_NETWORK_ERROR",
        "模型服务网络请求失败，请检查 LLM_BASE_URL、DNS、代理或服务器网络连通性。",
      );
    }
    throw error;
  } finally {
    clearTimeout(timeoutHandle);
  }
}
