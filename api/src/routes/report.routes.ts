import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { stream } from "hono/streaming";
import { AppEnv } from "../bindings";
import { REPORT_HEADER, toReportRow } from "../services/report.service";

const router = new Hono<AppEnv>();

router.get("/api/report/:ietf_code/:resource_type", async (c) => {
  const { repos } = c.get("container");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");

    const resourceId = await repos.resources.getId(ietf_code, resource_type);
    const dbBatch = resourceId
      ? await repos.batches.findByResourceId(resourceId)
      : null;

    if (!dbBatch) {
      throw new HTTPException(404, { message: "batch not found" });
    }

    const words = await repos.words.findForReport(dbBatch.id);

    c.header("Content-Type", "text/csv");
    c.header("Content-Disposition", 'attachment; filename="report.csv"');

    return stream(c, async (s) => {
      await s.write(REPORT_HEADER);
      for (const word of words) {
        const row = toReportRow(word);
        if (row !== null) await s.write(`${row}\n`);
      }
    });
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching report: ${
        error.message || error
      }`,
    });
  }
});

export default router;
