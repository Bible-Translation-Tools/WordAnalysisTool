import OpenAI from "openai";
import { oneLine } from "common-tags";
import { BatchError, ChatResponse } from "./types";
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

  private systemPrompt: string = oneLine`You are a Senior {language} Linguist specializing in orthography and corpus linguistics.
  You will be given a list of words in this language and you will check each word against related dictionary, 
  taking into account declensions and endings. 
  Respond with a status for each word: 1 = exists, 0 = doesn't exist, 2 = proper name. 
  Don treat capitalized words as proper names unless confirmed by dictionary.
  Do not correct misspellings. Do not duplicate words in your response. 
  Treat capitalized and lowercase versions of the same word as distinct entries.`;

  constructor(env: CloudflareBindings) {
    this.env = env;
    this.baseUrl = `https://gateway.ai.cloudflare.com/v1/${env.CLOUDFLARE_ID}/wat-ai`;
  }

  async chat(
    model: string,
    language: string,
    prompt: string,
  ): Promise<ChatResponse[] | BatchError> {
    const client = this.getClient(model);

    if (client === null) {
      return Promise.reject("model is invalid");
    }

    let response = null;

    try {
      response = await client.chat.completions.parse({
        model: model,
        messages: [
          {
            role: "system",
            content: this.systemPrompt.replace("{language}", language),
          },
          {
            role: "user",
            content: prompt,
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
        prompt,
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
