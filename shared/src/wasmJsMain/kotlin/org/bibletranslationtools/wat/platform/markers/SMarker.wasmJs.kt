package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsSMarker

actual class SMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val weight
        get() = wrapper.toJs<JsSMarker>().weight
    actual val text
        get() = wrapper.toJs<JsSMarker>().text
}