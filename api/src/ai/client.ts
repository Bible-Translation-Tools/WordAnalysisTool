import { BatchError, ChatResponse, WordContext } from "../types";
import { buildSystem, buildUser } from "./shared";
import { getModelConfig } from "./registry";
import { ADAPTERS } from "./providers";

export default class AiClient {
  private env: CloudflareBindings;

  constructor(env: CloudflareBindings) {
    this.env = env;
  }

  async chat(
    model: string,
    language: string,
    words: WordContext[],
  ): Promise<ChatResponse[] | BatchError> {
    const config = getModelConfig(model);

    if (config === null) {
      return Promise.reject("model is invalid");
    }

    const system = buildSystem(language);
    const user = buildUser(words);
    const adapter = ADAPTERS[config.provider];

    try {
      return await adapter({
        env: this.env,
        model,
        language,
        words,
        system,
        user,
        tuning: config.tuning,
      });
    } catch (error) {
      return {
        prompt: words.map((w) => w.word).join(", "),
        message: error instanceof Error ? error.message : String(error),
        model,
        response: null,
      };
    }
  }
}
