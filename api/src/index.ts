import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
import { jwt, sign, verify } from "hono/jwt";
import type { JwtVariables } from "hono/jwt";
import { v4 as uuid4 } from "uuid";
import sqlString from "sqlstring";
import {
  BatchRequest,
  BatchProgress,
  BatchDetails,
  BatchStatus,
  Batch,
  BatchEntity,
  WordResponse,
  ModelResponse,
  WordModelEntity,
} from "./types";
import AiClient from "./ai-client";
import { use } from "hono/jsx";

const app = new Hono<{
  Bindings: CloudflareBindings;
  Variables: JwtVariables;
}>();
app.use("*", cors());

app.use("/api/*", (c, next) => {
  const jwtMiddleware = jwt({
    secret: c.env.JWT_SECRET_KEY,
  });
  return jwtMiddleware(c, next);
});

app.get("/", async (c) => {
  return c.env.ASSETS.fetch(c.req.url);
});

app.get("/auth/tokens/:state", async (c) => {
  const state = c.req.param("state");

  // Expire state after 30 minutes
  const user = (await c.env.DB.prepare(
    "SELECT * FROM Users WHERE state = ? AND updated_at > DATETIME('now', '-30 minutes')"
  )
    .bind(state)
    .first()) as any;

  if (user === null) {
    throw new HTTPException(404, { message: "wrong or expired state" });
  }

  await c.env.DB.prepare("UPDATE Users SET state = NULL WHERE state = ?")
    .bind(state)
    .run();

  const payload = {
    username: user.username,
    email: user.email,
    exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24, // expires in 1 day
  };
  return c.json({
    accessToken: await sign(payload, c.env.JWT_SECRET_KEY),
  });
});

app.get("/auth/callback", async (c) => {
  try {
    const params = {
      code: c.req.query("code"),
      state: c.req.query("state"),
    };

    if (params.code === undefined || params.state === undefined) {
      throw new HTTPException(404, { message: "wrong parameters" });
    }

    const tokenRes = await fetch(c.env.AUTH_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({
        client_id: c.env.WACS_CLIENT,
        client_secret: c.env.WACS_SECRET,
        code: params.code,
        scope: encodeURIComponent(
          "openid email profile read:user write:repository"
        ),
        grant_type: "authorization_code",
        redirect_uri: c.env.WACS_CALLBACK,
      }),
    });

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

    const { count } = (await c.env.DB.prepare(
      "SELECT COUNT(*) AS count FROM Users WHERE email = ?"
    )
      .bind(user.email)
      .first()) as any;

    if (count > 0) {
      await c.env.DB.prepare(
        `UPDATE Users 
        SET access_token = ?, refresh_token = ?,
        token_type = ?, state = ?, 
        wacs_user_id = ?, username = ?, 
        updated_at = CURRENT_TIMESTAMP
        WHERE email = ?`
      )
        .bind(
          tokens.access_token,
          tokens.refresh_token,
          tokens.token_type,
          params.state,
          user.id,
          user.username,
          user.email
        )
        .run();
    } else {
      await c.env.DB.prepare(
        `INSERT INTO Users
        (
            username,
            email,
            wacs_user_id,
            access_token,
            refresh_token,
            token_type,
            state
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)`
      )
        .bind(
          user.username,
          user.email,
          user.id,
          tokens.access_token,
          tokens.refresh_token,
          tokens.token_type,
          params.state
        )
        .run();
    }

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
    throw new HTTPException(error.code || 403, {
      message: error.message || error,
    });
  }
});

// Just to verify if token is not expired
app.get("/api/verify", async (c) => {
  return c.json(true);
});

app.post("/api/batch/:ietf_code/:resource_type", async (c) => {
  const ietf_code = c.req.param("ietf_code");
  const resource_type = c.req.param("resource_type");
  const json = await c.req.json();
  const models: string[] = json.models || [];
  const words: string[] = json.words || [];
  const language: string = json.language || null;
  const payload = c.get("jwtPayload");

  try {
    if (models.length === 0) {
      throw new HTTPException(404, { message: "no models provided" });
    }

    if (words.length === 0) {
      throw new HTTPException(404, { message: "no words provided" });
    }

    if (language == null || language.trim() === "") {
      throw new HTTPException(404, { message: "no language provided" });
    }

    const user = (await c.env.DB.prepare("SELECT id FROM Users WHERE email = ?")
      .bind(payload.email)
      .first()) as any;

    // TODO add current user (AND user_id = ? - user.id)
    const batchEntity = (await c.env.DB.prepare(
      `SELECT id, pending FROM Batches
      WHERE ietf_code = ? AND resource_type = ?`
    )
      .bind(ietf_code, resource_type)
      .first()) as any;

    let batchId: string = batchEntity?.id;
    const pending: Boolean = batchEntity?.pending;

    if (!batchId) {
      batchId = uuid4();

      await c.env.DB.prepare(
        `INSERT INTO Batches
        (id, ietf_code, resource_type, pending, total_pending, user_id)
        VALUES (?, ?, ?, ?, ?, ?)`
      )
        .bind(
          batchId,
          ietf_code,
          resource_type,
          1,
          words.length * models.length,
          user.id
        )
        .run();
    } else {
      if (pending) {
        throw new HTTPException(403, { message: "batch in progress" });
      }
      await c.env.DB.prepare(
        "UPDATE Batches SET pending = 1, total_pending = ?, error = NULL WHERE id = ?"
      )
        .bind(words.length * models.length, batchId)
        .run();
    }

    try {
      // Insert or replace current words
      const insertedWords = words
        .map(
          (item) => `(${sqlString.escape(item)}, ${sqlString.escape(batchId)})`
        )
        .join(", ");

      await c.env.DB.prepare(
        `INSERT OR REPLACE INTO Words (word, batch_id) VALUES ${insertedWords}`
      ).run();

      // Delete old model results
      const deletedModels = words
        .map(
          (word) =>
            `(SELECT id FROM Words WHERE word = ${sqlString.escape(
              word
            )} AND batch_id = ${sqlString.escape(batchId)})`
        )
        .join(",");

      await c.env.DB.prepare(
        `DELETE FROM Models WHERE word_id IN (${deletedModels})`
      ).run();

      // Insert empty model results
      const insertedModels = words
        .map((word) =>
          models.map(
            (model) =>
              `(${sqlString.escape(
                model
              )}, -1, (SELECT id FROM Words WHERE word = ${sqlString.escape(
                word
              )} AND batch_id = ${sqlString.escape(batchId)}))`
          )
        )
        .join(",");

      await c.env.DB.prepare(
        `INSERT INTO Models (model, status, word_id) VALUES ${insertedModels}`
      ).run();
    } catch (error) {
      console.error(error);
    }

    const progress: BatchProgress = {
      completed: 0,
      total: words.length,
    };

    const details: BatchDetails = {
      status: BatchStatus.QUEUED,
      error: null,
      output: [],
      progress: progress,
    };

    const batch: Batch = {
      id: batchId,
      ietf_code: ietf_code,
      resource_type: resource_type,
      details: details,
    };

    const batchRequest: BatchRequest = {
      batchId: batchId,
      language: language,
      words: words,
      models: models,
    };
    await c.env.WAT_QUEUE.send(batchRequest);

    return c.json(batch);
  } catch (error: any) {
    throw new HTTPException(error.code || 403, {
      message: `error creating batch: ${error.message || error}`,
    });
  }
});

app.get("/api/batch/:ietf_code/:resource_type", async (c) => {
  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");
    const payload = c.get("jwtPayload");

    // TODO Get for current user (AND u.email = ? - payload.email)
    const batchEntity = await c.env.DB.prepare(
      `SELECT b.* FROM Batches AS b 
      LEFT JOIN Users AS u ON u.id = b.user_id 
      WHERE b.ietf_code = ? AND b.resource_type = ?`
    )
      .bind(ietf_code, resource_type)
      .first<BatchEntity>();

    if (batchEntity === null) {
      throw new HTTPException(403, {
        message: "batch not found",
      });
    }

    const models = await c.env.DB.prepare(
      `SELECT w.word, m.model, m.status FROM Words AS w 
      LEFT JOIN Models AS m ON m.word_id = w.id 
      WHERE w.batch_id = ?`
    )
      .bind(batchEntity.id)
      .all<WordModelEntity>();

    const entities = Object.groupBy(models.results, (model) => model.word);

    const output: WordResponse[] = [];

    for (const key of Object.keys(entities)) {
      const entitiy = entities[key];
      if (!entitiy) continue;

      const response: WordResponse = {
        word: key,
        results: entitiy
          .filter((item) => item.model !== null)
          .map(
            (item): ModelResponse => ({
              model: item.model,
              status: item.status,
            })
          ),
      };
      output.push(response);
    }

    const incomplete = output
      .map((item) => item.results.filter((res) => res.status < 0).length)
      .reduce((a, b) => a + b, 0);

    const completed = batchEntity.total_pending - incomplete;

    const progress: BatchProgress = {
      completed: completed,
      total: batchEntity.total_pending,
    };

    let p = 1;
    if (progress.total > 0) {
      p = progress.completed / progress.total;
    }

    let status: BatchStatus;
    switch (p) {
      case 0:
        status = BatchStatus.QUEUED;
        break;
      case 1:
        status = BatchStatus.COMPLETE;
        break;
      default:
        status = BatchStatus.RUNNING;
    }

    if (batchEntity.error !== null) {
      status = BatchStatus.ERRORED;
    }

    const details: BatchDetails = {
      status: status,
      error: batchEntity.error,
      progress: progress,
      output: output,
    };
    const batch: Batch = {
      id: batchEntity.id,
      ietf_code: ietf_code,
      resource_type: resource_type,
      details: details,
    };

    return c.json(batch);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `error fetching batch: ${error.message || error}`,
    });
  }
});

app.delete("/api/batch/:ietf_code/:resource_type", async (c) => {
  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");
    const payload = c.get("jwtPayload");

    const user = (await c.env.DB.prepare("SELECT id FROM Users WHERE email = ?")
      .bind(payload.email)
      .first()) as any;

    // TODO Get current user (AND user_id = ? - user.id)
    const { count } = (await c.env.DB.prepare(
      `SELECT COUNT(*) AS count 
      FROM Batches 
      WHERE ietf_code = ? AND resource_type = ?`
    )
      .bind(ietf_code, resource_type)
      .first()) as any;

    if (count === 0) {
      throw new HTTPException(404, {
        message: "batch not found",
      });
    }

    // TODO Delete only by current user (AND user_id = ? - user.id)
    const result = (await c.env.DB.prepare(
      `DELETE FROM Batches 
      WHERE ietf_code = ? AND resource_type = ?`
    )
      .bind(ietf_code, resource_type)
      .run()) as any;

    const deleted = result.meta.changes > 0;

    return c.json(deleted);
  } catch (error: any) {
    throw new HTTPException(error.code || 403, {
      message: `error deleting batch: ${error.message || error}`,
    });
  }
});

export default {
  fetch: app.fetch,
  async queue(
    batch: MessageBatch<BatchRequest>,
    env: CloudflareBindings,
    ctx: ExecutionContext
  ) {
    const client = new AiClient(env);

    for (const message of batch.messages) {
      const batchId = message.body.batchId;
      const language = message.body.language;
      const words = message.body.words;
      const models = message.body.models;

      let prompt = `Language: ${language}. Words: ${words.join(", ")}`;
      let errorDetails: string | null = null;

      for (const model of models) {
        try {
          const results = await client.chat(model, prompt);
          if (results !== null) {
            for (const result of results) {
              const wordResult = result.word.trim();
              const wordStatus = result.status;

              if (words.includes(wordResult)) {
                await env.DB.prepare(
                  `UPDATE Models 
                  SET status = ? 
                  WHERE model = ? AND word_id = (SELECT id FROM Words WHERE word = ? AND batch_id = ?)`
                )
                  .bind(wordStatus, model, wordResult, batchId)
                  .run();
              } else {
                errorDetails = `model ${model} returned wrong word result: ${wordResult}`;
              }
            }
          } else {
            errorDetails = `model ${model} returned invalid json`;
          }
        } catch (error: any) {
          errorDetails = `model ${model} failed. ${error.message || error}`;
        }
      }

      try {
        await env.DB.prepare(
          "UPDATE Batches SET error = ?, pending = 0, total_pending = 0 WHERE id = ?"
        )
          .bind(errorDetails, batchId)
          .run();
      } catch (error) {
        console.error(error);
      }

      message.ack();
    }
  },
};
