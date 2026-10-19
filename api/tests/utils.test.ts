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

describe("toReportRow", () => {
  it("joins the reference as 'book chapter:verse'", async () => {
    const { toReportRow } = await import("../src/services/report.service");
    const row = toReportRow({
      word: "w",
      verse: { bookCode: "gen", chapter: 1, verse: "1" },
      models: [{ model: "m", status: 1 }],
      reviews: [{ correct: true }],
    });
    expect(row?.split(",").slice(0, 2)).toEqual(["w", "gen 1:1"]);
  });

  it("header has one column per model", async () => {
    const { reportHeader } = await import("../src/services/report.service");
    expect(reportHeader(5)).toBe(
      "word,reference,model1,model2,model3,model4,model5,AI consensus,correct/reviews,verdict,anomaly\n",
    );
  });
});
