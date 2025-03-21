package org.bibletranslationtools.wat.data

enum class Consensus {
    MISSPELLING,
    PROPER_NAME,
    SOMETHING_ELSE,
    UNDEFINED
}

data class SingletonWord(
    val count: Int,
    val ref: Verse,
    var consensus: Consensus? = null
)
