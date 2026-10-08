import { createRepositories, Repositories } from "./db";
import { D1UsageMeter, globalD1Meter } from "./db/sqlite/metrics";
import AiClient, { AiChat } from "./ai/client";
import { MockAiClient } from "./ai/mock";
import { isDev } from "./lib/utils";
import {
  createIngestionService,
  IngestionService,
} from "./services/ingestion.service";
import {
  createAiProcessor,
  AiProcessor,
} from "./services/ai-processing.service";

/**
 * Application container: the repositories (over whichever database driver
 * `DB_DRIVER` selects) and services are built once here. Both the HTTP path
 * (via middleware) and the cron path use `createContainer` so there is a
 * single construction site.
 */
export type Container = {
  env: CloudflareBindings;
  repos: Repositories;
  /** D1 rows read/written meter; set only in DEV on the d1 driver. */
  usage?: D1UsageMeter;
  ai: AiChat;
  services: {
    ingestion: IngestionService;
    aiProcessor: AiProcessor;
  };
};

export function createContainer(env: CloudflareBindings): Container {
  const dev = isDev(env);
  // In DEV, meter D1 usage so each tick/request can log what it cost.
  const usage = dev && env.DB_DRIVER === "d1" ? globalD1Meter : undefined;
  const repos = createRepositories(env, { d1Meter: usage });
  // ENVIRONMENT=DEV swaps the AI providers for a deterministic mock (no API calls).
  const ai: AiChat = dev ? new MockAiClient() : new AiClient(env);

  return {
    env,
    repos,
    usage,
    ai,
    services: {
      ingestion: createIngestionService(repos),
      aiProcessor: createAiProcessor(repos, ai),
    },
  };
}
