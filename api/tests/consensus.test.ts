import { describe, it, expect } from "vitest";
import { classifyVotes, REPORT_LABEL } from "../src/services/consensus";

describe("classifyVotes", () => {
  it("all correct -> correct", () => {
    expect(classifyVotes([1, 1, 1])).toBe("correct");
  });

  it("all incorrect -> incorrect", () => {
    expect(classifyVotes([0, 0])).toBe("incorrect");
  });

  it("3-model majority (2 correct, 1 incorrect) -> correct", () => {
    expect(classifyVotes([1, 1, 0])).toBe("correct");
  });

  it("3-model majority (2 incorrect, 1 correct) -> incorrect", () => {
    expect(classifyVotes([0, 0, 1])).toBe("incorrect");
  });

  it("2-model tie -> review", () => {
    expect(classifyVotes([1, 0])).toBe("review");
  });

  it("single model never ties", () => {
    expect(classifyVotes([1])).toBe("correct");
    expect(classifyVotes([0])).toBe("incorrect");
  });

  it("ignores votes > 1 (legacy 'name')", () => {
    expect(classifyVotes([1, 1, 2])).toBe("correct");
    expect(classifyVotes([0, 2])).toBe("incorrect");
  });

  it("no valid 0/1 vote -> none", () => {
    expect(classifyVotes([2, 2])).toBe("none");
    expect(classifyVotes([])).toBe("none");
  });

  it("maps to report labels", () => {
    expect(REPORT_LABEL[classifyVotes([1, 1])]).toBe("Likely Correct");
    expect(REPORT_LABEL[classifyVotes([0, 0])]).toBe("Likely Incorrect");
    expect(REPORT_LABEL[classifyVotes([1, 0])]).toBe("Review Needed");
    expect(REPORT_LABEL[classifyVotes([2])]).toBe("Not Processed");
  });
});
