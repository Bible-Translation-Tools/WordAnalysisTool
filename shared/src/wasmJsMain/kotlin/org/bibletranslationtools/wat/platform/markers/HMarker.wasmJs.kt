package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsHMarker

actual class HMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val headerText
        get() = wrapper.toJs<JsHMarker>().headerText
}