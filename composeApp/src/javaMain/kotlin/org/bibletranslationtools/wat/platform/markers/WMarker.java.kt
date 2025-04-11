package org.bibletranslationtools.wat.platform.markers

import org.wycliffeassociates.usfmtools.models.markers.WMarker

typealias JavaWMarker = WMarker

actual class WMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val term = (wrapper as JavaWMarker).term
    actual val attributes: Map<String, String> = (wrapper as JavaWMarker).attributes
}