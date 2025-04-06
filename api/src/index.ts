import {
  WorkflowEntrypoint,
  WorkflowEvent,
  WorkflowStep,
} from "cloudflare:workers";
import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
import { jwt, sign, verify } from "hono/jwt";
import type { JwtVariables } from "hono/jwt";
import { jsonl } from "js-jsonl";
import { v4 as uuid4 } from "uuid";
import {
  WordsParams,
  BatchRequest,
  WordRequest,
  BatchProgress,
  BatchDetails,
  BatchStatus,
  Batch,
  BatchEntity,
  WordEntity,
  WordResponse,
  ModelResponse,
} from "./types";
import AiClient from "./ai-client";

const app = new Hono<{
  Bindings: CloudflareBindings;
  Variables: JwtVariables;
}>();
app.use("*", cors());

export class WatWorkflow extends WorkflowEntrypoint<
  CloudflareBindings,
  WordsParams
> {
  async run(event: WorkflowEvent<WordsParams>, step: WorkflowStep) {
    await step.do("sending words to the queue", async () => {
      const batchId = event.payload.batchId;
      const words = event.payload.words;

      for (const word of words) {
        await step.do(`sending word: ${word.id}`, async () => {
          // wait a bit before sending message
          //await step.sleep("sleep", "1 second");

          const batchRequest: BatchRequest = {
            batchId: batchId,
            request: word,
          };
          await this.env.WAT_QUEUE.send(batchRequest);
        });
      }

      return true;
    });
  }
}

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
  const payload = c.get("jwtPayload");
  const body = await c.req.blob();

  if (body.type !== "application/octet-stream") {
    throw new HTTPException(403, { message: "invalid file" });
  }

  try {
    const text = await new Response(body).text();
    const words = jsonl.parse<WordRequest>(text);

    const user = (await c.env.DB.prepare("SELECT id FROM Users WHERE email = ?")
      .bind(payload.email)
      .first()) as any;

    // TODO add current user (AND user_id = ? - user.id)
    const existentBatch = (await c.env.DB.prepare(
      `SELECT * FROM Batches
      WHERE ietf_code = ? AND resource_type = ?`
    )
      .bind(ietf_code, resource_type)
      .first()) as any;

    let batch_id: string;

    if (existentBatch !== null) {
      const { incomplete } = (await c.env.DB.prepare(
        `SELECT COUNT(*) AS incomplete 
        FROM Words
        WHERE batch_id = ? AND result IS NULL AND errored = 0`
      )
        .bind(existentBatch.id)
        .first()) as any;

      if (incomplete > 0) {
        throw new HTTPException(403, { message: "batch in progress" });
      }

      await c.env.DB.prepare(`UPDATE Batches SET total = ? WHERE id = ?`)
        .bind(words.length, existentBatch.id)
        .run();

      batch_id = existentBatch.id;
    } else {
      batch_id = uuid4();
      await c.env.DB.prepare(
        `INSERT INTO Batches 
        (id, ietf_code, resource_type, total, user_id) 
        VALUES (?, ?, ?, ?, ?)`
      )
        .bind(batch_id, ietf_code, resource_type, words.length, user.id)
        .run();
    }

    for (const word of words) {
      await c.env.DB.prepare("INSERT INTO Words (word, batch_id) VALUES(?, ?)")
        .bind(word.id, batch_id)
        .run();
    }

    const progress: BatchProgress = {
      completed: 0,
      failed: 0,
      total: words.length,
    };

    const details: BatchDetails = {
      status: BatchStatus.QUEUED,
      error: null,
      output: null,
      progress: progress,
    };

    const batch: Batch = {
      id: batch_id,
      ietf_code: ietf_code,
      resource_type: resource_type,
      details: details,
    };

    const params: WordsParams = {
      batchId: batch_id,
      words: words,
    };
    await c.env.WAT_WORKFLOW.create({ params: params });

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

    const { results } = await c.env.DB.prepare(
      `SELECT json(result) AS result, * 
      FROM Words 
      WHERE batch_id = ?`
    )
      .bind(batchEntity.id)
      .all<WordEntity>();

    const output: WordResponse[] = [];
    let failed = 0;

    for (const word of results) {
      if (word.result !== null) {
        const results: ModelResponse[] = JSON.parse(word.result);
        const response: WordResponse = {
          id: word.word,
          errored: false,
          last_error: null,
          results: results,
        };
        output.push(response);
      } else if (word.errored) {
        const response: WordResponse = {
          id: word.word,
          errored: true,
          last_error: word.last_error,
          results: null,
        };
        output.push(response);
        failed++;
      }
    }

    const incomplete = results.length - output.length;
    const completed = batchEntity.total - incomplete - failed;

    const progress: BatchProgress = {
      completed: completed,
      failed: failed,
      total: batchEntity.total,
    };

    let p = 0;
    if (progress.total > 0) {
      p = (progress.completed + progress.failed) / progress.total;
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

    const details: BatchDetails = {
      status: status,
      error: null,
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
    if (batch.queue === "wat-queue") {
      const client = new AiClient(env);

      for (const message of batch.messages) {
        const batchId = message.body.batchId;
        const word = message.body.request;

        try {
          const modelResults: ModelResponse[] = [];

          for (const model of word.models) {
            const request = {
              messages: [
                {
                  role: "user",
                  content: word.prompt,
                },
              ],
            };

            const result = await client.chat(model, word.prompt);
            //const responses = ["misspell", "proper noun", "something else"];
            //const randomItem =
            //  responses[Math.floor(Math.random() * responses.length)];
            //const result = randomItem;

            const output: ModelResponse = {
              model: model,
              result: result,
            };
            modelResults.push(output);
          }

          await env.DB.prepare(
            `UPDATE Words SET result = json(?), last_error = NULL WHERE word = ? AND batch_id = ?`
          )
            .bind(JSON.stringify(modelResults), word.id, batchId)
            .run();

          message.ack();
        } catch (error: any) {
          await env.DB.prepare(
            `UPDATE Words SET last_error = ? WHERE word = ? AND batch_id = ?`
          )
            .bind(`${error}`, word.id, batchId)
            .run();

          console.error(error);
          message.retry({ delaySeconds: 1 });
        }
      }
    } else if (batch.queue === "errors-queue") {
      for (const message of batch.messages) {
        const batchId = message.body.batchId;
        const word = message.body.request;

        const { last_error } = (await env.DB.prepare(
          "SELECT last_error FROM Words WHERE word = ? AND batch_id = ?"
        )
          .bind(word.id, batchId)
          .first()) as any;

        let error = last_error;

        if (last_error === null) {
          error = "Unknown error occurred.";
        }
        await env.DB.prepare(
          "UPDATE Words SET last_error = ?, errored = 1 WHERE word = ? AND batch_id = ?"
        )
          .bind(error, word.id, batchId)
          .run();

        message.ack();
      }
    }
  },
};
