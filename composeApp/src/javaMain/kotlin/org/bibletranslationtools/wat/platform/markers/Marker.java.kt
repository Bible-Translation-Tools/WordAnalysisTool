package org.bibletranslationtools.wat.platform.markers

import kotlin.reflect.KClass

typealias JavaMarker = org.wycliffeassociates.usfmtools.models.markers.Marker

private const val PLATFORM_PACKAGE = "org.wycliffeassociates.usfmtools.models.markers"

actual open class MarkerWrapper(
    actual open val wrapper: Any
) : IMarker {
    actual override val contents: List<IMarker>
        get() = (wrapper as JavaMarker).contents.map { MarkerFactory.create(it) }

    actual override fun getIdentifier(): String {
        return (wrapper as JavaMarker).identifier
    }

    actual override fun getPosition(): Int {
        return (wrapper as JavaMarker).position
    }

    actual override fun setPosition(value: Int) {
        (wrapper as JavaMarker).position = value
    }

    actual override fun getAllowedContents(): List<Any> {
        return (wrapper as JavaMarker).allowedContents
    }

    actual override fun preProcess(input: String): String {
        return (wrapper as JavaMarker).preProcess(input)
    }

    actual override fun tryInsert(input: IMarker): Boolean {
        return (wrapper as JavaMarker).tryInsert(input.toPlatform())
    }

    actual override fun getHierarchyToMarker(target: IMarker): List<IMarker> {
        return (wrapper as JavaMarker).getHierarchyToMarker(target.toPlatform())
            .map { MarkerFactory.create(it) }
    }

    actual override fun <T : IMarker> getChildMarkers(clazz: KClass<T>): List<T> {
        return getPlatformMarkerClass(clazz)?.let { outClass ->
            val platformMarkers = (wrapper as JavaMarker).getChildMarkers(outClass)
            platformMarkers.map { MarkerFactory.create(it) as T }
        } ?: emptyList()
    }

    actual override fun <T : IMarker, P : IMarker> getChildMarkers(
        clazz: KClass<T>,
        ignoredParents: List<KClass<out P>>
    ): List<T> {
        return getPlatformMarkerClass(clazz)?.let { outClass ->
            val ignored = ignoredParents.mapNotNull {
                getPlatformMarkerClass(it)
            }
            val platformMarkers = (wrapper as JavaMarker).getChildMarkers(outClass, ignored)
            platformMarkers.mapNotNull { MarkerFactory.create(it) as? T }
        } ?: emptyList()
    }

    actual override fun getLastDescendant(): IMarker {
        return MarkerFactory.create((wrapper as JavaMarker).lastDescendent)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T: IMarker> getPlatformMarkerClass(clazz: KClass<T>): Class<JavaMarker>? {
        return try {
            Class.forName("$PLATFORM_PACKAGE.${clazz.simpleName}") as Class<JavaMarker>
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
            is JavaTOC3Marker -> TOC3Marker(marker)
            is JavaHMarker -> HMarker(marker)
            is JavaCMarker -> CMarker(marker)
            is JavaVMarker -> VMarker(marker)
            is JavaPMarker -> PMarker(marker)
            is JavaSMarker -> SMarker(marker)
            is JavaFMarker -> FMarker(marker)
            is JavaXMarker -> XMarker(marker)
            is JavaWMarker -> WMarker(marker)
            is JavaTextBlock -> TextBlock(marker)
            else -> throw IllegalArgumentException("Unknown marker type")
        }
    }
}

internal fun IMarker.toPlatform(): JavaMarker {
    return (this as MarkerWrapper).wrapper as JavaMarker
}