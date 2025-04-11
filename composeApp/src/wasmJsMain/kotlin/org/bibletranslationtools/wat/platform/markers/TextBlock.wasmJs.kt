package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsTextBlock

actual class TextBlock(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val text
        get() = wrapper.toJs<JsTextBlock>().text
}