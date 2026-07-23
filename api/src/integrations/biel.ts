// Thin GraphQL client for the public BIEL (Bible In Every Language) Hasura API.
// Mirrors the queries the KMP client runs via Apollo (see shared graphql/*.graphql),
// but as plain fetch calls so they can run on the worker.

const GRAPHQL_URL = "https://api.bibleineverylanguage.org/v1/graphql";

export type ContentInfo = {
  url: string | null;
  bookName: string | null;
  bookSlug: string | null;
  chapter: number | null;
};

export type LanguageInfo = {
  ietfCode: string;
  englishName: string;
  nationalName: string;
  direction: string;
};

const GET_BOOKS_FOR_TRANSLATION = /* GraphQL */ `
  query GetBooksForTranslation($ietfCode: String!, $resourceType: String!) {
    content(
      where: {
        language: { ietf_code: { _eq: $ietfCode } }
        wa_content_metadata: {
          show_on_biel: { _eq: true }
          status: { _eq: "Primary" }
        }
        resource_type: { _eq: $resourceType }
      }
    ) {
      name
      resource_type
      rendered_contents(
        where: {
          file_type: { _eq: "usfm" }
          scriptural_rendering_metadata: { is_whole_book: { _eq: true } }
        }
      ) {
        url
        file_type
        scriptural_rendering_metadata {
          book_name
          book_slug
          chapter
        }
      }
    }
  }
`;

const GET_LANGUAGE_INFO = /* GraphQL */ `
  query GetLanguageInfo($ietfCode: String!) {
    language(where: { ietf_code: { _eq: $ietfCode } }) {
      ietf_code
      english_name
      national_name
      direction
    }
  }
`;

async function graphql<T>(
  query: string,
  variables: Record<string, unknown>,
): Promise<T> {
  const response = await fetch(GRAPHQL_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query, variables }),
  });

  if (!response.ok) {
    throw new Error(`BIEL GraphQL request failed: ${response.status}`);
  }

  const json = (await response.json()) as { data?: T; errors?: unknown };
  if (json.errors) {
    throw new Error(`BIEL GraphQL error: ${JSON.stringify(json.errors)}`);
  }
  if (!json.data) {
    throw new Error("BIEL GraphQL returned no data");
  }
  return json.data;
}

type BooksForTranslationData = {
  content: {
    name: string | null;
    resource_type: string | null;
    rendered_contents: {
      url: string | null;
      file_type: string | null;
      scriptural_rendering_metadata: {
        book_name: string | null;
        book_slug: string | null;
        chapter: number | null;
      } | null;
    }[];
  }[];
};

type LanguageInfoData = {
  language: {
    ietf_code: string;
    english_name: string;
    national_name: string;
    direction: string;
  }[];
};

/** USFM whole-book content links for a language + resource type. */
export async function getBooksForTranslation(
  ietfCode: string,
  resourceType: string,
): Promise<ContentInfo[]> {
  const data = await graphql<BooksForTranslationData>(
    GET_BOOKS_FOR_TRANSLATION,
    { ietfCode, resourceType },
  );

  const result: ContentInfo[] = [];
  for (const content of data.content) {
    for (const rendered of content.rendered_contents) {
      result.push({
        url: rendered.url,
        bookName: rendered.scriptural_rendering_metadata?.book_name ?? null,
        bookSlug: rendered.scriptural_rendering_metadata?.book_slug ?? null,
        chapter: rendered.scriptural_rendering_metadata?.chapter ?? null,
      });
    }
  }
  return result;
}

/** Basic language metadata (name / direction) for an ietf code. */
export async function getLanguageInfo(
  ietfCode: string,
): Promise<LanguageInfo | null> {
  const data = await graphql<LanguageInfoData>(GET_LANGUAGE_INFO, { ietfCode });
  const lang = data.language[0];
  if (!lang) return null;
  return {
    ietfCode: lang.ietf_code,
    englishName: lang.english_name,
    nationalName: lang.national_name,
    direction: lang.direction,
  };
}
