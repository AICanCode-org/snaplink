// Configuration from environment variables (12-factor). Same names in every track.
import { fileURLToPath } from "node:url";
import path from "node:path";

export interface Settings {
  databaseUrl: string;
  redisUrl: string | null;
  publicBaseUrl: string;
  port: number;
  migrationsDir: string;
  insightsProvider: "template" | "llm";
  llmBaseUrl: string;
  llmApiKey: string | null;
  llmModel: string;
  llmTimeoutMs: number;
}

const here = path.dirname(fileURLToPath(import.meta.url));
// tracks/typescript/src -> <repo>/spec/db/migrations (only used outside Docker)
const defaultMigrations = path.resolve(here, "..", "..", "..", "spec", "db", "migrations");

export function loadSettings(env: NodeJS.ProcessEnv = process.env): Settings {
  return {
    databaseUrl: env.DATABASE_URL ?? "postgresql://snaplink:snaplink@localhost:5432/snaplink",
    redisUrl: env.REDIS_URL || null,
    publicBaseUrl: (env.PUBLIC_BASE_URL ?? "http://localhost:8080").replace(/\/+$/, ""),
    port: Number(env.PORT ?? 8080),
    migrationsDir: env.MIGRATIONS_DIR || defaultMigrations,
    insightsProvider: (env.INSIGHTS_PROVIDER ?? "template").toLowerCase() === "llm" ? "llm" : "template",
    llmBaseUrl: (env.LLM_BASE_URL ?? "http://localhost:11434/v1").replace(/\/+$/, ""),
    llmApiKey: env.LLM_API_KEY || null,
    llmModel: env.LLM_MODEL ?? "llama3.2:1b",
    llmTimeoutMs: Number(env.LLM_TIMEOUT_MS ?? 5000),
  };
}
