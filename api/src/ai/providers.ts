import OpenAI from "openai";
import { zodResponseFormat } from "openai/helpers/zod";
import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { Mistral } from "@mistralai/mistralai";
import { GoogleGenAI } from "@google/genai";
import { ChatResponse } from "../types";
import { ModelTuning } from "./shared";
import {
  AiResponseObject,
  outputTokens,
  parseResponses,
  responseJsonSchema,
  thinkingBudget,
} from "./shared";
import { Provider } from "./registry";

export type ChatArgs = {
  env: CloudflareBindings;
  model: string;
  language: string;
  words: { word: string }[];
  system: string;
  user: string;
  tuning: ModelTuning;
};

export type ProviderAdapter = (args: ChatArgs) => Promise<ChatResponse[]>;

function gatewayBase(env: CloudflareBindings): string {
  return `https://gateway.ai.cloudflare.com/v1/${env.CLOUDFLARE_ID}/wat-ai`;
}

// Extra instruction for providers that don't get a strict schema and must be
// nudged to emit a bare JSON array with no surrounding prose.
const JSON_ONLY =
  '\n\nRespond with ONLY a JSON array, no prose or code fences: ' +
  '[{"word": "...", "status": 0 or 1}, ...]';

const openaiAdapter: ProviderAdapter = async ({
  env,
  model,
  system,
  user,
  tuning,
}) => {
  const client = new OpenAI({
    apiKey: env.OPENAI_API_KEY,
    baseURL: `${gatewayBase(env)}/openai`,
  });

  const response = await client.chat.completions.parse({
    model,
    messages: [
      { role: "system", content: system },
      { role: "user", content: user },
    ],
    response_format: zodResponseFormat(AiResponseObject, "responses"),
    ...(tuning.effort ? { reasoning_effort: tuning.effort } : {}),
  });

  const message = response.choices[0].message;
  if (message.refusal) {
    throw new Error(message.refusal);
  }
  if (message.parsed === null) {
    throw new Error("Could not parse AI response");
  }

  return message.parsed.responses.map((r) => ({
    word: r.word,
    status: r.status,
  }));
};

// Anthropic effort levels differ slightly from ours ("minimal" doesn't exist);
// map it onto the nearest supported level.
function anthropicEffort(
  effort: ModelTuning["effort"],
): "low" | "medium" | "high" | null {
  switch (effort) {
    case "minimal":
    case "low":
      return "low";
    case "medium":
      return "medium";
    case "high":
      return "high";
    default:
      return null;
  }
}

const anthropicAdapter: ProviderAdapter = async ({
  env,
  model,
  system,
  user,
  words,
  tuning,
}) => {
  const client = new Anthropic({
    apiKey: env.CLAUDEAI_API_KEY,
    baseURL: `${gatewayBase(env)}/anthropic`,
  });

  // Newer Claude models control reasoning via adaptive thinking +
  // output_config.effort rather than an explicit token budget.
  const effort = anthropicEffort(tuning.effort);
  const budget = thinkingBudget(tuning.effort);
  const maxTokens = outputTokens(words.length) + budget;

  const response = await client.messages.parse({
    model,
    max_tokens: maxTokens,
    system,
    messages: [{ role: "user", content: user }],
    thinking: { type: "adaptive" },
    output_config: {
      ...(effort ? { effort } : {}),
      format: zodOutputFormat(AiResponseObject),
    },
  });

  if (response.parsed_output === null) {
    throw new Error("Could not parse AI response");
  }

  return response.parsed_output.responses.map((r) => ({
    word: r.word,
    status: r.status,
  }));
};

const mistralAdapter: ProviderAdapter = async ({
  env,
  model,
  system,
  user,
  words,
  tuning,
}) => {
  const client = new Mistral({
    apiKey: env.MISTRAL_API_KEY,
    serverURL: `${gatewayBase(env)}/mistral`,
  });

  const response = await client.chat.complete({
    model,
    maxTokens: outputTokens(words.length),
    ...(tuning.temperature !== undefined
      ? { temperature: tuning.temperature }
      : {}),
    messages: [
      { role: "system", content: system },
      { role: "user", content: user },
    ],
    responseFormat: {
      type: "json_schema",
      jsonSchema: {
        name: "responses",
        schemaDefinition: responseJsonSchema as Record<string, unknown>,
        strict: true,
      },
    },
  });

  const content = response.choices?.[0]?.message?.content;
  const text =
    typeof content === "string"
      ? content
      : Array.isArray(content)
        ? content
            .map((chunk) => ("text" in chunk ? chunk.text : ""))
            .join("")
        : "";

  return parseResponses(text);
};

const googleAdapter: ProviderAdapter = async ({
  env,
  model,
  system,
  user,
  tuning,
}) => {
  const client = new GoogleGenAI({
    apiKey: env.GEMINI_API_KEY,
    httpOptions: { baseUrl: `${gatewayBase(env)}/google-ai-studio` },
  });

  const budget = thinkingBudget(tuning.effort);

  const response = await client.models.generateContent({
    model,
    contents: user + JSON_ONLY,
    config: {
      systemInstruction: system,
      responseMimeType: "application/json",
      ...(tuning.temperature !== undefined
        ? { temperature: tuning.temperature }
        : {}),
      // 0 disables thinking; only send when we actually want it.
      ...(budget > 0 ? { thinkingConfig: { thinkingBudget: budget } } : {}),
    },
  });

  return parseResponses(response.text);
};

export const ADAPTERS: Record<Provider, ProviderAdapter> = {
  openai: openaiAdapter,
  anthropic: anthropicAdapter,
  mistral: mistralAdapter,
  google: googleAdapter,
};
