package org.bibletranslationtools.wat.platform.markers

typealias JavaFMarker = org.wycliffeassociates.usfmtools.models.markers.FMarker

actual class FMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val footNoteCaller = (wrapper as JavaFMarker).footNoteCaller
}