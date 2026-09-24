import { Hono } from "hono";
import { cors } from "hono/cors";
import { AppEnv } from "./bindings";
import { injectContainer, jwtAuth } from "./middleware/auth";
import authRoutes from "./routes/auth.routes";
import verifyRoutes from "./routes/verify.routes";
import batchRoutes from "./routes/batch.routes";
import reportRoutes from "./routes/report.routes";
import statsRoutes from "./routes/stats.routes";
import reviewRoutes from "./routes/review.routes";
import wordRoutes from "./routes/word.routes";
import staticRoutes from "./routes/static.routes";

export function buildApp() {
  const app = new Hono<AppEnv>();

  app.use("*", cors());
  app.use("*", injectContainer);
  app.use("/api/*", jwtAuth);

  // Public auth flow.
  app.route("/", authRoutes);

  // Authenticated API (guarded by jwtAuth above).
  app.route("/", verifyRoutes);
  app.route("/", batchRoutes);
  app.route("/", reportRoutes);
  app.route("/", statsRoutes);
  app.route("/", reviewRoutes);
  app.route("/", wordRoutes);

  // SPA static fallback (must be last).
  app.route("/", staticRoutes);

  return app;
}
