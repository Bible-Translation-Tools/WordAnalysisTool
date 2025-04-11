package org.bibletranslationtools.wat

import kotlinx.datetime.LocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.writeString
import kotlin.collections.iterator

fun String.asSource(): Source {
    val buffer = Buffer()
    buffer.writeString(this)
    return buffer
}

fun String.formatWith(values: Map<String, String>): String {
    var result = this
    for ((key, value) in values) {
        result = result.replace("{$key}", value)
    }
    return result
}

fun LocalDateTime.format(): String {
    val year = year.toString().padStart(4, '0')
    val month = monthNumber.toString().padStart(2, '0')
    val day = dayOfMonth.toString().padStart(2, '0')
    val hour = hour.toString().padStart(2, '0')
    val minute = minute.toString().padStart(2, '0')
    val second = second.toString().padStart(2, '0')

    return "$year-$month-${day} $hour:$minute:$second"
}