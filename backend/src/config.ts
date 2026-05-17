import path from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const config = {
  port: Number(process.env.PORT ?? 8787),
  appEnv: process.env.APP_ENV ?? "development",
  devLoginCode: process.env.DEV_LOGIN_CODE ?? "123456",
  authCodeTtlMinutes: (() => {
    const parsed = Number(process.env.AUTH_CODE_TTL_MINUTES ?? 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 10;
  })(),
  emailMode: process.env.EMAIL_MODE ?? "dev",
  smtpHost: process.env.SMTP_HOST ?? "",
  smtpPort: (() => {
    const parsed = Number(process.env.SMTP_PORT ?? 587);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 587;
  })(),
  smtpTimeoutMs: (() => {
    const parsed = Number(process.env.SMTP_TIMEOUT_MS ?? 10000);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 10000;
  })(),
  smtpUser: process.env.SMTP_USER ?? "",
  smtpPass: process.env.SMTP_PASS ?? "",
  smtpFrom: process.env.SMTP_FROM ?? "",
  llmBaseUrl: process.env.LLM_BASE_URL ?? "",
  llmApiKey: process.env.LLM_API_KEY ?? "",
  llmModel: process.env.LLM_MODEL ?? "",
  llmTimeoutMs: (() => {
    const parsed = Number(process.env.LLM_TIMEOUT_MS ?? 12000);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 12000;
  })(),
  dataDir: path.resolve(__dirname, "../data"),
  dbPath: path.resolve(__dirname, "../data/thetwo-dev.sqlite"),
};
