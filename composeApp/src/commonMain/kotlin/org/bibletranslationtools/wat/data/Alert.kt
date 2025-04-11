package org.bibletranslationtools.wat.data

data class Alert(
    val message: String,
    val onClosed: () -> Unit = {}
)