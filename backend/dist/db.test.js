import test from "node:test";
import assert from "node:assert/strict";
import { config } from "./config.js";
import { db, createLoginCode, recordDevCode, verifyCodeAndCreateSession } from "./db.js";
test("createLoginCode returns 6 digits", () => {
    const code = createLoginCode();
    assert.match(code, /^\d{6}$/);
});
test("verifyCodeAndCreateSession rejects expired auth code", () => {
    const originalTtl = config.authCodeTtlMinutes;
    const email = `expired-${Date.now()}@example.com`;
    try {
        config.authCodeTtlMinutes = 1;
        recordDevCode(email, "654321");
        db.prepare(`
      UPDATE auth_codes
      SET created_at = ?
      WHERE email = ?
    `).run(new Date(Date.now() - 2 * 60 * 1000).toISOString(), email);
        assert.throws(() => verifyCodeAndCreateSession(email, "654321"), (error) => {
            assert.equal(error.errorCode, "INVALID_CODE");
            return true;
        });
    }
    finally {
        db.prepare(`DELETE FROM auth_codes WHERE email = ?`).run(email);
        config.authCodeTtlMinutes = originalTtl;
    }
});
