package org.bibletranslationtools.wat.platform.markers

expect class SMarker : MarkerWrapper {
    val weight: Int
    val text: String
}