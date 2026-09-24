ALTER TABLE "batches" ADD COLUMN "resource_id" integer;--> statement-breakpoint
ALTER TABLE "batches" ADD COLUMN "ingesting" boolean DEFAULT false NOT NULL;--> statement-breakpoint
ALTER TABLE "batches" ADD COLUMN "apostrophe_is_separator" boolean DEFAULT true NOT NULL;--> statement-breakpoint
ALTER TABLE "batches" ADD COLUMN "models" text;--> statement-breakpoint
ALTER TABLE "batches" ADD CONSTRAINT "batches_resource_id_resources_id_fk" FOREIGN KEY ("resource_id") REFERENCES "public"."resources"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "idx_batch_resource_id" ON "batches" USING btree ("resource_id");