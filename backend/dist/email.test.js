import test from "node:test";
import assert from "node:assert/strict";
import net from "node:net";
import { config } from "./config.js";
import { sendLoginCodeEmail } from "./email.js";
test("sendLoginCodeEmail returns DEV mode when emailMode is dev", async () => {
    const originalEmailMode = config.emailMode;
    try {
        config.emailMode = "dev";
        const result = await sendLoginCodeEmail({ email: "test@example.com", code: "654321" });
        assert.equal(result.deliveryMode, "DEV");
    }
    finally {
        config.emailMode = originalEmailMode;
    }
});
test("sendLoginCodeEmail throws EMAIL_NOT_CONFIGURED when SMTP config is incomplete", async () => {
    const originalEmailMode = config.emailMode;
    const originalSmtpHost = config.smtpHost;
    const originalSmtpUser = config.smtpUser;
    const originalSmtpPass = config.smtpPass;
    const originalSmtpFrom = config.smtpFrom;
    try {
        config.emailMode = "smtp";
        config.smtpHost = "";
        config.smtpUser = "";
        config.smtpPass = "";
        config.smtpFrom = "";
        await assert.rejects(() => sendLoginCodeEmail({ email: "test@example.com", code: "654321" }), { errorCode: "EMAIL_NOT_CONFIGURED" });
    }
    finally {
        config.emailMode = originalEmailMode;
        config.smtpHost = originalSmtpHost;
        config.smtpUser = originalSmtpUser;
        config.smtpPass = originalSmtpPass;
        config.smtpFrom = originalSmtpFrom;
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
    await new Promise((resolve, reject) => {
        server.listen(0, "127.0.0.1", () => resolve());
        server.once("error", reject);
    });
    const address = server.address();
    if (!address || typeof address === "string") {
        throw new Error("无法启动测试 SMTP 服务器");
    }
    try {
        config.emailMode = "smtp";
        config.smtpHost = "127.0.0.1";
        config.smtpPort = address.port;
        config.smtpTimeoutMs = 50;
        config.smtpUser = "user@example.com";
        config.smtpPass = "password";
        config.smtpFrom = "user@example.com";
        await assert.rejects(() => sendLoginCodeEmail({ email: "test@example.com", code: "654321" }), { errorCode: "EMAIL_TIMEOUT" });
    }
    finally {
        config.emailMode = original.emailMode;
        config.smtpHost = original.smtpHost;
        config.smtpPort = original.smtpPort;
        config.smtpTimeoutMs = original.smtpTimeoutMs;
        config.smtpUser = original.smtpUser;
        config.smtpPass = original.smtpPass;
        config.smtpFrom = original.smtpFrom;
        await new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
    }
});
