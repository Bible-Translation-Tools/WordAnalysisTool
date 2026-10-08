CREATE TABLE `batches` (
	`id` text PRIMARY KEY NOT NULL,
	`language_id` integer,
	`resource_id` integer,
	`ref_resource_id` integer,
	`user_id` integer NOT NULL,
	`ingesting` integer DEFAULT false NOT NULL,
	`pending` integer DEFAULT false NOT NULL,
	`apostrophe_is_separator` integer DEFAULT true NOT NULL,
	`models` text,
	`error` text,
	`retries` integer DEFAULT 0 NOT NULL,
	`created_at` integer DEFAULT (unixepoch()) NOT NULL,
	`updated_at` integer DEFAULT (unixepoch()) NOT NULL,
	FOREIGN KEY (`language_id`) REFERENCES `languages`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`resource_id`) REFERENCES `resources`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`ref_resource_id`) REFERENCES `resources`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idx_unique_batch` ON `batches` (`resource_id`);--> statement-breakpoint
CREATE INDEX `idx_batch_user_id` ON `batches` (`user_id`);--> statement-breakpoint
CREATE INDEX `idx_batch_language_id` ON `batches` (`language_id`);--> statement-breakpoint
CREATE INDEX `idx_batch_ref_resource_id` ON `batches` (`ref_resource_id`);--> statement-breakpoint
CREATE TABLE `languages` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`lc` text NOT NULL,
	`ln` text NOT NULL,
	`ang` text NOT NULL,
	`ld` text NOT NULL,
	`gw` integer DEFAULT false NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idx_unique_language` ON `languages` (`lc`);--> statement-breakpoint
CREATE TABLE `models` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`model` text NOT NULL,
	`status` integer NOT NULL,
	`retries` integer DEFAULT 0 NOT NULL,
	`word_id` integer NOT NULL,
	`created_at` integer DEFAULT (unixepoch()) NOT NULL,
	FOREIGN KEY (`word_id`) REFERENCES `words`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idx_unique_model` ON `models` (`word_id`,`model`);--> statement-breakpoint
CREATE TABLE `resources` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`resource_type` text NOT NULL,
	`language_id` integer NOT NULL,
	FOREIGN KEY (`language_id`) REFERENCES `languages`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idx_unique_resource` ON `resources` (`language_id`,`resource_type`);--> statement-breakpoint
CREATE TABLE `users` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`wacs_user_id` integer NOT NULL,
	`username` text NOT NULL,
	`email` text NOT NULL,
	`access_token` text,
	`refresh_token` text,
	`token_type` text,
	`state` text,
	`created_at` integer DEFAULT (unixepoch()) NOT NULL,
	`updated_at` integer DEFAULT (unixepoch()) NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idx_unique_user` ON `users` (`email`);--> statement-breakpoint
CREATE TABLE `verses` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`book_code` text NOT NULL,
	`chapter` integer NOT NULL,
	`verse` text NOT NULL,
	`text` text NOT NULL,
	`resource_id` integer NOT NULL,
	FOREIGN KEY (`resource_id`) REFERENCES `resources`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idx_unique_verse` ON `verses` (`resource_id`,`book_code`,`chapter`,`verse`);--> statement-breakpoint
CREATE TABLE `word_reviews` (
	`pk` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`word_id` integer NOT NULL,
	`user_id` integer NOT NULL,
	`correct` integer NOT NULL,
	FOREIGN KEY (`word_id`) REFERENCES `words`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idx_unique_word_review` ON `word_reviews` (`word_id`,`user_id`);--> statement-breakpoint
CREATE TABLE `words` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`word` text NOT NULL,
	`batch_id` text NOT NULL,
	`verse_id` integer NOT NULL,
	`created_at` integer DEFAULT (unixepoch()) NOT NULL,
	FOREIGN KEY (`batch_id`) REFERENCES `batches`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`verse_id`) REFERENCES `verses`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idx_unique_word` ON `words` (`batch_id`,`word`);--> statement-breakpoint
CREATE INDEX `idx_word_verse_id` ON `words` (`verse_id`);