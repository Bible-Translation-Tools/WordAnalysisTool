import OpenAI from "openai";
import { oneLine } from "common-tags";
import { ChatResponse } from "./types";

export default class AiClient {
  private env: CloudflareBindings;
  private baseUrl: string;

  private models = {
    openai: [
      "gpt-4.1",
      "gpt-4.1-mini",
      "gpt-4.1-nano",
      "gpt-4o",
      "gpt-4o-mini",
      "gpt-4-turbo",
    ],
    anthropic: [
      "claude-3-7-sonnet-latest",
      "claude-3-5-sonnet-latest",
      "claude-3-5-haiku-latest",
      "claude-3-opus-latest",
    ],
    qwen: [
      "qwen2.5-7b-instruct",
      "qwen2.5-14b-instruct",
      "qwen-max",
      "qwen-plus",
      "qwen-turbo",
    ],
    mistral: [
      "ministral-3b-latest",
      "codestral-latest",
      "mistral-large-latest",
      "pixtral-large-latest",
      "ministral-8b-latest",
    ],
  };

  private systemPrompt: string = oneLine`You are a language expert who is checking spelling. 
  You will be given a list of words and a language and you will respond with 
  whether the words exist in the language and whether they are proper names. 
  You will respond with JSON, like this: 
  [
    {
      "word": "TestWord1",
      "status": 0
    },
    {
      "word": "TestWord2",
      "status": 1
    }
  ]. 
  Where status: 0 - doesn't exist, 1 - exists, 2 - proper name. 
  Give no other commentary. 
  Here are the language and words to test.`;

  constructor(env: CloudflareBindings) {
    this.env = env;
    this.baseUrl = `https://gateway.ai.cloudflare.com/v1/${env.CLOUDFLARE_ID}/wat-ai`;
  }

  async chat(model: string, prompt: string): Promise<ChatResponse[] | null> {
    const client = this.getClient(model);

    if (client === null) {
      return Promise.reject("model is invalid");
    }

    const response = await client.chat.completions.create({
      model: model,
      messages: [
        {
          role: "system",
          content: this.systemPrompt,
        },
        {
          role: "user",
          content: prompt,
        },
      ],
    });

    try {
      let result = response.choices[0].message.content || "[]";
      const json = this.extractJson(result);

      if (json == null) {
        throw new Error("invalid json response");
      }

      return JSON.parse(json);
    } catch (error) {
      console.error(error);
      console.error(response.choices[0].message.content);
      return null;
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
    } else if (this.models.qwen.includes(model)) {
      return new OpenAI({
        apiKey: this.env.QWEN_API_KEY,
        baseURL: "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/",
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
