package org.bibletranslationtools.wat.domain

import androidx.compose.runtime.MutableState

enum class Settings {
    THEME,
    LOCALE,
    APOSTROPHE_IS_SEPARATOR,
    ACCESS_TOKEN
}

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class Model(val value: String) {
    GPT_5_4_MINI("gpt-5.4-mini"),
    GPT_5_4_NANO("gpt-5.4-nano"),
    CLAUDE_4_5_HAIKU("claude-haiku-4-5"),
    CLAUDE_4_6_SONNET("claude-sonnet-4-6"),
    MISTRAL_SMALL("mistral-small-latest"),
    MISTRAL_MEDIUM("mistral-medium-latest"),
    MINISTRAL_LARGE("mistral-large-latest"),
    MINISTRAL_8B("ministral-8b-latest"),
    MINISTRAL_14B("ministral-14b-latest")
}

enum class Locales(val value: String) {
    EN("English"),
    RU("Русский")
}

data class ModelStatus(
    val model: String,
    val active: MutableState<Boolean>
)

const val MODELS_SIZE = 3