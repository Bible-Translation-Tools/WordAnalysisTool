package org.bibletranslationtools.wat.platform

import kotlin.reflect.KClass

internal class JsonParser {
    fun parse(input: String): JsUsfmDocument {
        val json = toJSON(input)
        return convertBook(json)
    }
}

actual class AppUsfmParser : IUSFMParser {
    actual val wrapper: Any

    actual constructor() {
        wrapper = JsonParser()
    }

    actual constructor(tagsToIgnore: List<String>) {
        wrapper = JsonParser()
    }

    actual constructor(
        tagsToIgnore: List<String>,
        ignoreUnknownMarkers: Boolean
    ) {
        wrapper = JsonParser()
    }

    actual override fun parseFromString(input: String): UsfmDocument {
        val document = (wrapper as JsonParser).parse(input)
        return UsfmDocument(document)
    }
}

actual class UsfmDocument(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual override fun getIdentifier(): String {
        return wrapper.toJs<JsUsfmDocument>().getIdentifier()
    }

    actual override fun getAllowedContents(): List<Any> {
        return wrapper.toJs<JsUsfmDocument>().getAllowedContents().toList()
    }

    actual fun insertMarker(marker: IMarker) {
        wrapper.toJs<JsUsfmDocument>().insert(marker.toPlatform())
    }

    actual fun insertDocument(document: UsfmDocument) {
        TODO("Not yet implemented")
    }

    actual fun insertMultiple(iterable: Iterable<IMarker>) {
        TODO("Not yet implemented")
    }
}

actual open class MarkerWrapper(
    actual open val wrapper: Any
) : IMarker {
    actual override val contents: List<IMarker>
        get() = wrapper.toJs<JsMarker>().contents.toList().map { MarkerFactory.create(it) }

    actual override fun getIdentifier(): String {
        return wrapper.toJs<JsMarker>().getIdentifier()
    }

    actual override fun getPosition(): Int {
        return wrapper.toJs<JsMarker>().getPosition()
    }

    actual override fun setPosition(value: Int) {
        return wrapper.toJs<JsMarker>().setPosition(value)
    }

    actual override fun getAllowedContents(): List<Any> {
        return wrapper.toJs<JsMarker>().getAllowedContents().toList()
    }

    actual override fun preProcess(input: String): String {
        return wrapper.toJs<JsMarker>().preProcess(input)
    }

    actual override fun tryInsert(input: IMarker): Boolean {
        return wrapper.toJs<JsMarker>().tryInsert(input.toPlatform())
    }

    actual override fun getTypesPathToLastMarker(): List<Any> {
        return wrapper.toJs<JsMarker>().getTypesPathToLastMarker().toList()
    }

    actual override fun getHierarchyToMarker(target: IMarker): List<IMarker> {
        return wrapper.toJs<JsMarker>().getHierarchyToMarker(target.toPlatform())
            .toList()
            .map { MarkerFactory.create(it) }
    }

    actual override fun getHierarchyToMultipleMarkers(targets: List<IMarker>): Map<IMarker, List<IMarker>> {
        TODO("Not yet implemented")
    }

    actual override fun <U : IMarker> getChildMarkers(clazz: KClass<U>): List<U> {
        return getPlatformMarkerClass(clazz)?.let { outClass ->
            val platformMarkers = wrapper.toJs<JsMarker>().getChildMarkers(outClass)
            platformMarkers.toList().map { MarkerFactory.create(it) as U }
        } ?: emptyList()
    }

    actual override fun <U : IMarker, P : IMarker> getChildMarkers(
        clazz: KClass<U>,
        ignoredParents: List<KClass<out P>>
    ): List<U> {
        return getPlatformMarkerClass(clazz)?.let { outClass ->
            val ignored = ignoredParents.mapNotNull {
                getPlatformMarkerClass(it)?.toJs()
            }.toJsArray()
            val platformMarkers = wrapper.toJs<JsMarker>().getChildMarkers(outClass, ignored)
            platformMarkers.toList().mapNotNull { MarkerFactory.create(it) as? U }
        } ?: emptyList()
    }

    actual override fun getLastDescendant(): IMarker {
        return MarkerFactory.create(wrapper.toJs<JsMarker>().getLastDescendant())
    }

    private fun <T: IMarker> getPlatformMarkerClass(clazz: KClass<T>): JsAny? {
        return try {
            when (clazz) {
                HMarker::class -> JsHMarkerClass
                TOC3Marker::class -> JsTOC3MarkerClass
                CMarker::class -> JsCMarkerClass
                VMarker::class -> JsVMarkerClass
                TextBlock::class -> JsTextBlockClass
                FMarker::class -> JsFMarkerClass
                XMarker::class -> JsXMarkerClass
                else -> null
            }
        } catch (e: ClassCastException) {
            null
        }
    }
}

actual object MarkerFactory {
    actual fun create(marker: Any): IMarker {
        return when (marker) {
            is JsUsfmDocument -> UsfmDocument(marker)
            is JsTOC3Marker -> TOC3Marker(marker)
            is JsHMarker -> HMarker(marker)
            is JsCMarker -> CMarker(marker)
            is JsVMarker -> VMarker(marker)
            is JsTextBlock -> TextBlock(marker)
            is JsFMarker -> FMarker(marker)
            is JsXMarker -> XMarker(marker)
            else -> throw IllegalArgumentException("Unknown marker type $marker")
        }
    }
}

actual class TOC3Marker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val bookAbbreviation
        get() = wrapper.toJs<JsTOC3Marker>().bookAbbreviation
}

actual class HMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val headerText
        get() = wrapper.toJs<JsHMarker>().headerText
}

actual class CMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val number
        get() = wrapper.toJs<JsCMarker>().number.toIntOrNull() ?: -1
}

actual class VMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val verseNumber
        get() = wrapper.toJs<JsVMarker>().number
    actual val startingVerse
        get() = wrapper.toJs<JsVMarker>().number.toIntOrNull() ?: -1
    actual val endingVerse
        get() = wrapper.toJs<JsVMarker>().number.toIntOrNull() ?: -1
}

actual class FMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val footNoteCaller
        get() = wrapper.toJs<JsFMarker>().footNoteCaller
}

actual class XMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val crossRefCaller
        get() = wrapper.toJs<JsXMarker>().crossRefCaller
}

actual class TextBlock(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val text
        get() = wrapper.toJs<JsTextBlock>().text
}

private fun IMarker.toPlatform(): JsMarker {
    return (this as MarkerWrapper).wrapper.toJs()
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
private fun <T: JsAny> Any.toJs(): T {
    return (this as JsAny).unsafeCast()
}
