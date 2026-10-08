/**
 * D1 usage meter for local development. D1 bills rows read and rows written
 * (not queries or transactions); every result carries them in `meta`, but
 * Drizzle drops that. Wrapping the binding with `meterD1` sums them so a cron
 * tick or request can log what it cost, plus a running total for the isolate.
 */

export type D1Usage = {
  statements: number;
  rowsRead: number;
  rowsWritten: number;
};

const zero = (): D1Usage => ({ statements: 0, rowsRead: 0, rowsWritten: 0 });

type Meta = { rows_read?: number; rows_written?: number } | undefined;

export class D1UsageMeter {
  /** Since the last `flush`. */
  current: D1Usage = zero();
  /** Since the meter was created (the isolate's lifetime in `wrangler dev`). */
  total: D1Usage = zero();

  record(meta: Meta): void {
    for (const u of [this.current, this.total]) {
      u.statements += 1;
      u.rowsRead += meta?.rows_read ?? 0;
      u.rowsWritten += meta?.rows_written ?? 0;
    }
  }

  /** Log and reset the current window. Quiet when nothing ran. */
  flush(label: string): D1Usage {
    const c = this.current;
    if (c.statements > 0) {
      console.log(
        `[d1] ${label}: ${c.statements} stmts, read ${fmt(c.rowsRead)}, ` +
          `written ${fmt(c.rowsWritten)} | total: read ${fmt(this.total.rowsRead)}, ` +
          `written ${fmt(this.total.rowsWritten)}`,
      );
    }
    this.current = zero();
    return c;
  }
}

function fmt(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2)}M`;
  if (n >= 10_000) return `${(n / 1_000).toFixed(1)}k`;
  return String(n);
}

/** One running total per isolate, shared by every container built in it. */
export const globalD1Meter = new D1UsageMeter();

/**
 * A D1PreparedStatement that reports its result meta to the meter. Explicit
 * delegation (not a Proxy) so `batch` can hand the real statements to D1.
 */
class MeteredStatement implements D1PreparedStatement {
  constructor(
    readonly inner: D1PreparedStatement,
    private readonly meter: D1UsageMeter,
  ) {}

  bind(...values: unknown[]): D1PreparedStatement {
    return new MeteredStatement(this.inner.bind(...values), this.meter);
  }

  async first<T = unknown>(colName?: string): Promise<T | null> {
    // `first` returns no meta; count the statement without rows.
    const row = await (colName === undefined
      ? this.inner.first<T>()
      : this.inner.first<T>(colName));
    this.meter.record(undefined);
    return row;
  }

  async run<T = Record<string, unknown>>(): Promise<D1Result<T>> {
    const res = await this.inner.run<T>();
    this.meter.record(res.meta);
    return res;
  }

  async all<T = Record<string, unknown>>(): Promise<D1Result<T>> {
    const res = await this.inner.all<T>();
    this.meter.record(res.meta);
    return res;
  }

  raw<T = unknown[]>(options: { columnNames: true }): Promise<[string[], ...T[]]>;
  raw<T = unknown[]>(options?: { columnNames?: false }): Promise<T[]>;
  async raw(options?: { columnNames?: boolean }): Promise<unknown[]> {
    // Drizzle runs every typed select through `raw()`, which returns bare
    // arrays and drops `meta`, so reads would never be counted. workerd's
    // statement exposes the session call `raw()` itself makes; use it to get
    // rows and meta in one round trip, and fall back to plain `raw()` if the
    // internals ever change.
    const upstream = await this.sendRaw();
    if (upstream === null) {
      const rows = await (this.inner.raw as (o?: unknown) => Promise<unknown[]>)(options);
      this.meter.record(undefined);
      return rows;
    }
    this.meter.record(upstream.meta);
    return rowsFromUpstream(upstream, options?.columnNames === true);
  }

  private async sendRaw(): Promise<UpstreamResult | null> {
    const inner = this.inner as unknown as InternalStatement;
    const session = inner.dbSession;
    if (
      typeof session?._sendOrThrow !== "function" ||
      typeof inner.statement !== "string" ||
      !Array.isArray(inner.params)
    ) {
      warnOnce();
      return null;
    }
    const res = await session._sendOrThrow(
      "/query",
      inner.statement,
      inner.params,
      "ROWS_AND_COLUMNS",
      noopSpan,
    );
    return Array.isArray(res) ? res[0] : res;
  }
}

// --- workerd internals used by the metered raw() (see sendRaw) ---------------

type UpstreamResult = {
  meta?: Meta;
  results?:
    | Record<string, unknown>[]
    | { columns: string[]; rows: unknown[][] };
};

type InternalStatement = {
  statement?: string;
  params?: unknown[];
  dbSession?: {
    _sendOrThrow?: (
      endpoint: string,
      query: string,
      params: unknown[],
      resultsFormat: string,
      span: unknown,
    ) => Promise<UpstreamResult | UpstreamResult[]>;
  };
};

const noopSpan = { setAttribute() {} };

/** Same shaping as workerd's own `raw()`. */
function rowsFromUpstream(s: UpstreamResult, columnNames: boolean): unknown[] {
  if (!s.results) return [];
  if (Array.isArray(s.results)) {
    const raw: unknown[] = [];
    for (const row of s.results) {
      if (columnNames && raw.length === 0) raw.push(Object.keys(row));
      raw.push(Object.values(row));
    }
    return raw;
  }
  return [...(columnNames ? [s.results.columns] : []), ...s.results.rows];
}

let warned = false;
function warnOnce() {
  if (warned) return;
  warned = true;
  console.warn(
    "[d1] statement internals not available: rows read by selects are not metered",
  );
}

const unwrap = (s: D1PreparedStatement): D1PreparedStatement =>
  s instanceof MeteredStatement ? s.inner : s;

/** Wrap a D1 binding so that every statement's rows read/written are metered. */
export function meterD1(d1: D1Database, meter: D1UsageMeter): D1Database {
  const metered: Pick<D1Database, "prepare" | "batch" | "exec"> = {
    prepare: (query) => new MeteredStatement(d1.prepare(query), meter),
    batch: async <T = unknown>(statements: D1PreparedStatement[]) => {
      const results = await d1.batch<T>(statements.map(unwrap));
      for (const r of results) meter.record(r.meta);
      return results;
    },
    exec: (query) => d1.exec(query),
  };
  // Anything else (withSession, dump, …) goes straight to the binding.
  return new Proxy(d1, {
    get(target, prop, receiver) {
      if (prop in metered) return metered[prop as keyof typeof metered];
      const value = Reflect.get(target, prop, receiver);
      return typeof value === "function" ? value.bind(target) : value;
    },
  });
}
