// Worker-side USFM parsing + singleton finding.
// Ports shared/.../UsfmBookSourceImpl.kt and AnalyzeViewModel.findSingletonWords
// so the source ingestion pipeline can run entirely on the worker.

import {
  USFMParser,
  CMarker,
  VMarker,
  TextBlock,
  FMarker,
  XMarker,
  TOC3Marker,
} from "usfmtools";

export type Verse = {
  book: string;
  chapter: number;
  verse: string;
  text: string;
};

export type Singleton = {
  word: string;
  ref: string; // "book:chapter:verse"
};

/**
 * Parse a USFM string into a flat list of verses.
 * Mirrors UsfmBookSourceImpl: footnotes (\f) and cross references (\x) are
 * excluded from the verse text, and the book slug comes from \toc3.
 */
export function parseVerses(usfm: string, bookSlug?: string): Verse[] {
  const parser = new USFMParser(["s5"], true);
  const document = parser.parseFromString(usfm);

  const slug = (
    bookSlug ??
    document.getChildMarkers(TOC3Marker)[0]?.bookAbbreviation ??
    "unknown"
  ).toLowerCase();

  const verses: Verse[] = [];
  for (const chapter of document.getChildMarkers(CMarker)) {
    for (const verse of chapter.getChildMarkers(VMarker)) {
      const text = verse
        .getChildMarkers(TextBlock, [FMarker, XMarker])
        .map((t) => t.text)
        .join("")
        .trim();

      verses.push({
        book: slug,
        chapter: chapter.number,
        verse: verse.verseNumber,
        text,
      });
    }
  }
  return verses;
}

// Word tokenizers
//  - apostrophe kept as part of the word, or
//  - apostrophe treated as a separator.
const WORD_WITH_APOSTROPHE = /[\p{L}\p{M}]+(?:['’][\p{L}\p{M}]+)*/gu;
const WORD_NO_APOSTROPHE = /[\p{L}\p{M}]+/gu;

/**
 * Find words that occur exactly once across all verses.
 * first-seen verse wins (and since singletons appear once,
 * that verse is the only occurrence). Sorted case-insensitively.
 */
export function findSingletons(
  verses: Verse[],
  apostropheIsSeparator: boolean,
): Singleton[] {
  const regex = apostropheIsSeparator
    ? WORD_NO_APOSTROPHE
    : WORD_WITH_APOSTROPHE;

  const counts = new Map<string, { count: number; ref: string }>();

  for (const verse of verses) {
    const ref = `${verse.book}:${verse.chapter}:${verse.verse}`;
    const matches = verse.text.match(regex);
    if (!matches) continue;

    for (const raw of matches) {
      const word = raw.trim();
      if (word.length === 0) continue;

      const existing = counts.get(word);
      if (existing) {
        existing.count += 1;
      } else {
        counts.set(word, { count: 1, ref });
      }
    }
  }

  return Array.from(counts.entries())
    .filter(([, v]) => v.count === 1)
    .map(([word, v]) => ({ word, ref: v.ref }))
    .sort((a, b) => a.word.toLowerCase().localeCompare(b.word.toLowerCase()));
}
