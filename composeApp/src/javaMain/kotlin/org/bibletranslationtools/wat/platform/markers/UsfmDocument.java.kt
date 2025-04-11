package org.bibletranslationtools.wat.platform.markers

typealias JavaUSFMDocument = org.wycliffeassociates.usfmtools.models.markers.USFMDocument

actual class UsfmDocument(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {

    actual override fun getIdentifier(): String {
        return (wrapper as JavaUSFMDocument).identifier
    }

    actual override fun getAllowedContents(): List<Any> {
        return (wrapper as JavaUSFMDocument).allowedContents
    }

    actual fun insertMarker(marker: IMarker) {
        (wrapper as JavaUSFMDocument).insert(marker.toPlatform())
    }

    actual fun insertDocument(document: UsfmDocument) {
        (wrapper as JavaUSFMDocument).insert(document.toPlatform())
    }

    actual fun insertMultiple(iterable: Iterable<IMarker>) {
        (wrapper as JavaUSFMDocument).insertMultiple(iterable.map { it.toPlatform() })
    }
}