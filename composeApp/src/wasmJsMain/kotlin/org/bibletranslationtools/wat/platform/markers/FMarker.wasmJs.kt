package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsFMarker


actual class FMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val footNoteCaller
        get() = wrapper.toJs<JsFMarker>().footNoteCaller
}