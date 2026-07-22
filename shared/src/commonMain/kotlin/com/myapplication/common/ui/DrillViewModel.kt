package com.myapplication.common.ui

import com.myapplication.common.ai.GeminiService
import com.myapplication.common.audio.AudioController
import com.myapplication.common.data.ReviewState
import com.myapplication.common.data.VocabCard
import com.myapplication.common.data.VocabRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class DrillMode {
    AI_SPEAKS_USER_TYPES,
    AI_SPEAKS_USER_SPEAKS,
    AI_WRITES_USER_SPEAKS,
    AI_WRITES_USER_WRITES,
    CONTEXT_SENTENCE,
    MINI_DIALOGUE
}

sealed class DrillState {
    object Loading : DrillState()
    data class Active(
        val card: VocabCard,
        val mode: DrillMode,
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

    fun startSession(tagFilter: String? = null) {
        activeTagFilter = tagFilter
        fetchNextCard()
    }

    private fun fetchNextCard() {
        _uiState.value = DrillState.Loading
        scope.launch {
            val (card, reviewState) = repository.getNextCard(activeTagFilter)
            if (card != null) {
                currentReviewState = reviewState
                // Cycle through basic modes randomly, or we could let the user choose
                val modes = listOf(
                    DrillMode.AI_WRITES_USER_WRITES,
                    DrillMode.AI_SPEAKS_USER_WRITES,
                    DrillMode.AI_WRITES_USER_SPEAKS
                ) // Keeping it simple for demo
                
                // Randomly assign a mode for demonstration (Ideally selected by user or spaced repetition)
                val assignedMode = DrillMode.AI_WRITES_USER_WRITES 

                _uiState.value = DrillState.Active(
                    card = card,
                    mode = assignedMode
                )
                
                if (assignedMode == DrillMode.AI_SPEAKS_USER_TYPES || assignedMode == DrillMode.AI_SPEAKS_USER_SPEAKS) {
                    audioController.speak(card.spanish)
                }
            } else {
                _uiState.value = DrillState.Finished
            }
        }
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
            val normalizedTarget = state.card.spanish.trim().lowercase()
            
            // Simple string matching, could be improved with Levenshtein distance
            val isCorrect = normalizedInput == normalizedTarget
            
            _uiState.value = state.copy(isRevealed = true, isCorrect = isCorrect)
            
            if (isCorrect) {
                audioController.speak("¡Correcto!")
            } else {
                audioController.speak("La respuesta correcta es ${state.card.spanish}")
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
                audioController.speak(sentence)
            }
        }
    }
}
