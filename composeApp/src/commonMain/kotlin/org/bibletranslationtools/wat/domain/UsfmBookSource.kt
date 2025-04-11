package org.bibletranslationtools.wat.domain

import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.platform.AppUsfmParser
import org.bibletranslationtools.wat.platform.markers.CMarker
import org.bibletranslationtools.wat.platform.markers.FMarker
import org.bibletranslationtools.wat.platform.markers.HMarker
import org.bibletranslationtools.wat.platform.markers.TOC3Marker
import org.bibletranslationtools.wat.platform.markers.TextBlock
import org.bibletranslationtools.wat.platform.markers.UsfmDocument
import org.bibletranslationtools.wat.platform.markers.VMarker
import org.bibletranslationtools.wat.platform.markers.XMarker

interface UsfmBookSource {
    suspend fun import(bytes: ByteArray)
    suspend fun parse(
        usfm: String,
        bookSlug: String? = null,
        bookName: String? = null
    ): List<Verse>
}

class UsfmBookSourceImpl : UsfmBookSource {

    override suspend fun import(bytes: ByteArray) {
        try {
            val usfm = bytes.decodeToString()
            val usfmParser = AppUsfmParser(arrayListOf("s5"), true)
            val document = usfmParser.parseFromString(usfm)

            val bookSlug = document
                .getChildMarkers(TOC3Marker::class)
                .firstOrNull()
                ?.bookAbbreviation
                ?.lowercase()

            val bookName = document
                .getChildMarkers(HMarker::class)
                .firstOrNull()
                ?.headerText

            if (bookSlug == null || bookName == null) {
                throw IllegalArgumentException("Book header is not complete.")
            }

//            val existentBook = bookDataSource.getBySlug(bookSlug)
//            if (existentBook != null) {
//                bookDataSource.update(existentBook.copy(content = usfm))
//            } else {
//                bookDataSource.add(
//                    slug = bookSlug,
//                    name = bookName,
//                    content = usfm
//                )
//            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not import file.", e)
        }
    }

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

        val bookNameFinal = document
            .getChildMarkers(HMarker::class)
            .firstOrNull()
            ?.headerText ?: "Unknown"

        return getVerses(document, bookSlugFinal, bookNameFinal)
    }

    private fun getVerses(
        document: UsfmDocument,
        bookSlug: String,
        bookName: String
    ): List<Verse> {
        return document.getChildMarkers(CMarker::class).map { chapter ->
            chapter.getChildMarkers(VMarker::class).map { verse ->
                Verse(
                    number = verse.startingVerse,
                    text = verse.getText(),
                    bookSlug = bookSlug,
                    bookName = bookName,
                    chapter = chapter.number
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