ALTER TABLE `words` ADD `consensus` text;--> statement-breakpoint
ALTER TABLE `words` ADD `unanimous` integer DEFAULT false NOT NULL;--> statement-breakpoint
-- Backfill from existing votes (same rule as consensusSql / unanimousSql).
UPDATE `words` SET
  `consensus` = s.consensus,
  `unanimous` = s.unanimous
FROM (
  SELECT
    `word_id`,
    CASE
      WHEN max(`status` = -1) THEN NULL
      WHEN count(*) FILTER (WHERE `status` IN (0, 1)) = 0 THEN 'none'
      WHEN count(*) FILTER (WHERE `status` = 1) > count(*) FILTER (WHERE `status` = 0) THEN 'correct'
      WHEN count(*) FILTER (WHERE `status` = 0) > count(*) FILTER (WHERE `status` = 1) THEN 'incorrect'
      ELSE 'review'
    END AS consensus,
    (min(`status`) = max(`status`) AND min(`status`) IN (0, 1)) AS unanimous
  FROM `models`
  GROUP BY `word_id`
) AS s
WHERE s.`word_id` = `words`.`id`;
