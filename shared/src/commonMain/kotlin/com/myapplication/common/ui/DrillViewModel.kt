package com.myapplication.common.ui

import com.myapplication.common.ai.GeminiService
import com.myapplication.common.audio.AudioController
import com.myapplication.common.data.ReviewState
import com.myapplication.common.data.SettingsRepository
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
    object Idle : DrillState()
    object Loading : DrillState()
    data class Active(
        val card: VocabCard,
        val mode: DrillMode,
        val config: DrillConfig,
        val userInput: String = "",
        val aiGeneratedText: String? = null,
        val isListening: Boolean = false,
        val isRevealed: Boolean = false,
        val isCorrect: Boolean = false,
        val isProcessingVoice: Boolean = false
    ) : DrillState()
    object Finished : DrillState()
}

class DrillViewModel(
    private val repository: VocabRepository,
    private val audioController: AudioController,
    private val geminiService: GeminiService,
    private val settingsRepository: SettingsRepository? = null
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _uiState = MutableStateFlow<DrillState>(DrillState.Idle)
    val uiState: StateFlow<DrillState> = _uiState

    private var currentReviewState: ReviewState? = null
    private var activeTagFilter: String? = null
    private var activeFilterSpec: TagFilterSpec = TagFilterSpec()
    private var activeConfig: DrillConfig = DrillConfig()

    fun setProgressionMode(mode: ProgressionMode) {
        activeConfig = activeConfig.copy(progressionMode = mode)
        settingsRepository?.saveProgressionMode(mode.name)
        val state = _uiState.value
        if (state is DrillState.Active) {
            _uiState.value = state.copy(config = activeConfig)
        }
    }

    fun startSession(filterSpec: TagFilterSpec, config: DrillConfig = DrillConfig()) {
        activeFilterSpec = filterSpec
        activeTagFilter = null
        activeConfig = config
        settingsRepository?.saveAppAction(config.appAction)
        settingsRepository?.saveAppLanguage(config.appLanguage)
        settingsRepository?.saveUserAction(config.userAction)
        settingsRepository?.saveUserLanguage(config.userLanguage)
        settingsRepository?.saveProgressionMode(config.progressionMode.name)
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
                _uiState.value = state.copy(isListening = false, isProcessingVoice = true)
            } else {
                _uiState.value = state.copy(isListening = true, userInput = "", isProcessingVoice = false)
                val langCode = if (state.config.isUserEnglish) "en" else "es"
                audioController.startListening(
                    lang = langCode,
                    onResult = { result ->
                        val currentState = _uiState.value
                        if (currentState is DrillState.Active) {
                            _uiState.value = currentState.copy(
                                userInput = result,
                                isListening = false,
                                isProcessingVoice = false
                            )
                            checkAnswer() // Auto-check on final result
                        }
                    },
                    onPartial = { partial ->
                        val currentState = _uiState.value
                        if (currentState is DrillState.Active) {
                            _uiState.value = currentState.copy(
                                userInput = partial,
                                isProcessingVoice = false
                            )
                        }
                    }
                )
            }
        }
    }

    private fun normalizeForComparison(text: String): String {
        return text.lowercase()
            .replace(Regex("[\\u00A0\\u200B\\uFEFF]"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun deriveMasculineSpanish(spanish: String): String {
        var result = spanish
            .replace(Regex("\\bellas\\b", RegexOption.IGNORE_CASE), "ellos")
            .replace(Regex("\\bnosotras\\b", RegexOption.IGNORE_CASE), "nosotros")
            .replace(Regex("\\bvosotras\\b", RegexOption.IGNORE_CASE), "vosotros")
            .replace(Regex("\\bla\\b", RegexOption.IGNORE_CASE), "el")
            .replace(Regex("\\buna\\b", RegexOption.IGNORE_CASE), "un")
            .replace(Regex("\\blas\\b", RegexOption.IGNORE_CASE), "los")
            .replace(Regex("\\bunas\\b", RegexOption.IGNORE_CASE), "unos")

        val exceptions = setOf("para", "que", "día", "dia", "mapa", "clima", "tema", "problema", "sistema", "idioma", "hasta", "contra")

        val words = result.split(" ").map { word ->
            val lower = word.lowercase()
            when {
                lower.endsWith("ora") && lower.length > 3 -> word.substring(0, word.length - 1)
                lower.endsWith("oras") && lower.length > 4 -> word.substring(0, word.length - 2) + "es"
                lower.endsWith("a") && lower.length > 2 && lower !in exceptions -> word.substring(0, word.length - 1) + "o"
                lower.endsWith("as") && lower.length > 3 && lower !in exceptions -> word.substring(0, word.length - 2) + "os"
                else -> word
            }
        }

        return words.joinToString(" ")
    }

    private fun removeSpanishSubjectPronoun(spanish: String): String {
        return spanish
            .replace(Regex("^\\b(yo|tú|tu|él|el|ella|usted|ud|nosotros|nosotras|vosotros|vosotras|ellos|ellas|ustedes|uds)\\b\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun getTargetCandidates(
        targetAnswer: String,
        isUserSpanish: Boolean,
        isAppSpeaking: Boolean = false
    ): Set<String> {
        val rawPieces = targetAnswer.split(Regex("[/,;]"))
        val candidates = mutableSetOf<String>()

        for (piece in rawPieces) {
            val trimmed = piece.trim()
            if (trimmed.isEmpty()) continue

            val normalizedRaw = normalizeForComparison(trimmed)
            if (normalizedRaw.isNotBlank()) {
                candidates.add(normalizedRaw)
            }

            if (!isUserSpanish) {
                val withoutParens = trimmed.replace(Regex("\\([^)]*\\)"), " ")
                val normalizedWithoutParens = normalizeForComparison(withoutParens)
                if (normalizedWithoutParens.isNotBlank()) {
                    candidates.add(normalizedWithoutParens)
                }

                if (normalizedWithoutParens.startsWith("to ")) {
                    val withoutTo = normalizedWithoutParens.substring(3).trim()
                    if (withoutTo.isNotBlank()) {
                        candidates.add(withoutTo)
                    }
                }
            } else {
                if (isAppSpeaking) {
                    val derivedMasculine = deriveMasculineSpanish(trimmed)
                    val normalizedMasculine = normalizeForComparison(derivedMasculine)
                    if (normalizedMasculine.isNotBlank()) {
                        candidates.add(normalizedMasculine)
                    }
                }

                val withoutPronoun = removeSpanishSubjectPronoun(trimmed)
                val normalizedWithoutPronoun = normalizeForComparison(withoutPronoun)
                if (normalizedWithoutPronoun.isNotBlank()) {
                    candidates.add(normalizedWithoutPronoun)
                }
            }
        }

        return candidates
    }

    fun checkAnswer() {
        val state = _uiState.value
        if (state is DrillState.Active && !state.isRevealed) {
            val targetAnswer = if (state.config.isUserSpanish) state.card.spanish else state.card.english
            val candidates = getTargetCandidates(
                targetAnswer = targetAnswer,
                isUserSpanish = state.config.isUserSpanish,
                isAppSpeaking = state.config.isAppSpeaking
            )

            val normalizedInput = normalizeForComparison(state.userInput)
            val normalizedInputNoParens = normalizeForComparison(state.userInput.replace(Regex("\\([^)]*\\)"), " "))
            val normalizedInputNoPronoun = if (state.config.isUserSpanish) {
                normalizeForComparison(removeSpanishSubjectPronoun(state.userInput))
            } else {
                ""
            }

            val isCorrect = (normalizedInput.isNotBlank() && candidates.contains(normalizedInput)) ||
                    (normalizedInputNoParens.isNotBlank() && candidates.contains(normalizedInputNoParens)) ||
                    (normalizedInputNoPronoun.isNotBlank() && candidates.contains(normalizedInputNoPronoun))

            _uiState.value = state.copy(isRevealed = true, isCorrect = isCorrect)

            if (isCorrect) {
                audioController.speak("Correct!", "en")
            } else {
                audioController.speak("Incorrect. The correct answer is $targetAnswer", "en")
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
