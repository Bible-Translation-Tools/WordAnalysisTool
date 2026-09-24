import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { AppEnv } from "../bindings";
import {
  computeBatchProgress,
  deriveStatus,
} from "../services/stats.service";
import { parseModels } from "../lib/utils";
import { Batch, BatchDetails, BatchError, PublicUser } from "../types";

const router = new Hono<AppEnv>();

router.get("/api/stats/:ietf_code/:resource_type", async (c) => {
  const { db, repos } = c.get("container");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");

    const resourceId = await repos.resources.getId(ietf_code, resource_type);
    const dbBatch = resourceId
      ? await repos.batches.findByResourceIdWithUser(resourceId)
      : null;

    if (!dbBatch) {
      throw new HTTPException(404, { message: "batch not found" });
    }

    const progress = await computeBatchProgress(db, dbBatch.id);
    const status = deriveStatus(progress, {
      ingesting: dbBatch.ingesting,
      pending: dbBatch.pending,
    });

    let batchError: BatchError | null = null;
    if (dbBatch.error) {
      try {
        batchError = JSON.parse(dbBatch.error);
      } catch {
        batchError = {
          message: dbBatch.error || "Unknown error occurred.",
          prompt: null,
          model: null,
          response: null,
        };
      }
    }

    const creator: PublicUser = { username: dbBatch.user.username };

    const details: BatchDetails = {
      status,
      error: batchError,
      progress,
      output: [],
    };

    const reference = dbBatch.refResourceId
      ? await repos.resources.getRef(dbBatch.refResourceId)
      : null;

    const batch: Batch = {
      id: dbBatch.id,
      ietf_code,
      resource_type,
      details,
      creator,
      apostrophe_is_separator: dbBatch.apostropheIsSeparator,
      models: parseModels(dbBatch.models),
      reference: reference
        ? {
            ietf: reference.ietf,
            resource_type: reference.resourceType,
            name: reference.name,
          }
        : null,
    };

    return c.json(batch);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching stats: ${error.message || error}`,
    });
  }
});

export default router;
