import { Hono } from "hono";
import { AppEnv } from "../bindings";

const router = new Hono<AppEnv>();

// Just to verify the token is not expired.
router.get("/api/verify", (c) => c.json(true));

export default router;
