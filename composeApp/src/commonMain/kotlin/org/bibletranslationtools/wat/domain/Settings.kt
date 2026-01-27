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
    GPT_5("gpt-5"),
    GPT_5_MINI("gpt-5-mini"),
    GPT_5_NANO("gpt-5-nano"),
//    GPT_4_1("gpt-4.1"),
//    GPT_4_1_MINI("gpt-4.1-mini"),
//    GPT_4_1_NANO("gpt-4.1-nano"),
    GPT_4_O("gpt-4o"),
    GPT_4_O_MINI("gpt-4o-mini"),
//    GPT_4_TURBO("gpt-4-turbo"),
    CLAUDE_4_5_HAIKU("claude-haiku-4-5"),
    CLAUDE_4_5_OPUS("claude-opus-4-5"),
    CLAUDE_4_1_OPUS("claude-opus-4-1"),
    CLAUDE_4_5_SONNET("claude-sonnet-4-5"),
//    CLAUDE_3_7_SONNET_LATEST("claude-3-7-sonnet-latest"),
//    CLAUDE_3_5_SONNET_LATEST("claude-3-5-sonnet-latest"),
//    CLAUDE_3_5_HAIKU_LATEST("claude-3-5-haiku-latest"),
//    CLAUDE_3_OPUS_LATEST("claude-3-opus-latest"),
    MISTRAL_SMALL("mistral-small-latest"),
    MISTRAL_MEDIUM("mistral-medium-latest"),
    MINISTRAL_LARGE("mistral-large-latest"),
    MINISTRAL_8B("ministral-8b-latest"),
    MINISTRAL_14B("ministral-14b-latest"),
//    QWEN_2_5_7B_INSTRUCT("qwen2.5-7b-instruct"),
//    QWEN_2_5_14B_INSTRUCT("qwen2.5-14b-instruct"),
//    QWEN_MAX("qwen-max"),
//    QWEN_PLUS("qwen-plus"),
//    QWEN_TURBO("qwen-turbo"),
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