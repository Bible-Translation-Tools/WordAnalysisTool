import type { JwtVariables } from "hono/jwt";
import { Container } from "./container";

export type AppVariables = JwtVariables & {
  container: Container;
};

export type AppEnv = {
  Bindings: CloudflareBindings;
  Variables: AppVariables;
};
