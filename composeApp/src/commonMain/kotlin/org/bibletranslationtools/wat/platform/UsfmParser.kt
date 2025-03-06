package org.bibletranslationtools.wat.platform

import kotlin.reflect.KClass

interface IMarker {
    val contents: List<IMarker>
    fun getIdentifier(): String
    fun getPosition(): Int
    fun setPosition(value: Int)
    fun getAllowedContents(): List<Any>
    fun preProcess(input: String): String
    fun tryInsert(input: IMarker): Boolean
    fun getTypesPathToLastMarker(): List<Any>
    fun getHierarchyToMarker(target: IMarker): List<IMarker>
    fun getHierarchyToMultipleMarkers(targets: List<IMarker>): Map<IMarker, List<IMarker>>
    fun <U: IMarker> getChildMarkers(clazz: KClass<U>): List<U>
    fun <U: IMarker, P: IMarker> getChildMarkers(clazz: KClass<U>, ignoredParents: List<KClass<out P>>): List<U>
    fun getLastDescendant(): IMarker
}

interface IUSFMParser {
    fun parseFromString(input: String): UsfmDocument
}

expect open class MarkerWrapper : IMarker {
    val wrapper: Any
    override val contents: List<IMarker>
    override fun getIdentifier(): String
    override fun getPosition(): Int
    override fun setPosition(value: Int)
    override fun getAllowedContents(): List<Any>
    override fun preProcess(input: String): String
    override fun tryInsert(input: IMarker): Boolean
    override fun getTypesPathToLastMarker(): List<Any>
    override fun getHierarchyToMarker(target: IMarker): List<IMarker>
    override fun getHierarchyToMultipleMarkers(targets: List<IMarker>): Map<IMarker, List<IMarker>>
    override fun <U : IMarker> getChildMarkers(clazz: KClass<U>): List<U>
    override fun <U : IMarker, P : IMarker> getChildMarkers(clazz: KClass<U>, ignoredParents: List<KClass<out P>>): List<U>
    override fun getLastDescendant(): IMarker
}

expect object MarkerFactory {
    fun create(marker: Any): IMarker
}

expect class UsfmDocument : MarkerWrapper {
    override fun getIdentifier(): String
    override fun getAllowedContents(): List<Any>

    fun insertMarker(marker: IMarker)
    fun insertDocument(document: UsfmDocument)
    fun insertMultiple(iterable: Iterable<IMarker>)
}

expect class AppUsfmParser: IUSFMParser {
    val wrapper: Any
    constructor()
    constructor(tagsToIgnore: List<String>)
    constructor(tagsToIgnore: List<String>, ignoreUnknownMarkers: Boolean)

    override fun parseFromString(input: String): UsfmDocument
}

expect class TOC3Marker : MarkerWrapper {
    val bookAbbreviation: String
}

expect class HMarker : MarkerWrapper {
    val headerText: String
}

expect class CMarker : MarkerWrapper {
    val number: Int
}

expect class VMarker : MarkerWrapper {
    val verseNumber: String
    val startingVerse: Int
    val endingVerse: Int
}

expect class FMarker : MarkerWrapper {
    val footNoteCaller: String
}

expect class XMarker : MarkerWrapper {
    val crossRefCaller: String
}

expect class TextBlock : MarkerWrapper {
    val text: String
}

