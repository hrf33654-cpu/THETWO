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
