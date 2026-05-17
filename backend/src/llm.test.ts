import test from "node:test";
import assert from "node:assert/strict";
import { config } from "./config.js";
import { ApiError } from "./errors.js";
import { createChatCompletion } from "./llm.js";

test("createChatCompletion fails fast when llm config is missing", async () => {
  const original = {
    llmBaseUrl: config.llmBaseUrl,
    llmApiKey: config.llmApiKey,
    llmModel: config.llmModel,
  };

  config.llmBaseUrl = "";
  config.llmApiKey = "";
  config.llmModel = "";

  try {
    await assert.rejects(
      () =>
        createChatCompletion({
          model: "unused",
          messages: [{ role: "system", content: "test" }],
          temperature: 0.8,
          max_tokens: 10,
        }),
      (error: unknown) => {
        assert.ok(error instanceof ApiError);
        assert.equal(error.errorCode, "LLM_NOT_CONFIGURED");
        assert.equal(error.statusCode, 503);
        return true;
      },
    );
  } finally {
    config.llmBaseUrl = original.llmBaseUrl;
    config.llmApiKey = original.llmApiKey;
    config.llmModel = original.llmModel;
  }
});

test("createChatCompletion classifies auth failures from upstream", async () => {
  const original = globalThis.fetch;
  const originalConfig = {
    llmBaseUrl: config.llmBaseUrl,
    llmApiKey: config.llmApiKey,
    llmModel: config.llmModel,
  };

  config.llmBaseUrl = "https://example.test/v1";
  config.llmApiKey = "bad-key";
  config.llmModel = "test-model";

  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        error: {
          code: "invalid_api_key",
          message: "Invalid API key",
        },
      }),
      {
        status: 401,
        statusText: "Unauthorized",
        headers: { "Content-Type": "application/json" },
      },
    );

  try {
    await assert.rejects(
      () =>
        createChatCompletion({
          model: "test-model",
          messages: [{ role: "system", content: "test" }],
          temperature: 0.2,
          max_tokens: 8,
        }),
      (error: unknown) => {
        assert.ok(error instanceof ApiError);
        assert.equal(error.errorCode, "LLM_AUTH_FAILED");
        assert.equal(error.statusCode, 502);
        assert.equal(error.details?.upstreamStatus, 401);
        return true;
      },
    );
  } finally {
    globalThis.fetch = original;
    config.llmBaseUrl = originalConfig.llmBaseUrl;
    config.llmApiKey = originalConfig.llmApiKey;
    config.llmModel = originalConfig.llmModel;
  }
});

test("createChatCompletion classifies rate limit failures from upstream", async () => {
  const original = globalThis.fetch;
  const originalConfig = {
    llmBaseUrl: config.llmBaseUrl,
    llmApiKey: config.llmApiKey,
    llmModel: config.llmModel,
  };

  config.llmBaseUrl = "https://example.test/v1";
  config.llmApiKey = "test-key";
  config.llmModel = "test-model";

  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        error: {
          code: "insufficient_quota",
          message: "usage limit exceeded",
        },
      }),
      {
        status: 429,
        statusText: "Too Many Requests",
        headers: { "Content-Type": "application/json" },
      },
    );

  try {
    await assert.rejects(
      () =>
        createChatCompletion({
          model: "test-model",
          messages: [{ role: "system", content: "test" }],
          temperature: 0.2,
          max_tokens: 8,
        }),
      (error: unknown) => {
        assert.ok(error instanceof ApiError);
        assert.equal(error.errorCode, "LLM_RATE_LIMITED");
        assert.equal(error.statusCode, 502);
        return true;
      },
    );
  } finally {
    globalThis.fetch = original;
    config.llmBaseUrl = originalConfig.llmBaseUrl;
    config.llmApiKey = originalConfig.llmApiKey;
    config.llmModel = originalConfig.llmModel;
  }
});

test("createChatCompletion classifies model not found failures from upstream", async () => {
  const original = globalThis.fetch;
  const originalConfig = {
    llmBaseUrl: config.llmBaseUrl,
    llmApiKey: config.llmApiKey,
    llmModel: config.llmModel,
  };

  config.llmBaseUrl = "https://example.test/v1";
  config.llmApiKey = "test-key";
  config.llmModel = "missing-model";

  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        error: {
          code: "model_not_found",
          message: "The model does not exist",
        },
      }),
      {
        status: 404,
        statusText: "Not Found",
        headers: { "Content-Type": "application/json" },
      },
    );

  try {
    await assert.rejects(
      () =>
        createChatCompletion({
          model: "missing-model",
          messages: [{ role: "system", content: "test" }],
          temperature: 0.2,
          max_tokens: 8,
        }),
      (error: unknown) => {
        assert.ok(error instanceof ApiError);
        assert.equal(error.errorCode, "LLM_MODEL_NOT_FOUND");
        assert.equal(error.statusCode, 502);
        return true;
      },
    );
  } finally {
    globalThis.fetch = original;
    config.llmBaseUrl = originalConfig.llmBaseUrl;
    config.llmApiKey = originalConfig.llmApiKey;
    config.llmModel = originalConfig.llmModel;
  }
});
