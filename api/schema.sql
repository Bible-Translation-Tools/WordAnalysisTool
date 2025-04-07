DROP TABLE IF EXISTS Batches;

CREATE TABLE
    IF NOT EXISTS Batches (
        id TEXT PRIMARY KEY,
        ietf_code TEXT NOT NULL,
        resource_type TEXT NOT NULL,
        pending BIT NOT NULL DEFAULT 0,
        total_pending INTEGER NOT NULL,
        error TEXT DEFAULT NULL,
        user_id INTEGER NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES Users (id) ON DELETE CASCADE
    );

DROP TABLE IF EXISTS Words;

CREATE TABLE
    IF NOT EXISTS Words (
        id INTEGER PRIMARY KEY,
        word TEXT NOT NULL,
        batch_id TEXT NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (batch_id) REFERENCES Batches (id) ON DELETE CASCADE
    );

DROP TABLE IF EXISTS Models;

CREATE TABLE
    IF NOT EXISTS Models (
        id INTEGER PRIMARY KEY,
        model TEXT NOT NULL,
        status INTEGER NOT NULL,
        word_id INTEGER NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (word_id) REFERENCES Words (id) ON DELETE CASCADE
    );

DROP TABLE IF EXISTS Users;

CREATE TABLE
    IF NOT EXISTS Users (
        id INTEGER PRIMARY KEY,
        wacs_user_id INTEGER NOT NULL,
        username TEXT NOT NULL,
        email TEXT NOT NULL,
        access_token TEXT,
        refresh_token TEXT,
        token_type TEXT,
        state TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_batch_user_id ON Batches (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_batch ON Batches (ietf_code, resource_type);

CREATE INDEX IF NOT EXISTS idx_word_batch_id ON Words (batch_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_word ON Words (word, batch_id);

CREATE INDEX IF NOT EXISTS idx_model_word_id ON Models (word_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_model ON Models (model, word_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_user ON Users (email);

PRAGMA optimize;