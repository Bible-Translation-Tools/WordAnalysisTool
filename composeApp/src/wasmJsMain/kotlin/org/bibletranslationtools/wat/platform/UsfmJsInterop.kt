@file:JsModule("./usfm.mjs")

package org.bibletranslationtools.wat.platform

external fun convertBook(obj: JsAny): JsUsfmDocument

@JsName("JsUsfmDocument")
external val JsUsfmDocumentClass: JsAny

@JsName("JsTOC3Marker")
external val JsTOC3MarkerClass: JsAny

@JsName("JsHMarker")
external val JsHMarkerClass: JsAny

@JsName("JsCMarker")
external val JsCMarkerClass: JsAny

@JsName("JsVMarker")
external val JsVMarkerClass: JsAny

@JsName("JsFMarker")
external val JsFMarkerClass: JsAny

@JsName("JsXMarker")
external val JsXMarkerClass: JsAny

@JsName("JsTextBlock")
external val JsTextBlockClass: JsAny

open external class JsMarker : JsAny {
    val contents: JsArray<JsMarker>
    open fun getIdentifier(): String
    fun getPosition(): Int
    fun setPosition(value: Int)
    open fun getAllowedContents(): JsArray<JsAny>
    open fun preProcess(input: String): String
    open fun tryInsert(input: JsMarker): Boolean
    fun getTypesPathToLastMarker(): JsArray<JsAny>
    fun getHierarchyToMarker(target: JsMarker): JsArray<JsMarker>
    fun getHierarchyToMultipleMarkers(targets: JsArray<JsMarker>): JsArray<JsMarker>
    fun getChildMarkers(clazz: JsAny): JsArray<JsMarker>
    @JsName("getChildMarkersIgnored")
    fun getChildMarkers(clazz: JsAny, ignoredParents: JsArray<JsAny>): JsArray<JsMarker>
    fun getLastDescendant(): JsMarker
}

external class JsUsfmDocument: JsMarker {
    override fun getIdentifier(): String
    override fun getAllowedContents(): JsArray<JsAny>
    fun insert(marker: JsMarker)
}

external class JsTOC3Marker(bookAbbreviation: String): JsMarker {
    val bookAbbreviation: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
}

external class JsHMarker(headerText: String): JsMarker {
    val headerText: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
}

external class JsCMarker(number: String) : JsMarker {
    val number: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
    override fun getAllowedContents(): JsArray<JsAny>
}

external class JsVMarker(number: String): JsMarker {
    val number: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
    override fun tryInsert(input: JsMarker): Boolean
    override fun getAllowedContents(): JsArray<JsAny>
}

external class JsFMarker(footNoteCaller: String): JsMarker {
    val footNoteCaller: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
}

external class JsXMarker(crossRefCaller: String): JsMarker {
    val crossRefCaller: String
    override fun getIdentifier(): String
    override fun preProcess(input: String): String
}

external class JsTextBlock(text: String): JsMarker {
    val text: String
    override fun getIdentifier(): String
}
