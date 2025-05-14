import { PrismaD1 } from "@prisma/adapter-d1";
import { PrismaClient } from "@prisma/client";
import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
import type { JwtVariables } from "hono/jwt";
import { jwt, sign } from "hono/jwt";
import SqlString from "sqlstring";
import { v4 as uuid4 } from "uuid";
import AiClient from "./ai-client";
import {
  Batch,
  BatchDetails,
  BatchProgress,
  BatchRequest,
  BatchStatus,
  ModelResponse,
  PublicUser,
  WordResponse,
} from "./types";

interface AppVariables extends JwtVariables {
  prisma: PrismaClient;
}

const app = new Hono<{
  Bindings: CloudflareBindings;
  Variables: AppVariables;
}>();
app.use("*", cors());

app.use("*", async (c, next) => {
  const adapter = new PrismaD1(c.env.DB);
  const prisma = new PrismaClient({ adapter });
  c.set("prisma", prisma);
  await next();
});

app.use("/api/*", async (c, next) => {
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
  const prisma = c.get("prisma");

  // Expire state after 30 minutes
  const thirtyMinutesAgo = new Date(Date.now() - 30 * 60 * 1000);
  const user = await prisma.user.findFirst({
    where: {
      state: state,
      updated_at: {
        gt: thirtyMinutesAgo,
      },
    },
  });

  if (user === null) {
    throw new HTTPException(400, { message: "wrong or expired state" });
  }

  await prisma.user.updateMany({
    where: {
      state: state,
    },
    data: {
      state: null,
    },
  });

  const admins = c.env.WAT_ADMINS.split(",");
  const admin = admins.includes(user.username);

  const payload = {
    username: user.username,
    email: user.email,
    admin: admin,
    exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24, // expires in 1 day
  };
  return c.json({
    accessToken: await sign(payload, c.env.JWT_SECRET_KEY),
  });
});

app.get("/auth/callback", async (c) => {
  const prisma = c.get("prisma");

  try {
    const params = {
      code: c.req.query("code"),
      state: c.req.query("state"),
    };

    if (params.code === undefined || params.state === undefined) {
      throw new HTTPException(400, { message: "wrong parameters" });
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

    await prisma.user.upsert({
      where: {
        email: user.email,
      },
      create: {
        email: user.email,
        username: user.username,
        wacs_user_id: user.id,
        access_token: tokens.access_token,
        refresh_token: tokens.refresh_token,
        token_type: tokens.token_type,
        state: params.state,
      },
      update: {
        username: user.username,
        access_token: tokens.access_token,
        refresh_token: tokens.refresh_token,
        token_type: tokens.token_type,
        state: params.state,
        updated_at: new Date(),
      },
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
  const prisma = c.get("prisma");
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

    const user = await prisma.user.findUnique({
      where: {
        email: payload.email,
      },
    });

    if (user == null) {
      throw new HTTPException(404, { message: "user not found" });
    }

    const creator: PublicUser = {
      username: user.username,
    };

    // TODO add current user (AND user_id = ? - user.id)
    const dbBatch = await prisma.batch.findUnique({
      where: {
        idx_unique_batch: {
          ietf_code: ietf_code,
          resource_type: resource_type,
        },
      },
      select: {
        id: true,
        pending: true,
      },
    });

    let batchId = dbBatch?.id;
    const pending = dbBatch?.pending;

    if (!batchId) {
      batchId = uuid4();

      await prisma.batch.create({
        data: {
          id: batchId,
          ietf_code: ietf_code,
          resource_type: resource_type,
          pending: true,
          total_pending: words.length * models.length,
          user_id: user.id,
        },
      });
    } else {
      if (pending) {
        throw new HTTPException(403, { message: "batch in progress" });
      }
      await prisma.batch.update({
        where: {
          id: batchId,
        },
        data: {
          pending: true,
          total_pending: words.length * models.length,
          error: null,
        },
      });
    }

    try {
      // Reset current words
      const wordValues = words.map((item) => {
        return `(${SqlString.escape(item)},${SqlString.escape(batchId)})`;
      });

      await prisma.$executeRawUnsafe(
        `INSERT OR REPLACE INTO Words (word, batch_id) VALUES ${wordValues}`
      );

      const dbWords = await prisma.word.findMany({
        where: {
          word: {
            in: words,
          },
          batch_id: batchId,
        },
        select: {
          id: true,
        },
      });

      const wordIds = dbWords.map((word) => word.id);

      // Reset model results
      const modelValues = wordIds.flatMap((wordId) =>
        models.map((model) => {
          return `(${SqlString.escape(model)},-1,${wordId})`;
        })
      );

      await prisma.$executeRawUnsafe(
        `INSERT OR REPLACE INTO Models (model, status, word_id) VALUES ${modelValues}`
      );
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
      creator: creator,
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
  const prisma = c.get("prisma");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");
    const payload = c.get("jwtPayload");

    // TODO Get for current user (AND u.email = ? - payload.email)
    const dbBatch = await prisma.batch.findUnique({
      where: {
        idx_unique_batch: {
          ietf_code: ietf_code,
          resource_type: resource_type,
        },
      },
      select: {
        id: true,
        total_pending: true,
        error: true,
        user: true,
      },
    });

    if (dbBatch === null) {
      throw new HTTPException(403, {
        message: "batch not found",
      });
    }

    const modelsDb = await prisma.model.findMany({
      where: {
        word: {
          batch_id: dbBatch.id,
        },
      },
      select: {
        word: {
          select: {
            word: true,
            correct: true,
          },
        },
        model: true,
        status: true,
      },
    });

    const words = Object.groupBy(modelsDb, (model) => model.word.word);

    const output: WordResponse[] = [];

    for (const word of Object.keys(words)) {
      const models = words[word];
      if (!models) continue;

      const response: WordResponse = {
        word: word,
        correct: models[0].word.correct,
        results: models
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

    const completed = dbBatch.total_pending - incomplete;

    const progress: BatchProgress = {
      completed: completed,
      total: dbBatch.total_pending,
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

    if (dbBatch.error !== null) {
      status = BatchStatus.ERRORED;
    }

    const creator: PublicUser = {
      username: dbBatch.user.username,
    };
    const details: BatchDetails = {
      status: status,
      error: dbBatch.error,
      progress: progress,
      output: output,
    };
    const batch: Batch = {
      id: dbBatch.id,
      ietf_code: ietf_code,
      resource_type: resource_type,
      details: details,
      creator: creator,
    };

    return c.json(batch);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `error fetching batch: ${error.message || error}`,
    });
  }
});

app.get("/api/batch/recent", async (c) => {
  const prisma = c.get("prisma");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");
    const payload = c.get("jwtPayload");

    const dbBatches = await prisma.batch.findMany({
      where: {
        words: {
          some: {
            models: {
              some: {},
            },
          },
        },
      },
      select: {
        id: true,
        ietf_code: true,
        resource_type: true,
        user: true,
      },
    });

    const progress: BatchProgress = {
      completed: 0,
      total: 0,
    };

    const details: BatchDetails = {
      status: BatchStatus.COMPLETE,
      error: null,
      progress: progress,
      output: [],
    };

    const batches = dbBatches.map((item) => {
      const creator: PublicUser = {
        username: item.user.username,
      };
      const batch: Batch = {
        id: item.id,
        ietf_code: item.ietf_code,
        resource_type: item.resource_type,
        details: details,
        creator: creator,
      };
      return batch;
    });

    return c.json(batches);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `error fetching batch: ${error.message || error}`,
    });
  }
});

app.delete("/api/batch/:batch_id", async (c) => {
  const prisma = c.get("prisma");

  try {
    const batch_id = c.req.param("batch_id");
    const payload = c.get("jwtPayload");

    const user = await prisma.user.findUnique({
      where: {
        email: payload.email,
      },
    });

    if (user === null) {
      throw new HTTPException(404, {
        message: "user not found",
      });
    }

    // TODO Delete only by current user (AND user_id = ? - user.id)
    const deleted = await prisma.batch.delete({
      where: {
        id: batch_id,
      },
    });

    return c.json(deleted !== null);
  } catch (error: any) {
    throw new HTTPException(error.code || 403, {
      message: `error deleting batch: ${error.message || error}`,
    });
  }
});

app.post("/api/word", async (c) => {
  const prisma = c.get("prisma");
  const json = await c.req.json();

  const batch_id = json.batch_id || null;
  const word = json.word || null;
  const correct = json.correct;
  const payload = c.get("jwtPayload");

  try {
    if (!batch_id || !word) {
      throw new HTTPException(403, { message: "invalid parameters" });
    }

    const user = await prisma.user.findUnique({
      where: {
        email: payload.email,
      },
    });

    if (user == null) {
      throw new HTTPException(404, { message: "user not found" });
    }

    await prisma.word.update({
      where: {
        idx_unique_word: {
          batch_id: batch_id,
          word: word,
        },
      },
      data: {
        correct: correct,
      },
    });

    return c.json(true);
  } catch (error: any) {
    throw new HTTPException(error.code || 403, {
      message: `error updating word: ${error.message || error}`,
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
    const adapter = new PrismaD1(env.DB);
    const prisma = new PrismaClient({ adapter });

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
                await prisma.model.updateMany({
                  where: {
                    model: model,
                    word: {
                      word: wordResult,
                      batch_id: batchId,
                    },
                  },
                  data: {
                    status: wordStatus,
                  },
                });
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
        await prisma.batch.update({
          where: {
            id: batchId,
          },
          data: {
            error: errorDetails,
            pending: false,
            total_pending: 0,
          },
        });
      } catch (error) {
        console.error(error);
      }

      message.ack();
    }
  },
};
