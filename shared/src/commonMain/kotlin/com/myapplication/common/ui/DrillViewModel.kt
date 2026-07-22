package com.myapplication.common.ui

import com.myapplication.common.ai.GeminiService
import com.myapplication.common.audio.AudioController
import com.myapplication.common.data.ReviewState
import com.myapplication.common.data.TagFilterSpec
import com.myapplication.common.data.VocabCard
import com.myapplication.common.data.VocabRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ProgressionMode {
    LINEAR,
    RANDOM
}

enum class DrillMode {
    AI_SPEAKS_USER_TYPES,
    AI_SPEAKS_USER_SPEAKS,
    AI_WRITES_USER_SPEAKS,
    AI_WRITES_USER_WRITES,
    CONTEXT_SENTENCE,
    MINI_DIALOGUE
}

data class DrillConfig(
    val appAction: String = "writes",
    val appLanguage: String = "English",
    val userAction: String = "writes",
    val userLanguage: String = "Spanish",
    val progressionMode: ProgressionMode = ProgressionMode.RANDOM
) {
    val isAppSpeaking get() = appAction.equals("speaks", ignoreCase = true)
    val isAppWriting get() = appAction.equals("writes", ignoreCase = true)
    val isAppEnglish get() = appLanguage.equals("English", ignoreCase = true)
    val isAppSpanish get() = appLanguage.equals("Spanish", ignoreCase = true)

    val isUserSpeaking get() = userAction.equals("speaks", ignoreCase = true)
    val isUserWriting get() = userAction.equals("writes", ignoreCase = true)
    val isUserSpanish get() = userLanguage.equals("Spanish", ignoreCase = true)
    val isUserEnglish get() = userLanguage.equals("English", ignoreCase = true)

    fun toLegacyMode(): DrillMode {
        return when {
            isAppSpeaking && isUserWriting -> DrillMode.AI_SPEAKS_USER_TYPES
            isAppSpeaking && isUserSpeaking -> DrillMode.AI_SPEAKS_USER_SPEAKS
            isAppWriting && isUserSpeaking -> DrillMode.AI_WRITES_USER_SPEAKS
            else -> DrillMode.AI_WRITES_USER_WRITES
        }
    }
}

sealed class DrillState {
    object Loading : DrillState()
    data class Active(
        val card: VocabCard,
        val mode: DrillMode,
        val config: DrillConfig = DrillConfig(),
        val userInput: String = "",
        val aiGeneratedText: String? = null,
        val isListening: Boolean = false,
        val isRevealed: Boolean = false,
        val isCorrect: Boolean = false
    ) : DrillState()
    object Finished : DrillState()
}

class DrillViewModel(
    private val repository: VocabRepository,
    private val audioController: AudioController,
    private val geminiService: GeminiService
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _uiState = MutableStateFlow<DrillState>(DrillState.Loading)
    val uiState: StateFlow<DrillState> = _uiState

    private var currentReviewState: ReviewState? = null
    private var activeTagFilter: String? = null
    private var activeFilterSpec: TagFilterSpec = TagFilterSpec()
    private var activeConfig: DrillConfig = DrillConfig()

    fun setProgressionMode(mode: ProgressionMode) {
        activeConfig = activeConfig.copy(progressionMode = mode)
        val state = _uiState.value
        if (state is DrillState.Active) {
            _uiState.value = state.copy(config = activeConfig)
        }
    }

    fun startSession(filterSpec: TagFilterSpec, config: DrillConfig = DrillConfig()) {
        activeFilterSpec = filterSpec
        activeTagFilter = null
        activeConfig = config
        fetchNextCard()
    }

    fun startSession(tagFilter: String?, config: DrillConfig) {
        val spec = if (tagFilter.isNullOrBlank()) TagFilterSpec() else TagFilterSpec(chapters = setOf(tagFilter.trim()))
        startSession(spec, config)
    }

    fun startSession(tagFilter: String? = null, mode: DrillMode) {
        val config = when (mode) {
            DrillMode.AI_SPEAKS_USER_TYPES -> DrillConfig("speaks", "Spanish", "writes", "Spanish")
            DrillMode.AI_SPEAKS_USER_SPEAKS -> DrillConfig("speaks", "Spanish", "speaks", "Spanish")
            DrillMode.AI_WRITES_USER_SPEAKS -> DrillConfig("writes", "English", "speaks", "Spanish")
            else -> DrillConfig("writes", "English", "writes", "Spanish")
        }
        startSession(tagFilter, config)
    }

    private fun fetchNextCard() {
        _uiState.value = DrillState.Loading
        scope.launch {
            val (card, reviewState) = repository.getNextCard(activeFilterSpec, activeConfig.progressionMode)
            if (card != null) {
                currentReviewState = reviewState
                _uiState.value = DrillState.Active(
                    card = card,
                    mode = activeConfig.toLegacyMode(),
                    config = activeConfig
                )
                
                if (activeConfig.isAppSpeaking) {
                    val promptText = if (activeConfig.isAppEnglish) card.english else card.spanish
                    val promptLang = if (activeConfig.isAppEnglish) "en" else "es"
                    audioController.speak(promptText, promptLang)
                }
            } else {
                _uiState.value = DrillState.Finished
            }
        }
    }

    fun replayAudio(text: String, lang: String = "es") {
        audioController.speak(text, lang)
    }

    fun onUserInputChanged(input: String) {
        val state = _uiState.value
        if (state is DrillState.Active) {
            _uiState.value = state.copy(userInput = input)
        }
    }

    fun toggleListening() {
        val state = _uiState.value
        if (state is DrillState.Active) {
            if (state.isListening) {
                audioController.stopListening()
                _uiState.value = state.copy(isListening = false)
            } else {
                _uiState.value = state.copy(isListening = true)
                audioController.startListening(
                    onResult = { result ->
                        _uiState.value = (_uiState.value as DrillState.Active).copy(
                            userInput = result,
                            isListening = false
                        )
                        checkAnswer() // Auto-check on final result
                    },
                    onPartial = { partial ->
                        _uiState.value = (_uiState.value as DrillState.Active).copy(userInput = partial)
                    }
                )
            }
        }
    }

    fun checkAnswer() {
        val state = _uiState.value
        if (state is DrillState.Active && !state.isRevealed) {
            val normalizedInput = state.userInput.trim().lowercase()
            val targetAnswer = if (state.config.isUserSpanish) state.card.spanish else state.card.english
            val normalizedTarget = targetAnswer.trim().lowercase()
            
            val isCorrect = normalizedInput == normalizedTarget
            
            _uiState.value = state.copy(isRevealed = true, isCorrect = isCorrect)
            
            if (isCorrect) {
                audioController.speak("¡Correcto!", "es")
            } else {
                val feedback = if (state.config.isUserSpanish) "La respuesta correcta es $targetAnswer" else "The correct answer is $targetAnswer"
                val feedbackLang = if (state.config.isUserSpanish) "es" else "en"
                audioController.speak(feedback, feedbackLang)
            }
        }
    }

    fun submitGradeAndNext(grade: Int) {
        val state = _uiState.value
        if (state is DrillState.Active) {
            scope.launch {
                repository.submitReview(state.card.id, grade, currentReviewState)
                fetchNextCard()
            }
        }
    }

    fun generateContextSentence() {
        val state = _uiState.value
        if (state is DrillState.Active) {
            scope.launch {
                val sentence = geminiService.generateContextSentence(state.card.spanish)
                _uiState.value = state.copy(aiGeneratedText = sentence)
                audioController.speak(sentence, "es")
            }
        }
    }
}
