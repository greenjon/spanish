package com.myapplication.common.audio

interface AudioController {
    fun speak(text: String, lang: String = "es")
    fun startListening(onResult: (String) -> Unit, onPartial: (String) -> Unit)
    fun stopListening()
}
