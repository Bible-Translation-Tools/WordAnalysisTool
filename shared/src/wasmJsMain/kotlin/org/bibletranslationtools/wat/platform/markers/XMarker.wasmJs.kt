package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsXMarker

actual class XMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val crossRefCaller
        get() = wrapper.toJs<JsXMarker>().crossRefCaller
}