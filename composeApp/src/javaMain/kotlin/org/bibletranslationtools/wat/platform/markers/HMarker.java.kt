package org.bibletranslationtools.wat.platform.markers

typealias JavaHMarker = org.wycliffeassociates.usfmtools.models.markers.HMarker

actual class HMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val headerText = (wrapper as JavaHMarker).headerText
}