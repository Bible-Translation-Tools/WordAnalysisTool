import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { sign } from "hono/jwt";
import { AppEnv } from "../bindings";
import { isAdmin } from "../lib/utils";

const router = new Hono<AppEnv>();

router.get("/auth/tokens/:state", async (c) => {
  const state = c.req.param("state");
  const { repos } = c.get("container");

  // Expire state after 30 minutes.
  const thirtyMinutesAgo = new Date(Date.now() - 30 * 60 * 1000);
  const user = await repos.users.findByFreshState(state, thirtyMinutesAgo);
  if (!user) {
    throw new HTTPException(400, { message: "wrong or expired state" });
  }

  await repos.users.clearState(state);

  const payload = {
    username: user.username,
    email: user.email,
    admin: isAdmin(user.username, c.env),
    exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 7, // 7 days
  };

  return c.json({ accessToken: await sign(payload, c.env.JWT_SECRET_KEY) });
});

router.get("/auth/callback", async (c) => {
  const { repos } = c.get("container");

  try {
    const params = { code: c.req.query("code"), state: c.req.query("state") };
    if (params.code === undefined || params.state === undefined) {
      throw new HTTPException(400, { message: "wrong parameters" });
    }

    const tokenRes = await fetch(c.env.AUTH_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: c.env.WACS_CLIENT,
        client_secret: c.env.WACS_SECRET,
        code: params.code,
        scope: encodeURIComponent(
          "openid email profile read:user write:repository",
        ),
        grant_type: "authorization_code",
        redirect_uri: c.env.WACS_CALLBACK,
      }),
    });

    if (tokenRes.status != 200) {
      return c.text("Unable to authorize. Please try again later.");
    }

    const tokens = (await tokenRes.json()) as any;
    if (tokens.error !== undefined) {
      throw new HTTPException(403, {
        message:
          tokens.error_description || "authorization unsuccessful, try again",
      });
    }

    const userRes = await fetch(`${c.env.WACS_API}/user`, {
      headers: {
        Authorization: `${tokens.token_type} ${tokens.access_token}`,
        Accept: "application/json",
      },
    });
    const user = (await userRes.json()) as any;

    if (user.username === undefined) {
      throw new HTTPException(404, {
        message: user.message || "user not found, try again",
      });
    }

    await repos.users.upsertFromOAuth({
      email: user.email,
      username: user.username,
      wacsUserId: user.id,
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
      tokenType: tokens.token_type,
      state: params.state,
    });

    const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <title>Authorized</title>
    </head>
    <body>
      <p>Authentication successful! This window will close in a moment.</p>
      <script>
        setTimeout(() => { window.close(); }, 3000)
      </script>
    </body>
    </html>
  `;
    return c.html(html);
  } catch (error: any) {
    console.error(error);
    throw new HTTPException(403, {
      message: `${error.code}: ${error.message || error}`,
    });
  }
});

export default router;
