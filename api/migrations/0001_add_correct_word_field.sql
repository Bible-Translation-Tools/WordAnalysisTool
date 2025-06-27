-- Migration number: 0001 	 2025-04-14T09:35:31.092Z
ALTER TABLE Words
ADD COLUMN correct BIT DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_word_correct ON Words (correct);