package org.bibletranslationtools.wat.platform

import org.bibletranslationtools.wat.platform.markers.UsfmDocument

typealias JavaUSFMParser = org.wycliffeassociates.usfmtools.USFMParser

actual class AppUsfmParser: IUSFMParser {
    actual val wrapper: Any

    actual constructor() {
        wrapper = JavaUSFMParser()
    }

    actual constructor(tagsToIgnore: List<String>) {
        val ignoredTags = arrayListOf<String>()
        ignoredTags.addAll(tagsToIgnore)
        wrapper = JavaUSFMParser(ignoredTags)
    }

    actual constructor(
        tagsToIgnore: List<String>,
        ignoreUnknownMarkers: Boolean
    ) {
        val ignoredTags = arrayListOf<String>()
        ignoredTags.addAll(tagsToIgnore)
        wrapper = JavaUSFMParser(ignoredTags, ignoreUnknownMarkers)
    }

    actual override fun parseFromString(input: String): UsfmDocument {
        return UsfmDocument((wrapper as JavaUSFMParser).parseFromString(input))
    }
}
