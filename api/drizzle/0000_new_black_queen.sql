CREATE TABLE "batches" (
	"id" varchar(255) PRIMARY KEY NOT NULL,
	"ietf_code" varchar(255) NOT NULL,
	"language" varchar(255) DEFAULT '' NOT NULL,
	"resource_type" varchar(255) NOT NULL,
	"pending" boolean DEFAULT false NOT NULL,
	"error" text,
	"user_id" integer NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "models" (
	"id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY (sequence name "models_id_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START WITH 1 CACHE 1),
	"model" varchar(255) NOT NULL,
	"status" integer NOT NULL,
	"word_id" integer NOT NULL,
	"created_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "users" (
	"id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY (sequence name "users_id_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START WITH 1 CACHE 1),
	"wacs_user_id" integer NOT NULL,
	"username" varchar(255) NOT NULL,
	"email" varchar(255) NOT NULL,
	"access_token" text,
	"refresh_token" text,
	"token_type" varchar(255),
	"state" varchar(255),
	"created_at" timestamp DEFAULT now() NOT NULL,
	"updated_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "words" (
	"id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY (sequence name "words_id_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START WITH 1 CACHE 1),
	"word" varchar(255) NOT NULL,
	"batch_id" varchar(255) NOT NULL,
	"correct" boolean,
	"created_at" timestamp DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "batches" ADD CONSTRAINT "batches_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "models" ADD CONSTRAINT "models_word_id_words_id_fk" FOREIGN KEY ("word_id") REFERENCES "public"."words"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "words" ADD CONSTRAINT "words_batch_id_batches_id_fk" FOREIGN KEY ("batch_id") REFERENCES "public"."batches"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_batch" ON "batches" USING btree ("ietf_code","resource_type");--> statement-breakpoint
CREATE INDEX "idx_batch_user_id" ON "batches" USING btree ("user_id");--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_model" ON "models" USING btree ("model","word_id");--> statement-breakpoint
CREATE INDEX "idx_model_word_id" ON "models" USING btree ("word_id");--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_user" ON "users" USING btree ("email");--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_word" ON "words" USING btree ("word","batch_id");--> statement-breakpoint
CREATE INDEX "idx_word_batch_id" ON "words" USING btree ("batch_id");--> statement-breakpoint
CREATE INDEX "idx_word_correct" ON "words" USING btree ("correct");