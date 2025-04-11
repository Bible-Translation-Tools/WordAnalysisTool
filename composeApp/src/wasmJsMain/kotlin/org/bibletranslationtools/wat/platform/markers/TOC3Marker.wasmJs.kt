package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsTOC3Marker

actual class TOC3Marker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val bookAbbreviation
        get() = wrapper.toJs<JsTOC3Marker>().bookAbbreviation
}