package org.bibletranslationtools.wat.platform

import org.bibletranslationtools.wat.platform.markers.UsfmDocument

actual class AppUsfmParser : IUSFMParser {
    actual val wrapper: Any

    actual constructor() {
        wrapper = JsUSFMParser()
    }

    actual constructor(tagsToIgnore: List<String>) {
        val tagsToIgnoreJs = tagsToIgnore.map { it.toJsString() }.toJsArray()
        wrapper = JsUSFMParser(tagsToIgnoreJs)
    }

    actual constructor(
        tagsToIgnore: List<String>,
        ignoreUnknownMarkers: Boolean
    ) {
        val tagsToIgnoreJs = tagsToIgnore.map { it.toJsString() }.toJsArray()
        wrapper = JsUSFMParser(tagsToIgnoreJs, ignoreUnknownMarkers)
    }

    actual override fun parseFromString(input: String): UsfmDocument {
        val document = (wrapper as JsUSFMParser).parseFromString(input)
        return UsfmDocument(document)
    }
}
