package org.bibletranslationtools.wat.data

data class Verse(
    val number: Int,
    val text: String,
    val bookSlug: String,
    val bookName: String,
    val chapter: Int
)
