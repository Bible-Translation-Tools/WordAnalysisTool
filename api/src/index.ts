import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
import type { JwtVariables } from "hono/jwt";
import { jwt, sign } from "hono/jwt";
import { v4 as uuid4 } from "uuid";
import AiClient from "./ai-client";
import { isAdmin, isChatError, splitBatchJson } from "./utils";
import {
  Batch,
  BatchDetails,
  BatchProgress,
  BatchStatus,
  ModelResult,
  PublicUser,
  WordResponse,
  BatchError,
  WordsParams,
  WordData,
} from "./types";
import { BATCH_MAX_RETRIES, WORDS_PER_BATCH } from "./constants";
import DbHelper from "./db";
import { stream } from "hono/streaming";
import { batchesTable, modelsTable, usersTable, wordsTable } from "./db/schema";
import {
  and,
  eq,
  exists,
  gt,
  sql,
  asc,
  count,
  inArray,
  max,
  min,
} from "drizzle-orm";

const emptyProgress: BatchProgress = {
  correct: 0,
  incorrect: 0,
  name: 0,
  review_needed: 0,
  reviewed: 0,
  completed: 0,
  total: 0,
};

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
    const words: WordData[] = json.words || [];
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

    await dbHelper.insertWords(words, batchId);

    const wordIds = await dbHelper.fetchWordIds(words, batchId);

    await dbHelper.insertModels(wordIds, models);

    const progress: BatchProgress = {
      ...emptyProgress,
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

app.get("/api/report/:ietf_code/:resource_type", async (c) => {
  const dbHelper = c.get("db");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");

    const dbBatch = await dbHelper.getDb().query.batchesTable.findFirst({
      where: and(
        eq(batchesTable.ietfCode, ietf_code),
        eq(batchesTable.resourceType, resource_type)
      ),
      columns: { id: true },
    });

    if (!dbBatch) {
      throw new HTTPException(404, { message: "batch not found" });
    }

    const words = await dbHelper.getDb().query.wordsTable.findMany({
      where: eq(wordsTable.batchId, dbBatch.id),
      with: {
        models: {
          orderBy: [asc(modelsTable.model)],
        },
      },
    });

    const statusMap: { [key: number]: string } = {
      0: "Likely Incorrect",
      1: "Likely Correct",
      2: "Name",
      [-1]: "Not Processed",
    };

    const getConsensus = (st: number[]) => {
      if (st.every((s) => s === 0)) return "Likely Incorrect";
      if (st.every((s) => s === 1)) return "Likely Correct";
      if (st.every((s) => s === 2)) return "Name";
      return "Review Needed";
    };

    c.header("Content-Type", "text/csv");
    c.header("Content-Disposition", 'attachment; filename="report.csv"');

    return stream(c, async (s) => {
      // Header
      await s.write(
        "word,book,chapter,verse,model1,model2,model3,consensus,correct\n"
      );

      // Body
      for (const word of words) {
        const [book, chapter, verse] = word.ref.split(":");
        const modelResults = word.models.map(
          (m) => `"${m.model}\n${statusMap[m.status]}"`
        );
        const consensus = getConsensus(word.models.map((m) => m.status));
        const correct =
          word.correct === null ? "" : word.correct ? "Yes" : "No";

        const row = [
          word.word,
          book || "",
          chapter || "",
          verse || "",
          ...modelResults,
          consensus,
          correct,
        ].join(",");

        await s.write(`${row}\n`);
      }
    });
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching report: ${
        error.message || error
      }`,
    });
  }
});

app.get("/api/stats/:ietf_code/:resource_type", async (c) => {
  const dbHelper = c.get("db");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");

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
      throw new HTTPException(404, { message: "batch not found" });
    }

    const consensusSubquery = dbHelper
      .getDb()
      .select({
        wordId: modelsTable.wordId,
        consensus: sql<string>`
          CASE
            WHEN bool_or(status = -1) THEN NULL
            ELSE
              CASE
                WHEN array_agg(status) @> ARRAY[0, 0, 0]::smallint[] THEN 'Incorrect'
                WHEN array_agg(status) @> ARRAY[1, 1, 1]::smallint[] THEN 'Correct'
                WHEN array_agg(status) @> ARRAY[2, 2, 2]::smallint[] THEN 'Name'
                ELSE 'Review Needed'
              END
          END
        `.as("consensus"),
        isProcessed: sql<boolean>`NOT bool_or(status = -1)`.as("is_processed"),
      })
      .from(modelsTable)
      .groupBy(modelsTable.wordId)
      .as("consensus_subquery");

    const stats = await dbHelper
      .getDb()
      .select({
        correct: count(sql`CASE WHEN consensus = 'Correct' THEN 1 END`),
        incorrect: count(sql`CASE WHEN consensus = 'Incorrect' THEN 1 END`),
        name: count(sql`CASE WHEN consensus = 'Name' THEN 1 END`),
        reviewNeeded: count(
          sql`CASE WHEN consensus = 'Review Needed' THEN 1 END`
        ),
        total: count(wordsTable.id),
        completed: count(sql`CASE WHEN is_processed THEN 1 END`),
        reviewed: count(wordsTable.correct),
      })
      .from(wordsTable)
      .leftJoin(consensusSubquery, eq(wordsTable.id, consensusSubquery.wordId))
      .where(eq(wordsTable.batchId, dbBatch.id));

    const statsInfo = stats[0];

    const progress: BatchProgress = {
      correct: statsInfo.correct,
      incorrect: statsInfo.incorrect,
      name: statsInfo.name,
      review_needed: statsInfo.reviewNeeded,
      reviewed: statsInfo.reviewed,
      completed: statsInfo.completed,
      total: statsInfo.total,
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

    let batchError: BatchError | null = null;
    if (dbBatch.error) {
      try {
        batchError = JSON.parse(dbBatch.error);
      } catch (error) {
        batchError = {
          message: dbBatch.error || "Unknown error occurred.",
          prompt: null,
          model: null,
          response: null,
        };
      }
    }

    const creator: PublicUser = {
      username: dbBatch.user.username,
    };

    const details: BatchDetails = {
      status: status,
      error: batchError,
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

    return c.json(batch);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching stats: ${error.message || error}`,
    });
  }
});

app.get("/api/review/:ietf_code/:resource_type", async (c) => {
  const dbHelper = c.get("db");

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");
    const payload = c.get("jwtPayload");

    const page = parseInt(c.req.query("page") || "1", 10);
    const limit = parseInt(c.req.query("limit") || "5", 10);

    const dbBatch = await dbHelper.getDb().query.batchesTable.findFirst({
      where: and(
        eq(batchesTable.ietfCode, ietf_code),
        eq(batchesTable.resourceType, resource_type)
      ),
      columns: { id: true },
      with: {
        user: true,
      },
    });

    if (!dbBatch) {
      throw new HTTPException(404, { message: "batch not found" });
    }

    const goodWordsSubQuery = dbHelper
      .getDb()
      .select({ wordId: modelsTable.wordId })
      .from(modelsTable)
      .innerJoin(wordsTable, eq(modelsTable.wordId, wordsTable.id))
      .where(eq(wordsTable.batchId, dbBatch.id))
      .groupBy(modelsTable.wordId)
      .having(
        and(
          eq(min(modelsTable.status), max(modelsTable.status)),
          inArray(min(modelsTable.status), [0, 1])
        )
      )
      .as("good_words");

    // Get the progress numbers
    const countResults = await dbHelper
      .getDb()
      .select({
        totalCount: count(),
        reviewedCount:
          sql<number>`count(CASE WHEN ${wordsTable.correct} IS NOT NULL THEN 1 END)`.as(
            "reviewedCount"
          ),
      })
      .from(wordsTable)
      .innerJoin(
        goodWordsSubQuery,
        eq(wordsTable.id, goodWordsSubQuery.wordId)
      );

    const total = countResults[0].totalCount;
    const reviewed = countResults[0].reviewedCount;

    const progress: BatchProgress = {
      ...emptyProgress,
      reviewed: reviewed,
      total: total,
    };

    let targetPage = page;
    if (targetPage <= 0) {
      if (reviewed >= total && total > 0) {
        targetPage = Math.ceil(total / limit);
      } else {
        targetPage = Math.floor(reviewed / limit) + 1;
      }
    }
    targetPage = Math.max(1, targetPage);
    const offset = (targetPage - 1) * limit;

    // Fetch only the words for the requested page
    const words = await dbHelper
      .getDb()
      .select()
      .from(wordsTable)
      .innerJoin(goodWordsSubQuery, eq(wordsTable.id, goodWordsSubQuery.wordId))
      .orderBy(asc(wordsTable.word))
      .limit(limit)
      .offset(offset);

    // Map the database results to the desired response format
    const output = words.map((word: any) => {
      const wordResponse: WordResponse = {
        word: word.words.word,
        ref: word.words.ref,
        correct: word.words.correct,
        results: [],
      };
      return wordResponse;
    });

    const batchDetails: BatchDetails = {
      status: BatchStatus.COMPLETE,
      error: null,
      progress: progress,
      output,
    };

    const creator: PublicUser = {
      username: dbBatch.user.username,
    };

    const batch: Batch = {
      id: dbBatch.id,
      ietf_code: ietf_code,
      resource_type: resource_type,
      details: batchDetails,
      creator: creator,
    };

    return c.json(batch);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching words: ${error.message || error}`,
    });
  }
});

app.delete("/api/batch/pause/:batch_id", async (c) => {
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

    if (cancelled.length > 0) {
      // also delete incomplete models
      const badWordIdsSubQuery = dbHelper
        .getDb()
        .selectDistinct({ wordId: modelsTable.wordId })
        .from(modelsTable)
        .where(eq(modelsTable.status, -1));

      await dbHelper
        .getDb()
        .delete(modelsTable)
        .where(inArray(modelsTable.wordId, badWordIdsSubQuery));
    }

    return c.json(cancelled.length > 0);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error pausing batch: ${error.message || error}`,
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

    const progress = emptyProgress;

    const details: BatchDetails = {
      status: BatchStatus.COMPLETE,
      error: null,
      progress: progress,
      output: [],
    };

    const batches = dbBatches.map((item) => ({
      id: item.id,
      ietf_code: item.ietfCode,
      resource_type: item.resourceType,
      details: details,
      creator: { username: item.user.username },
    }));

    return c.json(batches);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error fetching batch: ${error.message || error}`,
    });
  }
});

app.post("/api/words", async (c) => {
  const dbHelper = c.get("db");
  const payload = c.get("jwtPayload");

  const request: WordsParams = await c.req.json();

  try {
    const user = await dbHelper.getDb().query.usersTable.findFirst({
      where: eq(usersTable.email, payload.email),
    });

    if (!user) {
      throw new HTTPException(404, { message: "user not found" });
    }

    const whenClauses = request.words.map((w) => {
      return sql`WHEN ${wordsTable.word} = ${w.word} THEN ${w.correct}`;
    });

    const wordStrings = request.words.map((w) => w.word);

    const caseSql = sql`CASE ${sql.join(whenClauses, sql` `)} END`;

    await dbHelper
      .getDb()
      .update(wordsTable)
      .set({
        correct: caseSql,
      })
      .where(
        and(
          eq(wordsTable.batchId, request.batchId),
          inArray(wordsTable.word, wordStrings)
        )
      );

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
    try {
      const client = new AiClient(env);
      const dbHelper = new DbHelper(env);
      const batch = await dbHelper.getDb().query.batchesTable.findFirst({
        where: eq(batchesTable.pending, true),
        orderBy: [asc(batchesTable.createdAt)],
      });

      if (batch) {
        const batchId = batch.id;
        let errorDetails: BatchError | null = null;

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
            words: { word: string; status: number }[];
          }

          const models = words.reduce((acc: TmpModel[], wordObj) => {
            wordObj.models.forEach((modelObj) => {
              const existingModel = acc.find((m) => m.model === modelObj.model);
              if (existingModel) {
                existingModel.words.push({
                  word: wordObj.word,
                  status: modelObj.status,
                });
              } else {
                acc.push({
                  model: modelObj.model,
                  words: [
                    {
                      word: wordObj.word,
                      status: modelObj.status,
                    },
                  ],
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
              // Use cached results
              const completed = model.words.every((word) => word.status > -1);
              if (completed) {
                const results = model.words.map(({ word, status }) => ({
                  word,
                  status,
                }));
                const modelResult: ModelResult = {
                  model: model.model,
                  results: results,
                };
                modelsResults.push(modelResult);
              } else {
                const results = await client.chat(model.model, prompt);
                if (!isChatError(results)) {
                  const modelResult: ModelResult = {
                    model: model.model,
                    results: results,
                  };
                  modelsResults.push(modelResult);
                } else {
                  errorDetails = results;
                }
              }
            } catch (error: any) {
              errorDetails = {
                prompt,
                message: error.message || error,
                model: model.model,
                response: null,
              };
            }
          }

          const updateError = await dbHelper.updateModelResults(
            words.map((w) => w.word),
            batchId,
            modelsResults
          );

          if (updateError) {
            updateError.prompt = prompt;
            errorDetails = updateError;
          }
        }

        const toUpdate: any = {
          updatedAt: new Date(),
        };

        if (words.length == 0) {
          toUpdate.pending = false;
        }

        if (errorDetails) {
          let retries = batch.retries + 1;
          if (retries >= BATCH_MAX_RETRIES) {
            // stop batch when retries counter exceeds limit
            toUpdate.pending = false;
            toUpdate.retries = 0;
          } else {
            toUpdate.retries = retries;
          }
          toUpdate.error = JSON.stringify(errorDetails);
        } else {
          // reset retries if there was no error
          toUpdate.retries = 0;
          toUpdate.error = "";
        }

        await dbHelper
          .getDb()
          .update(batchesTable)
          .set(toUpdate)
          .where(eq(batchesTable.id, batchId));
      }
    } catch (error) {
      console.error(error);
    }
  },
};
