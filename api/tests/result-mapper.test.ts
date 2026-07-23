import { describe, it, expect } from "vitest";
import { mapResultsToWords } from "../src/services/ai-processing.service";

const words = [{ word: "alpha" }, { word: "beta" }, { word: "gamma" }];

describe("mapResultsToWords", () => {
  it("maps strict matches in original word order", () => {
    const out = mapResultsToWords(
      words,
      [
        { word: "gamma", status: 1 },
        { word: "alpha", status: 0 },
        { word: "beta", status: 1 },
      ],
      0,
    );
    expect(out).toEqual([
      { word: "alpha", status: 0 },
      { word: "beta", status: 1 },
      { word: "gamma", status: 1 },
    ]);
  });

  it("falls back to accent/case-insensitive matching", () => {
    const out = mapResultsToWords(
      [{ word: "Bånana" }],
      [{ word: "banana", status: 1 }],
      0,
    );
    expect(out).toEqual([{ word: "Bånana", status: 1 }]);
  });

  it("marks a missing word as unchecked (-1) while retries remain", () => {
    const out = mapResultsToWords(words, [{ word: "alpha", status: 1 }], 0);
    expect(out.find((w) => w.word === "beta")?.status).toBe(-1);
  });

  it("marks a missing word as incorrect (0) once retries are exhausted", () => {
    const out = mapResultsToWords(words, [{ word: "alpha", status: 1 }], 3);
    expect(out.find((w) => w.word === "beta")?.status).toBe(0);
  });

  it("does not reuse a single response for duplicate-looking words", () => {
    const out = mapResultsToWords(
      [{ word: "alpha" }, { word: "alpha" }],
      [{ word: "alpha", status: 1 }],
      0,
    );
    // first claims the match, second is unmatched -> -1
    expect(out.map((w) => w.status)).toEqual([1, -1]);
  });
});
