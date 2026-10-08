ALTER TABLE "batches" ADD COLUMN "language_id" integer;--> statement-breakpoint
ALTER TABLE "batches" ADD CONSTRAINT "batches_language_id_languages_id_fk" FOREIGN KEY ("language_id") REFERENCES "public"."languages"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "idx_batch_language_id" ON "batches" USING btree ("language_id");