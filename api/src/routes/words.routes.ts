import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { AppEnv } from "../bindings";
import { getUser } from "../middleware/auth";
import { WordsParams } from "../types";

const router = new Hono<AppEnv>();

router.post("/api/words", async (c) => {
  const { repos } = c.get("container");
  const request: WordsParams = await c.req.json();

  try {
    const user = await getUser(c);

    const wordStrings = request.words.map((w) => w.word);
    const wordMap = await repos.words.findIdsByWords(
      request.batchId,
      wordStrings,
    );

    const reviewsToUpsert = request.words
      .filter((w) => wordMap.has(w.word))
      .map((w) => ({
        wordId: wordMap.get(w.word)!,
        userId: user.id,
        correct: w.correct,
      }));

    await repos.reviews.upsertMany(reviewsToUpsert);

    return c.json(true);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error updating word: ${error.message || error}`,
    });
  }
});

export default router;
