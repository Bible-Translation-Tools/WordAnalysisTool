import { createContainer } from "../container";

/**
 * Cron entry point. Source ingestion takes priority over AI processing: handle
 * one ingesting batch per tick, otherwise process one chunk of the oldest
 * pending batch. Later ticks make forward progress.
 */
export async function scheduledHandler(
  _controller: ScheduledController,
  env: CloudflareBindings,
  _ctx: ExecutionContext,
): Promise<void> {
  try {
    const container = createContainer(env);

    const ingestBatch = await container.repos.batches.findIngesting();
    if (ingestBatch) {
      await container.services.ingestion.ingestSource(ingestBatch);
      return;
    }

    await container.services.aiProcessor.processPending();
  } catch (error) {
    console.error("cron error:", error);
  }
}
