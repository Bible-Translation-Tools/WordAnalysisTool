import { createDb, Database } from "./db/client";
import { createRepositories, Repositories } from "./db/repositories";
import AiClient from "./ai/client";
import {
  createIngestionService,
  IngestionService,
} from "./services/ingestion.service";
import {
  createAiProcessor,
  AiProcessor,
} from "./services/ai-processing.service";

/**
 * Application container: the drizzle client, repositories and services are
 * built once here. Both the HTTP path (via middleware) and the cron path use
 * `createContainer` so there is a single construction site.
 */
export type Container = {
  env: CloudflareBindings;
  db: Database;
  repos: Repositories;
  ai: AiClient;
  services: {
    ingestion: IngestionService;
    aiProcessor: AiProcessor;
  };
};

export function createContainer(env: CloudflareBindings): Container {
  const db = createDb(env);
  const repos = createRepositories(db);
  const ai = new AiClient(env);

  return {
    env,
    db,
    repos,
    ai,
    services: {
      ingestion: createIngestionService(repos),
      aiProcessor: createAiProcessor(repos, ai),
    },
  };
}
