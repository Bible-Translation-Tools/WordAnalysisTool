package org.bibletranslationtools.wat

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform