package org.bibletranslationtools.wat.domain

import androidx.compose.runtime.MutableState

enum class Settings {
    THEME,
    LOCALE,
    PROMPT,
    APOSTROPHE_IS_SEPARATOR,
    SORT_WORDS,
    ACCESS_TOKEN
}

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class Model(val value: String) {
    GPT_4_O("gpt-4o"),
    GPT_4_TURBO("gpt-4-turbo"),
    GPT_3_5_TURBO("gpt-3.5-turbo"),
    O_3_MINI("o3-mini"),
    O_1("o1"),
    O_1_MINI("o3-mini"),
    CLAUDE_3_7_SONNET_LATEST("claude-3-7-sonnet-latest"),
    CLAUDE_3_5_SONNET_LATEST("claude-3-5-sonnet-latest"),
    CLAUDE_3_5_HAIKU_LATEST("claude-3-5-haiku-latest"),
    CLAUDE_3_OPUS_LATEST("claude-3-opus-latest"),
    MINISTRAL_3B_LATEST("ministral-3b-latest"),
    CODESTRAL_LATEST("codestral-latest"),
    MINISTRAL_LARGE_LATEST("mistral-large-latest"),
    PIXTRAL_LARGE_LATEST("pixtral-large-latest"),
    MINISTRAL_8B_LATEST("ministral-8b-latest"),
    QWEN_2_5_7B_INSTRUCT("qwen2.5-7b-instruct"),
    QWEN_2_5_14B_INSTRUCT("qwen2.5-14b-instruct"),
    QWEN_MAX("qwen-max"),
    QWEN_PLUS("qwen-plus"),
    QWEN_TURBO("qwen-turbo"),
}

enum class Locales(val value: String) {
    EN("English"),
    RU("Русский")
}

data class ModelStatus(
    val model: String,
    val active: MutableState<Boolean>
)