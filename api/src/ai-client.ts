import OpenAI from "openai";
import { BatchError, ChatResponse, WordContext } from "./types";
import { zodResponseFormat } from "openai/helpers/zod";
import { z } from "zod";

const AiResponse = z.object({
  word: z.string(),
  status: z.number(),
});

const AiResponseArray = z.object({
  responses: z.array(AiResponse),
});

export default class AiClient {
  private env: CloudflareBindings;
  private baseUrl: string;

  private models = {
    openai: ["gpt-5.4-mini", "gpt-5.5", "gpt-5.6-terra"],
    anthropic: ["claude-haiku-4-5", "claude-sonnet-5"],
    mistral: ["mistral-medium-2604", "mistral-large-2512"],
  };

  private systemPrompt: string = `You are a senior {language} linguist and orthography expert reviewing a {language} Bible translation for spelling errors.

You will receive a JSON array of entries. Each entry has:
- "word": a single {language} word to evaluate
- "reference": the verse reference the word comes from
- "source": the {language} verse text where the word occurs (context)
- "referenceVerse": the same verse in a reference translation from a related, well-resourced language (may be empty)

For each entry, classify ONLY the "word" with exactly one status:
- 1 = correctly spelled: a valid {language} word, INCLUDING inflected/declined/conjugated forms, affixed forms, and rare words
- 0 = misspelled: not a valid {language} word — a typo, wrong/missing/extra/transposed letters, two words run together, or a truncated word
- 2 = proper name: a person, place, people group, or other name, regardless of its spelling

How to judge:
- Use "source" to see the word in context and judge whether it is a plausible {language} form (consider prefixes, suffixes, and inflection of a real root — these are correct, status 1, not errors).
- Use "referenceVerse" to understand what the verse means and to recognize proper names: the matching name usually appears (often capitalized) in the reference translation.
- A word occurring only once is NOT evidence of a misspelling — do not penalize rarity.
- Do NOT treat a word as a proper name just because it is capitalized; sentence-initial words are capitalized too. Confirm names from meaning/context.

Rules:
- Evaluate the target "word" only, not the rest of the verse. Do not correct spelling and do not explain.
- Return exactly one result per input entry, with "word" copied verbatim. Do not add, drop, merge, or duplicate words.
- Treat capitalized and lowercase forms as distinct entries.`;

  constructor(env: CloudflareBindings) {
    this.env = env;
    this.baseUrl = `https://gateway.ai.cloudflare.com/v1/${env.CLOUDFLARE_ID}/wat-ai`;
  }

  async chat(
    model: string,
    language: string,
    words: WordContext[],
  ): Promise<ChatResponse[] | BatchError> {
    const client = this.getClient(model);

    if (client === null) {
      return Promise.reject("model is invalid");
    }

    const userContent =
      `Evaluate the "word" in each of the following ${words.length} entries. ` +
      `Return one {word, status} per entry.\n\n` +
      JSON.stringify(words);

    let response = null;

    try {
      response = await client.chat.completions.parse({
        model: model,
        messages: [
          {
            role: "system",
            content: this.systemPrompt.replaceAll("{language}", language),
          },
          {
            role: "user",
            content: userContent,
          },
        ],
        response_format: zodResponseFormat(AiResponseArray, "responses"),
      });

      let result = response.choices[0].message;

      if (result.refusal) {
        throw new Error(result.refusal);
      }

      if (result.parsed === null) {
        throw new Error("Could not parse AI response");
      }

      return result.parsed.responses.map((response) => {
        const status: ChatResponse = {
          word: response.word,
          status: response.status,
        };
        return status;
      });
    } catch (error) {
      return {
        prompt: words.map((w) => w.word).join(", "),
        message: error instanceof Error ? error.message : String(error),
        model,
        response: response?.choices[0].message.content || null,
      };
    }
  }

  private getClient(model: string): OpenAI | null {
    if (this.models.openai.includes(model)) {
      return new OpenAI({
        apiKey: this.env.OPENAI_API_KEY,
        baseURL: `${this.baseUrl}/openai`,
      });
    } else if (this.models.anthropic.includes(model)) {
      return new OpenAI({
        apiKey: this.env.CLAUDEAI_API_KEY,
        baseURL: `${this.baseUrl}/anthropic`,
      });
    } else if (this.models.mistral.includes(model)) {
      return new OpenAI({
        apiKey: this.env.MISTRAL_API_KEY,
        baseURL: `${this.baseUrl}/mistral`,
      });
    } else {
      return null;
    }
  }

  private extractJson(json: string): string | null {
    const startIndex = json.indexOf("[");

    if (startIndex === -1) {
      return null; // No opening bracket found
    }

    const endIndex = json.lastIndexOf("]") + 1;

    if (endIndex <= startIndex) {
      return null; // No corresponding closing bracket or it appears before the start
    }

    return json.substring(startIndex, endIndex);
  }
}
