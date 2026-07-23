package org.bibletranslationtools.wat.domain

import androidx.compose.runtime.MutableState

enum class Settings {
    THEME,
    LOCALE,
    ACCESS_TOKEN
}

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class Model(val value: String) {
    GPT_5_4_MINI("gpt-5.4-mini"),
    GPT_5_5("gpt-5.5"),
    GPT_5_6_TERRA("gpt-5.6-terra"),
    CLAUDE_4_5_HAIKU("claude-haiku-4-5"),
    CLAUDE_5_SONNET("claude-sonnet-5"),
    MISTRAL_MEDIUM_3_5("mistral-medium-2604"),
    MISTRAL_LARGE_3("mistral-large-2512"),
    GEMINI_FLASH_3_5_LITE("gemini-3.5-flash-lite"),
    GEMINI_FLASH_3_6("gemini-3.6-flash")
}

enum class Locales(val value: String) {
    EN("English"),
    RU("Русский")
}

data class ModelStatus(
    val model: String,
    val active: MutableState<Boolean>
)

