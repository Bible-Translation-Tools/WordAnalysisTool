package org.bibletranslationtools.wat.platform

import kotlin.reflect.KClass

typealias PlatformUSFMParser = org.wycliffeassociates.usfmtools.USFMParser
typealias PlatformUSFMDocument = org.wycliffeassociates.usfmtools.models.markers.USFMDocument
typealias PlatformMarker = org.wycliffeassociates.usfmtools.models.markers.Marker

typealias PlatformTOC3Marker = org.wycliffeassociates.usfmtools.models.markers.TOC3Marker
typealias PlatformHMarker = org.wycliffeassociates.usfmtools.models.markers.HMarker
typealias PlatformCMarker = org.wycliffeassociates.usfmtools.models.markers.CMarker
typealias PlatformVMarker = org.wycliffeassociates.usfmtools.models.markers.VMarker
typealias PlatformFMarker = org.wycliffeassociates.usfmtools.models.markers.FMarker
typealias PlatformXMarker = org.wycliffeassociates.usfmtools.models.markers.XMarker
typealias PlatformTextBlock = org.wycliffeassociates.usfmtools.models.markers.TextBlock

private const val PLATFORM_PACKAGE = "org.wycliffeassociates.usfmtools.models.markers"

actual class AppUsfmParser: IUSFMParser {
    actual val wrapper: Any

    actual constructor() {
        wrapper = PlatformUSFMParser()
    }

    actual constructor(tagsToIgnore: List<String>) {
        val ignoredTags = arrayListOf<String>()
        ignoredTags.addAll(tagsToIgnore)
        wrapper = PlatformUSFMParser(ignoredTags)
    }

    actual constructor(
        tagsToIgnore: List<String>,
        ignoreUnknownMarkers: Boolean
    ) {
        val ignoredTags = arrayListOf<String>()
        ignoredTags.addAll(tagsToIgnore)
        wrapper = PlatformUSFMParser(ignoredTags, ignoreUnknownMarkers)
    }

    actual override fun parseFromString(input: String): UsfmDocument {
        return UsfmDocument((wrapper as PlatformUSFMParser).parseFromString(input))
    }
}

actual class UsfmDocument(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {

    actual override fun getIdentifier(): String {
        return (wrapper as PlatformUSFMDocument).identifier
    }

    actual override fun getAllowedContents(): List<Any> {
        return (wrapper as PlatformUSFMDocument).allowedContents
    }

    actual fun insertMarker(marker: IMarker) {
        (wrapper as PlatformUSFMDocument).insert(marker.toPlatform())
    }

    actual fun insertDocument(document: UsfmDocument) {
        (wrapper as PlatformUSFMDocument).insert(document.toPlatform())
    }

    actual fun insertMultiple(iterable: Iterable<IMarker>) {
        (wrapper as PlatformUSFMDocument).insertMultiple(iterable.map { it.toPlatform() })
    }
}

actual open class MarkerWrapper(
    actual open val wrapper: Any
) : IMarker {
    actual override val contents: List<IMarker>
        get() = (wrapper as PlatformMarker).contents.map { MarkerFactory.create(it) }

    actual override fun getIdentifier(): String {
        return (wrapper as PlatformMarker).identifier
    }

    actual override fun getPosition(): Int {
        return (wrapper as PlatformMarker).position
    }

    actual override fun setPosition(value: Int) {
        (wrapper as PlatformMarker).position = value
    }

    actual override fun getAllowedContents(): List<Any> {
        return (wrapper as PlatformMarker).allowedContents
    }

    actual override fun preProcess(input: String): String {
        return (wrapper as PlatformMarker).preProcess(input)
    }

    actual override fun tryInsert(input: IMarker): Boolean {
        return (wrapper as PlatformMarker).tryInsert(input.toPlatform())
    }

    actual override fun getTypesPathToLastMarker(): List<Any> {
        return (wrapper as PlatformMarker).typesPathToLastMarker
    }

    actual override fun getHierarchyToMarker(target: IMarker): List<IMarker> {
        return (wrapper as PlatformMarker).getHierarchyToMarker(target.toPlatform())
            .map { MarkerFactory.create(it) }
    }

    actual override fun getHierarchyToMultipleMarkers(
        targets: List<IMarker>
    ): Map<IMarker, List<IMarker>> {
        val platformMap = (wrapper as PlatformMarker).getHierachyToMultipleMarkers(
            targets.map { it.toPlatform() }
        )
        val map: Map<IMarker, List<IMarker>> = platformMap.entries.associate { (k, v) ->
            MarkerFactory.create(k) to v.map { MarkerFactory.create(it) }
        }
        return map
    }

    actual override fun <U : IMarker> getChildMarkers(clazz: KClass<U>): List<U> {
        return getPlatformMarkerClass(clazz)?.let { outClass ->
            val platformMarkers = (wrapper as PlatformMarker).getChildMarkers(outClass)
            platformMarkers.map { MarkerFactory.create(it) as U }
        } ?: emptyList()
    }

    actual override fun <U : IMarker, P : IMarker> getChildMarkers(
        clazz: KClass<U>,
        ignoredParents: List<KClass<out P>>
    ): List<U> {
        return getPlatformMarkerClass(clazz)?.let { outClass ->
            val ignored = ignoredParents.mapNotNull {
                getPlatformMarkerClass(it)
            }
            val platformMarkers = (wrapper as PlatformMarker).getChildMarkers(outClass, ignored)
            platformMarkers.mapNotNull { MarkerFactory.create(it) as? U }
        } ?: emptyList()
    }

    actual override fun getLastDescendant(): IMarker {
        return MarkerFactory.create((wrapper as PlatformMarker).lastDescendent)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T: IMarker> getPlatformMarkerClass(clazz: KClass<T>): Class<PlatformMarker>? {
        return try {
            Class.forName("$PLATFORM_PACKAGE.${clazz.simpleName}") as Class<PlatformMarker>
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: ClassCastException) {
            null
        }
    }
}

actual object MarkerFactory {
    actual fun create(marker: Any): IMarker {
        return when (marker) {
            is PlatformTOC3Marker -> TOC3Marker(marker)
            is PlatformHMarker -> HMarker(marker)
            is PlatformCMarker -> CMarker(marker)
            is PlatformVMarker -> VMarker(marker)
            is PlatformFMarker -> FMarker(marker)
            is PlatformXMarker -> XMarker(marker)
            is PlatformTextBlock -> TextBlock(marker)
            else -> throw IllegalArgumentException("Unknown marker type")
        }
    }
}

actual class TOC3Marker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val bookAbbreviation = (wrapper as PlatformTOC3Marker).bookAbbreviation
}

actual class HMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val headerText = (wrapper as PlatformHMarker).headerText
}

actual class CMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val number = (wrapper as PlatformCMarker).number
}

actual class VMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val verseNumber = (wrapper as PlatformVMarker).verseNumber
    actual val startingVerse = (wrapper as PlatformVMarker).startingVerse
    actual val endingVerse = (wrapper as PlatformVMarker).endingVerse
}

actual class FMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val footNoteCaller = (wrapper as PlatformFMarker).footNoteCaller
}

actual class XMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val crossRefCaller = (wrapper as PlatformXMarker).crossRefCaller
}

actual class TextBlock(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val text = (wrapper as PlatformTextBlock).text
}

private fun IMarker.toPlatform(): PlatformMarker {
    return (this as MarkerWrapper).wrapper as PlatformMarker
}