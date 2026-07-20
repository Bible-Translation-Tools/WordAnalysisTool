package org.bibletranslationtools.wat.platform.markers

typealias JavaVMarker = org.wycliffeassociates.usfmtools.models.markers.VMarker

actual class VMarker(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual val verseNumber = (wrapper as JavaVMarker).verseNumber
    actual val startingVerse = (wrapper as JavaVMarker).startingVerse
    actual val endingVerse = (wrapper as JavaVMarker).endingVerse
}