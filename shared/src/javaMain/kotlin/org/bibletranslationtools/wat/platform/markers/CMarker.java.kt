package org.bibletranslationtools.wat.platform.markers

typealias JavaCMarker = org.wycliffeassociates.usfmtools.models.markers.CMarker

actual class CMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val number = (wrapper as JavaCMarker).number
}