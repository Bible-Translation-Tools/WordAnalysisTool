import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
import type { JwtVariables } from "hono/jwt";
import { jwt, sign } from "hono/jwt";
import { v4 as uuid4 } from "uuid";
import AiClient from "./ai/client";
import { isAdmin, isChatError } from "./utils";
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
  ChatResponse,
  WordContext,
} from "./types";
import { BATCH_MAX_RETRIES, WORDS_PER_BATCH } from "./constants";
import DbHelper from "./db";
import { getBooksForTranslation, getLanguageInfo } from "./biel";
import { parseVerses, findSingletons } from "./usfm";
import { stream } from "hono/streaming";
import {
  batchesTable,
  languagesTable,
  modelsTable,
  resourcesTable,
  usersTable,
  versesTable,
  wordReviewsTable,
  wordsTable,
} from "./db/schema";
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
import { unionAll } from "drizzle-orm/pg-core";

const emptyProgress: BatchProgress = {
  correct: 0,
  incorrect: 0,
  review_needed: 0,
  reviewed: 0,
  completed: 0,
  total: 0,
};

type WordEntity = typeof wordsTable.$inferSelect;
type BatchEntity = typeof batchesTable.$inferSelect;

// Default reference translation when a batch doesn't specify one.
const DEFAULT_REF_IETF = "en";
const DEFAULT_REF_RESOURCE_TYPE = "ulb";

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// Keep error reasons short and useful. Drizzle wraps the driver error, so its
// `.message` is just the "Failed query" SQL dump — the real Postgres message is
// on `.cause`. Prefer that, take the first line (drops the params dump that
// embeds verse text), and cap the length.
const briefReason = (e: any): string => {
  const msg = e?.cause?.message ?? e?.message ?? String(e);
  return String(msg).split("\n")[0].slice(0, 200);
};

/**
 * Fetch a USFM file, retrying on transient failures (the content host throttles
 * bursts of requests, so a timed-out / 429 / 5xx book usually succeeds on retry).
 */
async function fetchUsfm(url: string, attempts = 4): Promise<string> {
  let lastReason = "unknown error";
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await fetch(url, {
        headers: { "User-Agent": "btt-writer-android" },
      });
      if (res.ok) return await res.text();
      lastReason = `status ${res.status}`;
      // 4xx (other than 429) won't get better by retrying
      if (res.status >= 400 && res.status < 500 && res.status !== 429) {
        break;
      }
    } catch (e: any) {
      lastReason = e?.message || String(e);
    }
    if (i < attempts - 1) await sleep(500 * (i + 1));
  }
  throw new Error(lastReason);
}

/**
 * Download and store any missing books of a translation into `resourceId`.
 * Idempotent per book (already-stored books are skipped), so retries make
 * forward progress. Throws if any book fails — callers must not proceed on a
 * partial set. The thrown error lists which books failed and why.
 */
async function ingestResource(
  dbHelper: DbHelper,
  ietfCode: string,
  resourceType: string,
  resourceId: number,
): Promise<void> {
  const contents = await getBooksForTranslation(ietfCode, resourceType);
  const usable = contents.filter((c) => c.url);
  if (usable.length === 0) {
    throw new Error(`no USFM content for ${ietfCode}/${resourceType}`);
  }

  const storedBooks = new Set(await dbHelper.getStoredBookCodes(resourceId));
  const failures: string[] = [];

  for (const content of usable) {
    const slug = content.bookSlug?.toLowerCase();
    if (slug && storedBooks.has(slug)) continue;

    const label = content.bookSlug ?? content.url ?? "?";
    try {
      const usfm = await fetchUsfm(content.url!);
      const bookVerses = parseVerses(usfm, content.bookSlug ?? undefined);
      if (bookVerses.length > 0) {
        await dbHelper.insertVerses(bookVerses, resourceId);
        storedBooks.add(bookVerses[0].book.toLowerCase());
      }
    } catch (e: any) {
      const reason = briefReason(e);
      console.error(
        `failed to ingest book ${label} for ${ietfCode}/${resourceType}: ${reason}`,
      );
      failures.push(`${label}: ${reason}`);
    }
  }

  // Completeness gate: don't proceed on a partial set.
  if (failures.length > 0) {
    throw new Error(
      `${failures.length} of ${usable.length} books failed for ${ietfCode}/${resourceType} — ${failures
        .slice(0, 10)
        .join("; ")}`,
    );
  }
}

/**
 * Source ingestion for one batch: ensure the translation's source and the
 * reference source are both fully stored, then compute singleton words (linked
 * to their verse) and queue the batch for AI processing.
 */
async function ingestSource(
  dbHelper: DbHelper,
  batch: BatchEntity,
): Promise<void> {
  const batchId = batch.id;

  try {
    if (!batch.resourceId) {
      throw new Error("batch has no resource");
    }
    const resourceId = batch.resourceId;

    const models: string[] = batch.models ? JSON.parse(batch.models) : [];
    if (models.length === 0) {
      throw new Error("batch has no models");
    }

    // 1. The batch's own translation source.
    const target = await dbHelper.getResourceRef(resourceId);
    if (!target) {
      throw new Error("batch resource not found");
    }
    await ingestResource(
      dbHelper,
      target.ietf,
      target.resourceType,
      resourceId,
    );

    // 2. The reference source for this batch (shared across batches; only
    //    missing books are downloaded). Skipped if the batch has no reference.
    if (batch.refResourceId) {
      const ref = await dbHelper.getResourceRef(batch.refResourceId);
      if (!ref) {
        throw new Error("reference resource not found");
      }
      await ingestResource(
        dbHelper,
        ref.ietf,
        ref.resourceType,
        batch.refResourceId,
      );
    }

    // 3. Compute singletons from the (complete) translation source and link
    //    each word to its verse.
    const verses = await dbHelper.getVersesByResource(resourceId);
    const singletons = findSingletons(verses, batch.apostropheIsSeparator);
    if (singletons.length === 0) {
      throw new Error("no singleton words found");
    }

    const verseRefMap = await dbHelper.getVerseRefMap(resourceId);
    const wordRows = singletons
      .map((s) => ({ word: s.word, verseId: verseRefMap.get(s.ref) }))
      .filter(
        (w): w is { word: string; verseId: number } => w.verseId !== undefined,
      );

    await dbHelper.insertWords(wordRows, batchId);
    const wordIds = await dbHelper.fetchWordIds(wordRows, batchId);
    await dbHelper.insertModels(wordIds, models);

    await dbHelper
      .getDb()
      .update(batchesTable)
      .set({
        ingesting: false,
        pending: false,
        error: null,
        retries: 0,
        updatedAt: new Date(),
      })
      .where(eq(batchesTable.id, batchId));
  } catch (error: any) {
    console.error("ingestion error:", error);

    const errorDetails: BatchError = {
      message: `ingestion error: ${briefReason(error)}`,
      prompt: null,
      model: null,
      response: null,
    };

    const toUpdate: any = {
      error: JSON.stringify(errorDetails),
      updatedAt: new Date(),
    };

    const retries = batch.retries + 1;
    if (retries >= BATCH_MAX_RETRIES) {
      // give up: stop ingesting so it doesn't loop forever
      toUpdate.ingesting = false;
      toUpdate.pending = false;
      toUpdate.retries = 0;
    } else {
      toUpdate.retries = retries;
    }

    await dbHelper
      .getDb()
      .update(batchesTable)
      .set(toUpdate)
      .where(eq(batchesTable.id, batchId));
  }
}

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
    alg: "HS256",
  });
  return jwtMiddleware(c, next);
});

app.get("/auth/tokens/:state", async (c) => {
  const state = c.req.param("state");
  const dbHelper = c.get("db");

  // Expire state after 30 minutes
  const thirtyMinutesAgo = new Date(Date.now() - 30 * 60 * 1000);
  const user = await dbHelper.getDb().query.usersTable.findFirst({
    where: and(
      eq(usersTable.state, state),
      gt(usersTable.updatedAt, thirtyMinutesAgo),
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
    exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 7, // expires in 7 days
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
    const apostropheIsSeparator: boolean = json.apostropheIsSeparator ?? true;
    // Per-batch reference translation (defaults applied when omitted).
    const refIetf: string = json.refIetf || DEFAULT_REF_IETF;
    const refResourceType: string =
      json.refResourceType || DEFAULT_REF_RESOURCE_TYPE;

    if (models.length === 0) {
      throw new HTTPException(404, { message: "no models provided" });
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

    // Resolve the language (reuse an existing row, else create from BIEL).
    const languageInfo = await getLanguageInfo(ietf_code);
    if (!languageInfo) {
      throw new HTTPException(404, { message: "language not found" });
    }
    const languageId = await dbHelper.upsertLanguage(languageInfo);
    const resourceId = await dbHelper.upsertResource(resource_type, languageId);

    // Resolve the per-batch reference translation to a resource id (its verses
    // are downloaded later during ingestion).
    const refLanguageInfo = await getLanguageInfo(refIetf);
    if (!refLanguageInfo) {
      throw new HTTPException(404, {
        message: `reference language (${refIetf}) not found`,
      });
    }
    const refLanguageId = await dbHelper.upsertLanguage(refLanguageInfo);
    const refResourceId = await dbHelper.upsertResource(
      refResourceType,
      refLanguageId,
    );

    // TODO add current user (AND user_id = ? - user.id)
    const dbBatch =
      (await dbHelper.getDb().query.batchesTable.findFirst({
        where: eq(batchesTable.resourceId, resourceId),
        columns: {
          id: true,
          pending: true,
          ingesting: true,
        },
      })) || null;

    let batchId = dbBatch?.id;

    const ingestFields = {
      languageId: languageId,
      resourceId: resourceId,
      refResourceId: refResourceId,
      models: JSON.stringify(models),
      apostropheIsSeparator: apostropheIsSeparator,
      ingesting: true,
      pending: false,
      error: null,
    };

    if (!batchId) {
      batchId = uuid4();

      await dbHelper
        .getDb()
        .insert(batchesTable)
        .values({
          id: batchId,
          userId: user.id,
          ...ingestFields,
        });
    } else {
      if (dbBatch?.pending || dbBatch?.ingesting) {
        throw new HTTPException(403, { message: "batch in progress" });
      }
      await dbHelper
        .getDb()
        .update(batchesTable)
        .set({
          ...ingestFields,
          updatedAt: new Date(),
        })
        .where(eq(batchesTable.id, batchId));
    }

    const details: BatchDetails = {
      status: BatchStatus.QUEUED,
      error: null,
      output: [],
      progress: emptyProgress,
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

    const resourceId = await dbHelper.getResourceId(ietf_code, resource_type);
    const dbBatch = resourceId
      ? await dbHelper.getDb().query.batchesTable.findFirst({
          where: eq(batchesTable.resourceId, resourceId),
          columns: { id: true },
        })
      : null;

    if (!dbBatch) {
      throw new HTTPException(404, { message: "batch not found" });
    }

    const words = await dbHelper.getDb().query.wordsTable.findMany({
      where: eq(wordsTable.batchId, dbBatch.id),
      with: {
        models: {
          orderBy: [asc(modelsTable.model)],
        },
        reviews: true,
        verse: true,
      },
      orderBy: [asc(wordsTable.word)],
    });

    const statusMap: { [key: number]: string } = {
      0: "Likely Incorrect",
      1: "Likely Correct",
      [-1]: "Not Processed",
    };

    const getConsensus = (st: number[]) => {
      // Only 0 (incorrect) and 1 (correct) are valid votes; ignore anything else.
      const correct = st.filter((s) => s === 1).length;
      const incorrect = st.filter((s) => s === 0).length;
      if (correct === 0 && incorrect === 0) return "Not Processed";
      if (correct > incorrect) return "Likely Correct";
      if (incorrect > correct) return "Likely Incorrect";
      return "Review Needed";
    };

    c.header("Content-Type", "text/csv");
    c.header("Content-Disposition", 'attachment; filename="report.csv"');

    return stream(c, async (s) => {
      // Header
      await s.write(
        "word,book,chapter,verse,model1,model2,model3,AI consensus,correct/reviews,verdict,anomaly\n",
      );

      // Body
      for (const word of words) {
        const book = word.verse.bookCode;
        const chapter = word.verse.chapter;
        const verse = word.verse.verse;
        const modelResults = word.models.map(
          (m) => `"${m.model}\n${statusMap[m.status]}"`,
        );

        const totalReviews = word.reviews.length;

        if (totalReviews === 0) continue;

        const correctReviews = word.reviews.filter((r) => r.correct).length;
        const reviewsStr = `${correctReviews}/${totalReviews}`;
        let verdict = "";
        if (totalReviews > 0) {
          verdict = correctReviews / totalReviews >= 0.5 ? "Yes" : "No";
        }
        const consensus = getConsensus(word.models.map((m) => m.status));
        let anomaly = "";
        if (
          (consensus === "Likely Incorrect" && verdict === "Yes") ||
          (consensus === "Likely Correct" && verdict === "No")
        ) {
          anomaly = "⚠️";
        }

        const row = [
          word.word,
          book || "",
          chapter || "",
          verse || "",
          ...modelResults,
          consensus,
          reviewsStr,
          verdict,
          anomaly,
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

    const resourceId = await dbHelper.getResourceId(ietf_code, resource_type);
    const dbBatch = resourceId
      ? await dbHelper.getDb().query.batchesTable.findFirst({
          where: eq(batchesTable.resourceId, resourceId),
          columns: {
            id: true,
            pending: true,
            ingesting: true,
            error: true,
            refResourceId: true,
          },
          with: {
            user: true,
          },
        })
      : null;

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
            -- Only 0/1 count as votes; a word with no valid votes is excluded.
            WHEN count(*) FILTER (WHERE status IN (0, 1)) = 0 THEN NULL
            WHEN count(*) FILTER (WHERE status = 1) > count(*) FILTER (WHERE status = 0) THEN 'Correct'
            WHEN count(*) FILTER (WHERE status = 0) > count(*) FILTER (WHERE status = 1) THEN 'Incorrect'
            ELSE 'Review Needed'
          END
        `.as("consensus"),
        isProcessed: sql<boolean>`NOT bool_or(status = -1)`.as("is_processed"),
      })
      .from(modelsTable)
      .groupBy(modelsTable.wordId)
      .as("consensus_subquery");

    const [stats] = await dbHelper
      .getDb()
      .select({
        correct: count(sql`CASE WHEN consensus = 'Correct' THEN 1 END`),
        incorrect: count(sql`CASE WHEN consensus = 'Incorrect' THEN 1 END`),
        reviewNeeded: count(
          sql`CASE WHEN consensus = 'Review Needed' THEN 1 END`,
        ),
        total: count(wordsTable.id),
        completed: count(sql`CASE WHEN is_processed THEN 1 END`),
      })
      .from(wordsTable)
      .leftJoin(consensusSubquery, eq(wordsTable.id, consensusSubquery.wordId))
      .where(eq(wordsTable.batchId, dbBatch.id));

    const userReviewCounts = await dbHelper
      .getDb()
      .select({
        userId: wordReviewsTable.userId,
        count: count(),
      })
      .from(wordReviewsTable)
      .innerJoin(wordsTable, eq(wordReviewsTable.wordId, wordsTable.id))
      .where(eq(wordsTable.batchId, dbBatch.id))
      .groupBy(wordReviewsTable.userId);

    const totalReviews = userReviewCounts.reduce(
      (sum, row) => sum + row.count,
      0,
    );
    const averageReviews =
      userReviewCounts.length > 0 ? totalReviews / userReviewCounts.length : 0;

    const statsInfo = {
      ...stats,
      reviewed: Math.round(averageReviews),
    };

    const progress: BatchProgress = {
      correct: statsInfo.correct,
      incorrect: statsInfo.incorrect,
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

    if (dbBatch.ingesting) {
      // Still preparing the source — keep the client polling + spinner running.
      status = BatchStatus.QUEUED;
    } else if (!dbBatch.pending) {
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

    const reference = dbBatch.refResourceId
      ? await dbHelper.getResourceRef(dbBatch.refResourceId)
      : null;

    const batch: Batch = {
      id: dbBatch.id,
      ietf_code: ietf_code,
      resource_type: resource_type,
      details: details,
      creator: creator,
      reference: reference
        ? {
            ietf: reference.ietf,
            resource_type: reference.resourceType,
            name: reference.name,
          }
        : null,
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
  const db = dbHelper.getDb();

  const HARDCODED_TOTAL_LIMIT = 370;

  try {
    const ietf_code = c.req.param("ietf_code");
    const resource_type = c.req.param("resource_type");
    const payload = c.get("jwtPayload");

    const user = await dbHelper.getDb().query.usersTable.findFirst({
      where: eq(usersTable.email, payload.email),
    });

    if (!user) {
      throw new HTTPException(404, { message: "user not found" });
    }

    const page = parseInt(c.req.query("page") || "1", 10);
    const limit = parseInt(c.req.query("limit") || "4", 10);

    const resourceId = await dbHelper.getResourceId(ietf_code, resource_type);
    const dbBatch = resourceId
      ? await dbHelper.getDb().query.batchesTable.findFirst({
          where: eq(batchesTable.resourceId, resourceId),
          columns: { id: true, pending: true, resourceId: true },
          with: {
            user: true,
          },
        })
      : null;

    if (!dbBatch) {
      throw new HTTPException(404, { message: "batch not found" });
    }

    if (dbBatch.pending) {
      throw new HTTPException(400, { message: "batch is still processing" });
    }

    const categorizedGoodWordsSubQuery = db
      .select({
        wordId: modelsTable.wordId,
        status: min(modelsTable.status).as("status"),
      })
      .from(modelsTable)
      .innerJoin(wordsTable, eq(modelsTable.wordId, wordsTable.id))
      .where(eq(wordsTable.batchId, dbBatch.id))
      .groupBy(modelsTable.wordId)
      .having(
        and(
          eq(min(modelsTable.status), max(modelsTable.status)),
          inArray(min(modelsTable.status), [0, 1]),
        ),
      )
      .as("categorized_good_words");

    const categoryCounts = await db
      .select({
        status: categorizedGoodWordsSubQuery.status,
        count: count().as("count"),
      })
      .from(categorizedGoodWordsSubQuery)
      .groupBy(categorizedGoodWordsSubQuery.status);

    const totalGoodWords = categoryCounts.reduce(
      (sum, row) => sum + row.count,
      0,
    );

    let sampledGoodWordsSubQuery;

    if (totalGoodWords <= HARDCODED_TOTAL_LIMIT) {
      sampledGoodWordsSubQuery = db
        .select({ wordId: categorizedGoodWordsSubQuery.wordId })
        .from(categorizedGoodWordsSubQuery)
        .as("good_words");
    } else {
      const limitsPerStatus = categoryCounts.map((category) => ({
        status: category.status,
        limit: Math.round(
          (category.count / totalGoodWords) * HARDCODED_TOTAL_LIMIT,
        ),
      }));

      const summedLimits = limitsPerStatus.reduce(
        (sum, cat) => sum + cat.limit,
        0,
      );
      if (
        summedLimits !== HARDCODED_TOTAL_LIMIT &&
        limitsPerStatus.length > 0
      ) {
        limitsPerStatus[0].limit += HARDCODED_TOTAL_LIMIT - summedLimits;
      }

      const queriesPerStatus = limitsPerStatus.map((cat) => {
        return db
          .select({ wordId: categorizedGoodWordsSubQuery.wordId })
          .from(categorizedGoodWordsSubQuery)
          .where(eq(categorizedGoodWordsSubQuery.status, cat.status))
          .limit(cat.limit);
      });

      if (queriesPerStatus.length === 0) {
        sampledGoodWordsSubQuery = db
          .select({ wordId: modelsTable.wordId })
          .from(modelsTable)
          .where(sql`false`)
          .as("good_words");
      } else if (queriesPerStatus.length === 1) {
        sampledGoodWordsSubQuery = queriesPerStatus[0].as("good_words");
      } else {
        const [firstQuery, secondQuery, ...restOfQueries] = queriesPerStatus;
        sampledGoodWordsSubQuery = unionAll(
          firstQuery,
          secondQuery,
          ...restOfQueries,
        ).as("good_words");
      }
    }

    const countResults = await db
      .select({
        totalCount: count(wordsTable.id),
        reviewedCount: count(wordReviewsTable.pk),
      })
      .from(wordsTable)
      .innerJoin(
        sampledGoodWordsSubQuery,
        eq(wordsTable.id, sampledGoodWordsSubQuery.wordId),
      )
      .leftJoin(
        wordReviewsTable,
        and(
          eq(wordsTable.id, wordReviewsTable.wordId),
          eq(wordReviewsTable.userId, user.id),
        ),
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

    // Fetch only the words for the requested page, joined to their verse so the
    // review screen gets ref + source text without parsing USFM on the client.
    const wordsData = await db
      .select({
        word: wordsTable.word,
        review: wordReviewsTable,
        book: versesTable.bookCode,
        chapter: versesTable.chapter,
        verse: versesTable.verse,
        text: versesTable.text,
      })
      .from(wordsTable)
      .innerJoin(
        sampledGoodWordsSubQuery,
        eq(wordsTable.id, sampledGoodWordsSubQuery.wordId),
      )
      .innerJoin(versesTable, eq(wordsTable.verseId, versesTable.id))
      .leftJoin(
        wordReviewsTable,
        and(
          eq(wordsTable.id, wordReviewsTable.wordId),
          eq(wordReviewsTable.userId, user.id),
        ),
      )
      .orderBy(asc(wordsTable.word))
      .limit(limit)
      .offset(offset);

    // Map the database results to the desired response format
    const output = wordsData.map((row) => {
      const wordResponse: WordResponse = {
        word: row.word,
        ref: `${row.book}:${row.chapter}:${row.verse}`,
        text: row.text,
        correct: row.review ? row.review.correct : null,
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

app.put("/api/review/reset/:batch_id", async (c) => {
  const dbHelper = c.get("db");

  try {
    const batchId = c.req.param("batch_id");
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

    const reset = await dbHelper
      .getDb()
      .delete(wordReviewsTable)
      .where(
        inArray(
          wordReviewsTable.wordId,
          dbHelper
            .getDb()
            .select({ id: wordsTable.id })
            .from(wordsTable)
            .where(eq(wordsTable.batchId, batchId)),
        ),
      );

    return c.json(reset.length > 0);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error resetting review: ${
        error.message || error
      }`,
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
        ietfCode: languagesTable.code,
        resourceType: resourcesTable.resourceType,
        user: {
          username: usersTable.username,
        },
      })
      .from(batchesTable)
      .innerJoin(wordsTable, eq(batchesTable.id, wordsTable.batchId))
      .innerJoin(modelsTable, eq(wordsTable.id, modelsTable.wordId))
      .innerJoin(usersTable, eq(batchesTable.userId, usersTable.id))
      .innerJoin(resourcesTable, eq(batchesTable.resourceId, resourcesTable.id))
      .innerJoin(
        languagesTable,
        eq(resourcesTable.languageId, languagesTable.id),
      );

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

    const wordStrings = request.words.map((w) => w.word);
    const foundWords = await dbHelper
      .getDb()
      .select({
        id: wordsTable.id,
        word: wordsTable.word,
      })
      .from(wordsTable)
      .where(
        and(
          eq(wordsTable.batchId, request.batchId),
          inArray(wordsTable.word, wordStrings),
        ),
      );

    const wordMap = new Map(foundWords.map((row) => [row.word, row.id]));
    const reviewsToUpsert = request.words
      .filter((w) => wordMap.has(w.word))
      .map((w) => ({
        wordId: wordMap.get(w.word)!,
        userId: user.id,
        correct: w.correct,
      }));

    if (reviewsToUpsert.length > 0) {
      await dbHelper
        .getDb()
        .insert(wordReviewsTable)
        .values(reviewsToUpsert)
        .onConflictDoUpdate({
          target: [wordReviewsTable.wordId, wordReviewsTable.userId],
          set: { correct: sql`excluded.correct` },
        });
    }

    return c.json(true);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error updating word: ${error.message || error}`,
    });
  }
});

app.get("*", async (c) => {
  const response = await c.env.ASSETS.fetch(c.req.raw);
  if (response.status === 404) {
    const indexRequest = new Request(new URL("/index.html", c.req.url), {
      method: "GET",
      headers: c.req.raw.headers,
    });
    return c.env.ASSETS.fetch(indexRequest);
  }
  return response;
});

export default {
  fetch: app.fetch,
  async scheduled(
    controller: ScheduledController,
    env: CloudflareBindings,
    ctx: ExecutionContext,
  ) {
    const validateAndMapResults = (
      words: WordEntity[],
      chatResponse: ChatResponse[],
      retries: number,
    ): ChatResponse[] => {
      const usedIndices = new Set<number>();
      let hasLoggedContext = false;

      // Helper to remove accents and lower case: "Bånana" -> "banana"
      const normalize = (str: string) =>
        str
          .normalize("NFD")
          .replace(/[\u0300-\u036f]/g, "")
          .toLowerCase()
          .trim();

      // Helper: Lazy Logger for the "Big Picture"
      const logContextOnce = () => {
        if (hasLoggedContext) return;
        hasLoggedContext = true;

        const refListStr = words.map((w) => w.word).join(", ");
        const chatListStr = chatResponse.map((w) => w.word).join(", ");

        console.warn(
          `\n🔍 MISMATCH DETECTED - DEBUG CONTEXT\n` +
            `--------------------------------------------------\n` +
            `Sizes: Ref (${words.length}) vs Chat (${chatResponse.length})\n` +
            `Ref List:  [${refListStr}]\n` +
            `Chat List: [${chatListStr}]\n` +
            `--------------------------------------------------`,
        );
      };

      return words.map((refItem) => {
        const targetStrict = refItem.word;
        const targetLoose = normalize(refItem.word);

        // Try to find the exact word anywhere in the list
        let matchIndex = -1;
        let matchType: "strict" | "loose" | "missing" = "missing";

        matchIndex = chatResponse.findIndex(
          (chatItem, index) =>
            chatItem.word === targetStrict && !usedIndices.has(index),
        );

        if (matchIndex !== -1) {
          matchType = "strict";
        }

        // If strict failed, try matching normalized strings (accents, case)
        if (matchIndex === -1) {
          matchIndex = chatResponse.findIndex(
            (chatItem, index) =>
              normalize(chatItem.word) === targetLoose &&
              !usedIndices.has(index),
          );
          if (matchIndex !== -1) matchType = "loose";
        }

        if (matchIndex !== -1) {
          usedIndices.add(matchIndex); // Claim this index so it's not used again
          const foundItem = chatResponse[matchIndex];

          // LOGGING: Only log if it wasn't a perfect strict match
          if (matchType !== "strict") {
            logContextOnce(); // Print the lists first if we haven't yet

            const msg = `⚠️ Loose Match for "${refItem.word}" -> Found "${foundItem.word}"`;
            console.warn(msg);
          }

          return {
            ...refItem,
            status: foundItem.status,
          };
        }

        // If completely MISSING
        logContextOnce(); // Print context
        console.error(`❌ Missing Word: "${refItem.word}"`);

        if (retries >= 3) {
          console.error(
            `❌ Giving up on word "${refItem.word}" after ${retries} retries. Marking as failed.`,
          );
        }

        return {
          ...refItem,
          status: retries < 3 ? -1 : 0,
        };
      });
    };

    try {
      const client = new AiClient(env);
      const dbHelper = new DbHelper(env);

      // Source ingestion takes priority over AI processing. Handle one
      // ingesting batch per tick, then return; AI processing runs on later ticks.
      const ingestBatch = await dbHelper.getDb().query.batchesTable.findFirst({
        where: eq(batchesTable.ingesting, true),
        orderBy: [asc(batchesTable.createdAt)],
      });

      if (ingestBatch) {
        await ingestSource(dbHelper, ingestBatch);
        return;
      }

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
                      eq(modelsTable.status, -1),
                    ),
                  ),
              ),
            ),
          with: {
            models: true,
            verse: true,
          },
          limit: WORDS_PER_BATCH,
        });

        if (words.length > 0) {
          interface TmpModel {
            model: string;
            words: { word: string; status: number }[];
            retries: number;
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
                  retries: modelObj.retries,
                });
              }
            });
            return acc;
          }, []);

          const modelsResults: ModelResult[] = [];
          let wordsPrompt = words.map((w) => w.word).join(", ");

          // Language name for the AI prompt, derived from the batch's language.
          const languageName = batch.languageId
            ? await dbHelper.getLanguageName(batch.languageId)
            : "";

          // Build per-word context: the source verse each word occurs in, plus
          // the aligned verse from the reference resource. Only the verses this
          // chunk needs are fetched.
          const referenceByRef = batch.refResourceId
            ? await dbHelper.getVerseTextsByRefs(
                batch.refResourceId,
                words.map((w) => ({
                  book: w.verse.bookCode,
                  chapter: w.verse.chapter,
                  verse: w.verse.verse,
                })),
              )
            : new Map<string, string>();

          const wordContexts: WordContext[] = words.map((w) => {
            const ref = `${w.verse.bookCode}:${w.verse.chapter}:${w.verse.verse}`;
            return {
              word: w.word,
              reference: `${w.verse.bookCode} ${w.verse.chapter}:${w.verse.verse}`,
              source: w.verse.text,
              referenceVerse: referenceByRef.get(ref) ?? "",
            };
          });

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
                  retries: model.retries + 1,
                };
                modelsResults.push(modelResult);
              } else {
                const chatResponse = await client.chat(
                  model.model,
                  languageName,
                  wordContexts,
                );

                if (!isChatError(chatResponse)) {
                  // Sometimes the AI might change the words, so we re-map them here from the original array
                  const results = validateAndMapResults(
                    words,
                    chatResponse,
                    model.retries,
                  );

                  const modelResult: ModelResult = {
                    model: model.model,
                    results: results,
                    retries: model.retries + 1,
                  };
                  modelsResults.push(modelResult);
                } else {
                  errorDetails = chatResponse;
                }
              }
            } catch (error: any) {
              errorDetails = {
                prompt: wordsPrompt,
                message: error.message || error,
                model: model.model,
                response: null,
              };
            }
          }

          if (modelsResults.length > 0) {
            const updateError = await dbHelper.updateModelResults(
              batchId,
              modelsResults,
            );

            if (updateError) {
              updateError.prompt = wordsPrompt;
              errorDetails = updateError;
            }
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
      console.error("cron error:", error);
    }
  },
};
