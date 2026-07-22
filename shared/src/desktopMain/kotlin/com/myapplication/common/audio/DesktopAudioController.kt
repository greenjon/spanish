package com.myapplication.common.audio

import kotlinx.coroutines.*
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DesktopAudioController : AudioController {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null
    private var microphone: TargetDataLine? = null

    override fun speak(text: String) {
        scope.launch {
            try {
                // Using espeak for Linux desktop TTS
                val process = ProcessBuilder("espeak", "-v", "es", text).start()
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun startListening(onResult: (String) -> Unit, onPartial: (String) -> Unit) {
        if (listeningJob?.isActive == true) return

        listeningJob = scope.launch {
            try {
                val modelPath = "vosk-model-es"
                if (!File(modelPath).exists()) {
                    println("Vosk model not found at $modelPath. Please download 'vosk-model-small-es-0.42' and extract to 'vosk-model-es'.")
                    return@launch
                }

                LibVosk.setLogLevel(LogLevel.WARNING)

                val sampleRate = 16000.0f
                val format = AudioFormat(sampleRate, 16, 1, true, false)
                val info = DataLine.Info(TargetDataLine::class.java, format)

                if (!AudioSystem.isLineSupported(info)) {
                    println("Microphone audio format not supported by the system.")
                    return@launch
                }

                microphone = AudioSystem.getLine(info) as TargetDataLine
                microphone?.open(format)
                microphone?.start()

                Model(modelPath).use { model ->
                    Recognizer(model, sampleRate).use { recognizer ->
                        val buffer = ByteArray(4096)
                        while (isActive) {
                            val bytesRead = microphone?.read(buffer, 0, buffer.size) ?: 0
                            if (bytesRead > 0) {
                                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                                    val resultJson = recognizer.result
                                    val text = parseVoskJson(resultJson, "text")
                                    if (text.isNotBlank()) {
                                        withContext(Dispatchers.Main) { onResult(text) }
                                    }
                                } else {
                                    val partialJson = recognizer.partialResult
                                    val partial = parseVoskJson(partialJson, "partial")
                                    if (partial.isNotBlank()) {
                                        withContext(Dispatchers.Main) { onPartial(partial) }
                                    }
                                }
                            }
                        }
                        
                        // Final result when stopped
                        val finalResultJson = recognizer.finalResult
                        val finalText = parseVoskJson(finalResultJson, "text")
                        if (finalText.isNotBlank()) {
                            withContext(Dispatchers.Main) { onResult(finalText) }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cleanupMicrophone()
            }
        }
    }

    override fun stopListening() {
        listeningJob?.cancel()
        cleanupMicrophone()
    }

    private fun cleanupMicrophone() {
        microphone?.stop()
        microphone?.close()
        microphone = null
    }

    private fun parseVoskJson(jsonString: String, key: String): String {
        return try {
            val json = Json.parseToJsonElement(jsonString).jsonObject
            json[key]?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
