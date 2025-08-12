package org.bibletranslationtools.wat.data

import kotlinx.serialization.Serializable

typealias VerseRef = Map<String, Verse>
typealias MutableVerseRef = MutableMap<String, Verse>

@Serializable
data class Verse(
    val book: String,
    val chapter: Int,
    val verse: String,
    val text: String
) {
    override fun toString(): String {
        return "$book:$chapter:$verse"
    }
}
