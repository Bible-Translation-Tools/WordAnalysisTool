package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.consoleLog
import org.bibletranslationtools.wat.platform.JsCMarker
import org.bibletranslationtools.wat.platform.JsHMarkerClass
import org.bibletranslationtools.wat.platform.JsTOC3MarkerClass
import org.bibletranslationtools.wat.platform.JsCMarkerClass
import org.bibletranslationtools.wat.platform.JsFMarker
import org.bibletranslationtools.wat.platform.JsVMarkerClass
import org.bibletranslationtools.wat.platform.JsPMarkerClass
import org.bibletranslationtools.wat.platform.JsSMarkerClass
import org.bibletranslationtools.wat.platform.JsTextBlockClass
import org.bibletranslationtools.wat.platform.JsFMarkerClass
import org.bibletranslationtools.wat.platform.JsHMarker
import org.bibletranslationtools.wat.platform.JsXMarkerClass
import org.bibletranslationtools.wat.platform.JsMarker
import org.bibletranslationtools.wat.platform.JsPMarker
import org.bibletranslationtools.wat.platform.JsSMarker
import org.bibletranslationtools.wat.platform.JsTOC3Marker
import org.bibletranslationtools.wat.platform.JsTextBlock
import org.bibletranslationtools.wat.platform.JsUsfmDocument
import org.bibletranslationtools.wat.platform.JsVMarker
import org.bibletranslationtools.wat.platform.JsWMarker
import org.bibletranslationtools.wat.platform.JsWMarkerClass
import org.bibletranslationtools.wat.platform.JsXMarker
import kotlin.reflect.KClass

actual open class MarkerWrapper(
    actual open val wrapper: Any
) : IMarker {
    actual override val contents: List<IMarker>
        get() = wrapper.toJs<JsMarker>().contents.toList().map { MarkerFactory.create(it) }

    actual override fun getIdentifier(): String {
        return wrapper.toJs<JsMarker>().getIdentifier()
    }

    actual override fun getPosition(): Int {
        return wrapper.toJs<JsMarker>().position
    }

    actual override fun setPosition(value: Int) {
        wrapper.toJs<JsMarker>().position = value
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

    actual override fun getHierarchyToMarker(target: IMarker): List<IMarker> {
        return wrapper.toJs<JsMarker>().getHierarchyToMarker(target.toPlatform())
            .toList()
            .map { MarkerFactory.create(it) }
    }

    actual override fun <T : IMarker> getChildMarkers(clazz: KClass<T>): List<T> {
        return getPlatformMarkerClass(clazz)?.let { outClass ->
            val platformMarkers = wrapper.toJs<JsMarker>().getChildMarkers(outClass)
            platformMarkers.toList().map { MarkerFactory.create(it) as T }
        } ?: emptyList()
    }

    actual override fun <T : IMarker, P : IMarker> getChildMarkers(
        clazz: KClass<T>,
        ignoredParents: List<KClass<out P>>
    ): List<T> {
        return getPlatformMarkerClass(clazz)?.let { outClass ->
            val ignored = ignoredParents.mapNotNull {
                getPlatformMarkerClass(it)?.toJs()
            }.toJsArray()
            val platformMarkers = wrapper.toJs<JsMarker>().getChildMarkers(outClass, ignored)
            platformMarkers.toList().mapNotNull { MarkerFactory.create(it) as? T }
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
                PMarker::class -> JsPMarkerClass
                SMarker::class -> JsSMarkerClass
                TextBlock::class -> JsTextBlockClass
                WMarker::class -> JsWMarkerClass
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
            is JsPMarker -> PMarker(marker)
            is JsSMarker -> SMarker(marker)
            is JsWMarker -> WMarker(marker)
            else -> {
                consoleLog(marker::class.simpleName)
                throw IllegalArgumentException("Unknown marker type $marker")
            }
        }
    }
}

internal fun IMarker.toPlatform(): JsMarker {
    return (this as MarkerWrapper).wrapper.toJs()
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
internal fun <T: JsAny> Any.toJs(): T {
    return (this as JsAny).unsafeCast()
}