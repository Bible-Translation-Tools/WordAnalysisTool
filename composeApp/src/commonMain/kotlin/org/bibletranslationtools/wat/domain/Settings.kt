package org.bibletranslationtools.wat.domain

enum class Settings {
    THEME,
    LOCALE,
    MODEL_1,
    MODEL_2,
    MODEL_3,
    MODEL_4,
    APOSTROPHE_IS_SEPARATOR
}

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class Model(val value: String) {
    LLAMA_3_1_8B("@cf/meta/llama-3.1-8b-instruct-fast"),
    META_LLAMA_3_8B_INSTRUCT("@hf/meta-llama/meta-llama-3-8b-instruct"),
    OPEN_HERMES_2_5_MISTRAL_7B("@hf/thebloke/openhermes-2.5-mistral-7b-awq"),
    NEURAL_CHAT_7B_V3_1("@hf/thebloke/neural-chat-7b-v3-1-awq"),
    QWEN_1_5_14B_CHAT("@cf/qwen/qwen1.5-14b-chat-awq");

    companion object {
        fun ofValue(value: String): Model? {
            return entries.firstOrNull { it.value == value }
        }
    }
}

enum class Locales(val value: String) {
    EN("English"),
    RU("Русский")
}