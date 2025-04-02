DROP TABLE IF EXISTS Batches;

CREATE TABLE
    IF NOT EXISTS Batches (id TEXT PRIMARY KEY, total INTEGER);

DROP TABLE IF EXISTS Words;

CREATE TABLE
    IF NOT EXISTS Words (
        id INTEGER PRIMARY KEY,
        word TEXT NOT NULL,
        result TEXT,
        batch_id TEXT,
        FOREIGN KEY (batch_id) REFERENCES Batches (id) ON DELETE CASCADE
    );

DROP TABLE IF EXISTS Logins;

CREATE TABLE
    IF NOT EXISTS Logins (
        id INTEGER PRIMARY KEY,
        username TEXT NOT NULL,
        email TEXT NOT NULL,
        access_token TEXT NOT NULL,
        refresh_token TEXT NOT NULL,
        token_type TEXT NOT NULL,
        state TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );