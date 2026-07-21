ALTER TABLE "batches" ADD COLUMN "ref_resource_id" integer;--> statement-breakpoint
ALTER TABLE "words" ADD COLUMN "verse_id" integer NOT NULL;--> statement-breakpoint
ALTER TABLE "batches" ADD CONSTRAINT "batches_ref_resource_id_resources_id_fk" FOREIGN KEY ("ref_resource_id") REFERENCES "public"."resources"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "words" ADD CONSTRAINT "words_verse_id_verses_id_fk" FOREIGN KEY ("verse_id") REFERENCES "public"."verses"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "idx_batch_ref_resource_id" ON "batches" USING btree ("ref_resource_id");--> statement-breakpoint
CREATE INDEX "idx_word_verse_id" ON "words" USING btree ("verse_id");--> statement-breakpoint
ALTER TABLE "words" DROP COLUMN "ref";