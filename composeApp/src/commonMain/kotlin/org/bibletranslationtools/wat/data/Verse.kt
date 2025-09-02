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

fun String.toVerse(): Verse {
    val parts = this.split(":")
    return Verse(
        book = parts[0],
        chapter = parts[1].toInt(),
        verse = parts[2],
        text = "Verse text not found.")
}
