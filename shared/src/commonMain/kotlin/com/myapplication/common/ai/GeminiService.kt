package com.myapplication.common.ai

import com.myapplication.common.data.SettingsRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Simple Ktor client for Gemini API
class GeminiService(private val settingsRepository: SettingsRepository) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    suspend fun generateContextSentence(vocabWord: String): String {
        val apiKey = settingsRepository.getApiKey()
        if (apiKey.isNullOrBlank()) {
            return "API Key not configured."
        }

        val prompt = "Generate a very simple, beginner-friendly Spanish 1A sentence using the word '$vocabWord'. Only return the Spanish sentence, nothing else."
        
        return try {
            val requestBody = GeminiRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                )
            )

            val response: GeminiResponse = client.post("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from Gemini."
        } catch (e: Exception) {
            e.printStackTrace()
            "Error contacting Gemini."
        }
    }

    suspend fun generateMiniDialogue(vocabWord: String): List<String> {
        val apiKey = settingsRepository.getApiKey()
        if (apiKey.isNullOrBlank()) {
            return listOf("API Key not configured.")
        }

        val prompt = "Generate a simple 3-turn present-tense Spanish roleplay dialogue using the word '$vocabWord'. Format as 3 distinct lines alternating between Person A and Person B. Only return the Spanish dialogue."
        
        return try {
            val requestBody = GeminiRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                )
            )

            val response: GeminiResponse = client.post("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            text.lines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf("Error contacting Gemini.")
        }
    }
}

@Serializable
data class GeminiRequest(
    val contents: List<Content>
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)
