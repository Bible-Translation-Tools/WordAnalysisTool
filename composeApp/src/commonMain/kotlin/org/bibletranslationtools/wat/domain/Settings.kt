package org.bibletranslationtools.wat.domain

import org.bibletranslationtools.wat.domain.GeminiModel.entries
import org.bibletranslationtools.wat.domain.OpenAiModel.entries


enum class Settings {
    THEME,
    LOCALE,
    GEMINI_MODEL,
    GEMINI_API_KEY,
    GEMINI_ACTIVE,
    OPENAI_MODEL,
    OPENAI_API_KEY,
    OPENAI_ACTIVE,
    QWEN_MODEL,
    QWEN_API_KEY,
    QWEN_ACTIVE,
    CLAUDEAI_MODEL,
    CLAUDEAI_API_KEY,
    CLAUDEAI_ACTIVE,
    APOSTROPHE_IS_SEPARATOR
}

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class AiApi {
    GEMINI,
    OPENAI,
    QWEN,
    CLAUDE_AI
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

enum class QwenModel(override val value: String): AiModel {
    QWEN_PLUS("qwen-plus"),
    QWEN_TURBO("qwen-turbo"),
    QWEN_MAX("qwen-max"),
    QWEN2_5_14B_INSTRUCT("qwen2.5-14b-instruct"),
    QWEN2_5_7B_INSTRUCT("qwen2.5-7b-instruct");

    companion object {
        fun getOrDefault(name: String): QwenModel {
            return entries.singleOrNull { it.name == name } ?: QWEN_PLUS
        }
    }
}

enum class ClaudeAiModel(override val value: String): AiModel {
    CLAUDE_3_7_SONNET("claude-3-7-sonnet-20250219"),
    CLAUDE_3_5_SONNET("claude-3-5-sonnet-20241022"),
    CLAUDE_3_5_HAIKU("claude-3-5-haiku-20241022");

    companion object {
        fun getOrDefault(name: String): ClaudeAiModel {
            return entries.singleOrNull { it.name == name } ?: CLAUDE_3_7_SONNET
        }
    }
}

enum class Locales(val value: String) {
    EN("English"),
    RU("Русский")
}