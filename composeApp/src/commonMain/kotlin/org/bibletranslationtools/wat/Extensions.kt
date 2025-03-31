package org.bibletranslationtools.wat

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.writeString
import kotlin.collections.iterator

fun String.asSource(): Source {
    val buffer = Buffer()
    buffer.writeString(this)
    return buffer
}

fun String.formatWith(values: Map<String, String>): String {
    var result = this
    for ((key, value) in values) {
        result = result.replace("{$key}", value)
    }
    return result
}