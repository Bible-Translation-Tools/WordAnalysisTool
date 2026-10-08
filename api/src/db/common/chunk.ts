/**
 * Split rows into chunks so that a single statement binds at most `maxParams`
 * values: each row binds `paramsPerRow`, and `reserved` is for the statement's
 * fixed parameters (e.g. a batch id in the WHERE clause).
 */
export function chunkByParams<T>(
  rows: T[],
  paramsPerRow: number,
  maxParams: number,
  reserved = 0,
): T[][] {
  const size = Math.max(1, Math.floor((maxParams - reserved) / paramsPerRow));
  const chunks: T[][] = [];
  for (let i = 0; i < rows.length; i += size) {
    chunks.push(rows.slice(i, i + size));
  }
  return chunks;
}
