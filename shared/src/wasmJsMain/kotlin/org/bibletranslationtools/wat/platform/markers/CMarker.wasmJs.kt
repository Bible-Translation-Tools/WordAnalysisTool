package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsCMarker


actual class CMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val number
        get() = wrapper.toJs<JsCMarker>().number
}