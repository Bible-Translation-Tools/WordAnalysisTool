ALTER TABLE `words` ADD `sampled` integer DEFAULT false NOT NULL;--> statement-breakpoint
CREATE INDEX `idx_word_sampled` ON `words` (`batch_id`,`word`) WHERE "words"."sampled" = 1;