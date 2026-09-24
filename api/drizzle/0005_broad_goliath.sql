CREATE TABLE "languages" (
	"id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY (sequence name "languages_id_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START WITH 1 CACHE 1),
	"lc" varchar(255) NOT NULL,
	"ln" text NOT NULL,
	"ang" text NOT NULL,
	"ld" text NOT NULL,
	"gw" boolean DEFAULT false NOT NULL
);
--> statement-breakpoint
CREATE TABLE "resources" (
	"id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY (sequence name "resources_id_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START WITH 1 CACHE 1),
	"resource_type" text NOT NULL,
	"language_id" integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE "verses" (
	"id" integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY (sequence name "verses_id_seq" INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START WITH 1 CACHE 1),
	"book_code" text NOT NULL,
	"chapter" integer NOT NULL,
	"verse" text NOT NULL,
	"resource_id" integer NOT NULL
);
--> statement-breakpoint
ALTER TABLE "resources" ADD CONSTRAINT "resources_language_id_languages_id_fk" FOREIGN KEY ("language_id") REFERENCES "public"."languages"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "verses" ADD CONSTRAINT "verses_resource_id_resources_id_fk" FOREIGN KEY ("resource_id") REFERENCES "public"."resources"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_language" ON "languages" USING btree ("lc");--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_resource" ON "resources" USING btree ("resource_type","language_id");--> statement-breakpoint
CREATE INDEX "idx_resource_language_id" ON "resources" USING btree ("language_id");--> statement-breakpoint
CREATE UNIQUE INDEX "idx_unique_verse" ON "verses" USING btree ("book_code","chapter","verse","resource_id");--> statement-breakpoint
CREATE INDEX "idx_verse_resource_id" ON "verses" USING btree ("resource_id");