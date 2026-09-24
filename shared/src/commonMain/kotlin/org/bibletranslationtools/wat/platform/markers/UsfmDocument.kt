package org.bibletranslationtools.wat.platform.markers

expect class UsfmDocument : MarkerWrapper {
    override fun getIdentifier(): String
    override fun getAllowedContents(): List<Any>

    fun insertMarker(marker: IMarker)
    fun insertDocument(document: UsfmDocument)
    fun insertMultiple(iterable: Iterable<IMarker>)
}