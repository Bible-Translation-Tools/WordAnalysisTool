import { buildApp } from "./app";
import { scheduledHandler } from "./scheduled";

const app = buildApp();

export default {
  fetch: app.fetch,
  scheduled: scheduledHandler,
};
