package org.bibletranslationtools.wat.data

data class ReviewWord(
    val word: String,
    val ref: Verse,
    val correct: Boolean? = null
)
