package org.bibletranslationtools.wat.data

data class Word(
    val count: Int,
    val refs: List<Verse>
)
