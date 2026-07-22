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
}
