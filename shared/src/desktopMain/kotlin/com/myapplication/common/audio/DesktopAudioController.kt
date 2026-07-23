package com.myapplication.common.audio

import kotlinx.coroutines.*
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import com.myapplication.common.data.SettingsRepository

class DesktopAudioController(
    private val settingsRepository: SettingsRepository? = null
) : AudioController {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null
    private var microphone: TargetDataLine? = null

    override fun speak(text: String, lang: String) {
        scope.launch {
            try {
                if (trySpeakPiper(text, lang)) return@launch

                // Fallback to macOS say if Piper is missing or fails on macOS
                val isEnglish = lang.startsWith("en", ignoreCase = true)
                val macOSVoice = if (isEnglish) "Alex" else "Monica"
                tryRunProcess("say", "-v", macOSVoice, text)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun trySpeakPiper(text: String, lang: String): Boolean {
        return try {
            val isEnglish = lang.startsWith("en", ignoreCase = true)
            val selectedModelName = if (isEnglish) {
                settingsRepository?.getEnglishVoice() ?: "en_US-lessac-medium"
            } else {
                settingsRepository?.getSpanishVoice() ?: "es_MX-ald-medium"
            }

            val piperExecutablePaths = listOf(
                "desktopApp/piper/bin/piper",
                "piper/bin/piper",
                "bin/piper",
                "piper"
            )
            val piperExe = piperExecutablePaths.firstOrNull { File(it).exists() } ?: "piper"

            val modelPaths = listOf(
                "desktopApp/piper/models/$selectedModelName.onnx",
                "piper/models/$selectedModelName.onnx",
                "models/$selectedModelName.onnx",
                "$selectedModelName.onnx"
            )
            val modelFile = modelPaths.firstOrNull { File(it).exists() } ?: run {
                println("Piper model file for $selectedModelName not found!")
                return false
            }

            val processBuilder = ProcessBuilder(piperExe, "--model", modelFile, "--output_file", "-")
                .redirectError(ProcessBuilder.Redirect.DISCARD)

            val process = processBuilder.start()

            // Write text to piper stdin
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(text)
                writer.newLine()
                writer.flush()
            }

            // Read WAV bytes from piper stdout
            val audioBytes = process.inputStream.readBytes()
            process.waitFor()

            if (audioBytes.isEmpty()) {
                println("Piper produced empty audio output.")
                return false
            }

            playWavData(audioBytes)
        } catch (e: Exception) {
            println("Piper TTS error: ${e.message}")
            false
        }
    }

    private suspend fun playWavData(audioBytes: ByteArray): Boolean {
        if (audioBytes.isEmpty()) return false

        val tempFile = try {
            File.createTempFile("piper_speech_", ".wav").apply {
                writeBytes(audioBytes)
                deleteOnExit()
            }
        } catch (e: Exception) {
            null
        }

        return try {
            if (tempFile != null && tempFile.exists()) {
                val audioPlayers = listOf(
                    arrayOf("paplay", tempFile.absolutePath),
                    arrayOf("pw-play", tempFile.absolutePath),
                    arrayOf("aplay", "-q", tempFile.absolutePath),
                    arrayOf("afplay", tempFile.absolutePath)
                )

                for (cmd in audioPlayers) {
                    if (tryRunProcess(*cmd)) {
                        tempFile.delete()
                        return true
                    }
                }
            }

            // Fallback: Java Sound Clip with CountDownLatch
            val byteArrayInputStream = java.io.ByteArrayInputStream(audioBytes)
            val audioInputStream = AudioSystem.getAudioInputStream(byteArrayInputStream)
            val clip = AudioSystem.getClip()
            val latch = java.util.concurrent.CountDownLatch(1)

            clip.addLineListener { event ->
                if (event.type == javax.sound.sampled.LineEvent.Type.STOP || event.type == javax.sound.sampled.LineEvent.Type.CLOSE) {
                    latch.countDown()
                }
            }

            clip.open(audioInputStream)
            clip.start()

            withContext(Dispatchers.IO) {
                latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
            }

            clip.close()
            audioInputStream.close()
            tempFile?.delete()
            true
        } catch (e: Exception) {
            println("Audio playback error: ${e.message}")
            tempFile?.delete()
            false
        }
    }

    private fun tryRunProcess(vararg command: String): Boolean {
        return try {
            val process = ProcessBuilder(*command).start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun startListening(lang: String, onResult: (String) -> Unit, onPartial: (String) -> Unit) {
        if (listeningJob?.isActive == true) return

        listeningJob = scope.launch {
            val audioStream = ByteArrayOutputStream()
            val sampleRate = 16000.0f
            val format = AudioFormat(sampleRate, 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                println("Microphone audio format not supported by the system.")
                return@launch
            }

            try {
                microphone = AudioSystem.getLine(info) as TargetDataLine
                microphone?.open(format)
                microphone?.start()

                withContext(Dispatchers.Main) { onPartial("Listening...") }

                val buffer = ByteArray(4096)
                while (isActive) {
                    val bytesRead = microphone?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        audioStream.write(buffer, 0, bytesRead)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    e.printStackTrace()
                }
            } finally {
                cleanupMicrophone()

                val pcmData = audioStream.toByteArray()
                if (pcmData.isNotEmpty()) {
                    val transcribedText = runFasterWhisper(pcmData, format, lang)
                    if (transcribedText.isNotBlank()) {
                        withContext(Dispatchers.Main) { onResult(transcribedText) }
                    } else {
                        withContext(Dispatchers.Main) { onPartial("") }
                    }
                }
            }
        }
    }

    private suspend fun runFasterWhisper(pcmData: ByteArray, format: AudioFormat, lang: String): String = withContext(Dispatchers.IO) {
        val tempWavFile = File.createTempFile("whisper_input_", ".wav")
        try {
            val bais = ByteArrayInputStream(pcmData)
            val audioInputStream = AudioInputStream(bais, format, pcmData.size.toLong() / format.frameSize)
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, tempWavFile)

            val scriptPaths = listOf(
                "desktopApp/whisper/transcribe.py",
                "whisper/transcribe.py",
                "transcribe.py"
            )
            val scriptFile = scriptPaths.firstOrNull { File(it).exists() } ?: "desktopApp/whisper/transcribe.py"

            val targetLang = if (lang.equals("en", ignoreCase = true)) "en" else "es"

            val pythonExecs = listOf(
                "desktopApp/whisper/venv/bin/python",
                "whisper/venv/bin/python",
                "venv/bin/python",
                "python3",
                "python"
            )
            var outputText = ""

            for (python in pythonExecs) {
                try {
                    val pb = ProcessBuilder(
                        python,
                        scriptFile,
                        "--audio", tempWavFile.absolutePath,
                        "--lang", targetLang,
                        "--model", "small",
                        "--compute_type", "int8"
                    )
                    val process = pb.start()
                    val result = process.inputStream.bufferedReader().readText().trim()
                    val err = process.errorStream.bufferedReader().readText().trim()
                    process.waitFor()

                    if (process.exitValue() == 0 && result.isNotBlank()) {
                        outputText = result
                        break
                    } else if (err.isNotBlank()) {
                        println("FasterWhisper error output: $err")
                    }
                } catch (e: Exception) {
                    println("Failed running python exec $python: ${e.message}")
                }
            }
            outputText
        } catch (e: Exception) {
            println("Error in runFasterWhisper: ${e.message}")
            ""
        } finally {
            if (tempWavFile.exists()) {
                tempWavFile.delete()
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
}

