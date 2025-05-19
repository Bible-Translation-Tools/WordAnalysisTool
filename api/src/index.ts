import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
import type { JwtVariables } from "hono/jwt";
import { jwt, sign } from "hono/jwt";
import { v4 as uuid4 } from "uuid";
import AiClient from "./ai-client";
import { isAdmin, splitBatchJson } from "./utils";
import {
  Batch,
  BatchDetails,
  BatchProgress,
  BatchStatus,
  ModelResponse,
  ModelResult,
  PublicUser,
  WordResponse,
} from "./types";
import { SQL_BATCH_LIMIT, WORDS_PER_BATCH } from "./constants";
import DbHelper from "./db";
import { stream } from "hono/streaming";
import { batchesTable, modelsTable, usersTable, wordsTable } from "./db/schema";
import { and, eq, exists, gt, sql, asc, count } from "drizzle-orm";

interface AppVariables extends JwtVariables {
  db: DbHelper;
}

const app = new Hono<{
  Bindings: CloudflareBindings;
  Variables: AppVariables;
}>();
app.use("*", cors());

app.use("*", async (c, next) => {
  const dbHelper = new DbHelper(c.env);
  c.set("db", dbHelper);
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
  const dbHelper = c.get("db");

  // Expire state after 30 minutes
  const thirtyMinutesAgo = new Date(Date.now() - 30 * 60 * 1000);
  const user = await dbHelper.getDb().query.usersTable.findFirst({
    where: and(
      eq(usersTable.state, state),
      gt(usersTable.updatedAt, thirtyMinutesAgo)
    ),
  });

  if (!user) {
    throw new HTTPException(400, { message: "wrong or expired state" });
  }

  await dbHelper
    .getDb()
    .update(usersTable)
    .set({
      state: null,
      updatedAt: new Date(),
    })
    .where(eq(usersTable.state, state));

  const payload = {
    username: user.username,
    email: user.email,
    admin: isAdmin(user.username, c.env),
    exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24, // expires in 1 day
  };

  return c.json({
    accessToken: await sign(payload, c.env.JWT_SECRET_KEY),
  });
});

app.get("/auth/callback", async (c) => {
  const dbHelper = c.get("db");

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

    await dbHelper
      .getDb()
      .insert(usersTable)
      .values({
        email: user.email,
        username: user.username,
        wacsUserId: user.id,
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
        tokenType: tokens.token_type,
        state: params.state,
      })
      .onConflictDoUpdate({
        target: usersTable.email,
        set: {
          username: sql`EXCLUDED.username`,
          accessToken: sql`EXCLUDED.access_token`,
          refreshToken: sql`EXCLUDED.refresh_token`,
          tokenType: sql`EXCLUDED.token_type`,
          state: sql`EXCLUDED.state`,
          updatedAt: new Date(),
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
    console.error(error);
    throw new HTTPException(403, {
      message: `${error.code}: ${error.message || error}`,
    });
  }
});

// Just to verify if token is not expired
app.get("/api/verify", async (c) => {
  return c.json(true);
});

app.post("/api/batch/:ietf_code/:resource_type", async (c) => {
  const dbHelper = c.get("db");

  const ietf_code = c.req.param("ietf_code");
  const resource_type = c.req.param("resource_type");
  const body = await c.req.blob();
  const payload = c.get("jwtPayload");

  if (body.type !== "application/octet-stream") {
    throw new HTTPException(403, { message: "invalid batch file" });
  }

  try {
    const text = await new Response(body).text();
    const json = await JSON.parse(text);
    const models: string[] = json.models || [];
    const words: string[] = json.words || [];
    const language: string = json.language || null;

    if (models.length === 0) {
      throw new HTTPException(404, { message: "no models provided" });
    }

    if (words.length === 0) {
      throw new HTTPException(404, { message: "no words provided" });
    }

    if (language == null || language.trim() === "") {
      throw new HTTPException(404, { message: "no language provided" });
    }

    const user = await dbHelper.getDb().query.usersTable.findFirst({
      where: eq(usersTable.email, payload.email),
    });

    if (!user) {
      throw new HTTPException(404, { message: "user not found" });
    }

    if (!isAdmin(user.username, c.env)) {
      throw new HTTPException(403, { message: "not allowed" });
    }

    const creator: PublicUser = {
      username: user.username,
    };

    // TODO add current user (AND user_id = ? - user.id)
    const dbBatch =
      (await dbHelper.getDb().query.batchesTable.findFirst({
        where: and(
          eq(batchesTable.ietfCode, ietf_code),
          eq(batchesTable.resourceType, resource_type)
        ),
        columns: {
          id: true,
          pending: true,
        },
      })) || null;

    let batchId = dbBatch?.id;
    const pending = dbBatch?.pending;

    if (!batchId) {
      batchId = uuid4();

      await dbHelper.getDb().insert(batchesTable).values({
        id: batchId,
        ietfCode: ietf_code,
        language: language,
        resourceType: resource_type,
        pending: true,
        userId: user.id,
      });
    } else {
      if (pending) {
        throw new HTTPException(403, { message: "batch in progress" });
      }
      await dbHelper
        .getDb()
        .update(batchesTable)
        .set({
          language: language,
          pending: true,
          error: null,
          updatedAt: new Date(),
        })
        .where(eq(batchesTable.id, batchId));
    }

    // Reset current words
    await dbHelper.insertWords(words, batchId);

    const wordIds = await dbHelper.fetchWordIds(words, batchId);

    // Reset model results
    await dbHelper.insertModels(wordIds, models);

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

    return c.json(batch);
  } catch (error: any) {
    throw new HTTPException(400, {
      message: `${error.code}: error creating batch: ${error.message || error}`,
    });
  }
});

app.get("/api/batch/:ietf_code/:resource_type", async (c) => {
  const dbHelper = c.get("db");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");
    const payload = c.get("jwtPayload");

    // TODO Get for current user (AND u.email = ? - payload.email)
    const dbBatch = await dbHelper.getDb().query.batchesTable.findFirst({
      where: and(
        eq(batchesTable.ietfCode, ietf_code),
        eq(batchesTable.resourceType, resource_type)
      ),
      columns: {
        id: true,
        pending: true,
        error: true,
      },
      with: {
        user: true,
      },
    });

    if (!dbBatch) {
      throw new HTTPException(404, {
        message: "batch not found",
      });
    }

    const totalResult = await dbHelper
      .getDb()
      .select({
        count: count(),
      })
      .from(wordsTable)
      .where(eq(wordsTable.batchId, dbBatch.id));

    const total = totalResult[0].count;
    const completed = await dbHelper.getCompletedWordsCount(dbBatch.id);

    const progress: BatchProgress = {
      completed: completed,
      total: total,
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

    if (!dbBatch.pending) {
      status = BatchStatus.COMPLETE;
    }

    const creator: PublicUser = {
      username: dbBatch.user.username,
    };
    const details: BatchDetails = {
      status: status,
      error: dbBatch.error,
      progress: progress,
      output: [],
    };
    const batch: Batch = {
      id: dbBatch.id,
      ietf_code: ietf_code,
      resource_type: resource_type,
      details: details,
      creator: creator,
    };

    const batchJson = JSON.stringify(batch);
    const splitJson = splitBatchJson(batchJson);

    return stream(c, async (s) => {
      await s.write(splitJson.left);

      try {
        let skip = 0;
        let hasMore = true;
        let firstOutputItem = true;

        while (hasMore) {
          const words = await dbHelper.getDb().query.wordsTable.findMany({
            where: (words, { eq, and, exists, not, lte }) =>
              and(
                eq(words.batchId, dbBatch.id),
                exists(
                  dbHelper
                    .getDb()
                    .select()
                    .from(modelsTable)
                    .where(
                      and(
                        eq(modelsTable.wordId, words.id),
                        not(lte(modelsTable.status, -1))
                      )
                    )
                )
              ),
            columns: {
              word: true,
              correct: true,
            },
            with: {
              models: true,
            },
            offset: skip,
            limit: SQL_BATCH_LIMIT,
            orderBy: [asc(wordsTable.id)],
          });

          if (words.length > 0) {
            for (const word of words) {
              if (!firstOutputItem) {
                await s.write(",");
              }

              const modelResponses = word.models.map((m) => {
                const modelResponse: ModelResponse = {
                  model: m.model,
                  status: m.status,
                };
                return modelResponse;
              });
              const wordResponse: WordResponse = {
                word: word.word,
                correct: word.correct,
                results: modelResponses,
              };

              await s.write(JSON.stringify(wordResponse));
              firstOutputItem = false;
            }
            skip += SQL_BATCH_LIMIT;
          } else {
            hasMore = false;
          }
        }
      } catch (error) {
        console.error(error);
      } finally {
        await s.write(splitJson.right);
        s.close;
      }
    });
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching batch: ${error.message || error}`,
    });
  }
});

app.delete("/api/batch/cancel/:batch_id", async (c) => {
  const dbHelper = c.get("db");

  try {
    const batch_id = c.req.param("batch_id");
    const payload = c.get("jwtPayload");

    const user = await dbHelper.getDb().query.usersTable.findFirst({
      where: eq(usersTable.email, payload.email),
    });

    if (!user) {
      throw new HTTPException(404, {
        message: "user not found",
      });
    }

    if (!isAdmin(user.username, c.env)) {
      throw new HTTPException(403, { message: "not allowed" });
    }

    const cancelled = await dbHelper
      .getDb()
      .update(batchesTable)
      .set({
        pending: false,
        error: null,
      })
      .where(eq(batchesTable.id, batch_id))
      .returning();

    return c.json(cancelled.length > 0);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error deleting batch: ${error.message || error}`,
    });
  }
});

app.delete("/api/batch/delete/:batch_id", async (c) => {
  const dbHelper = c.get("db");

  try {
    const batch_id = c.req.param("batch_id");
    const payload = c.get("jwtPayload");

    const user = await dbHelper.getDb().query.usersTable.findFirst({
      where: eq(usersTable.email, payload.email),
    });

    if (!user) {
      throw new HTTPException(404, {
        message: "user not found",
      });
    }

    if (!isAdmin(user.username, c.env)) {
      throw new HTTPException(403, { message: "not allowed" });
    }

    // TODO Delete only by current user (AND user_id = ? - user.id)
    const deleted = await dbHelper
      .getDb()
      .delete(batchesTable)
      .where(eq(batchesTable.id, batch_id))
      .returning();

    return c.json(deleted.length > 0);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error deleting batch: ${error.message || error}`,
    });
  }
});

app.get("/api/batch/recent", async (c) => {
  const dbHelper = c.get("db");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");
    const payload = c.get("jwtPayload");

    const dbBatches = await dbHelper
      .getDb()
      .selectDistinct({
        id: batchesTable.id,
        ietfCode: batchesTable.ietfCode,
        resourceType: batchesTable.resourceType,
        user: {
          username: usersTable.username,
        },
      })
      .from(batchesTable)
      .innerJoin(wordsTable, eq(batchesTable.id, wordsTable.batchId))
      .innerJoin(modelsTable, eq(wordsTable.id, modelsTable.wordId))
      .innerJoin(usersTable, eq(batchesTable.userId, usersTable.id));

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
        ietf_code: item.ietfCode,
        resource_type: item.resourceType,
        details: details,
        creator: creator,
      };
      return batch;
    });

    return c.json(batches);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching batch: ${error.message || error}`,
    });
  }
});

app.post("/api/word", async (c) => {
  const dbHelper = c.get("db");
  const json = await c.req.json();

  const batch_id = json.batch_id || null;
  const word = json.word || null;
  const correct = json.correct;
  const payload = c.get("jwtPayload");

  try {
    if (!batch_id || !word) {
      throw new HTTPException(403, { message: "invalid parameters" });
    }

    const user = await dbHelper.getDb().query.usersTable.findFirst({
      where: eq(usersTable.email, payload.email),
    });

    if (!user) {
      throw new HTTPException(404, { message: "user not found" });
    }

    await dbHelper
      .getDb()
      .update(wordsTable)
      .set({
        correct: correct,
      })
      .where(and(eq(wordsTable.batchId, batch_id), eq(wordsTable.word, word)));

    return c.json(true);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error updating word: ${error.message || error}`,
    });
  }
});

export default {
  fetch: app.fetch,
  async scheduled(
    controller: ScheduledController,
    env: CloudflareBindings,
    ctx: ExecutionContext
  ) {
    const client = new AiClient(env);
    const dbHelper = new DbHelper(env);
    const batch = await dbHelper.getDb().query.batchesTable.findFirst({
      where: eq(batchesTable.pending, true),
      orderBy: [asc(batchesTable.createdAt)],
    });

    if (batch) {
      const batchId = batch.id;
      let errorDetails: string | null = null;

      const words = await dbHelper.getDb().query.wordsTable.findMany({
        where: (words, { and, eq }) =>
          and(
            eq(words.batchId, batchId),
            exists(
              dbHelper
                .getDb()
                .select({ id: modelsTable.id })
                .from(modelsTable)
                .where(
                  and(
                    eq(modelsTable.wordId, words.id),
                    eq(modelsTable.status, -1)
                  )
                )
            )
          ),
        with: {
          models: true,
        },
        limit: WORDS_PER_BATCH,
      });

      if (words.length > 0) {
        interface TmpModel {
          model: string;
          words: { word: string }[];
        }

        const models = words.reduce((acc: TmpModel[], wordObj) => {
          wordObj.models.forEach((modelObj) => {
            const existingModel = acc.find((m) => m.model === modelObj.model);
            if (existingModel) {
              existingModel.words.push({ word: wordObj.word });
            } else {
              acc.push({
                model: modelObj.model,
                words: [{ word: wordObj.word }],
              });
            }
          });
          return acc;
        }, []);

        const modelsResults: ModelResult[] = [];
        let prompt = `Language: ${batch.language}. Words: ${words
          .map((w) => w.word)
          .join(", ")}`;

        for (const model of models) {
          try {
            // const results = words.map((w) => {
            //   const resp: ChatResponse = {
            //     word: w.word,
            //     status: 1,
            //   };
            //   return resp;
            // });
            const results = await client.chat(model.model, prompt);
            if (results !== null) {
              const modelResult: ModelResult = {
                model: model.model,
                results: results,
              };
              modelsResults.push(modelResult);
            } else {
              errorDetails = `model ${model.model} returned invalid json`;
            }
          } catch (error: any) {
            errorDetails = `model ${model.model} failed. ${
              error.message || error
            }`;
          }
        }

        errorDetails = await dbHelper.updateModelResults(
          words.map((w) => w.word),
          batchId,
          modelsResults
        );
      }

      const toUpdate: any = {
        error: errorDetails,
        updatedAt: new Date(),
      };

      if (words.length == 0) {
        toUpdate.pending = false;
      }

      await dbHelper
        .getDb()
        .update(batchesTable)
        .set(toUpdate)
        .where(eq(batchesTable.id, batchId));
    }
  },
};
