package org.bibletranslationtools.wat.platform

import org.bibletranslationtools.wat.platform.markers.UsfmDocument

interface IUSFMParser {
    fun parseFromString(input: String): UsfmDocument
}

expect class AppUsfmParser: IUSFMParser {
    val wrapper: Any
    constructor()
    constructor(tagsToIgnore: List<String>)
    constructor(tagsToIgnore: List<String>, ignoreUnknownMarkers: Boolean)

    override fun parseFromString(input: String): UsfmDocument
}
