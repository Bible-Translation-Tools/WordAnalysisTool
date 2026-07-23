import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { v4 as uuid4 } from "uuid";
import { AppEnv } from "../bindings";
import { getAdminUser } from "../middleware/auth";
import { getLanguageInfo } from "../integrations/biel";
import { emptyProgress } from "../services/stats.service";
import {
  DEFAULT_REF_IETF,
  DEFAULT_REF_RESOURCE_TYPE,
} from "../config/constants";
import {
  Batch,
  BatchDetails,
  BatchStatus,
  PublicUser,
} from "../types";

const router = new Hono<AppEnv>();

router.post("/api/batch/:ietf_code/:resource_type", async (c) => {
  const { repos } = c.get("container");

  const ietf_code = c.req.param("ietf_code");
  const resource_type = c.req.param("resource_type");
  const body = await c.req.blob();

  if (body.type !== "application/octet-stream") {
    throw new HTTPException(403, { message: "invalid batch file" });
  }

  try {
    const text = await new Response(body).text();
    const json = JSON.parse(text);
    const models: string[] = json.models || [];
    const apostropheIsSeparator: boolean = json.apostropheIsSeparator ?? true;
    const refIetf: string = json.refIetf || DEFAULT_REF_IETF;
    const refResourceType: string =
      json.refResourceType || DEFAULT_REF_RESOURCE_TYPE;

    if (models.length === 0) {
      throw new HTTPException(404, { message: "no models provided" });
    }

    const user = await getAdminUser(c);
    const creator: PublicUser = { username: user.username };

    // Resolve the language (reuse an existing row, else create from BIEL).
    const languageInfo = await getLanguageInfo(ietf_code);
    if (!languageInfo) {
      throw new HTTPException(404, { message: "language not found" });
    }
    const languageId = await repos.languages.upsert(languageInfo);
    const resourceId = await repos.resources.upsert(resource_type, languageId);

    // Resolve the per-batch reference translation to a resource id (verses are
    // downloaded later during ingestion).
    const refLanguageInfo = await getLanguageInfo(refIetf);
    if (!refLanguageInfo) {
      throw new HTTPException(404, {
        message: `reference language (${refIetf}) not found`,
      });
    }
    const refLanguageId = await repos.languages.upsert(refLanguageInfo);
    const refResourceId = await repos.resources.upsert(
      refResourceType,
      refLanguageId,
    );

    const dbBatch = await repos.batches.findByResourceId(resourceId);
    let batchId = dbBatch?.id;

    const ingestFields = {
      languageId,
      resourceId,
      refResourceId,
      models: JSON.stringify(models),
      apostropheIsSeparator,
      ingesting: true,
      pending: false,
      error: null,
    };

    if (!batchId) {
      batchId = uuid4();
      await repos.batches.create({ id: batchId, userId: user.id, ...ingestFields });
    } else {
      if (dbBatch?.pending || dbBatch?.ingesting) {
        throw new HTTPException(403, { message: "batch in progress" });
      }
      await repos.batches.updateById(batchId, {
        ...ingestFields,
        updatedAt: new Date(),
      });
    }

    const details: BatchDetails = {
      status: BatchStatus.QUEUED,
      error: null,
      output: [],
      progress: emptyProgress,
    };

    const batch: Batch = {
      id: batchId,
      ietf_code,
      resource_type,
      details,
      creator,
    };

    return c.json(batch);
  } catch (error: any) {
    throw new HTTPException(400, {
      message: `${error.code}: error creating batch: ${error.message || error}`,
    });
  }
});

router.delete("/api/batch/pause/:batch_id", async (c) => {
  const { repos } = c.get("container");

  try {
    const batchId = c.req.param("batch_id");
    await getAdminUser(c);

    const paused = await repos.batches.pause(batchId);
    if (paused) {
      // also delete incomplete models
      await repos.models.deleteIncomplete();
    }
    return c.json(paused);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error pausing batch: ${error.message || error}`,
    });
  }
});

router.delete("/api/batch/delete/:batch_id", async (c) => {
  const { repos } = c.get("container");

  try {
    const batchId = c.req.param("batch_id");
    await getAdminUser(c);

    const deleted = await repos.batches.deleteById(batchId);
    return c.json(deleted);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error deleting batch: ${error.message || error}`,
    });
  }
});

router.get("/api/batch/recent", async (c) => {
  const { repos } = c.get("container");

  try {
    const dbBatches = await repos.batches.listRecent();

    const details: BatchDetails = {
      status: BatchStatus.COMPLETE,
      error: null,
      progress: emptyProgress,
      output: [],
    };

    const batches = dbBatches.map((item) => ({
      id: item.id,
      ietf_code: item.ietfCode,
      resource_type: item.resourceType,
      details,
      creator: { username: item.user.username },
    }));

    return c.json(batches);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching batch: ${error.message || error}`,
    });
  }
});

export default router;
