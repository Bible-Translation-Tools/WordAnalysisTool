package org.bibletranslationtools.wat.domain

import androidx.compose.runtime.MutableState

enum class Settings {
    THEME,
    LOCALE,
    APOSTROPHE_IS_SEPARATOR
}

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class Model(val value: String) {
    GPT_4_O("gpt-4o"),
    CLAUDE_3_5_HAIKU_LATEST("claude-3-5-haiku-latest"),
    MINISTRAL_3B_LATEST("ministral-3b-latest"),
    QWEN_2_5_7B_INSTRUCT("qwen2.5-7b-instruct")
}

enum class Locales(val value: String) {
    EN("English"),
    RU("Русский")
}

data class ModelStatus(
    val model: String,
    val active: MutableState<Boolean>
)