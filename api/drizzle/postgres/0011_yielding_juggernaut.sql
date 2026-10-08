DROP INDEX "idx_batch_resource_id";--> statement-breakpoint
DROP INDEX "idx_model_word_id";--> statement-breakpoint
DROP INDEX "idx_resource_language_id";--> statement-breakpoint
DROP INDEX "idx_verse_resource_id";--> statement-breakpoint
DROP INDEX "idx_word_batch_id";--> statement-breakpoint
DROP INDEX "idx_unique_model";--> statement-breakpoint
DROP INDEX "idx_unique_resource";--> statement-breakpoint
DROP INDEX "idx_unique_verse";--> statement-breakpoint
DROP INDEX "idx_unique_word";--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_model" ON "models" USING btree ("word_id","model");--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_resource" ON "resources" USING btree ("language_id","resource_type");--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_verse" ON "verses" USING btree ("resource_id","book_code","chapter","verse");--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_word" ON "words" USING btree ("batch_id","word");