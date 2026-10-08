import { describe, expect, it } from "vitest";
import { chunkByParams } from "../src/db/common/chunk";
import { dedupeVerses, uniqueRefs } from "../src/db/common/verses";
import { computeReviewLimits } from "../src/services/review.service";

describe("chunkByParams", () => {
  it("keeps every statement at or under the parameter limit", () => {
    const rows = Array.from({ length: 50 }, (_, i) => i);
    const chunks = chunkByParams(rows, 5, 100, 1);
    expect(chunks.every((c) => c.length * 5 + 1 <= 100)).toBe(true);
    expect(chunks.map((c) => c.length)).toEqual([19, 19, 12]);
    expect(chunks.flat()).toEqual(rows);
  });

  it("never produces an empty chunk and copes with tiny limits", () => {
    expect(chunkByParams([], 3, 100)).toEqual([]);
    expect(chunkByParams([1, 2], 50, 10)).toEqual([[1], [2]]);
  });
});

describe("dedupeVerses", () => {
  it("keeps the last occurrence of a repeated ref", () => {
    const out = dedupeVerses(
      [
        { book: "gen", chapter: 1, verse: "1", text: "a" },
        { book: "gen", chapter: 1, verse: "2", text: "b" },
        { book: "gen", chapter: 1, verse: "1", text: "c" },
      ],
      1,
    );
    expect(out.map((v) => v.text)).toEqual(["c", "b"]);
  });

  it("uniqueRefs drops repeated refs", () => {
    expect(
      uniqueRefs([
        { book: "gen", chapter: 1, verse: "1" },
        { book: "gen", chapter: 1, verse: "1" },
      ]),
    ).toHaveLength(1);
  });
});

describe("computeReviewLimits", () => {
  it("returns null when the pool fits", () => {
    expect(computeReviewLimits([{ status: 1, count: 300 }], 370)).toBeNull();
    expect(computeReviewLimits([], 370)).toBeNull();
  });

  it("splits the limit proportionally and fixes rounding on the first status", () => {
    const limits = computeReviewLimits(
      [
        { status: 1, count: 1000 },
        { status: 0, count: 500 },
      ],
      370,
    );
    expect(limits).toEqual([
      { status: 1, limit: 247 },
      { status: 0, limit: 123 },
    ]);
    expect(limits!.reduce((s, l) => s + l.limit, 0)).toBe(370);
  });
});
