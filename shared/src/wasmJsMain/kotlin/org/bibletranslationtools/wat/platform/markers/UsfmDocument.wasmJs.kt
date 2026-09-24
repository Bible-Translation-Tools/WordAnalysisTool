package org.bibletranslationtools.wat.platform.markers

import org.bibletranslationtools.wat.platform.JsUsfmDocument

actual class UsfmDocument(
    override val wrapper: Any
) : MarkerWrapper(wrapper) {
    actual override fun getIdentifier(): String {
        return wrapper.toJs<JsUsfmDocument>().getIdentifier()
    }

    actual override fun getAllowedContents(): List<Any> {
        return wrapper.toJs<JsUsfmDocument>().getAllowedContents().toList()
    }

    actual fun insertMarker(marker: IMarker) {
        wrapper.toJs<JsUsfmDocument>().insert(marker.toPlatform())
    }

    actual fun insertDocument(document: UsfmDocument) {
        TODO("Not yet implemented")
    }

    actual fun insertMultiple(iterable: Iterable<IMarker>) {
        TODO("Not yet implemented")
    }
}