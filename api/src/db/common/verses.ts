import type { Verse } from "../../usfm";

export const verseKey = (v: { book: string; chapter: number; verse: string }) =>
  `${v.book}:${v.chapter}:${v.verse}`;

/**
 * De-duplicate verses by (book, chapter, verse): a single INSERT ... ON
 * CONFLICT DO UPDATE cannot affect the same conflict target twice, and some
 * source USFM repeats a verse ref. Last occurrence wins. Duplicates are logged
 * so they can be found in the Cloudflare console.
 */
export function dedupeVerses(verses: Verse[], resourceId: number): Verse[] {
  const unique = new Map<string, Verse>();
  const duplicates: string[] = [];
  for (const v of verses) {
    const ref = verseKey(v);
    if (unique.has(ref)) duplicates.push(ref);
    unique.set(ref, v);
  }
  if (duplicates.length > 0) {
    console.error(
      `duplicate verse refs in resource ${resourceId} (${duplicates.length}): ${duplicates.join(", ")}`,
    );
  }
  return [...unique.values()];
}

/** Unique refs, keyed by "book:chapter:verse". */
export function uniqueRefs<T extends { book: string; chapter: number; verse: string }>(
  refs: T[],
): T[] {
  return [...new Map(refs.map((r) => [verseKey(r), r])).values()];
}
