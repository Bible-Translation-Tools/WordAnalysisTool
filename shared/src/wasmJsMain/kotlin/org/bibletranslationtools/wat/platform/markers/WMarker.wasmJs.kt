package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsWMarker
import org.bibletranslationtools.wat.platform.toMap

actual class WMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val term
        get() = wrapper.toJs<JsWMarker>().term
    actual val attributes
        get() = wrapper.toJs<JsWMarker>().attributes.toMap().map {
            it.key.toString() to it.value.toString()
        }.toMap()
}