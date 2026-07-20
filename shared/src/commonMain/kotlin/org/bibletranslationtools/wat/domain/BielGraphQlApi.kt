package org.bibletranslationtools.wat.domain

import com.apollographql.apollo.ApolloClient
import org.bibletranslationtools.wat.GetGatewayLanguagesQuery
import org.bibletranslationtools.wat.GetHeartLanguagesQuery
import org.bibletranslationtools.wat.GetLanguageInfoQuery
import org.bibletranslationtools.wat.GetUsfmForHeartLanguageQuery
import org.bibletranslationtools.wat.data.ContentInfo
import org.bibletranslationtools.wat.data.Direction
import org.bibletranslationtools.wat.data.LanguageInfo

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
                    angName = it.english_name,
                    direction = Direction.of(it.direction)
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
                    angName = it.english_name,
                    direction = Direction.of(it.direction)
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