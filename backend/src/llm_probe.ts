import { config } from "./config.js";

function normalizeBaseUrl(baseUrl: string): string {
  return baseUrl.replace(/\/+$/, "");
}

async function main() {
  const requestUrl = `${normalizeBaseUrl(config.llmBaseUrl)}/chat/completions`;

  console.log("=== THETWO LLM Probe ===");
  console.log(`LLM_BASE_URL: ${config.llmBaseUrl || "<empty>"}`);
  console.log(`LLM_MODEL: ${config.llmModel || "<empty>"}`);
  console.log(`LLM_API_KEY present: ${config.llmApiKey ? "yes" : "no"}`);
  console.log(`Resolved URL: ${requestUrl}`);

  if (!config.llmBaseUrl || !config.llmApiKey || !config.llmModel) {
    console.error("Missing one or more required env vars: LLM_BASE_URL / LLM_API_KEY / LLM_MODEL");
    process.exitCode = 1;
    return;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.llmTimeoutMs);

  try {
    const response = await fetch(requestUrl, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${config.llmApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: config.llmModel,
        messages: [
          { role: "system", content: "Reply with exactly: ok" },
          { role: "user", content: "test" },
        ],
        temperature: 0,
        max_tokens: 8,
      }),
      signal: controller.signal,
    });

    const raw = await response.text();
    console.log(`HTTP ${response.status} ${response.statusText}`);
    console.log(`Probe diagnosis: ${diagnoseProbe(response.status, raw)}`);
    console.log("Raw response:");
    console.log(raw);
  } catch (error) {
    console.error("Probe failed:");
    console.error(error);
    process.exitCode = 1;
  } finally {
    clearTimeout(timeout);
  }
}

function diagnoseProbe(status: number, rawBody: string): string {
  const normalized = rawBody.toLowerCase();
  if (status === 200) return "success";
  if (
    status === 401 ||
    status === 403 ||
    normalized.includes("invalid api key") ||
    normalized.includes("unauthorized")
  ) {
    return "auth_failed";
  }
  if (
    status === 429 ||
    normalized.includes("usage limit exceeded") ||
    normalized.includes("rate limit") ||
    normalized.includes("quota") ||
    normalized.includes("insufficient_quota")
  ) {
    return "rate_limited_or_quota";
  }
  if (
    status === 404 ||
    normalized.includes("model not found") ||
    normalized.includes("no such model") ||
    normalized.includes("does not exist")
  ) {
    return "model_not_found";
  }
  if (status >= 500) {
    return "upstream_unavailable";
  }
  return "unknown_failure";
}

void main();
