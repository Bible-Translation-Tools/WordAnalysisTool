import { describe, it, expect } from "vitest";
import { isChatError, briefReason } from "../src/lib/utils";

describe("isChatError", () => {
  it("accepts a well-formed BatchError", () => {
    expect(
      isChatError({ message: "boom", prompt: null, model: null, response: null }),
    ).toBe(true);
    expect(
      isChatError({ message: "boom", prompt: "p", model: "m", response: "r" }),
    ).toBe(true);
  });

  it("rejects a ChatResponse array element", () => {
    expect(isChatError({ word: "x", status: 1 })).toBe(false);
  });

  it("rejects null / non-objects", () => {
    expect(isChatError(null)).toBe(false);
    expect(isChatError("nope")).toBe(false);
  });
});

describe("briefReason", () => {
  it("prefers cause.message and takes the first line", () => {
    const err = { message: "Failed query\nlots of SQL", cause: { message: "duplicate key\nparams" } };
    expect(briefReason(err)).toBe("duplicate key");
  });

  it("falls back to message when no cause", () => {
    expect(briefReason(new Error("plain error"))).toBe("plain error");
  });
});
