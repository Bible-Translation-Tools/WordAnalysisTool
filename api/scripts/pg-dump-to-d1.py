#!/usr/bin/env python3
"""
Convert a Postgres data dump (`pg_dump --data-only` / `supabase db dump
--data-only`, multi-row INSERT format) into SQL files D1 can import.

    python3 scripts/pg-dump-to-d1.py ../build/tmp/data.sql ../build/tmp/d1
    for f in ../build/tmp/d1/*.sql; do npx wrangler d1 execute DB --remote --file "$f"; done

Column names are the same in both schemas. Booleans become 0/1, timestamps
become unix seconds (the Postgres values are UTC), everything else is passed
through. Tables are written in foreign-key order; each INSERT stays under
D1's 100 KB statement limit.
"""
import os
import re
import sys
from datetime import datetime, timezone

# Foreign-key order; anything else in the dump (drizzle migrations, auth) is skipped.
TABLES = ["languages", "resources", "users", "batches", "verses", "words", "models", "word_reviews"]
BOOL_COLS = {"gw", "pending", "ingesting", "apostrophe_is_separator", "unanimous", "sampled", "correct"}
TIME_COLS = {"created_at", "updated_at"}
MAX_STATEMENT_BYTES = 90_000  # D1 rejects statements over 100 KB

HEADER = re.compile(r'INSERT INTO "public"\."(\w+)" \(([^)]*)\)(?: OVERRIDING SYSTEM VALUE)? VALUES\n')


def parse_values(text, pos):
    """Parse `(v, v, ...),\n(...);` tuples starting at `pos`. Yields lists of raw tokens."""
    n = len(text)
    while True:
        while pos < n and text[pos] in " \t\r\n":
            pos += 1
        if text[pos] != "(":
            raise ValueError(f"expected '(' at {pos}: {text[pos:pos+40]!r}")
        pos += 1
        row = []
        while True:
            while text[pos] == " ":
                pos += 1
            if text[pos] == "'":
                end = pos + 1
                while True:
                    end = text.index("'", end)
                    if text[end + 1] == "'":  # escaped quote
                        end += 2
                        continue
                    break
                row.append(text[pos : end + 1])
                pos = end + 1
            else:
                end = pos
                while text[end] not in ",)":
                    end += 1
                row.append(text[pos:end].strip())
                pos = end
            if text[pos] == ",":
                pos += 1
                continue
            pos += 1  # ')'
            break
        yield row
        while text[pos] in " \t\r\n":
            pos += 1
        if text[pos] == ";":
            return
        if text[pos] != ",":
            raise ValueError(f"expected ',' or ';' at {pos}: {text[pos:pos+40]!r}")
        pos += 1


def convert(col, tok):
    if tok == "NULL":
        return tok
    if col in BOOL_COLS:
        return {"true": "1", "false": "0", "t": "1", "f": "0"}[tok.strip("'")]
    if col in TIME_COLS:
        raw = tok.strip("'").replace("+00", "")
        if "." not in raw:
            raw += ".0"
        dt = datetime.strptime(raw, "%Y-%m-%d %H:%M:%S.%f").replace(tzinfo=timezone.utc)
        return str(int(dt.timestamp()))
    return tok


def main(src, out_dir):
    text = open(src, encoding="utf-8").read()
    os.makedirs(out_dir, exist_ok=True)
    rows_by_table = {t: [] for t in TABLES}
    cols_by_table = {}
    for m in HEADER.finditer(text):
        table = m.group(1)
        if table not in rows_by_table:
            continue
        cols = [c.strip().strip('"') for c in m.group(2).split(",")]
        cols_by_table.setdefault(table, cols)
        assert cols_by_table[table] == cols, f"{table}: column list changed between statements"
        for row in parse_values(text, m.end()):
            assert len(row) == len(cols), f"{table}: {len(row)} values for {len(cols)} columns"
            rows_by_table[table].append([convert(c, v) for c, v in zip(cols, row)])

    for i, table in enumerate(TABLES):
        rows = rows_by_table[table]
        if not rows:
            print(f"{table}: no rows")
            continue
        cols = ", ".join(f"`{c}`" for c in cols_by_table[table])
        path = os.path.join(out_dir, f"{i:02d}_{table}.sql")
        head = f"INSERT INTO `{table}` ({cols}) VALUES\n"
        with open(path, "w", encoding="utf-8") as f:
            chunk, size = [], len(head)
            for r in rows:
                value = "(" + ", ".join(r) + ")"
                if chunk and size + len(value.encode()) > MAX_STATEMENT_BYTES:
                    f.write(head + ",\n".join(chunk) + ";\n")
                    chunk, size = [], len(head)
                chunk.append(value)
                size += len(value.encode()) + 2
            f.write(head + ",\n".join(chunk) + ";\n")
        print(f"{table}: {len(rows)} rows -> {path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
