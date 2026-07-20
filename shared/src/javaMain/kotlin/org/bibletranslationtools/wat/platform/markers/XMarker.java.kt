package org.bibletranslationtools.wat.platform.markers

typealias JavaXMarker = org.wycliffeassociates.usfmtools.models.markers.XMarker

actual class XMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val crossRefCaller = (wrapper as JavaXMarker).crossRefCaller
}