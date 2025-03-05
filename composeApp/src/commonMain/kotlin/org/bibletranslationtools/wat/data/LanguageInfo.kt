package org.bibletranslationtools.wat.data

data class LanguageInfo(
    val ietfCode: String,
    val name: String,
    val angName: String
) {
    override fun toString(): String {
        return "[$ietfCode] $name ($angName)"
    }
}
