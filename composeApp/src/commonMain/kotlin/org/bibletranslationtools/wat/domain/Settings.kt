package org.bibletranslationtools.wat.domain

import org.bibletranslationtools.wat.domain.GeminiModel.entries
import org.bibletranslationtools.wat.domain.OpenAiModel.entries


enum class Settings {
    THEME,
    LOCALE,
    AI_API,
    AI_MODEL,
    GEMINI_MODEL,
    OPENAI_MODEL,
    AI_API_KEY,
    GEMINI_API_KEY,
    OPENAI_API_KEY,
    APOSTROPHE_IS_SEPARATOR
}

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class AiApi {
    GEMINI,
    OPENAI
}

interface AiModel {
    val value: String
    val name: String
}

enum class GeminiModel(override val value: String): AiModel {
    FLASH_2("gemini-2.0-flash"),
    FLASH_2_LITE("gemini-2.0-flash-lite"),
    FLASH_1_5("gemini-1.5-flash"),
    FLASH_1_5_8B("gemini-1.5-flash-8b"),
    FLASH_1_5_PRO("gemini-1.5-pro");

    companion object {
        fun getOrDefault(name: String): GeminiModel {
            return entries.singleOrNull { it.name == name } ?: FLASH_2
        }
    }
}

enum class OpenAiModel(override val value: String): AiModel {
    GPT_3_5_TURBO("gpt-3.5-turbo"),
    GPT_4_TURBO("gpt-4-turbo"),
    GPT_4("gpt-4");

    companion object {
        fun getOrDefault(name: String): OpenAiModel {
            return entries.singleOrNull { it.name == name } ?: GPT_3_5_TURBO
        }
    }
}

enum class Locales(val value: String) {
    EN("English"),
    RU("Русский")
}