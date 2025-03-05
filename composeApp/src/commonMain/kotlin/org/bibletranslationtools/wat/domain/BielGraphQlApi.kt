package org.bibletranslationtools.wat.domain

import com.apollographql.apollo.ApolloClient
import org.bibletranslationtools.wat.GetBooksForTranslationQuery
import org.bibletranslationtools.wat.GetGatewayLanguagesQuery
import org.bibletranslationtools.wat.GetHeartLanguagesQuery
import org.bibletranslationtools.wat.GetUsfmForHeartLanguageQuery
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.ContentInfo

class BielGraphQlApi {
    private val graphQlServer = "https://api.bibleineverylanguage.org/v1/graphql"

    private val apolloClient = ApolloClient.Builder()
        .serverUrl(graphQlServer)
        .build()

    suspend fun getHeartLanguages(): List<LanguageInfo> {
        val response = apolloClient.query(GetHeartLanguagesQuery()).execute()
        return response.data?.let { data ->
            data.language.map {
                LanguageInfo(
                    ietfCode = it.ietf_code,
                    name = it.national_name,
                    angName = it.english_name
                )
            }
        } ?: listOf()
    }

    suspend fun getGatewayLanguages(): List<LanguageInfo> {
        val response = apolloClient.query(GetGatewayLanguagesQuery()).execute()
        return response.data?.let { data ->
            data.language.map {
                LanguageInfo(
                    ietfCode = it.ietf_code,
                    name = it.national_name,
                    angName = it.english_name
                )
            }
        } ?: listOf()
    }

    suspend fun getUsfmForHeartLanguage(
        ietfCode: String
    ): Map<String, List<ContentInfo>> {
        val response = apolloClient
            .query(GetUsfmForHeartLanguageQuery(ietfCode))
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

    suspend fun getBooksForTranslation(
        ietfCode: String,
        resourceType: String
    ): List<ContentInfo> {
        val response = apolloClient
            .query(GetBooksForTranslationQuery(ietfCode, resourceType))
            .execute()

        val usfmContent = mutableListOf<ContentInfo>()

        response.data?.let { data ->
            data.content.forEach { content ->
                content.rendered_contents.forEach { renderedContent ->
                    val contentInfo = ContentInfo(
                        renderedContent.url,
                        renderedContent.scriptural_rendering_metadata?.book_name,
                        renderedContent.scriptural_rendering_metadata?.book_slug,
                        renderedContent.scriptural_rendering_metadata?.chapter
                    )
                    usfmContent.add(contentInfo)
                }
            }
        }

        return usfmContent
    }
}