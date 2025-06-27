package org.bibletranslationtools.wat.platform.markers

expect class VMarker : MarkerWrapper {
    val verseNumber: String
    val startingVerse: Int
    val endingVerse: Int
}