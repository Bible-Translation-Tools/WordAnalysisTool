DROP TABLE IF EXISTS Batches;

CREATE TABLE
    IF NOT EXISTS Batches (
        id TEXT PRIMARY KEY,
        ietf_code TEXT NOT NULL,
        resource_type TEXT NOT NULL,
        total INTEGER NOT NULL,
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
        result TEXT DEFAULT NULL,
        batch_id TEXT NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (batch_id) REFERENCES Batches (id) ON DELETE CASCADE
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