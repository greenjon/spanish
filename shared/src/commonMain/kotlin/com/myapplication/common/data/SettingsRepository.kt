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

    // Drill settings persistence
    fun getAppAction(): String = settings.getString("drill_app_action", "writes")
    fun saveAppAction(action: String) = settings.putString("drill_app_action", action)

    fun getAppLanguage(): String = settings.getString("drill_app_lang", "English")
    fun saveAppLanguage(lang: String) = settings.putString("drill_app_lang", lang)

    fun getUserAction(): String = settings.getString("drill_user_action", "writes")
    fun saveUserAction(action: String) = settings.putString("drill_user_action", action)

    fun getUserLanguage(): String = settings.getString("drill_user_lang", "Spanish")
    fun saveUserLanguage(lang: String) = settings.putString("drill_user_lang", lang)

    fun getProgressionMode(): String = settings.getString("drill_progression_mode", "RANDOM")
    fun saveProgressionMode(modeName: String) = settings.putString("drill_progression_mode", modeName)

    // Window dimensions persistence
    fun getWindowSize(): Pair<Int, Int> {
        val width = settings.getInt("window_width", 1000)
        val height = settings.getInt("window_height", 800)
        return Pair(width, height)
    }

    fun saveWindowSize(width: Int, height: Int) {
        settings.putInt("window_width", width)
        settings.putInt("window_height", height)
    }
}

