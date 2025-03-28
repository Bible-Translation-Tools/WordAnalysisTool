package org.bibletranslationtools.wat.data

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.writeString

fun <K : Comparable<K>, V> Map<K, V>.sortedByKey(): Map<K, V> {
    return entries.sortedBy { it.key }.associate { it.toPair() }
}

fun <K, V> Map<K, V>.sortedByKeyWith(comparator: Comparator<K>): Map<K, V> {
    return entries.sortedWith(compareBy(comparator) { it.key }).associate { it.toPair() }
}

fun <K, V> Map<K, V>.sortedByValueWith(comparator: Comparator<V>): Map<K, V> {
    return entries.sortedWith(compareBy(comparator) { it.value }).associate { it.toPair() }
}

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