import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { AppEnv } from "../bindings";
import { getUser } from "../middleware/auth";
import { WordParams } from "../types";

const router = new Hono<AppEnv>();

router.post("/api/word", async (c) => {
  const { repos } = c.get("container");
  const request: WordParams = await c.req.json();

  try {
    const user = await getUser(c);

    const wordId = await repos.words.findIdByWord(
      request.batchId,
      request.word,
    );
    if (!wordId) {
      throw new HTTPException(404, { message: "word not found" });
    }

    await repos.reviews.upsert({
      wordId,
      userId: user.id,
      correct: request.correct,
    });

    return c.json(true);
  } catch (error: any) {
    throw new HTTPException(403, {
      message: `${error.code}: error updating word: ${error.message || error}`,
    });
  }
});

export default router;
