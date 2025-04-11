package org.bibletranslationtools.wat.platform.markers

typealias JavaPMarker = org.wycliffeassociates.usfmtools.models.markers.PMarker

actual class PMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper)