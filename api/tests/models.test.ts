import { describe, expect, it } from "vitest";
import { parseModels } from "../src/lib/utils";

describe("parseModels", () => {
  it("reads the JSON array batches are written with", () => {
    expect(parseModels('["gpt-5.5","claude-haiku-4-5"]')).toEqual([
      "gpt-5.5",
      "claude-haiku-4-5",
    ]);
  });

  it("reads plain comma separated names from older rows", () => {
    expect(parseModels("gpt-5.5, claude-haiku-4-5")).toEqual([
      "gpt-5.5",
      "claude-haiku-4-5",
    ]);
  });

  it("has nothing to report for an unset column", () => {
    expect(parseModels(null)).toEqual([]);
    expect(parseModels("")).toEqual([]);
    expect(parseModels("   ")).toEqual([]);
  });

  it("drops entries that are not names", () => {
    expect(parseModels('["gpt-5.5",null,7]')).toEqual(["gpt-5.5"]);
    expect(parseModels("[not json")).toEqual(["[not json"]);
  });
});
