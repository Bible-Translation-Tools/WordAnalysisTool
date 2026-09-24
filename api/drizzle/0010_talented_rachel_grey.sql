DROP INDEX "idx_unique_batch";--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_batch" ON "batches" USING btree ("resource_id");--> statement-breakpoint
ALTER TABLE "batches" DROP COLUMN "ietf_code";--> statement-breakpoint
ALTER TABLE "batches" DROP COLUMN "language";--> statement-breakpoint
ALTER TABLE "batches" DROP COLUMN "resource_type";