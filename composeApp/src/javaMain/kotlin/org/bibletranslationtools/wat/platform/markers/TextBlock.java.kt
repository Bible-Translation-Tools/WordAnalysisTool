package org.bibletranslationtools.wat.platform.markers

typealias JavaTextBlock = org.wycliffeassociates.usfmtools.models.markers.TextBlock

actual class TextBlock(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val text = (wrapper as JavaTextBlock).text
}