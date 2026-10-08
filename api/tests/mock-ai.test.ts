import { describe, expect, it } from "vitest";
import { MockAiClient, mockStatus } from "../src/ai/mock";
import { isDev } from "../src/lib/utils";

describe("MockAiClient", () => {
  const client = new MockAiClient();
  const words = ["alpha", "beta", "gamma"].map((word) => ({
    word,
    reference: "gen 1:1",
    source: "",
    referenceVerse: "",
  }));

  it("returns one deterministic verdict per word, in order", async () => {
    const a = await client.chat("gpt-5.5", "Testish", words);
    const b = await client.chat("gpt-5.5", "Testish", words);
    expect(a).toEqual(b);
    expect(Array.isArray(a) && a.map((r) => r.word)).toEqual(["alpha", "beta", "gamma"]);
    expect(Array.isArray(a) && a.every((r) => r.status === 0 || r.status === 1)).toBe(true);
  });

  it("rejects unknown models like the real client", async () => {
    await expect(client.chat("no-such-model", "Testish", words)).rejects.toBe(
      "model is invalid",
    );
  });

  it("marks a minority of words incorrect", () => {
    const sample = Array.from({ length: 1000 }, (_, i) => `word${i}`);
    const incorrect = sample.filter((w) => mockStatus(w, "gpt-5.5") === 0).length;
    expect(incorrect).toBeGreaterThan(50);
    expect(incorrect).toBeLessThan(350);
  });
});

describe("isDev", () => {
  it("is true only for ENVIRONMENT=DEV", () => {
    expect(isDev({ ENVIRONMENT: "DEV" })).toBe(true);
    expect(isDev({ ENVIRONMENT: "dev" })).toBe(true);
    expect(isDev({ ENVIRONMENT: "PROD" })).toBe(false);
    expect(isDev({ ENVIRONMENT: "true" })).toBe(false);
    expect(isDev({})).toBe(false);
  });
});
