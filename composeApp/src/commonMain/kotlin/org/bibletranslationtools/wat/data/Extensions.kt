package org.bibletranslationtools.wat.data

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.writeString

fun <K : Comparable<K>, V> MutableMap<K, V>.sortedByKey(): Map<K, V> {
    return entries.sortedBy { it.key }.associate { it.toPair() }
}

fun <K, V> MutableMap<K, V>.sortedByKeyWith(comparator: Comparator<K>): Map<K, V> {
    return entries.sortedWith(compareBy(comparator) { it.key }).associate { it.toPair() }
}

fun String.asSource(): Source {
    val buffer = Buffer()
    buffer.writeString(this)
    return buffer
}