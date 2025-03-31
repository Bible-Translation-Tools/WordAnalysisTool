import {
  WorkflowEntrypoint,
  WorkflowEvent,
  WorkflowStep,
} from "cloudflare:workers";
import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
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

const app = new Hono<{ Bindings: CloudflareBindings }>();
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

app.get("/", async (c) => {
  return c.env.ASSETS.fetch(c.req.url);
});

app.get("/auth/tokens/:state", async (c) => {
  const state = c.req.param("state");

  const user = (await c.env.DB.prepare(
    "SELECT * FROM Logins WHERE state = ? AND updated_at > DATETIME('now', '-30 minutes')"
  )
    .bind(state)
    .first()) as any;

  if (user === null) {
    throw new HTTPException(404, { message: "wrong or expired state" });
  }

  return c.json(user);
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
        Authorization: `Bearer ${tokens.access_token}`,
        Accept: "application/json",
      },
    });

    const user = (await userRes.json()) as any;

    if (user.username === undefined) {
      throw new HTTPException(403, {
        message: user.message || "user not found, try again",
      });
    }

    const response = {
      username: user.username,
      email: user.email,
      access_token: tokens.access_token,
      refresh_token: tokens.refresh_token,
    };

    const existentUser = (await c.env.DB.prepare(
      "SELECT COUNT(*) AS count FROM Logins WHERE username = ? AND email = ?"
    )
      .bind(response.username, response.email)
      .first()) as any;

    if (existentUser.count > 0) {
      await c.env.DB.prepare(
        `UPDATE Logins 
        SET access_token = ?, refresh_token = ?, state = ?, updated_at = CURRENT_TIMESTAMP
        WHERE username = ? AND email = ?`
      )
        .bind(
          response.access_token,
          response.refresh_token,
          params.state,
          response.username,
          response.email
        )
        .run();
    } else {
      await c.env.DB.prepare(
        "INSERT INTO Logins (username, email, access_token, refresh_token, state) VALUES (?, ?, ?, ?, ?)"
      )
        .bind(
          response.username,
          response.email,
          response.access_token,
          response.refresh_token,
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
  } catch (err: any) {
    throw new HTTPException(err.code || 403, {
      message: err.message || "unknown error",
    });
  }
});

app.post("/batch", async (c) => {
  const body = await c.req.blob();

  if (body.type !== "application/octet-stream") {
    throw new HTTPException(403, { message: "invalid file" });
  }

  try {
    const batchId = uuid4();

    const text = await new Response(body).text();
    const words = jsonl.parse<WordRequest>(text);

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
      id: batchId,
      details: details,
    };

    await c.env.DB.prepare("INSERT INTO Batches (id, total) VALUES (?, ?)")
      .bind(batchId, words.length)
      .run();

    const params: WordsParams = {
      batchId: batchId,
      words: words,
    };
    await c.env.WAT_WORKFLOW.create({ params: params });

    return c.json(batch);
  } catch (error) {
    throw new HTTPException(403, { message: `error creating batch: ${error}` });
  }
});

app.get("/batch/:id", async (c) => {
  try {
    const batchId = c.req.param("id");
    const batchEntity = await c.env.DB.prepare(
      "SELECT * FROM Batches WHERE id = ?"
    )
      .bind(batchId)
      .first<BatchEntity>();

    if (batchEntity === null) {
      throw new HTTPException(403, {
        message: "batch not found",
      });
    }

    const { results } = await c.env.DB.prepare(
      "SELECT word, word, json(result) AS result FROM Words WHERE batch_id = ?"
    )
      .bind(batchId)
      .all<WordEntity>();

    const output: WordResponse[] = [];

    for (const word of results) {
      const results: ModelResponse[] = JSON.parse(word.result);
      const response: WordResponse = {
        id: word.word,
        results: results,
      };
      output.push(response);
    }

    const progress: BatchProgress = {
      completed: results.length,
      failed: 0,
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
      id: batchId,
      details: details,
    };

    return c.json(batch);
  } catch (error) {
    throw new HTTPException(403, { message: `error fetching batch: ${error}` });
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
      try {
        const batchId = message.body.batchId;
        const word = message.body.request;

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

          const client = new AiClient(env);
          const result = await client.chat(model, word.prompt);
          // const responses = ["misspell", "proper noun", "something else"];
          // const randomItem =
          //   responses[Math.floor(Math.random() * responses.length)];
          // const result = {
          //   response: randomItem,
          // };

          const output: ModelResponse = {
            model: model,
            result: result,
          };
          modelResults.push(output);
        }

        await env.DB.prepare(
          `INSERT INTO Words (word, result, batch_id) VALUES(?, json(?), ?)`
        )
          .bind(word.id, JSON.stringify(modelResults), batchId)
          .run();
        message.ack();
      } catch (error) {
        console.error(error);
        message.retry({ delaySeconds: 5 });
      }
    }
  },
};
