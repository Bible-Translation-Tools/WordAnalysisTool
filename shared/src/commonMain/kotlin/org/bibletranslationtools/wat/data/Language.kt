package org.bibletranslationtools.wat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Language(
    @SerialName("lc")
    val code: String,
    @SerialName("ln")
    val name: String,
    @SerialName("ang")
    val angName: String,
    @SerialName("ld")
    val direction: String,
    @SerialName("gw")
    val gateway: Boolean = false
) {
    override fun toString(): String {
        val language = if (name != angName) {
            "$name ($angName)"
        } else name
        return "[$code] $language"
    }
}
