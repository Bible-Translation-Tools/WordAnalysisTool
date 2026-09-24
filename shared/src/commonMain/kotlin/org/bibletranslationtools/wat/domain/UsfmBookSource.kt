package org.bibletranslationtools.wat.domain

import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.platform.AppUsfmParser
import org.bibletranslationtools.wat.platform.markers.CMarker
import org.bibletranslationtools.wat.platform.markers.FMarker
import org.bibletranslationtools.wat.platform.markers.TOC3Marker
import org.bibletranslationtools.wat.platform.markers.TextBlock
import org.bibletranslationtools.wat.platform.markers.UsfmDocument
import org.bibletranslationtools.wat.platform.markers.VMarker
import org.bibletranslationtools.wat.platform.markers.XMarker

interface UsfmBookSource {
    suspend fun parse(
        usfm: String,
        bookSlug: String? = null,
        bookName: String? = null
    ): List<Verse>
}

class UsfmBookSourceImpl : UsfmBookSource {

    override suspend fun parse(
        usfm: String,
        bookSlug: String?,
        bookName: String?
    ): List<Verse> {
        val usfmParser = AppUsfmParser(arrayListOf("s5"), true)
        val document = usfmParser.parseFromString(usfm)

        val bookSlugFinal = bookSlug ?: document
            .getChildMarkers(TOC3Marker::class)
            .firstOrNull()
            ?.bookAbbreviation
            ?.lowercase() ?: "unknown"

        return getVerses(document, bookSlugFinal)
    }

    private fun getVerses(
        document: UsfmDocument,
        bookSlug: String
    ): List<Verse> {
        return document.getChildMarkers(CMarker::class).map { chapter ->
            chapter.getChildMarkers(VMarker::class).map { verse ->
                Verse(
                    book = bookSlug,
                    chapter = chapter.number,
                    verse = verse.verseNumber,
                    text = verse.getText()
                )
            }
        }.flatten()
    }
}

fun VMarker.getText(): String {
    val ignoredMarkers = listOf(FMarker::class, XMarker::class)
    val textBlocks = getChildMarkers(TextBlock::class, ignoredMarkers)
    val sb = StringBuilder()
    for ((idx, textBlock) in textBlocks.withIndex()) {
        sb.append(textBlock.text.trim())
        if (idx != textBlocks.lastIndex) {
            sb.append(" ")
        }
    }
    return sb.toString()
}