package com.myapplication.common.data

import com.russhwolf.settings.Settings

class SettingsRepository {
    private val settings: Settings = Settings()

    fun getApiKey(): String? {
        return settings.getStringOrNull("gemini_api_key")
    }

    fun saveApiKey(apiKey: String) {
        settings.putString("gemini_api_key", apiKey)
    }

    fun getEnglishVoice(): String {
        return settings.getString("english_tts_voice", "en_US-lessac-medium")
    }

    fun saveEnglishVoice(voice: String) {
        settings.putString("english_tts_voice", voice)
    }

    fun getSpanishVoice(): String {
        return settings.getString("spanish_tts_voice", "es_MX-ald-medium")
    }

    fun saveSpanishVoice(voice: String) {
        settings.putString("spanish_tts_voice", voice)
    }
}

