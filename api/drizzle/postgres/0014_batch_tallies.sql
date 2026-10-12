ALTER TABLE "batches" ADD COLUMN "words_total" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "batches" ADD COLUMN "words_completed" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "batches" ADD COLUMN "words_correct" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "batches" ADD COLUMN "words_incorrect" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "batches" ADD COLUMN "words_review" integer DEFAULT 0 NOT NULL;--> statement-breakpoint
-- Backfill tallies from words.consensus.
UPDATE "batches" b SET
  "words_total" = s.total,
  "words_completed" = s.completed,
  "words_correct" = s.correct,
  "words_incorrect" = s.incorrect,
  "words_review" = s.review
FROM (
  SELECT
    "batch_id",
    count(*) AS total,
    count("consensus") AS completed,
    count(*) FILTER (WHERE "consensus" = 'correct') AS correct,
    count(*) FILTER (WHERE "consensus" = 'incorrect') AS incorrect,
    count(*) FILTER (WHERE "consensus" = 'review') AS review
  FROM "words"
  GROUP BY "batch_id"
) s
WHERE s."batch_id" = b."id";
