@file:JsModule("usfmtools")

package org.bibletranslationtools.wat.platform

@JsName("USFMParser")
external class JsUSFMParser(
    tagsToIgnore: JsArray<JsString>? = definedExternally,
    ignoreUnknownMarkers: Boolean = definedExternally
) {
    fun parseFromString(input: String): JsUsfmDocument
}

@JsName("Marker")
external val JsMarkerClass: JsAny

@JsName("Marker")
open external class JsMarker() : JsAny {
    val contents: JsArray<JsMarker>
    var position: Int
    open fun getIdentifier(): String
    open fun getAllowedContents(): JsArray<JsMarker>
    open fun preProcess(input: String): String
    open fun tryInsert(input: JsMarker): Boolean
    fun getHierarchyToMarker(target: JsMarker): JsArray<JsMarker>
    fun getChildMarkers(
        type: JsAny,
        ignoredParents: JsArray<JsAny>? = definedExternally
    ): JsArray<JsMarker>
    fun getLastDescendant(): JsMarker
}

@JsName("USFMDocument")
external val JsUsfmDocumentClass: JsAny

@JsName("USFMDocument")
external class JsUsfmDocument: JsMarker {
    override fun getIdentifier(): String
    override fun getAllowedContents(): JsArray<JsMarker>
    fun insert(marker: JsMarker)
}

@JsName("TOC3Marker")
external val JsTOC3MarkerClass: JsAny

@JsName("TOC3Marker")
external class JsTOC3Marker: JsMarker {
    val bookAbbreviation: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
}

@JsName("HMarker")
external val JsHMarkerClass: JsAny

@JsName("HMarker")
external class JsHMarker: JsMarker {
    val headerText: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
}

@JsName("PMarker")
external val JsPMarkerClass: JsAny

@JsName("PMarker")
external class JsPMarker: JsMarker {
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
    override fun getAllowedContents(): JsArray<JsMarker>
}

@JsName("SMarker")
external val JsSMarkerClass: JsAny

@JsName("SMarker")
external class JsSMarker: JsMarker {
    val weight: Int
    val text: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
    override fun getAllowedContents(): JsArray<JsMarker>
}

@JsName("CMarker")
external val JsCMarkerClass: JsAny

@JsName("CMarker")
external class JsCMarker: JsMarker {
    val number: Int
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
    override fun getAllowedContents(): JsArray<JsMarker>
}

@JsName("VMarker")
external val JsVMarkerClass: JsAny

@JsName("VMarker")
external class JsVMarker: JsMarker {
    val verseNumber: String
    val startingVerse: Int
    val endingVerse: Int
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
    override fun tryInsert(input: JsMarker): Boolean
    override fun getAllowedContents(): JsArray<JsMarker>
    fun getVerseCharacter(): String
}

@JsName("FMarker")
external val JsFMarkerClass: JsAny

@JsName("FMarker")
external class JsFMarker: JsMarker {
    val footNoteCaller: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
}

@JsName("XMarker")
external val JsXMarkerClass: JsAny

@JsName("XMarker")
external class JsXMarker: JsMarker {
    val crossRefCaller: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
}

@JsName("WMarker")
external val JsWMarkerClass: JsAny

@JsName("WMarker")
external class JsWMarker: JsMarker {
    val term: String
    val attributes: JsMap<JsString, JsString>
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
    override fun getAllowedContents(): JsArray<JsMarker>
}

@JsName("TextBlock")
external val JsTextBlockClass: JsAny

@JsName("TextBlock")
external class JsTextBlock: JsMarker {
    val text: String
    override fun getIdentifier(): String
}
