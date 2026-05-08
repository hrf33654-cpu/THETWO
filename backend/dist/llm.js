import { config } from "./config.js";
import { badGateway, gatewayTimeout, serviceUnavailable } from "./errors.js";
function normalizeBaseUrl(baseUrl) {
    return baseUrl.replace(/\/+$/, "");
}
function resolveChatCompletionsUrl(baseUrl) {
    return `${normalizeBaseUrl(baseUrl)}/chat/completions`;
}
function extractAssistantContent(response) {
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
export async function createChatCompletion(request) {
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
            let upstreamMessage = raw.trim();
            try {
                const parsed = JSON.parse(raw);
                upstreamMessage = parsed.error?.message?.trim() || upstreamMessage;
            }
            catch {
                // Keep raw upstream body when JSON parsing fails.
            }
            badGateway("LLM_UPSTREAM_FAILED", upstreamMessage || `模型服务返回状态 ${response.status}`);
        }
        const payload = (await response.json());
        const content = extractAssistantContent(payload);
        if (!content) {
            badGateway("LLM_EMPTY_REPLY", "模型未返回有效回复");
        }
        return content;
    }
    catch (error) {
        if (error instanceof Error && error.name === "AbortError") {
            gatewayTimeout("LLM_TIMEOUT", "模型响应超时");
        }
        throw error;
    }
    finally {
        clearTimeout(timeoutHandle);
    }
}
