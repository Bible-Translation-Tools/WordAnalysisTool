import { ModelTuning } from "./shared";

export type Provider = "openai" | "anthropic" | "mistral" | "google";

export type ModelConfig = {
  provider: Provider;
  tuning: ModelTuning;
};

// Per-model configuration. This is the single place to fine-tune each model:
// pick its provider and set reasoning effort / temperature. Effort maps to the
// provider's native knob (OpenAI reasoning_effort, Anthropic + Gemini thinking
// budget). Mistral has no reasoning knob, so only temperature applies there.
export const MODEL_REGISTRY: Record<string, ModelConfig> = {
  "gpt-5.4-mini": { provider: "openai", tuning: { effort: "low" } },
  "gpt-5.5": { provider: "openai", tuning: { effort: "low" } },
  "gpt-5.6-terra": { provider: "openai", tuning: { effort: "low" } },

  "claude-haiku-4-5": { provider: "anthropic", tuning: { effort: "low" } },
  "claude-sonnet-5": { provider: "anthropic", tuning: { effort: "low" } },

  "mistral-medium-2604": { provider: "mistral", tuning: { temperature: 0.2 } },
  "mistral-large-2512": { provider: "mistral", tuning: { temperature: 0.2 } },

  "gemini-3.6-flash": { provider: "google", tuning: { effort: "low" } },
};

export function getModelConfig(model: string): ModelConfig | null {
  return MODEL_REGISTRY[model] ?? null;
}
