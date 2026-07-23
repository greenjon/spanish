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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.myapplication.common.data.SettingsRepository
import com.myapplication.common.data.TagCategories
import com.myapplication.common.data.TagCategory
import com.myapplication.common.data.TagFilterSpec
import com.myapplication.common.data.TagOption
import com.myapplication.common.data.VocabCardDto
import com.myapplication.common.data.VocabRepository
import com.myapplication.common.ui.DrillConfig
import com.myapplication.common.ui.DrillMode
import com.myapplication.common.ui.DrillState
import com.myapplication.common.ui.DrillViewModel
import com.myapplication.common.ui.ProgressionMode
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.resource

enum class Screen {
    HOME, SETTINGS
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
                            drillViewModel = drillViewModel,
                            vocabRepository = vocabRepository
                        )
                        Screen.SETTINGS -> SettingsScreen(settingsRepository)
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressionToggle(
    selectedMode: ProgressionMode,
    onModeSelected: (ProgressionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colors.primary.copy(alpha = 0.08f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp)
        ) {
            ProgressionMode.values().forEach { mode ->
                val isSelected = mode == selectedMode
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colors.primary else Color.Transparent,
                    elevation = if (isSelected) 2.dp else 0.dp,
                    modifier = Modifier.clickable { onModeSelected(mode) }
                ) {
                    Text(
                        text = when (mode) {
                            ProgressionMode.LINEAR -> "Linear"
                            ProgressionMode.RANDOM -> "Random"
                        },
                        color = if (isSelected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
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
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 6.dp,
    verticalGap: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val horizontalGapPx = horizontalGap.roundToPx()
        val verticalGapPx = verticalGap.roundToPx()

        val rows = mutableListOf<List<Placeable>>()
        val rowHeights = mutableListOf<Int>()
        var currentRow = mutableListOf<Placeable>()
        var currentRowWidth = 0
        var currentRowHeight = 0

        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }

        for (placeable in placeables) {
            val neededWidth = if (currentRow.isEmpty()) {
                placeable.width
            } else {
                currentRowWidth + horizontalGapPx + placeable.width
            }

            if (neededWidth <= constraints.maxWidth || currentRow.isEmpty()) {
                currentRow.add(placeable)
                currentRowWidth = neededWidth
                currentRowHeight = maxOf(currentRowHeight, placeable.height)
            } else {
                rows.add(currentRow)
                rowHeights.add(currentRowHeight)
                currentRow = mutableListOf(placeable)
                currentRowWidth = placeable.width
                currentRowHeight = placeable.height
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowHeights.add(currentRowHeight)
        }

        val totalHeight = if (rows.isEmpty()) 0 else rowHeights.sum() + (rows.size - 1) * verticalGapPx

        layout(width = constraints.maxWidth, height = totalHeight) {
            var y = 0
            for (i in rows.indices) {
                val row = rows[i]
                val height = rowHeights[i]
                var x = 0
                for (placeable in row) {
                    val yOffset = (height - placeable.height) / 2
                    placeable.placeRelative(x = x, y = y + yOffset)
                    x += placeable.width + horizontalGapPx
                }
                y += height + verticalGapPx
            }
        }
    }
}

@Composable
fun TagChip(
    label: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
        elevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier.clickable { onToggle() }
    ) {
        Text(
            text = label,
            color = if (isSelected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun CategoryAccordion(
    title: String,
    selectedCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        elevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (selectedCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                "$selectedCount",
                                color = MaterialTheme.colors.onPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(if (isExpanded) "▲" else "▼", fontSize = 12.sp, color = Color.Gray)
            }
            if (isExpanded) {
                Divider(color = Color.LightGray.copy(alpha = 0.4f))
                Box(modifier = Modifier.padding(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun FacetedTagFilterCard(
    vocabRepository: VocabRepository,
    filterSpec: TagFilterSpec,
    onFilterSpecChange: (TagFilterSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    var matchingCardCount by remember { mutableStateOf(805) }
    var expandedCategories by remember { mutableStateOf(setOf(TagCategories.chapters.title, TagCategories.partsOfSpeech.title)) }
    var isFilterExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(filterSpec) {
        matchingCardCount = vocabRepository.getMatchingCardCount(filterSpec)
    }

    Card(
        modifier = modifier.fillMaxWidth(0.95f),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isFilterExpanded = !isFilterExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Vocabulary Filter",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isFilterExpanded) "▲" else "▼",
                            fontSize = 14.sp,
                            color = MaterialTheme.colors.primary
                        )
                    }
                    Text(
                        text = if (filterSpec.isEmpty) "All categories active" else "${filterSpec.totalSelectedCount} filter(s) applied",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (matchingCardCount > 0) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color(0xFFFFEBEE),
                    elevation = 0.dp
                ) {
                    Text(
                        text = "✨ $matchingCardCount Cards",
                        color = if (matchingCardCount > 0) MaterialTheme.colors.primary else Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (isFilterExpanded) {
                Spacer(Modifier.height(12.dp))

                if (filterSpec.totalSelectedCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onFilterSpecChange(TagFilterSpec()) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear All Filters", fontSize = 12.sp, color = MaterialTheme.colors.primary)
                        }
                    }
                }

                // Categories
                TagCategories.allCategories.forEach { category ->
                    val selectedSet = when (category.title) {
                        TagCategories.chapters.title -> filterSpec.chapters
                        TagCategories.partsOfSpeech.title -> filterSpec.partsOfSpeech
                        TagCategories.verbTypes.title -> filterSpec.verbTypes
                        TagCategories.topics.title -> filterSpec.topics
                        TagCategories.grammarTags.title -> filterSpec.grammarTags
                        else -> emptySet()
                    }

                    val isExpanded = expandedCategories.contains(category.title)

                    CategoryAccordion(
                        title = category.title,
                        selectedCount = selectedSet.size,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedCategories = if (isExpanded) {
                                expandedCategories - category.title
                            } else {
                                expandedCategories + category.title
                            }
                        }
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalGap = 6.dp,
                            verticalGap = 6.dp
                        ) {
                            category.options.forEach { option ->
                                val isSelected = selectedSet.contains(option.tag)
                                TagChip(
                                    label = option.label,
                                    isSelected = isSelected,
                                    onToggle = {
                                        val newSet = if (isSelected) selectedSet - option.tag else selectedSet + option.tag
                                        val updatedSpec = when (category.title) {
                                            TagCategories.chapters.title -> filterSpec.copy(chapters = newSet)
                                            TagCategories.partsOfSpeech.title -> filterSpec.copy(partsOfSpeech = newSet)
                                            TagCategories.verbTypes.title -> filterSpec.copy(verbTypes = newSet)
                                            TagCategories.topics.title -> filterSpec.copy(topics = newSet)
                                            TagCategories.grammarTags.title -> filterSpec.copy(grammarTags = newSet)
                                            else -> filterSpec
                                        }
                                        onFilterSpecChange(updatedSpec)
                                    }
                                )
                            }
                        }
                    }
                }

                if (matchingCardCount == 0) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ No vocabulary cards match all your selected filters. Try removing some filters.",
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    drillViewModel: DrillViewModel,
    vocabRepository: VocabRepository
) {
    var filterSpec by remember { mutableStateOf(TagFilterSpec()) }
    var matchingCardCount by remember { mutableStateOf(805) }

    var appAction by remember { mutableStateOf("writes") }
    var appLanguage by remember { mutableStateOf("English") }
    var userAction by remember { mutableStateOf("writes") }
    var userLanguage by remember { mutableStateOf("Spanish") }
    var progressionMode by remember { mutableStateOf(ProgressionMode.RANDOM) }

    val drillState by drillViewModel.uiState.collectAsState()

    LaunchedEffect(filterSpec) {
        matchingCardCount = vocabRepository.getMatchingCardCount(filterSpec)
    }

    fun updateAppLanguage(newLang: String) {
        appLanguage = newLang
        userLanguage = if (newLang == "English") "Spanish" else "English"
    }

    fun updateUserLanguage(newLang: String) {
        userLanguage = newLang
        appLanguage = if (newLang == "English") "Spanish" else "English"
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Persistent top filter bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            FacetedTagFilterCard(
                vocabRepository = vocabRepository,
                filterSpec = filterSpec,
                onFilterSpecChange = { filterSpec = it }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Ready to Drill?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(0.95f),
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalGap = 4.dp,
                        verticalGap = 8.dp
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
                        Text(" and ", fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val config = DrillConfig(
                                    appAction = appAction,
                                    appLanguage = appLanguage,
                                    userAction = userAction,
                                    userLanguage = userLanguage,
                                    progressionMode = progressionMode
                                )
                                drillViewModel.startSession(filterSpec, config)
                            },
                            enabled = matchingCardCount > 0,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Go", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("Order: ", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        ProgressionToggle(
                            selectedMode = progressionMode,
                            onModeSelected = { progressionMode = it }
                        )
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
                            text = "💡 $promptDesc ➔ $userDesc (${progressionMode.name.lowercase()} order)",
                            modifier = Modifier.padding(10.dp),
                            fontSize = 13.sp,
                            color = Color.DarkGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Inline Drill Section
            if (drillState !is DrillState.Idle) {
                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    elevation = 4.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (val s = drillState) {
                            is DrillState.Loading -> CircularProgressIndicator()
                            is DrillState.Finished -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🎉 You've finished all matching cards!", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = {
                                        val config = DrillConfig(
                                            appAction = appAction,
                                            appLanguage = appLanguage,
                                            userAction = userAction,
                                            userLanguage = userLanguage,
                                            progressionMode = progressionMode
                                        )
                                        drillViewModel.startSession(filterSpec, config)
                                    }) {
                                        Text("Drill Again")
                                    }
                                }
                            }
                            is DrillState.Active -> {
                                ActiveDrillView(s, drillViewModel)
                            }
                            is DrillState.Idle -> {}
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
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
            is DrillState.Idle -> {}
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
    val inputFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(state.card.id, state.isRevealed) {
        if (!state.isRevealed && state.config.isUserWriting) {
            inputFocusRequester.requestFocus()
        } else if (state.isRevealed) {
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

        Row(
            modifier = Modifier.fillMaxWidth(0.9f).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("App $promptLangName (${config.appAction}) ➔ User $targetLangName (${config.userAction})", fontSize = 13.sp, color = Color.Gray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Order: ", fontSize = 13.sp, color = Color.Gray)
                ProgressionToggle(
                    selectedMode = state.config.progressionMode,
                    onModeSelected = { viewModel.setProgressionMode(it) }
                )
            }
        }
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
                modifier = Modifier.fillMaxWidth(0.8f).focusRequester(inputFocusRequester),
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
                        inputFocusRequester.requestFocus()
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