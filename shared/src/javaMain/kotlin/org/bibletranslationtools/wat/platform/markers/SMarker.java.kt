package org.bibletranslationtools.wat.platform.markers

typealias JavaSMarker = org.wycliffeassociates.usfmtools.models.markers.SMarker

actual class SMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val weight = (wrapper as JavaSMarker).weight
    actual val text = (wrapper as JavaSMarker).text
}