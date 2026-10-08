import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { DRIVERS, DriverFixture } from "./helpers/drivers";
import { Repositories } from "../src/db/store";
import { Verse } from "../src/usfm";
import { computeBatchProgress } from "../src/services/stats.service";
import { sampleReviewWords } from "../src/services/review.service";

const LANG = {
  ietfCode: "xx",
  englishName: "Testish",
  nationalName: "Testisch",
  direction: "ltr",
};
const REF_LANG = { ...LANG, ietfCode: "en", englishName: "English" };

const OAUTH = {
  email: "a@example.com",
  username: "alice",
  wacsUserId: 1,
  accessToken: "at",
  refreshToken: "rt",
  tokenType: "bearer",
  state: "state-1",
};

function verses(n: number, book = "gen"): Verse[] {
  return Array.from({ length: n }, (_, i) => ({
    book,
    chapter: Math.floor(i / 50) + 1,
    verse: String((i % 50) + 1),
    text: `verse ${i} of ${book}`,
  }));
}

/** A batch with `n` words over `n` verses; returns ids needed by the tests. */
async function seedBatch(
  repos: Repositories,
  opts: { words?: number; models?: string[]; id?: string } = {},
) {
  const wordCount = opts.words ?? 6;
  const models = opts.models ?? ["m1", "m2"];
  const batchId = opts.id ?? "batch-1";

  await repos.users.upsertFromOAuth(OAUTH);
  const user = (await repos.users.findByEmail(OAUTH.email))!;
  const languageId = await repos.languages.upsert(LANG);
  const resourceId = await repos.resources.upsert("ulb", languageId);
  const refLanguageId = await repos.languages.upsert(REF_LANG);
  const refResourceId = await repos.resources.upsert("ulb", refLanguageId);

  const vs = verses(wordCount);
  await repos.verses.insertMany(vs, resourceId);
  await repos.verses.insertMany(
    vs.map((v) => ({ ...v, text: `ref ${v.text}` })),
    refResourceId,
  );
  const refMap = await repos.verses.getRefMap(resourceId);

  await repos.batches.create({
    id: batchId,
    userId: user.id,
    languageId,
    resourceId,
    refResourceId,
    models: JSON.stringify(models),
    ingesting: false,
    pending: true,
  });

  const words = vs.map((v, i) => ({
    word: `w${String(i).padStart(4, "0")}`,
    verseId: refMap.get(`${v.book}:${v.chapter}:${v.verse}`)!,
  }));
  await repos.words.insertMany(words, batchId);
  const wordIds = await repos.words.fetchUnprocessedIds(words, batchId);
  await repos.models.seed(wordIds, models);

  return { user, languageId, resourceId, refResourceId, batchId, words, wordIds, models };
}

describe.each(DRIVERS)("%s repositories", (_name, make) => {
  let fx: DriverFixture;
  let repos: Repositories;

  beforeAll(async () => {
    fx = await make();
    repos = fx.repos;
  }, 60_000);

  afterAll(async () => {
    await fx.close();
  });

  beforeEach(async () => {
    // A batch cascades to words -> models/reviews, so deleting it isolates
    // each test; languages, resources and verses are upserted, so re-seeding
    // is idempotent.
    await repos.batches.deleteById("batch-1");
    await repos.batches.deleteById("batch-2");
  });

  describe("users", () => {
    it("upserts by email and reads back", async () => {
      await repos.users.upsertFromOAuth(OAUTH);
      await repos.users.upsertFromOAuth({ ...OAUTH, username: "alice2", state: "s2" });

      const user = await repos.users.findByEmail(OAUTH.email);
      expect(user?.username).toBe("alice2");
      expect(user?.state).toBe("s2");
      expect(user?.createdAt).toBeInstanceOf(Date);
      expect(user?.updatedAt).toBeInstanceOf(Date);
    });

    it("finds a fresh state and clears it", async () => {
      await repos.users.upsertFromOAuth({ ...OAUTH, state: "fresh" });
      const old = new Date(Date.now() - 60_000);
      const future = new Date(Date.now() + 60_000);

      expect(await repos.users.findByFreshState("fresh", old)).toBeDefined();
      expect(await repos.users.findByFreshState("fresh", future)).toBeUndefined();
      expect(await repos.users.findByFreshState("nope", old)).toBeUndefined();

      await repos.users.clearState("fresh");
      expect(await repos.users.findByFreshState("fresh", old)).toBeUndefined();
      expect((await repos.users.findByEmail(OAUTH.email))?.state).toBeNull();
    });
  });

  describe("languages & resources", () => {
    it("upserts idempotently and refreshes names", async () => {
      const id1 = await repos.languages.upsert(LANG);
      const id2 = await repos.languages.upsert({ ...LANG, englishName: "Renamed" });
      expect(id2).toBe(id1);
      expect(await repos.languages.getName(id1)).toBe("Renamed");
      expect(await repos.languages.getName(999_999)).toBe("");

      const r1 = await repos.resources.upsert("ulb", id1);
      const r2 = await repos.resources.upsert("ulb", id1);
      const r3 = await repos.resources.upsert("udb", id1);
      expect(r2).toBe(r1);
      expect(r3).not.toBe(r1);

      expect(await repos.resources.getId("xx", "ulb")).toBe(r1);
      expect(await repos.resources.getId("xx", "missing")).toBeNull();
      expect(await repos.resources.getRef(r3)).toEqual({
        ietf: "xx",
        resourceType: "udb",
        name: "Renamed",
      });
      expect(await repos.resources.getRef(999_999)).toBeNull();

      // Restore the name other tests expect.
      await repos.languages.upsert(LANG);
    });
  });

  describe("verses", () => {
    it("inserts in bulk (beyond one statement's params), dedupes and upserts text", async () => {
      const languageId = await repos.languages.upsert(LANG);
      const resourceId = await repos.resources.upsert("big", languageId);

      const many = verses(500, "exo");
      // A duplicate ref inside one call: last occurrence wins.
      many.push({ ...many[0], text: "overridden" });
      await repos.verses.insertMany(many, resourceId);

      const stored = await repos.verses.getByResource(resourceId);
      expect(stored).toHaveLength(500);
      expect(stored.find((v) => v.chapter === 1 && v.verse === "1")?.text).toBe(
        "overridden",
      );

      // Re-inserting replaces text instead of failing on the unique index.
      await repos.verses.insertMany([{ ...many[1], text: "again" }], resourceId);
      const refMap = await repos.verses.getRefMap(resourceId);
      expect(refMap.size).toBe(500);
      const texts = await repos.verses.getTextsByRefs(resourceId, [
        many[1],
        many[1],
        ...many.slice(2, 120),
        { book: "exo", chapter: 99, verse: "1" },
      ]);
      expect(texts.get("exo:1:2")).toBe("again");
      expect(texts.size).toBe(119);
      expect(await repos.verses.getStoredBookCodes(resourceId)).toEqual(["exo"]);
      expect(await repos.verses.getTextsByRefs(resourceId, [])).toEqual(new Map());
    });
  });

  describe("batches", () => {
    it("creates, finds, updates, pauses and deletes", async () => {
      const { user, resourceId, batchId } = await seedBatch(repos);

      const found = await repos.batches.findByResourceId(resourceId);
      expect(found?.id).toBe(batchId);
      expect(found?.pending).toBe(true);
      expect(found?.ingesting).toBe(false);
      expect(found?.apostropheIsSeparator).toBe(true);
      expect(found?.retries).toBe(0);
      expect(found?.createdAt).toBeInstanceOf(Date);

      const withUser = await repos.batches.findByResourceIdWithUser(resourceId);
      expect(withUser?.user.username).toBe(user.username);

      expect((await repos.batches.findPending())?.id).toBe(batchId);
      expect(await repos.batches.findIngesting()).toBeUndefined();

      await repos.batches.updateById(batchId, {
        ingesting: true,
        error: "boom",
        retries: 3,
        updatedAt: new Date(),
      });
      const updated = await repos.batches.findIngesting();
      expect(updated?.id).toBe(batchId);
      expect(updated?.error).toBe("boom");
      expect(updated?.retries).toBe(3);

      expect(await repos.batches.pause(batchId)).toBe(true);
      expect(await repos.batches.pause("missing")).toBe(false);
      const paused = await repos.batches.findByResourceId(resourceId);
      expect(paused?.pending).toBe(false);
      expect(paused?.error).toBeNull();

      expect(await repos.batches.deleteById(batchId)).toBe(true);
      expect(await repos.batches.deleteById(batchId)).toBe(false);
      expect(await repos.batches.findByResourceId(resourceId)).toBeUndefined();
    });

    it("lists only batches whose words have model rows", async () => {
      const { batchId, user } = await seedBatch(repos);
      // A batch with no words (not ingested yet) is not listed.
      await repos.batches.create({ id: "batch-2", userId: user.id });

      const recent = await repos.batches.listRecent();
      expect(recent).toEqual([
        { id: batchId, ietfCode: "xx", resourceType: "ulb", user: { username: "alice" } },
      ]);
    });
  });

  describe("words & models", () => {
    it("seeds unchecked models and resolves words", async () => {
      const { batchId, words, wordIds, models } = await seedBatch(repos, { words: 150 });
      expect(wordIds).toHaveLength(150);

      // Already-seeded words are not unprocessed any more.
      expect(await repos.words.fetchUnprocessedIds(words, batchId)).toEqual([]);
      // Re-inserting the same words is a no-op.
      await repos.words.insertMany(words, batchId);

      const id = await repos.words.findIdByWord(batchId, words[3].word);
      expect(id).toBe(wordIds[3]);
      expect(await repos.words.findIdByWord(batchId, "nope")).toBeNull();

      const unprocessed = await repos.words.findUnprocessedForBatch(batchId, 7);
      expect(unprocessed).toHaveLength(7);
      expect(unprocessed[0].models.map((m) => m.model).sort()).toEqual(models);
      expect(unprocessed[0].models.every((m) => m.status === -1)).toBe(true);
      expect(unprocessed[0].verse.text).toContain("verse");
      expect(unprocessed[0].verse.chapter).toBe(1);
    });

    it("updates results per model in bulk and drops incomplete models on pause", async () => {
      const { batchId, words } = await seedBatch(repos, { words: 120 });

      // More words than fit in one D1 statement, untrimmed words, mixed statuses.
      await repos.models.updateResults(batchId, [
        {
          model: "m1",
          retries: 2,
          results: words.map((w, i) => ({ word: ` ${w.word} `, status: i % 2 })),
        },
        {
          model: "m2",
          retries: 1,
          // Leave the last word unchecked for m2.
          results: words.slice(0, -1).map((w) => ({ word: w.word, status: 1 })),
        },
      ]);

      const report = await repos.words.findForReport(batchId);
      expect(report).toHaveLength(120);
      expect(report.map((w) => w.word)).toEqual(words.map((w) => w.word));
      expect(report[0].models.map((m) => [m.model, m.status, m.retries])).toEqual([
        ["m1", 0, 2],
        ["m2", 1, 1],
      ]);
      expect(report[1].models.map((m) => m.status)).toEqual([1, 1]);
      expect(report[119].models.map((m) => m.status)).toEqual([1, -1]);

      const left = await repos.words.findUnprocessedForBatch(batchId, 50);
      expect(left.map((w) => w.word)).toEqual([words[119].word]);

      await repos.models.deleteIncomplete(batchId);
      const after = await repos.words.findForReport(batchId);
      expect(after[119].models).toEqual([]);
      expect(after[0].models).toHaveLength(2);
      expect(await repos.words.findUnprocessedForBatch(batchId, 50)).toEqual([]);
    });
  });

  describe("reviews & stats", () => {
    /** words 0..3 unanimous correct, 4..5 unanimous incorrect, 6 split, 7 unchecked */
    async function seedVotes() {
      const seeded = await seedBatch(repos, { words: 8 });
      const { batchId, words } = seeded;
      const vote = (status: number) => (w: { word: string }) => ({ word: w.word, status });
      await repos.models.updateResults(batchId, [
        {
          model: "m1",
          retries: 1,
          results: [
            ...words.slice(0, 4).map(vote(1)),
            ...words.slice(4, 6).map(vote(0)),
            { word: words[6].word, status: 1 },
          ],
        },
        {
          model: "m2",
          retries: 1,
          results: [
            ...words.slice(0, 4).map(vote(1)),
            ...words.slice(4, 6).map(vote(0)),
            { word: words[6].word, status: 0 },
          ],
        },
      ]);
      return seeded;
    }

    it("tallies consensus and review progress", async () => {
      const { batchId, user, wordIds } = await seedVotes();

      expect(await repos.stats.getBatchTallies(batchId)).toEqual({
        correct: 4,
        incorrect: 2,
        reviewNeeded: 1,
        total: 8,
        completed: 7,
      });

      await repos.users.upsertFromOAuth({ ...OAUTH, email: "b@example.com", username: "bob" });
      const bob = (await repos.users.findByEmail("b@example.com"))!;
      await repos.reviews.upsert({ wordId: wordIds[0], userId: user.id, correct: true });
      await repos.reviews.upsert({ wordId: wordIds[0], userId: user.id, correct: false });
      await repos.reviews.upsert({ wordId: wordIds[1], userId: user.id, correct: true });
      await repos.reviews.upsert({ wordId: wordIds[0], userId: bob.id, correct: true });

      const counts = await repos.stats.getReviewCountsByUser(batchId);
      expect(counts.sort((a, b) => a.userId - b.userId)).toEqual([
        { userId: user.id, count: 2 },
        { userId: bob.id, count: 1 },
      ]);

      const progress = await computeBatchProgress(repos, batchId);
      expect(progress).toEqual({
        correct: 4,
        incorrect: 2,
        review_needed: 1,
        reviewed: 2, // round((2 + 1) / 2)
        completed: 7,
        total: 8,
      });

      const report = await repos.words.findForReport(batchId);
      expect(report[0].reviews.map((r) => r.correct).sort()).toEqual([false, true]);

      expect(await repos.reviews.deleteByBatch(batchId)).toBe(true);
      expect(await repos.reviews.deleteByBatch(batchId)).toBe(false);
      expect(await repos.stats.getReviewCountsByUser(batchId)).toEqual([]);
    });

    it("builds the review pool, whole and sampled", async () => {
      const { batchId, user, words, wordIds } = await seedVotes();

      expect(
        (await repos.reviews.countGoodWordsByStatus(batchId)).sort(
          (a, b) => a.status - b.status,
        ),
      ).toEqual([
        { status: 0, count: 2 },
        { status: 1, count: 4 },
      ]);

      await repos.reviews.upsert({ wordId: wordIds[2], userId: user.id, correct: false });

      const whole = await repos.reviews.fetchPool(batchId, user.id, null);
      expect(whole.map((r) => r.word)).toEqual(words.slice(0, 6).map((w) => w.word));
      expect(whole[2].review?.correct).toBe(false);
      expect(whole[0].review).toBeNull();
      expect(whole[0]).toMatchObject({ book: "gen", chapter: 1, verse: "1" });
      expect(whole[0].text).toContain("verse 0");

      const limits = [
        { status: 1, limit: 2 },
        { status: 0, limit: 1 },
      ];
      const sampled = await repos.reviews.fetchPool(batchId, user.id, limits);
      expect(sampled).toHaveLength(3);
      const correctWords = words.slice(0, 4).map((w) => w.word);
      const incorrectWords = words.slice(4, 6).map((w) => w.word);
      expect(sampled.filter((r) => correctWords.includes(r.word))).toHaveLength(2);
      expect(sampled.filter((r) => incorrectWords.includes(r.word))).toHaveLength(1);
      // Ordered by word, and deterministic per user.
      expect(sampled.map((r) => r.word)).toEqual([...sampled.map((r) => r.word)].sort());
      const again = await repos.reviews.fetchPool(batchId, user.id, limits);
      expect(again.map((r) => r.word)).toEqual(sampled.map((r) => r.word));

      const single = await repos.reviews.fetchPool(batchId, user.id, [{ status: 0, limit: 1 }]);
      expect(single).toHaveLength(1);
      expect(await repos.reviews.fetchPool(batchId, user.id, [])).toEqual([]);

      const { output, progress } = await sampleReviewWords(repos, batchId, user.id);
      expect(output).toHaveLength(6);
      expect(output[2]).toEqual({
        word: words[2].word,
        ref: "gen:1:3",
        text: expect.stringContaining("verse 2"),
        correct: false,
        results: [],
      });
      expect(progress.reviewed).toBe(1);
      expect(progress.total).toBe(6);
    });
  });

  describe("cascades", () => {
    it("deleting a batch removes its words, models and reviews", async () => {
      const { batchId, user, wordIds } = await seedBatch(repos, { words: 3 });
      await repos.reviews.upsert({ wordId: wordIds[0], userId: user.id, correct: true });
      await repos.batches.deleteById(batchId);
      expect(await repos.words.findForReport(batchId)).toEqual([]);
      expect(await repos.stats.getReviewCountsByUser(batchId)).toEqual([]);
      expect(await repos.stats.getBatchTallies(batchId)).toEqual({
        correct: 0,
        incorrect: 0,
        reviewNeeded: 0,
        total: 0,
        completed: 0,
      });
    });
  });
});
