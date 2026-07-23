import { Hono } from "hono";
import { AppEnv } from "../bindings";

const router = new Hono<AppEnv>();

// Serve the SPA: fall back to index.html for client-side routes (404s).
router.get("*", async (c) => {
  const response = await c.env.ASSETS.fetch(c.req.raw);
  if (response.status === 404) {
    const indexRequest = new Request(new URL("/index.html", c.req.url), {
      method: "GET",
      headers: c.req.raw.headers,
    });
    return c.env.ASSETS.fetch(indexRequest);
  }
  return response;
});

export default router;
