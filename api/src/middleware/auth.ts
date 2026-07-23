import { Context, MiddlewareHandler } from "hono";
import { HTTPException } from "hono/http-exception";
import { jwt } from "hono/jwt";
import { AppEnv } from "../bindings";
import { createContainer } from "../container";
import { isAdmin } from "../lib/utils";
import { UserEntity } from "../db/repositories/users.repo";

/** Build the per-request container and attach it to the context. */
export const injectContainer: MiddlewareHandler<AppEnv> = async (c, next) => {
  c.set("container", createContainer(c.env));
  await next();
};

/** JWT auth for /api/* routes. */
export const jwtAuth: MiddlewareHandler<AppEnv> = (c, next) => {
  const middleware = jwt({ secret: c.env.JWT_SECRET_KEY, alg: "HS256" });
  return middleware(c, next);
};

/** Resolve the authenticated user from the JWT payload, or 404. */
export async function getUser(c: Context<AppEnv>): Promise<UserEntity> {
  const payload = c.get("jwtPayload");
  const user = await c.get("container").repos.users.findByEmail(payload.email);
  if (!user) {
    throw new HTTPException(404, { message: "user not found" });
  }
  return user;
}

/** Resolve the authenticated user and require admin, or 403. */
export async function getAdminUser(c: Context<AppEnv>): Promise<UserEntity> {
  const user = await getUser(c);
  if (!isAdmin(user.username, c.env)) {
    throw new HTTPException(403, { message: "not allowed" });
  }
  return user;
}
