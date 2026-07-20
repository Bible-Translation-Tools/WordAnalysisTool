package org.bibletranslationtools.wat.platform.markers

typealias JavaTOC3Marker = org.wycliffeassociates.usfmtools.models.markers.TOC3Marker

actual class TOC3Marker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val bookAbbreviation = (wrapper as JavaTOC3Marker).bookAbbreviation
}