import test from "node:test";
import assert from "node:assert/strict";
import net from "node:net";
import { config } from "./config.js";
import { sendLoginCodeEmail } from "./email.js";

test("sendLoginCodeEmail returns DEV mode when emailMode is dev", async () => {
  const originalEmailMode = config.emailMode;
  try {
    (config as Record<string, unknown>).emailMode = "dev";
    const result = await sendLoginCodeEmail({ email: "test@example.com", code: "654321" });
    assert.equal(result.deliveryMode, "DEV");
  } finally {
    (config as Record<string, unknown>).emailMode = originalEmailMode;
  }
});

test("sendLoginCodeEmail throws EMAIL_NOT_CONFIGURED when SMTP config is incomplete", async () => {
  const originalEmailMode = config.emailMode;
  const originalSmtpHost = config.smtpHost;
  const originalSmtpUser = config.smtpUser;
  const originalSmtpPass = config.smtpPass;
  const originalSmtpFrom = config.smtpFrom;
  try {
    (config as Record<string, unknown>).emailMode = "smtp";
    (config as Record<string, unknown>).smtpHost = "";
    (config as Record<string, unknown>).smtpUser = "";
    (config as Record<string, unknown>).smtpPass = "";
    (config as Record<string, unknown>).smtpFrom = "";

    await assert.rejects(
      () => sendLoginCodeEmail({ email: "test@example.com", code: "654321" }),
      { errorCode: "EMAIL_NOT_CONFIGURED" },
    );
  } finally {
    (config as Record<string, unknown>).emailMode = originalEmailMode;
    (config as Record<string, unknown>).smtpHost = originalSmtpHost;
    (config as Record<string, unknown>).smtpUser = originalSmtpUser;
    (config as Record<string, unknown>).smtpPass = originalSmtpPass;
    (config as Record<string, unknown>).smtpFrom = originalSmtpFrom;
  }
});

test("sendLoginCodeEmail throws EMAIL_TIMEOUT when SMTP connect stalls", async () => {
  const original = {
    emailMode: config.emailMode,
    smtpHost: config.smtpHost,
    smtpPort: config.smtpPort,
    smtpTimeoutMs: config.smtpTimeoutMs,
    smtpUser: config.smtpUser,
    smtpPass: config.smtpPass,
    smtpFrom: config.smtpFrom,
  };
  const server = net.createServer(() => {
    // Intentionally do nothing so the SMTP handshake stalls.
  });

  await new Promise<void>((resolve, reject) => {
    server.listen(0, "127.0.0.1", () => resolve());
    server.once("error", reject);
  });

  const address = server.address();
  if (!address || typeof address === "string") {
    throw new Error("无法启动测试 SMTP 服务器");
  }

  try {
    (config as Record<string, unknown>).emailMode = "smtp";
    (config as Record<string, unknown>).smtpHost = "127.0.0.1";
    (config as Record<string, unknown>).smtpPort = address.port;
    (config as Record<string, unknown>).smtpTimeoutMs = 50;
    (config as Record<string, unknown>).smtpUser = "user@example.com";
    (config as Record<string, unknown>).smtpPass = "password";
    (config as Record<string, unknown>).smtpFrom = "user@example.com";

    await assert.rejects(
      () => sendLoginCodeEmail({ email: "test@example.com", code: "654321" }),
      { errorCode: "EMAIL_TIMEOUT" },
    );
  } finally {
    (config as Record<string, unknown>).emailMode = original.emailMode;
    (config as Record<string, unknown>).smtpHost = original.smtpHost;
    (config as Record<string, unknown>).smtpPort = original.smtpPort;
    (config as Record<string, unknown>).smtpTimeoutMs = original.smtpTimeoutMs;
    (config as Record<string, unknown>).smtpUser = original.smtpUser;
    (config as Record<string, unknown>).smtpPass = original.smtpPass;
    (config as Record<string, unknown>).smtpFrom = original.smtpFrom;
    await new Promise<void>((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
  }
});
