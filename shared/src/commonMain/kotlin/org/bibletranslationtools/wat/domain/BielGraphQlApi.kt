package org.bibletranslationtools.wat.domain

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import org.bibletranslationtools.wat.GetLanguageInfoQuery
import org.bibletranslationtools.wat.GetLanguagesQuery
import org.bibletranslationtools.wat.GetUsfmForLanguageQuery
import org.bibletranslationtools.wat.data.ContentInfo
import org.bibletranslationtools.wat.data.Direction
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.type.Boolean_comparison_exp
import org.bibletranslationtools.wat.type.Language_bool_exp
import org.bibletranslationtools.wat.type.Wa_language_metadata_bool_exp

class BielGraphQlApi {
    private val graphQlServer = "https://api.bibleineverylanguage.org/v1/graphql"

    private val apolloClient = ApolloClient.Builder()
        .serverUrl(graphQlServer)
        .build()

    suspend fun getLanguages(gateway: Boolean? = null): List<LanguageInfo> {
        val whereClause: Optional<Language_bool_exp?> = if (gateway != null) {
            Optional.Present(
                Language_bool_exp(
                    wa_language_metadata = Optional.Present(
                        Wa_language_metadata_bool_exp(
                            is_gateway = Optional.Present(
                                Boolean_comparison_exp(_eq = Optional.Present(gateway))
                            )
                        )
                    )
                )
            )
        } else {
            Optional.Present(Language_bool_exp())
        }

        val response = apolloClient.query(GetLanguagesQuery(where = whereClause)).execute()

        return response.data?.let { data ->
            data.language.map {
                LanguageInfo(
                    ietfCode = it.ietf_code,
                    name = it.national_name,
                    angName = it.english_name,
                    direction = Direction.of(it.direction)
                )
            }
        } ?: listOf()
    }

    suspend fun getUsfmForLanguage(
        ietfCode: String
    ): Map<String, List<ContentInfo>> {
        val response = apolloClient
            .query(GetUsfmForLanguageQuery(ietfCode))
            .execute()

        val groupedContent = mutableMapOf<String, MutableList<ContentInfo>>()

        response.data?.let { data ->
            data.content.forEach { content ->
                val resourceType = content.resource_type ?: "Unknown" // Handle null resource type
                content.rendered_contents.forEach { renderedContent ->
                    val contentInfo = ContentInfo(
                        renderedContent.url,
                        renderedContent.scriptural_rendering_metadata?.book_name,
                        renderedContent.scriptural_rendering_metadata?.book_slug,
                        renderedContent.scriptural_rendering_metadata?.chapter
                    )
                    groupedContent.getOrPut(resourceType) { mutableListOf() }
                        .add(contentInfo)
                }
            }
        }
        return groupedContent
    }

    suspend fun getLanguageInfo(ietfCode: String): LanguageInfo? {
        val response = apolloClient.query(GetLanguageInfoQuery(ietfCode)).execute()
        return response.data?.let { data ->
            data.language.firstOrNull()?.let {
                LanguageInfo(
                    ietfCode = it.ietf_code,
                    name = it.national_name,
                    angName = it.english_name,
                    direction = Direction.of(it.direction)
                )
            }
        }
    }
}