package com.myapplication.common.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class AndroidAudioController(private val context: Context) : AudioController, TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false
    private var pendingSpeakRequest: Pair<String, String>? = null
    
    private var onResultCallback: ((String) -> Unit)? = null
    private var onPartialCallback: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
        setupSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            pendingSpeakRequest?.let { (text, lang) ->
                speak(text, lang)
                pendingSpeakRequest = null
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    onResultCallback?.invoke("") // Or handle error
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResultCallback?.invoke(matches[0])
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onPartialCallback?.invoke(matches[0])
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    override fun speak(text: String, lang: String) {
        if (!isTtsReady) {
            pendingSpeakRequest = Pair(text, lang)
            return
        }
        val locale = if (lang.equals("en", ignoreCase = true)) Locale.US else Locale("es", "ES")
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallbackLocale = if (lang.equals("en", ignoreCase = true)) Locale.ENGLISH else Locale("es")
            tts?.setLanguage(fallbackLocale)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun startListening(lang: String, onResult: (String) -> Unit, onPartial: (String) -> Unit) {
        this.onResultCallback = onResult
        this.onPartialCallback = onPartial

        val langTag = if (lang.equals("en", ignoreCase = true)) "en-US" else "es-ES"

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    override fun stopListening() {
        speechRecognizer?.stopListening()
        onResultCallback = null
        onPartialCallback = null
    }

    fun cleanup() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
