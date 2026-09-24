import { z } from "zod";
import { ChatResponse, WordContext } from "../types";

export const AiResponse = z.object({
  word: z.string(),
  status: z.number(),
});

// Object wrapper (OpenAI structured output needs a root object).
export const AiResponseObject = z.object({
  responses: z.array(AiResponse),
});

// Bare array (used by providers that accept an array root schema).
export const AiResponseList = z.array(AiResponse);

// Plain JSON Schema for providers whose SDKs take a raw schema
// (Mistral / Google) instead of a zod helper.
export const responseJsonSchema = {
  type: "object",
  properties: {
    responses: {
      type: "array",
      items: {
        type: "object",
        properties: {
          word: { type: "string" },
          status: { type: "integer" },
        },
        required: ["word", "status"],
        additionalProperties: false,
      },
    },
  },
  required: ["responses"],
  additionalProperties: false,
} as const;

export const SYSTEM_PROMPT = `You are a senior {language} linguist and orthography expert reviewing a {language} Bible translation for spelling errors.

You will receive a JSON array of entries. Each entry has:
- "word": a single {language} word to evaluate
- "reference": the verse reference the word comes from
- "source": the {language} verse text where the word occurs (context)
- "referenceVerse": the same verse in a reference translation from a related, well-resourced language (may be empty)

For each entry, classify ONLY the "word" with exactly one status:
- 1 = correctly spelled: a valid {language} word, INCLUDING inflected/declined/conjugated forms, affixed forms, rare words, and proper names (person, place, people group), regardless of rarity
- 0 = misspelled: not a valid {language} word — a typo, wrong/missing/extra/transposed letters, two words run together, or a truncated word

How to judge:
- Use "source" to see the word in context and judge whether it is a plausible {language} form (consider prefixes, suffixes, and inflection of a real root — these are correct, status 1, not errors).
- Use "referenceVerse" to understand what the verse means; the matching word or name often appears in the reference translation.
- A word occurring only once is NOT evidence of a misspelling — do not penalize rarity.
- Proper names are correctly spelled (status 1) unless clearly mistyped; do not mark a name as misspelled just because it is unfamiliar.

Rules:
- Evaluate the target "word" only, not the rest of the verse. Do not correct spelling and do not explain.
- Return exactly one result per input entry, with "word" copied verbatim. Do not add, drop, merge, or duplicate words.
- Treat capitalized and lowercase forms as distinct entries.`;

export function buildSystem(language: string): string {
  return SYSTEM_PROMPT.replaceAll("{language}", language);
}

export function buildUser(words: WordContext[]): string {
  return (
    `Evaluate the "word" in each of the following ${words.length} entries. ` +
    `Return one {word, status} per entry.\n\n` +
    JSON.stringify(words)
  );
}

// Extract the first balanced JSON value (object or array) from a text blob and
// validate it into ChatResponse[]. Accepts either {responses:[...]} or a bare
// array [...].
export function parseResponses(text: string | null | undefined): ChatResponse[] {
  if (!text) {
    throw new Error("Empty AI response");
  }

  const json = extractJson(text);
  if (json === null) {
    throw new Error("Could not locate JSON in AI response");
  }

  const parsed = JSON.parse(json);
  const arr = Array.isArray(parsed) ? parsed : parsed?.responses;
  const result = AiResponseList.safeParse(arr);
  if (!result.success) {
    throw new Error("AI response failed schema validation");
  }

  return result.data.map((r) => ({ word: r.word, status: r.status }));
}

function extractJson(text: string): string | null {
  // Prefer an object root, fall back to an array root.
  const objStart = text.indexOf("{");
  const arrStart = text.indexOf("[");

  let start = -1;
  let open = "";
  let close = "";

  if (objStart !== -1 && (arrStart === -1 || objStart < arrStart)) {
    start = objStart;
    open = "{";
    close = "}";
  } else if (arrStart !== -1) {
    start = arrStart;
    open = "[";
    close = "]";
  }

  if (start === -1) return null;

  const end = text.lastIndexOf(close) + 1;
  if (end <= start) return null;

  return text.substring(start, end);
}

// Reasoning "effort" a caller can request per model. Providers translate this
// into whatever native knob they expose (reasoning_effort, thinking budget…).
export type Effort = "minimal" | "low" | "medium" | "high";

export type ModelTuning = {
  effort?: Effort;
  temperature?: number;
};

// Token budget for extended-thinking style knobs (Anthropic, Gemini).
// "minimal" (or undefined) disables thinking entirely.
export function thinkingBudget(effort: Effort | undefined): number {
  switch (effort) {
    case "low":
      return 1024;
    case "medium":
      return 4096;
    case "high":
      return 12000;
    default:
      return 0;
  }
}

// Rough output-token ceiling for a batch of `count` words.
export function outputTokens(count: number): number {
  return Math.max(1024, count * 24);
}
