package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsVMarker

actual class VMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val verseNumber
        get() = wrapper.toJs<JsVMarker>().verseNumber
    actual val startingVerse
        get() = wrapper.toJs<JsVMarker>().startingVerse
    actual val endingVerse
        get() = wrapper.toJs<JsVMarker>().endingVerse
}