package org.bibletranslationtools.wat.data

fun <K : Comparable<K>, V> MutableMap<K, V>.sortedByKey(): Map<K, V> {
    return entries.sortedBy { it.key }.associate { it.toPair() }
}

fun <K, V> MutableMap<K, V>.sortedByKeyWith(comparator: Comparator<K>): Map<K, V> {
    return entries.sortedWith(compareBy(comparator) { it.key }).associate { it.toPair() }
}