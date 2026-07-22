import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import com.myapplication.common.data.SettingsRepository
import com.myapplication.common.data.VocabCardDto
import com.myapplication.common.data.VocabRepository
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
        val scope = rememberCoroutineScope()
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
                            onStartDrill = { tag, mode ->
                                drillViewModel.startSession(tag, mode)
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
fun HomeScreen(onStartDrill: (String?, DrillMode) -> Unit) {
    var tagFilter by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(DrillMode.AI_WRITES_USER_WRITES) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ready to Drill?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = tagFilter,
            onValueChange = { tagFilter = it },
            label = { Text("Filter by Tag (e.g. '1A', 'ch1', leave empty for all)") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(Modifier.height(24.dp))
        Text("Select Drill Mode:")
        val modes = listOf(
            DrillMode.AI_WRITES_USER_WRITES to "AI writes in English, you type in Spanish",
            DrillMode.AI_SPEAKS_USER_TYPES to "AI speaks in Spanish, you type in Spanish",
            DrillMode.AI_SPEAKS_USER_SPEAKS to "AI speaks in Spanish, you speak in Spanish",
            DrillMode.AI_WRITES_USER_SPEAKS to "AI writes in English, you speak in Spanish"
        )
        modes.forEach { (mode, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(0.8f)) {
                RadioButton(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode }
                )
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onStartDrill(tagFilter.takeIf { it.isNotBlank() }, selectedMode) },
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
        Text("Mode: ${state.mode.name}", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))
    
    // Display English if AI writes
    if (state.mode == DrillMode.AI_WRITES_USER_SPEAKS || state.mode == DrillMode.AI_WRITES_USER_WRITES) {
        Text("Translate to Spanish:", fontSize = 18.sp)
        Text(state.card.english, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    } else {
        Text("Listen and provide the Spanish text:", fontSize = 18.sp)
    }

    Spacer(Modifier.height(32.dp))

    var textFieldValue by remember(state.userInput) { 
        mutableStateOf(TextFieldValue(
            text = state.userInput,
            selection = TextRange(state.userInput.length)
        )) 
    }

    if (state.mode == DrillMode.AI_WRITES_USER_WRITES || state.mode == DrillMode.AI_SPEAKS_USER_TYPES) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { 
                // Only update ViewModel if text actually changed (to prevent infinite loops)
                if (it.text != state.userInput) {
                    viewModel.onUserInputChanged(it.text)
                }
                // Always update local state to preserve cursor/selection
                textFieldValue = it
            },
            label = { Text("Your answer (Spanish)") },
            enabled = !state.isRevealed,
            modifier = Modifier.fillMaxWidth(0.8f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.checkAnswer() })
        )
    } else {
        // Voice modes
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
        Text("Correct answer: ${state.card.spanish}", fontSize = 18.sp)
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