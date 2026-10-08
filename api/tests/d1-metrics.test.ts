import { describe, expect, it, vi } from "vitest";
import { D1UsageMeter, meterD1 } from "../src/db/sqlite/metrics";

/** Minimal fake D1 binding whose results carry rows_read / rows_written. */
function fakeD1() {
  const seen: { sql: string; params: unknown[] }[] = [];
  const result = (sql: string) => ({
    results: [],
    success: true,
    meta: { rows_read: sql.length, rows_written: sql.startsWith("insert") ? 2 : 0 },
  });
  // Mimics workerd: the statement exposes its session, SQL and params, and
  // the session's `_sendOrThrow` returns rows + meta.
  const dbSession = {
    _sendOrThrow: async (
      _endpoint: string,
      sql: string,
      params: unknown[],
      format: string,
      span: { setAttribute: (k: string, v: unknown) => void },
    ) => {
      span.setAttribute("x", 1);
      seen.push({ sql, params });
      return {
        ...result(sql),
        results:
          format === "ROWS_AND_COLUMNS"
            ? { columns: ["a", "b"], rows: [[1, params[0] ?? null]] }
            : [{ a: 1, b: params[0] ?? null }],
      };
    },
  };
  const statement = (sql: string, params: unknown[] = []): any => ({
    sql,
    statement: sql,
    params,
    dbSession,
    bind: (...values: unknown[]) => statement(sql, values),
    run: async () => (seen.push({ sql, params }), result(sql)),
    all: async () => (seen.push({ sql, params }), result(sql)),
    first: async () => (seen.push({ sql, params }), null),
    raw: async () => {
      throw new Error("plain raw() must not be used when internals exist");
    },
  });
  const batched: unknown[][] = [];
  const d1: any = {
    prepare: (sql: string) => statement(sql),
    batch: async (stmts: any[]) => {
      batched.push(stmts);
      return stmts.map((s) => result(s.sql));
    },
    exec: async () => ({ count: 0, duration: 0 }),
    dump: async () => new ArrayBuffer(0),
  };
  return { d1, seen, batched };
}

describe("meterD1", () => {
  it("sums rows read/written over run, all and batch, and flushes per window", async () => {
    const { d1, batched } = fakeD1();
    const meter = new D1UsageMeter();
    const db = meterD1(d1, meter);

    await db.prepare("select 1").bind(1).all();
    await db.prepare("insert x").run();
    await db.prepare("select 22").first();
    await db.batch([db.prepare("insert y"), db.prepare("select 333")]);

    expect(meter.current).toEqual({
      statements: 5,
      rowsRead: "select 1".length + "insert x".length + "insert y".length + "select 333".length,
      rowsWritten: 4,
    });
    // batch received the real statements, not the wrappers
    expect(batched[0].every((s: any) => typeof s.sql === "string")).toBe(true);

    const log = vi.spyOn(console, "log").mockImplementation(() => {});
    const window = meter.flush("tick");
    expect(window.statements).toBe(5);
    expect(meter.current).toEqual({ statements: 0, rowsRead: 0, rowsWritten: 0 });
    expect(meter.total.statements).toBe(5);
    expect(log).toHaveBeenCalledWith(expect.stringContaining("[d1] tick: 5 stmts"));

    meter.flush("idle");
    expect(log).toHaveBeenCalledTimes(1);
    log.mockRestore();
  });

  it("meters raw() selects via the session and returns array rows", async () => {
    const { d1 } = fakeD1();
    const meter = new D1UsageMeter();
    const db = meterD1(d1, meter);

    const rows = await db.prepare("select raw").bind("v").raw();
    expect(rows).toEqual([[1, "v"]]);
    const withNames = await db.prepare("select raw").raw({ columnNames: true });
    expect(withNames).toEqual([["a", "b"], [1, null]]);
    expect(meter.current.statements).toBe(2);
    expect(meter.current.rowsRead).toBe("select raw".length * 2);
  });

  it("falls back to plain raw() when internals are missing", async () => {
    const { d1 } = fakeD1();
    const meter = new D1UsageMeter();
    const db = meterD1(d1, meter);
    const stmt: any = db.prepare("select plain");
    stmt.inner.dbSession = undefined;
    stmt.inner.raw = async () => [[42]];
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    expect(await stmt.raw()).toEqual([[42]]);
    expect(meter.current).toEqual({ statements: 1, rowsRead: 0, rowsWritten: 0 });
    warn.mockRestore();
  });

  it("passes other binding methods through", async () => {
    const { d1 } = fakeD1();
    const db = meterD1(d1, new D1UsageMeter());
    expect(await db.dump()).toBeInstanceOf(ArrayBuffer);
  });
});
