package org.bibletranslationtools.wat.platform.markers

expect class WMarker : MarkerWrapper {
    val term: String
    val attributes: Map<String, String>
}