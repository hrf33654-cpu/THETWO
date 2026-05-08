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
  llmBaseUrl: process.env.LLM_BASE_URL ?? "",
  llmApiKey: process.env.LLM_API_KEY ?? "",
  dataDir: path.resolve(__dirname, "../data"),
  dbPath: path.resolve(__dirname, "../data/thetwo-dev.sqlite"),
};
