import { BatchError, ChatResponse, WordContext } from "../types";
import { getModelConfig } from "./registry";
import type { AiChat } from "./client";

/** FNV-1a 32-bit hash: cheap, deterministic, spreads similar words apart. */
function hash(str: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h;
}

// Share of words every model marks misspelled, and share on which one model
// disagrees with the others (so stats show "Review Needed" rows too).
const INCORRECT_PERCENT = 15;
const DISAGREE_PERCENT = 5;

/**
 * Deterministic stand-in for the AI providers, used when `ENVIRONMENT=dev`. The
 * verdict depends only on the word (and a little on the model), so re-running
 * a batch gives the same results and no API key or network is needed.
 */
export function mockStatus(word: string, model: string): number {
  const base = hash(word) % 100 < INCORRECT_PERCENT ? 0 : 1;
  const flip = hash(`${model}:${word}`) % 100 < DISAGREE_PERCENT;
  return flip ? 1 - base : base;
}

export class MockAiClient implements AiChat {
  async chat(
    model: string,
    _language: string,
    words: WordContext[],
  ): Promise<ChatResponse[] | BatchError> {
    if (getModelConfig(model) === null) {
      return Promise.reject("model is invalid");
    }
    console.log(`[DEV] mocking ${model} for ${words.length} words`);
    return words.map((w) => ({ word: w.word, status: mockStatus(w.word, model) }));
  }
}
