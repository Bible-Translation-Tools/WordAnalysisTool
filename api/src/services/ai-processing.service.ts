import { Repositories } from "../db/repositories";
import AiClient from "../ai/client";
import { BatchError, ChatResponse, ModelResult, WordContext } from "../types";
import { isChatError } from "../lib/utils";
import { BATCH_MAX_RETRIES, WORDS_PER_BATCH } from "../config/constants";

/** Remove accents and lower-case: "Bånana" -> "banana". */
function normalize(str: string): string {
  return str
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .trim();
}

/**
 * Re-align an AI response back onto the words we asked about. Models sometimes
 * reorder, re-case, or drop words, so we match strict first, then accent/case
 * insensitive, and finally mark a still-missing word as unchecked (-1) for
 * retry — or incorrect (0) once we've given up after 3 retries. Pure and
 * order-preserving over `words`.
 */
export function mapResultsToWords<T extends { word: string }>(
  words: T[],
  chatResponse: ChatResponse[],
  retries: number,
): (T & { status: number })[] {
  const usedIndices = new Set<number>();
  let hasLoggedContext = false;

  const logContextOnce = () => {
    if (hasLoggedContext) return;
    hasLoggedContext = true;
    console.warn(
      `\n🔍 MISMATCH DETECTED - DEBUG CONTEXT\n` +
        `--------------------------------------------------\n` +
        `Sizes: Ref (${words.length}) vs Chat (${chatResponse.length})\n` +
        `Ref List:  [${words.map((w) => w.word).join(", ")}]\n` +
        `Chat List: [${chatResponse.map((w) => w.word).join(", ")}]\n` +
        `--------------------------------------------------`,
    );
  };

  return words.map((refItem) => {
    const targetStrict = refItem.word;
    const targetLoose = normalize(refItem.word);

    let matchIndex = chatResponse.findIndex(
      (chatItem, index) =>
        chatItem.word === targetStrict && !usedIndices.has(index),
    );
    let matchType: "strict" | "loose" | "missing" =
      matchIndex !== -1 ? "strict" : "missing";

    if (matchIndex === -1) {
      matchIndex = chatResponse.findIndex(
        (chatItem, index) =>
          normalize(chatItem.word) === targetLoose && !usedIndices.has(index),
      );
      if (matchIndex !== -1) matchType = "loose";
    }

    if (matchIndex !== -1) {
      usedIndices.add(matchIndex);
      const foundItem = chatResponse[matchIndex];
      if (matchType !== "strict") {
        logContextOnce();
        console.warn(
          `⚠️ Loose Match for "${refItem.word}" -> Found "${foundItem.word}"`,
        );
      }
      return { ...refItem, status: foundItem.status };
    }

    logContextOnce();
    console.error(`❌ Missing Word: "${refItem.word}"`);
    if (retries >= 3) {
      console.error(
        `❌ Giving up on word "${refItem.word}" after ${retries} retries. Marking as failed.`,
      );
    }
    return { ...refItem, status: retries < 3 ? -1 : 0 };
  });
}

type WordWithVerse = {
  word: string;
  verse: { bookCode: string; chapter: number; verse: string; text: string };
};

/** Build per-word AI context: source verse + aligned reference verse. */
export function buildWordContexts(
  words: WordWithVerse[],
  referenceByRef: Map<string, string>,
): WordContext[] {
  return words.map((w) => {
    const ref = `${w.verse.bookCode}:${w.verse.chapter}:${w.verse.verse}`;
    return {
      word: w.word,
      reference: `${w.verse.bookCode} ${w.verse.chapter}:${w.verse.verse}`,
      source: w.verse.text,
      referenceVerse: referenceByRef.get(ref) ?? "",
    };
  });
}

export function createAiProcessor(repos: Repositories, client: AiClient) {
  /** Process one chunk of the oldest pending batch through every model. */
  async function processPending(): Promise<void> {
    const batch = await repos.batches.findPending();
    if (!batch) return;

    const batchId = batch.id;
    let errorDetails: BatchError | null = null;

    const words = await repos.words.findUnprocessedForBatch(
      batchId,
      WORDS_PER_BATCH,
    );

    if (words.length > 0) {
      interface TmpModel {
        model: string;
        words: { word: string; status: number }[];
        retries: number;
      }

      const models = words.reduce((acc: TmpModel[], wordObj) => {
        wordObj.models.forEach((modelObj) => {
          const existing = acc.find((m) => m.model === modelObj.model);
          if (existing) {
            existing.words.push({ word: wordObj.word, status: modelObj.status });
          } else {
            acc.push({
              model: modelObj.model,
              words: [{ word: wordObj.word, status: modelObj.status }],
              retries: modelObj.retries,
            });
          }
        });
        return acc;
      }, []);

      const modelsResults: ModelResult[] = [];
      const wordsPrompt = words.map((w) => w.word).join(", ");

      const languageName = batch.languageId
        ? await repos.languages.getName(batch.languageId)
        : "";

      const referenceByRef = batch.refResourceId
        ? await repos.verses.getTextsByRefs(
            batch.refResourceId,
            words.map((w) => ({
              book: w.verse.bookCode,
              chapter: w.verse.chapter,
              verse: w.verse.verse,
            })),
          )
        : new Map<string, string>();

      const wordContexts = buildWordContexts(words, referenceByRef);

      for (const model of models) {
        try {
          const completed = model.words.every((word) => word.status > -1);
          if (completed) {
            modelsResults.push({
              model: model.model,
              results: model.words.map(({ word, status }) => ({ word, status })),
              retries: model.retries + 1,
            });
          } else {
            const chatResponse = await client.chat(
              model.model,
              languageName,
              wordContexts,
            );

            if (!isChatError(chatResponse)) {
              const results = mapResultsToWords(
                words,
                chatResponse,
                model.retries,
              );
              modelsResults.push({
                model: model.model,
                results,
                retries: model.retries + 1,
              });
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
        const updateError = await repos.models.updateResults(
          batchId,
          modelsResults,
        );
        if (updateError) {
          updateError.prompt = wordsPrompt;
          errorDetails = updateError;
        }
      }
    }

    const toUpdate: Record<string, unknown> = { updatedAt: new Date() };

    if (words.length === 0) {
      toUpdate.pending = false;
    }

    if (errorDetails) {
      const retries = batch.retries + 1;
      if (retries >= BATCH_MAX_RETRIES) {
        toUpdate.pending = false;
        toUpdate.retries = 0;
      } else {
        toUpdate.retries = retries;
      }
      toUpdate.error = JSON.stringify(errorDetails);
    } else {
      toUpdate.retries = 0;
      toUpdate.error = "";
    }

    await repos.batches.updateById(batchId, toUpdate);
  }

  return { processPending };
}

export type AiProcessor = ReturnType<typeof createAiProcessor>;
