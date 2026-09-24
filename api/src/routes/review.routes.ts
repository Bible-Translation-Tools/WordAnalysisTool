import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { AppEnv } from "../bindings";
import { getAdminUser, getUser } from "../middleware/auth";
import { sampleReviewWords } from "../services/review.service";
import {
  Batch,
  BatchDetails,
  BatchStatus,
  PublicUser,
} from "../types";

const router = new Hono<AppEnv>();

router.get("/api/review/:ietf_code/:resource_type", async (c) => {
  const { db, repos } = c.get("container");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");

    const user = await getUser(c);

    const resourceId = await repos.resources.getId(ietf_code, resource_type);
    const dbBatch = resourceId
      ? await repos.batches.findByResourceIdWithUser(resourceId)
      : null;

    if (!dbBatch) {
      throw new HTTPException(404, { message: "batch not found" });
    }
    if (dbBatch.pending) {
      throw new HTTPException(400, { message: "batch is still processing" });
    }

    const { output, progress } = await sampleReviewWords(
      db,
      dbBatch.id,
      user.id,
    );

    const details: BatchDetails = {
      status: BatchStatus.COMPLETE,
      error: null,
      progress,
      output,
    };

    const creator: PublicUser = { username: dbBatch.user.username };

    const batch: Batch = {
      id: dbBatch.id,
      ietf_code,
      resource_type,
      details,
      creator,
    };

    return c.json(batch);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching words: ${error.message || error}`,
    });
  }
});

router.put("/api/review/reset/:batch_id", async (c) => {
  const { repos } = c.get("container");

  try {
    const batchId = c.req.param("batch_id");
    await getAdminUser(c);

    const reset = await repos.reviews.deleteByBatch(batchId);
    return c.json(reset);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error resetting review: ${
        error.message || error
      }`,
    });
  }
});

export default router;
