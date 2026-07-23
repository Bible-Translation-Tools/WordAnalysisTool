import { classifyVotes, REPORT_LABEL } from "./consensus";

export const REPORT_HEADER =
  "word,book,chapter,verse,model1,model2,model3,AI consensus,correct/reviews,verdict,anomaly\n";

const MODEL_STATUS_LABEL: Record<number, string> = {
  0: "Likely Incorrect",
  1: "Likely Correct",
  [-1]: "Not Processed",
};

export type ReportWord = {
  word: string;
  verse: { bookCode: string; chapter: number; verse: string };
  models: { model: string; status: number }[];
  reviews: { correct: boolean }[];
};

/**
 * One CSV line for a reviewed word, or null when the word has no human reviews
 * (those are skipped). `verdict` is the human majority; `anomaly` marks a direct
 * AI↔human contradiction.
 */
export function toReportRow(word: ReportWord): string | null {
  const totalReviews = word.reviews.length;
  if (totalReviews === 0) return null;

  const modelCells = word.models.map(
    (m) => `"${m.model}\n${MODEL_STATUS_LABEL[m.status]}"`,
  );

  const correctReviews = word.reviews.filter((r) => r.correct).length;
  const reviewsStr = `${correctReviews}/${totalReviews}`;
  const verdict = correctReviews / totalReviews >= 0.5 ? "Yes" : "No";

  const consensus = REPORT_LABEL[classifyVotes(word.models.map((m) => m.status))];

  const anomaly =
    (consensus === "Likely Incorrect" && verdict === "Yes") ||
    (consensus === "Likely Correct" && verdict === "No")
      ? "⚠️"
      : "";

  return [
    word.word,
    word.verse.bookCode || "",
    word.verse.chapter || "",
    word.verse.verse || "",
    ...modelCells,
    consensus,
    reviewsStr,
    verdict,
    anomaly,
  ].join(",");
}
