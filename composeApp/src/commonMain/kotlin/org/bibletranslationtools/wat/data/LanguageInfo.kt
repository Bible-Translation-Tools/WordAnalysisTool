package org.bibletranslationtools.wat.data

enum class Direction(val dir: String) {
    LTR("ltr"),
    RTL("rtl");

    companion object {
        fun of(dir: String): Direction {
            return entries.find { it.dir == dir } ?: LTR
        }
    }
}

data class LanguageInfo(
    val ietfCode: String,
    val name: String,
    val angName: String,
    val direction: Direction,
) {
    override fun toString(): String {
        val language = if (name != angName) {
            "$name ($angName)"
        } else name
        return "[$ietfCode] $language"
    }
}
