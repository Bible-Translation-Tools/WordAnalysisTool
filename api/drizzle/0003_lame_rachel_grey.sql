CREATE TABLE "word_reviews" (
	"pk" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY (sequence name "word_reviews_pk_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START WITH 1 CACHE 1),
	"word_id" integer NOT NULL,
	"user_id" integer NOT NULL,
	"correct" boolean NOT NULL
);
--> statement-breakpoint
DROP INDEX "idx_word_correct";--> statement-breakpoint
ALTER TABLE "word_reviews" ADD CONSTRAINT "word_reviews_word_id_words_id_fk" FOREIGN KEY ("word_id") REFERENCES "public"."words"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "word_reviews" ADD CONSTRAINT "word_reviews_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_word_review" ON "word_reviews" USING btree ("word_id","user_id");--> statement-breakpoint
ALTER TABLE "words" DROP COLUMN "correct";