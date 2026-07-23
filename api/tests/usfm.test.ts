import { describe, it, expect } from "vitest";
import { tokenize, findSingletons, Verse } from "../src/usfm";

describe("tokenize", () => {
  it("splits on non-letters and keeps unicode letters", () => {
    expect(tokenize("Hello, world!", true)).toEqual(["Hello", "world"]);
  });

  it("apostrophe as separator splits the word", () => {
    expect(tokenize("don't", true)).toEqual(["don", "t"]);
  });

  it("apostrophe kept joins the word", () => {
    expect(tokenize("don't", false)).toEqual(["don't"]);
  });

  it("empty text -> no tokens", () => {
    expect(tokenize("   ", true)).toEqual([]);
  });
});

describe("findSingletons", () => {
  const verses: Verse[] = [
    { book: "gen", chapter: 1, verse: "1", text: "alpha beta beta" },
    { book: "gen", chapter: 1, verse: "2", text: "gamma alpha" },
    { book: "gen", chapter: 1, verse: "3", text: "delta" },
  ];

  it("returns only words occurring exactly once, sorted", () => {
    const singles = findSingletons(verses, true);
    expect(singles.map((s) => s.word)).toEqual(["delta", "gamma"]);
  });

  it("links a singleton to the verse it appears in", () => {
    const singles = findSingletons(verses, true);
    expect(singles.find((s) => s.word === "delta")?.ref).toBe("gen:1:3");
    expect(singles.find((s) => s.word === "gamma")?.ref).toBe("gen:1:2");
  });

  it("counts are case-sensitive per token but sorted case-insensitively", () => {
    const v: Verse[] = [{ book: "gen", chapter: 1, verse: "1", text: "Zeta apple" }];
    const singles = findSingletons(v, true);
    expect(singles.map((s) => s.word)).toEqual(["apple", "Zeta"]);
  });
});
