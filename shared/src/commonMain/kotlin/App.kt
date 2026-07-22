import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapplication.common.data.SettingsRepository
import com.myapplication.common.data.VocabCardDto
import com.myapplication.common.data.VocabRepository
import com.myapplication.common.ui.DrillConfig
import com.myapplication.common.ui.DrillMode
import com.myapplication.common.ui.DrillState
import com.myapplication.common.ui.DrillViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.resource

enum class Screen {
    HOME, DRILL, SETTINGS
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun App(
    drillViewModel: DrillViewModel,
    settingsRepository: SettingsRepository,
    vocabRepository: VocabRepository
) {
    MaterialTheme(
        colors = lightColors(
            primary = Color(0xFF6200EE),
            primaryVariant = Color(0xFF3700B3),
            secondary = Color(0xFF03DAC6)
        )
    ) {
        var currentScreen by remember { mutableStateOf(Screen.HOME) }
        var isDbLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            try {
                // Initialize database with JSON if empty
                val jsonBytes = resource("spanish_1a_vocab.json").readBytes()
                val jsonString = jsonBytes.decodeToString()
                val cards = Json.decodeFromString<List<VocabCardDto>>(jsonString)
                vocabRepository.populateIfEmpty(cards)
                isDbLoaded = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Spanish 1A Drill") },
                    actions = {
                        Button(onClick = { currentScreen = Screen.HOME }) { Text("Home") }
                        Button(onClick = { currentScreen = Screen.SETTINGS }) { Text("Settings") }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (!isDbLoaded) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    when (currentScreen) {
                        Screen.HOME -> HomeScreen(
                            onStartDrill = { tag, config ->
                                drillViewModel.startSession(tag, config)
                                currentScreen = Screen.DRILL
                            }
                        )
                        Screen.DRILL -> DrillScreen(drillViewModel)
                        Screen.SETTINGS -> SettingsScreen(settingsRepository)
                    }
                }
            }
        }
    }
}

@Composable
fun SentenceDropdown(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
            elevation = 0.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = selected,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = " ▼",
                    color = MaterialTheme.colors.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                ) {
                    Text(
                        text = option,
                        fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onStartDrill: (String?, DrillConfig) -> Unit) {
    var tagFilter by remember { mutableStateOf("") }

    var appAction by remember { mutableStateOf("writes") }
    var appLanguage by remember { mutableStateOf("English") }
    var userAction by remember { mutableStateOf("writes") }
    var userLanguage by remember { mutableStateOf("Spanish") }

    fun updateAppLanguage(newLang: String) {
        appLanguage = newLang
        userLanguage = if (newLang == "English") "Spanish" else "English"
    }

    fun updateUserLanguage(newLang: String) {
        userLanguage = newLang
        appLanguage = if (newLang == "English") "Spanish" else "English"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ready to Drill?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = tagFilter,
            onValueChange = { tagFilter = it },
            label = { Text("Filter by Tag (e.g. '1A', 'ch1', leave empty for all)") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            elevation = 4.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Configure Drill Sentence",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("App ", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    SentenceDropdown(
                        options = listOf("speaks", "writes"),
                        selected = appAction,
                        onSelect = { appAction = it }
                    )
                    Text(" in ", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    SentenceDropdown(
                        options = listOf("English", "Spanish"),
                        selected = appLanguage,
                        onSelect = { updateAppLanguage(it) }
                    )
                    Text(".", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("User ", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    SentenceDropdown(
                        options = listOf("speaks", "writes"),
                        selected = userAction,
                        onSelect = { userAction = it }
                    )
                    Text(" in ", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    SentenceDropdown(
                        options = listOf("English", "Spanish"),
                        selected = userLanguage,
                        onSelect = { updateUserLanguage(it) }
                    )
                    Text(".", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(16.dp))

                val promptDesc = if (appAction == "speaks") "AI speaks $appLanguage audio" else "AI displays $appLanguage text"
                val userDesc = if (userAction == "speaks") "you speak $userLanguage into mic" else "you type $userLanguage"

                Surface(
                    color = MaterialTheme.colors.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 $promptDesc ➔ $userDesc",
                        modifier = Modifier.padding(10.dp),
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val config = DrillConfig(
                    appAction = appAction,
                    appLanguage = appLanguage,
                    userAction = userAction,
                    userLanguage = userLanguage
                )
                onStartDrill(tagFilter.takeIf { it.isNotBlank() }, config)
            },
            modifier = Modifier.fillMaxWidth(0.5f).height(50.dp)
        ) {
            Text("Start Drilling", fontSize = 18.sp)
        }
    }
}

@Composable
fun SettingsScreen(settingsRepository: SettingsRepository) {
    var apiKey by remember { mutableStateOf(settingsRepository.getApiKey() ?: "") }
    var savedMessage by remember { mutableStateOf("") }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        Button(onClick = { uriHandler.openUri("https://aistudio.google.com/app/apikey") }) {
            Text("Get Gemini API Key")
        }
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            settingsRepository.saveApiKey(apiKey)
            savedMessage = "Saved!"
        }) {
            Text("Save")
        }
        if (savedMessage.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(savedMessage, color = Color.Green)
        }
    }
}

@Composable
fun DrillScreen(viewModel: DrillViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val s = state) {
            is DrillState.Loading -> CircularProgressIndicator()
            is DrillState.Finished -> {
                Text("You've finished all cards!", fontSize = 24.sp)
            }
            is DrillState.Active -> {
                ActiveDrillView(s, viewModel)
            }
        }
    }
}

@Composable
fun ActiveDrillView(state: DrillState.Active, viewModel: DrillViewModel) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(state.isRevealed) {
        if (state.isRevealed) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            if (!state.isRevealed) viewModel.checkAnswer()
                            true
                        }
                        Key.Zero, Key.NumPad0 -> {
                            if (state.isRevealed) viewModel.submitGradeAndNext(0)
                            true
                        }
                        Key.One, Key.NumPad1 -> {
                            if (state.isRevealed) viewModel.submitGradeAndNext(1)
                            true
                        }
                        Key.Two, Key.NumPad2 -> {
                            if (state.isRevealed) viewModel.submitGradeAndNext(2)
                            true
                        }
                        Key.Three, Key.NumPad3 -> {
                            if (state.isRevealed) viewModel.submitGradeAndNext(3)
                            true
                        }
                        Key.Four, Key.NumPad4 -> {
                            if (state.isRevealed) viewModel.submitGradeAndNext(4)
                            true
                        }
                        Key.Five, Key.NumPad5 -> {
                            if (state.isRevealed) viewModel.submitGradeAndNext(5)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val config = state.config
        val promptLangName = if (config.isAppEnglish) "English" else "Spanish"
        val targetLangName = if (config.isUserSpanish) "Spanish" else "English"

        Text("App $promptLangName (${config.appAction}) ➔ User $targetLangName (${config.userAction})", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        val promptText = if (config.isAppEnglish) state.card.english else state.card.spanish

        if (config.isAppWriting) {
            Text("Translate to $targetLangName:", fontSize = 18.sp)
            Text(promptText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        } else {
            Text("Listen to $promptLangName prompt:", fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val langCode = if (config.isAppEnglish) "en" else "es"
                viewModel.replayAudio(promptText, langCode)
            }) {
                Text("🔊 Replay Audio")
            }
        }

        Spacer(Modifier.height(32.dp))

        var textFieldValue by remember(state.card.id) { 
            mutableStateOf(TextFieldValue(
                text = state.userInput,
                selection = TextRange(state.userInput.length)
            )) 
        }

        if (config.isUserWriting) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { 
                    if (it.text != state.userInput) {
                        viewModel.onUserInputChanged(it.text)
                    }
                    textFieldValue = it
                },
                label = { Text("Your answer ($targetLangName)") },
                enabled = !state.isRevealed,
                modifier = Modifier.fillMaxWidth(0.8f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.checkAnswer() })
            )
            if (config.isUserSpanish && !state.isRevealed) {
                Spacer(Modifier.height(8.dp))
                SpanishAccentRow(
                    onInsertChar = { char ->
                        val selection = textFieldValue.selection
                        val text = textFieldValue.text
                        val start = selection.min.coerceIn(0, text.length)
                        val end = selection.max.coerceIn(0, text.length)
                        val newText = text.substring(0, start) + char + text.substring(end)
                        val newSelectionIndex = start + char.length
                        val newValue = TextFieldValue(
                            text = newText,
                            selection = TextRange(newSelectionIndex)
                        )
                        textFieldValue = newValue
                        viewModel.onUserInputChanged(newText)
                    }
                )
            }
        } else {
            // Voice mode
            Text("You said: ${state.userInput}", fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.toggleListening() },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (state.isListening) Color.Red else MaterialTheme.colors.primary
                ),
                enabled = !state.isRevealed
            ) {
                Text(if (state.isListening) "Stop Listening" else "Start Speaking")
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!state.isRevealed) {
            Button(onClick = { viewModel.checkAnswer() }) {
                Text("Check Answer")
            }
        } else {
            Text(if (state.isCorrect) "¡Correcto!" else "Incorrecto", 
                color = if (state.isCorrect) Color.Green else Color.Red,
                fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            val expectedAnswer = if (config.isUserSpanish) state.card.spanish else state.card.english
            Text("Correct answer: $expectedAnswer", fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            
            // Gemini Actions
            Button(onClick = { viewModel.generateContextSentence() }) {
                Text("AI: Generate Context Sentence")
            }
            if (state.aiGeneratedText != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.aiGeneratedText, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }

            Spacer(Modifier.height(24.dp))
            Text("Rate your memory:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { grade ->
                    Button(onClick = { viewModel.submitGradeAndNext(grade) }) {
                        Text("$grade")
                    }
                }
            }
            Button(onClick = { viewModel.submitGradeAndNext(0) }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)) {
                Text("Blackout (0)")
            }
        }
    }
}

@Composable
fun SpanishAccentRow(
    onInsertChar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isShift by remember { mutableStateOf(false) }
    val lowercaseChars = listOf("á", "é", "í", "ó", "ú", "ñ", "ü", "¿", "¡")
    val uppercaseChars = listOf("Á", "É", "Í", "Ó", "Ú", "Ñ", "Ü", "¿", "¡")
    val currentChars = if (isShift) uppercaseChars else lowercaseChars

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { isShift = !isShift },
            modifier = Modifier
                .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                .height(34.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = if (isShift) MaterialTheme.colors.primary.copy(alpha = 0.15f) else Color.Unspecified
            ),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text("⇧", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        currentChars.forEach { char ->
            OutlinedButton(
                onClick = {
                    onInsertChar(char)
                    if (isShift) isShift = false
                },
                modifier = Modifier
                    .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(char, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}